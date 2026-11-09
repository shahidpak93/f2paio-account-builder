package com.example.f2paio;
import java.nio.file.*;
import java.util.*;
/** Online contextual value learning from verified episode effects, not LLM confidence. */
public final class OutcomeLearning {
    static final class Value { double mean, xpPerHour; long count, rateCount; }
    private final Path file;
    private Map<String,Value> values=new HashMap<>();
    private GameState start; private Method method; private long started;
    public OutcomeLearning(Path file) {
        this.file=file;
        if(file!=null && Files.exists(file)) try {
            var type=new com.google.gson.reflect.TypeToken<Map<String,Value>>(){}.getType();
            Map<String,Value> loaded=FilesStore.JSON.fromJson(Files.readString(file),type);
            if(loaded!=null) loaded.forEach((k,v)-> { if(v!=null && Double.isFinite(v.mean) && v.count>0) values.put(k,v); });
        } catch(Exception ex) { DebugLog.event("ML_LOAD","Invalid outcome history ignored"); }
    }
    private String key(Method m,GameState s) {return m.id+":level="+s.levels.getOrDefault(m.skill,1)/10+":funded="+(s.coins>=500);}
    public double bonus(Method m,GameState s) {Value v=values.get(key(m,s));return v==null?0:Math.max(-40,Math.min(40,v.mean*8));}
    double rate(Method method, GameState state) {
        Value value = values.get(key(method,state));
        return value!=null && value.rateCount>=2 && Double.isFinite(value.xpPerHour) ? value.xpPerHour : 0;
    }

    static long trainingXp(Method method,GameState state) {
        if (method.kind==Method.Kind.COMBAT && method.skill==net.runelite.api.Skill.ATTACK)
            return (long) state.experience.getOrDefault(net.runelite.api.Skill.ATTACK,0)
                +state.experience.getOrDefault(net.runelite.api.Skill.STRENGTH,0)
                +state.experience.getOrDefault(net.runelite.api.Skill.DEFENCE,0);
        return state.experience.getOrDefault(method.skill,0);
    }

    public void begin(Method m,GameState s,long now) {method=m;start=s;started=now;}
    public void pause(long elapsed) { if(start!=null) started+=elapsed; }
    public void finish(GameState end,long now,boolean skipped) {
        if(start==null || method==null) return;
        if(!skipped) {
            boolean quest=method.kind==Method.Kind.QUEST && !ProgressionPolicy.done(start,method.label) && ProgressionPolicy.done(end,method.label);
            double reward=reward(end.xp-start.xp,quest,end.coins-start.coins,Math.max(1000,now-started));
            String key=key(method,start); Value v=values.computeIfAbsent(key,k->new Value());
            v.count++; v.mean+=(reward-v.mean)/Math.min(20,v.count);
            long elapsed = now-started;
            long trainingXp = trainingXp(method,end)-trainingXp(method,start);
            if (ProgressionPolicy.trains(method) && elapsed>=2*Tuning.MINUTE && trainingXp>0) {
                v.rateCount++;
                v.xpPerHour += (trainingXp*3_600_000.0/elapsed-v.xpPerHour)/Math.min(10,v.rateCount);
            }
            DebugLog.event("ML_UPDATE","method="+method.id+" reward="+reward+" trials="+v.count+" value="+v.mean);
            try {if(file!=null) {
                FilesStore.write(file,values);
                Path replay=file.resolveSibling(file.getFileName()+".episodes.jsonl");
                if(Files.exists(replay) && Files.size(replay)>5_000_000) Files.move(replay,replay.resolveSibling(replay.getFileName()+".previous"),StandardCopyOption.REPLACE_EXISTING);
                FilesStore.append(replay,Map.of("method",method.id,"context",key,"durationMs",Math.max(0,now-started),
                    "xpDelta",end.xp-start.xp,"questCompleted",quest,"coinDelta",end.coins-start.coins,"reward",reward,
                    "beforeItems",start.items,"afterItems",end.items));
            }} catch(Exception ex) {DebugLog.event("ML_SAVE",ex.getClass().getSimpleName());}
        }
        start=null; method=null;
    }
    public static double reward(long xp,boolean quest,long coins,long millis) {
        double minutes=Math.max(1,millis/60000.0);
        return Math.max(-10,Math.min(10,Math.max(0,xp)/minutes/100+(quest?8:0)-Math.min(3,Math.max(0,-coins)/100.0)-(xp<=0&&!quest?Math.min(3,minutes*.3):0)));
    }
    public String summary() {return "Online outcome contexts="+values.size();}
}
