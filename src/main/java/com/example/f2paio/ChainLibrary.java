package com.example.f2paio;
import java.nio.file.*;
import java.util.*;
/** Bounded evolutionary plan library: test variants, promote on observed runs, keep rollback parent. */
public final class ChainLibrary {
    public static final class Entry {
        public ChainPlan plan;
        public int success,failure;
        public long historicalSuccess,historicalFailure;
        public long verified,elapsedMs,xp,cash,lastAt;
        public String status="PROBATION",lastFailure="";
        double utility() { return (success+1.0)/(success+failure+2.0)
            + .2*Math.min(1,verified*60_000.0/Math.max(1,elapsedMs)/6); }
    }
    private final Path file;
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    public ChainLibrary(Path file) {
        this.file=file;
        if(Files.exists(file)) try {
            Entry[] saved=FilesStore.read(file,Entry[].class);
            if(saved!=null) for(Entry e:saved) if(e!=null && ChainPlan.valid(e.plan) && e.success>=0 && e.failure>=0 && java.util.Set.of("PROBATION","PROMOTED","QUARANTINED").contains(e.status)) entries.put(e.plan.id,e);
        } catch(Exception ex) { DebugLog.event("CHAIN_LOAD","Invalid chain history ignored"); }
    }
    public synchronized boolean propose(ChainPlan plan) {
        if(!ChainPlan.valid(plan) || entries.containsKey(plan.id)) return false;
        if(entries.values().stream().filter(e->e.plan.goal.equals(plan.goal)).count()>=12) return false;
        Entry entry=new Entry(); entry.plan=plan.copy(); entries.put(plan.id,entry); save();
        DebugLog.event("CHAIN_PROPOSED",plan.id+" goal="+plan.goal+" origin="+plan.origin); return true;
    }
    public synchronized ChainPlan choose(Method goal,Random random,long now) {
        return choose(goal,random,now,List.of(),p->true);
    }
    synchronized ChainPlan choose(Method goal,Random random,long now,List<ChainPlan> recipes,
        java.util.function.Predicate<ChainPlan> eligible) {
        return choose(goal,random,now,recipes,eligible,null);
    }

    synchronized ChainPlan choose(Method goal,Random random,long now,List<ChainPlan> recipes,
        java.util.function.Predicate<ChainPlan> eligible,RouteForecast forecast) {
        String seed="chain_"+goal.id;
        if(!entries.containsKey(seed)) propose(ChainPlan.seed(goal));
        for (ChainPlan recipe : recipes) propose(recipe);
        List<Entry> choices=new ArrayList<>();
        for(Entry e:entries.values()) if(e.plan.goal.equals(goal.id) && ChainPlan.valid(e.plan) && eligible.test(e.plan)) {
            if("QUARANTINED".equals(e.status)) {
                if(!"seed_manual".equals(e.plan.origin) && now-e.lastAt>20*Tuning.MINUTE) {
                    e.historicalSuccess+=e.success;e.historicalFailure+=e.failure;e.success=0;e.failure=0;
                    e.status="PROBATION";e.lastAt=now;save();
                }
                else continue;
            }
            choices.add(e);
        }
        if(choices.isEmpty()) return null;
        if (forecast!=null) {
            var ranked=choices.stream().sorted(Comparator
                .comparingDouble((Entry entry) -> forecast.estimate(entry.plan).score
                    + 60.0*entry.failure/(entry.success+entry.failure+2)
                    - Math.min(12,entry.success*3.0))
                .thenComparing(entry -> entry.plan.id)).collect(java.util.stream.Collectors.toList());
            Entry selected=ranked.get(0);
            DebugLog.event("ROUTE_COMPARE",selected.plan.id+" "+forecast.estimate(selected.plan).explanation
                +" | compared "+ranked.stream().limit(4).map(entry -> entry.plan.id+"="
                    + Math.round(forecast.estimate(entry.plan).score)).collect(java.util.stream.Collectors.joining(", ")));
            return selected.plan.copy();
        }
        List<Entry> proven=new ArrayList<>(),trial=new ArrayList<>();
        for(Entry e:choices) (e.status.equals("PROMOTED")?proven:trial).add(e);
        Entry selected;
        if(!trial.isEmpty() && (proven.isEmpty() || random.nextInt(5)==0))
            selected=trial.stream().min(Comparator.comparingInt(e->e.success+e.failure)).orElseThrow();
        else selected=proven.stream().max(Comparator.comparingDouble(Entry::utility)).orElseThrow();
        DebugLog.event("CHAIN_SELECTED",selected.plan.id+" status="+selected.status+" utility="+selected.utility());
        return selected.plan.copy();
    }
    public synchronized void observe(ChainPlan plan,boolean ok,int verified,long elapsed,long xp,long cash,String reason,Random random,long now) {
        Entry e=entries.get(plan.id); if(e==null) return;
        if(ok && verified>0) e.success++; else e.failure++;
        e.verified+=Math.max(0,verified); e.elapsedMs+=Math.max(1,elapsed);
        e.xp+=Math.max(0,xp); e.cash+=Math.max(0,cash); e.lastAt=now; e.lastFailure=ok?"":reason;
        if(e.failure>=2) e.status="QUARANTINED";
        else if(e.success>=3) e.status="PROMOTED";
        save();
        DebugLog.event("CHAIN_OUTCOME",plan.id+" status="+e.status+" successes="+e.success+" failures="+e.failure+" verified="+verified+" reason="+reason);
        if(e.status.equals("PROMOTED") && plan.generation<3) evolve(plan,random);
    }
    private void evolve(ChainPlan parent,Random random) {
        if(entries.values().stream().anyMatch(e->parent.id.equals(e.plan.parent))) return;
        ChainPlan child=parent.copy(); child.parent=parent.id; child.generation=parent.generation+1;
        child.origin=parent.origin.equals("recipe-dependency") ? "recipe-dependency" : "evolved";
        for(ChainPlan.Step step:child.steps) if(step.op==ChainPlan.Op.ACT)
            step.count=Math.max(4,Math.min(12,step.count+(random.nextBoolean()?4:-4)));
        child.verifySeconds=Math.max(20,Math.min(45,parent.verifySeconds+(random.nextBoolean()?5:-5)));
        child.id=ChainPlan.contentId(child);
        if(ChainPlan.valid(child)) propose(child);
    }
    public synchronized Entry evidence(String id) { return entries.get(id); }
    /** Compact learning state for the growth agents. */
    public synchronized List<String> details() {
        List<String> out=new ArrayList<>();
        for(Entry e:entries.values()) out.add(e.plan.id+" status="+e.status+" ok="+e.success
            +" fail="+e.failure+" verified="+e.verified+" reason="+e.lastFailure);
        return out;
    }
    public synchronized List<String> knownIds() { return new ArrayList<>(entries.keySet()); }
    public synchronized String summary() {
        long promoted=entries.values().stream().filter(e->"PROMOTED".equals(e.status)).count();
        long quarantined=entries.values().stream().filter(e->"QUARANTINED".equals(e.status)).count();
        return "Learning: "+promoted+" proven chains, "+(entries.size()-promoted-quarantined)+" on probation, "+quarantined+" quarantined";
    }
    private void save() {
        try { FilesStore.write(file,entries.values()); } catch(Exception ex) { DebugLog.error(ex); }
    }
}
