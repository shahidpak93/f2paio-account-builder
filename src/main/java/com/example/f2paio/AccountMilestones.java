package com.example.f2paio;
import java.nio.file.*;
import java.util.*;
import net.runelite.api.Skill;
/** Persistent skill milestones over supported, currently affordable capabilities. */
public final class AccountMilestones {
    static final class Target { String skill; int level; }
    private Target target;
    private UnlockPlanner.Route unlock;
    private final Path file;
    private String view="Account milestones";
    public AccountMilestones(Path file) {this.file=file;try {if(Files.exists(file)) target=FilesStore.read(file,Target.class);}catch(Exception ignored){}}
    public List<Method> focus(List<Method> candidates,GameState s) {
        return focus(candidates,s,new F2pAioConfig());
    }
    List<Method> focus(List<Method> candidates,GameState s,F2pAioConfig config) {
        unlock=null;
        List<Method> quests=new ArrayList<>();
        for(Method m:candidates) if(m.kind==Method.Kind.QUEST && new Requirements().missing(m,s).isEmpty()) quests.add(m);
        if(!quests.isEmpty()) {view="Ready quest unlock";return quests;}
        unlock=UnlockPlanner.next(candidates,s,config);
        if (unlock!=null) {
            view=unlock.quest.label+" -> train "+unlock.skill+" to "+unlock.level;
            return unlock.steps;
        }
        Skill skill=null;
        try {if(target!=null && s.levels.getOrDefault(Skill.valueOf(target.skill),99)<target.level) skill=Skill.valueOf(target.skill);}catch(Exception ignored){}
        final Skill wanted=skill;
        if(skill==null || candidates.stream().noneMatch(m->m.skill==wanted && m.kind!=Method.Kind.QUEST)) {
            Method lowest=candidates.stream().filter(m->m.kind!=Method.Kind.QUEST && m.kind!=Method.Kind.GROUND)
                .min(Comparator.comparingInt(m->s.levels.getOrDefault(m.skill,99))).orElse(null);
            if(lowest==null) return candidates;
            skill=lowest.skill;target=new Target();target.skill=skill.name();
            int level=s.levels.getOrDefault(skill,1);target.level=level<10?10:level<20?20:level<40?40:Math.min(99,level+10);
            try {FilesStore.write(file,target);}catch(Exception ex){DebugLog.event("MILESTONE_SAVE",ex.getClass().getSimpleName());}
        }
        view=target.skill+" -> "+target.level+" (affordable progression)";
        final Skill selected=skill;
        int currentLevel=s.levels.getOrDefault(selected,1);
        List<Method> result=new ArrayList<>();
        for(Method method:candidates)
            if(ProgressionPolicy.trains(method) && (method.skill==selected
                || s.levels.getOrDefault(method.skill,99)<=currentLevel+5)) result.add(method);
        return result.isEmpty()?candidates:result;
    }
    boolean unlocked(GameState state) {
        return unlock!=null && ProgressionPolicy.questReady(unlock.quest,state);
    }
    double preference(Method method) {
        return unlock==null && target!=null && ProgressionPolicy.trains(method)
            && method.skill.name().equals(target.skill) ? 15 : 0;
    }
    public String view(){return view;}
}
