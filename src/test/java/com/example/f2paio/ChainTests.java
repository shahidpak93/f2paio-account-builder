package com.example.f2paio;
import java.nio.file.*;
import java.util.*;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
public final class ChainTests {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static GameState state(int xp,int logs) {
        var levels=new EnumMap<Skill,Integer>(Skill.class); var exp=new EnumMap<Skill,Integer>(Skill.class);
        for(Skill skill:Skill.values()) if(skill!=Skill.OVERALL) { levels.put(skill,1); exp.put(skill,0); }
        exp.put(Skill.WOODCUTTING,xp);
        GameState s=new GameState("replay",new WorldPoint(3192,3223,0),xp,1+logs,false,false,100,false,levels,exp,Map.of(),1000);
        var items=new ArrayList<ResourceItem>(); items.add(new ResourceItem(1351,"Bronze axe",1));
        for(int n=0;n<logs;n++) items.add(new ResourceItem(1511,"Logs",1)); s.items=List.copyOf(items); return s;
    }
    private static final class Replay implements ChainRunner.Backend {
        int prepared,travelled,acted,banked,rejected;
        boolean supplies=true,explode; int limit=Integer.MAX_VALUE;
        java.util.List<String> order=new ArrayList<>();
        public boolean prepare(Method m,GameState s) { prepared++; return supplies; }
        public boolean travel(Method m,GameState s) { travelled++; return true; }
        public boolean act(Method m,GameState s) { if(acted>=limit) return false; acted++; order.add(m.id); if(explode) throw new IllegalStateException("fixture failure"); return true; }
        public boolean bank(Method m,GameState s) { banked++; return true; }
        public boolean sell(Method m,GameState s) { return true; }
        public void rejected(Method m,long now) { rejected++; }
    }
    static void run() throws Exception {
        var debugLogger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(DebugLog.class);
        var oldLevel=debugLogger.getLevel(); debugLogger.setLevel(ch.qos.logback.classic.Level.OFF);
        var transitions=new SessionTransitions();
        check(transitions.waiting(net.runelite.api.GameState.HOPPING,100),"World hopping gets a bounded grace period");
        check(!transitions.waiting(net.runelite.api.GameState.HOPPING,30100),"Stalled connection cannot wait forever");
        check(!transitions.waiting(net.runelite.api.GameState.LOGIN_SCREEN,30200),"Actual login screen is not treated as ongoing work");
        check(transitions.waiting(net.runelite.api.GameState.LOADING,30300),"A later distinct loading transition gets fresh grace");
        Method method=Methods.get("trees"); ChainPlan plan=ChainPlan.seed(method); plan.steps.get(2).count=1;
        check(ChainPlan.valid(plan),"Seed chain schema is executable");
        var draft=FilesStore.JSON.fromJson("{\"goal\":\"cook_shrimp\",\"methods\":[\"shrimp\",\"cook_shrimp\"],\"batch\":4}",com.google.gson.JsonObject.class);
        ChainPlan compiled=ChainDraft.compile(draft);
        check(ChainPlan.valid(compiled) && compiled.steps.size()==9,"Compact AI sequence compiles with inherited prerequisites, verifiers and settlement");
        draft.addProperty("goal","unknown");
        check(ChainDraft.compile(draft)==null,"Compact drafts cannot invent an executor capability");
        ChainPlan bad=plan.copy(); bad.steps.remove(3);
        check(!ChainPlan.valid(bad),"Action without effect verifier rejected");
        bad=plan.copy(); bad.steps.get(0).method="unknown";
        check(!ChainPlan.valid(bad),"Unknown capabilities rejected");
        bad=plan.copy(); bad.steps.get(2).count=1000;
        check(!ChainPlan.valid(bad),"Unbounded actions rejected");
        bad=plan.copy(); bad.steps.get(2).method="copper";
        check(!ChainPlan.valid(bad),"Action cannot borrow another method's preconditions");
        Replay backend=new Replay(); var runner=new ChainRunner(plan,backend,0); GameState initial=state(0,0);
        runner.step(initial,1); runner.step(initial,2); runner.step(initial,3);
        check(backend.prepared>=1 && backend.travelled==1 && backend.acted==1,"Prepare/travel/action execute in order");
        initial.animating=true; runner.step(initial,4);
        check(runner.verified()==0,"Animation never counts as a verified chain effect");
        runner.step(state(25,1),5);
        check(runner.verified()==1,"Actual XP/item growth verifies action");
        check(runner.step(state(25,1),6)==ChainRunner.Result.DONE && backend.banked==1,"Postcondition completes banked chain");
        runner.step(initial,7); check(backend.acted==1,"Finished chain cannot dispatch more actions");
        Replay missing=new Replay(); missing.supplies=false; var blocked=new ChainRunner(plan,missing,0);
        blocked.step(initial,1); blocked.step(initial,2);
        check(missing.acted==0 && missing.travelled==0,"No action or travel before supplies are ready");
        check(blocked.step(initial,241000)==ChainRunner.Result.FAILED,"Unresolvable preparation has a deadline");
        Replay noEffect=new Replay(); var timeout=new ChainRunner(plan,noEffect,0);
        timeout.step(initial,1); timeout.step(initial,2); timeout.step(initial,3);
        check(timeout.step(initial,30004)==ChainRunner.Result.FAILED && noEffect.rejected==1,"Dispatched action without effect fails and rejects target");
        var paused=new ChainRunner(plan,new Replay(),0);
        paused.step(initial,1); paused.step(initial,2); paused.step(initial,3); paused.pause(4);
        check(paused.step(initial,600004)==ChainRunner.Result.RUNNING,"Manual pause does not create a false action timeout");
        paused.reject("You can't reach that");
        check(paused.step(initial,600005)==ChainRunner.Result.FAILED,"Game rejection fails a pending verified action");
        Replay broken=new Replay(); broken.explode=true; var exception=new ChainRunner(plan,broken,0);
        exception.step(initial,1); exception.step(initial,2);
        check(exception.step(initial,3)==ChainRunner.Result.FAILED && exception.failure().contains("BACKEND_EXCEPTION"),"Backend exception becomes a failed trial instead of an endless retry loop");
        ChainPlan partial=plan.copy(); partial.steps.get(2).count=8;
        Replay sparse=new Replay(); sparse.limit=1; var partialRunner=new ChainRunner(partial,sparse,0);
        partialRunner.step(initial,1); partialRunner.step(initial,2); partialRunner.step(initial,3);
        partialRunner.step(state(25,1),4); partialRunner.step(state(25,1),5);
        partialRunner.step(state(25,1),30006);
        check(partialRunner.step(state(25,1),30007)==ChainRunner.Result.DONE && partialRunner.verified()==1,
            "Sparse drops settle a verified partial batch instead of discarding successful progress");
        ChainPlan multi=plan.copy(); multi.id="chain_two_capabilities";
        var copperBlock=ChainPlan.seed(Methods.get("copper")); copperBlock.steps.get(2).count=1;
        multi.steps.addAll(4,copperBlock.steps.subList(0,4));
        check(ChainPlan.valid(multi),"A typed chain can compose multiple registered capabilities");
        Replay sequence=new Replay(); var combined=new ChainRunner(multi,sequence,0);
        for(int n=1;n<=3;n++) combined.step(state(0,0),n);
        combined.step(state(25,1),4);
        for(int n=5;n<=7;n++) combined.step(state(25,1),n);
        GameState wood=state(25,1); var xpMap=new EnumMap<Skill,Integer>(wood.experience); xpMap.put(Skill.MINING,17);
        GameState both=new GameState("replay",wood.location,42,3,false,false,100,false,wood.levels,xpMap,Map.of(),1000);
        both.items=List.of(new ResourceItem(1511,"Logs",1),new ResourceItem(436,"Copper ore",1));
        combined.step(both,8);
        check(combined.step(both,9)==ChainRunner.Result.DONE && combined.verified()==2 && sequence.order.equals(List.of("trees","copper")),
            "Two capabilities execute in order with independent effect verifications");
        var missingSecondEffect = new ChainRunner(multi,new Replay(),0);
        for(int n=1;n<=3;n++) missingSecondEffect.step(initial,n);
        missingSecondEffect.step(wood,4);
        for(int n=5;n<=7;n++) missingSecondEffect.step(wood,n);
        check(missingSecondEffect.step(wood,30_008)==ChainRunner.Result.FAILED
            && missingSecondEffect.failure().equals("EFFECT_TIMEOUT: copper"),
            "Evidence from a successful first block cannot promote a failed downstream block");
        ChainPlan material=new ChainPlan();
        material.goal="trees";
        material.addBlock(method,1,new ItemNeed(1511,"Logs",2));
        material.id=ChainPlan.contentId(material);
        var materialRunner=new ChainRunner(material,new Replay(),0);
        materialRunner.step(initial,1); materialRunner.step(initial,2); materialRunner.step(initial,3);
        check(materialRunner.step(wood,4)==ChainRunner.Result.FAILED
            && materialRunner.failure().startsWith("MATERIAL_SHORTFALL"),
            "XP and one log cannot satisfy a dependency requesting two logs");
        var items=new ArrayList<ResourceItem>(); for(int n=0;n<23;n++) items.add(new ResourceItem(438,"Tin ore",1));
        items.add(new ResourceItem(995,"Coins",100)); items.add(new ResourceItem(436,"Copper ore",2));
        var batches=SaleBatches.group(items,Set.of("Tin ore","Copper ore","Coins"),Set.of("Copper ore"));
        check(batches.size()==1 && batches.get(0).quantity==23,"23 unstacked ores form one 23-item sale; reserves/coins excluded");
        check(SaleBatches.group(List.of(new ResourceItem(314,"Feather",500)),Set.of("Feather"),Set.of()).get(0).quantity==500,"Stacked quantities preserved");
        Path history=FilesStore.ROOT.resolve("learning/test-chains.json"); var library=new ChainLibrary(history); Random random=new Random(44);
        check(library.propose(plan),"Validated chain enters probation");
        for(int n=0;n<3;n++) library.observe(plan,true,8,60000,200,0,"",random,10000+n);
        check(library.evidence(plan.id).status.equals("PROMOTED"),"Three verified runs promote chain");
        String child=library.knownIds().stream().filter(id->!id.equals(plan.id)).findFirst().orElseThrow();
        check(library.evidence(child).status.equals("PROBATION") && ChainPlan.valid(library.evidence(child).plan),"Evolution produces a validated challenger, not an automatic replacement");
        var restored=new ChainLibrary(history);
        check(restored.evidence(plan.id).success==3 && restored.evidence(plan.id).verified==24,"Verified evidence persists across restart");
        ChainPlan challenger=restored.evidence(child).plan;
        restored.observe(challenger,false,0,60000,0,0,"timeout",random,20000);
        restored.observe(challenger,false,0,60000,0,0,"timeout",random,20001);
        check(restored.evidence(child).status.equals("QUARANTINED"),"Rejected challenger quarantined; parent retained");
        check(restored.choose(method,random,20002).id.equals(plan.id),"Policy rolls back to proven parent after challenger failure");
        // Deterministic replay across policies; no network, account, bank or game input is used.
        for(int n=0;n<64;n++) {
            ChainPlan variant=plan.copy(); variant.steps.get(2).count=1+random.nextInt(12);
            Replay sim=new Replay(); ChainRunner trial=new ChainRunner(variant,sim,0);
            int xp=0,logs=0; long now=0;
            for(int tick=0;tick<200;tick++) {
                ChainRunner.Result result=trial.step(state(xp,logs),++now);
                if(sim.acted>logs) { logs=sim.acted; xp=logs*25; }
                if(result==ChainRunner.Result.DONE) break;
            }
            check(trial.verified()==variant.steps.get(2).count,"Replay verifies every requested action in bounded variant");
        }
        debugLogger.setLevel(oldLevel);
        System.out.println("PASS "+checks+" chain/replay/evolution/batch regression checks");
    }
}
