package com.example.f2paio;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Bounded account-building curriculum.
 *
 * <p>Borrowed from Voyager's automatic-curriculum idea: rather than a fixed 10/20/40 ladder, score
 * the next useful aim from live state (ready quest, nearly-ready quest, lowest skill milestone).
 * Pure scoring only; the deterministic controller still owns execution.</p>
 */
public final class Curriculum
{
    static final int[] MILESTONES = {10, 20, 40, 60, 80, 99};
    private static final Set<Skill> TRAINABLE = trainableSkills();
    private String aim = "Account build";

    public String aim() { return aim; }

    private long lastCombatAt, lastSkillAt;

    /** Remember which category was just chosen so combat and skilling also get turns. */
    public void note(Method method, long now)
    {
        if (method == null) return;
        if (method.kind == Method.Kind.COMBAT) lastCombatAt = now;
        else if (skilling(method)) lastSkillAt = now;
    }

    private static boolean skilling(Method method)
    {
        return method.kind == Method.Kind.OBJECT || method.kind == Method.Kind.FISHING
            || method.kind == Method.Kind.PROCESS;
    }

    /** Fairness is applied before milestone filtering, so excluded categories can actually compete. */
    public List<Method> balance(List<Method> choices,long now) {
        var combat=new java.util.ArrayList<Method>();var skills=new java.util.ArrayList<Method>();
        for(Method m:choices) {if(m.kind==Method.Kind.COMBAT) combat.add(m);else if(skilling(m)) skills.add(m);}
        if(!combat.isEmpty() && (lastCombatAt==0 || now-lastCombatAt>=10*Tuning.MINUTE)) {
            aim="Combat progression is due";return combat;
        }
        if(!skills.isEmpty() && (lastSkillAt==0 || now-lastSkillAt>=10*Tuning.MINUTE)) {
            aim="Skilling progression is due";return skills;
        }
        return choices;
    }

    /** Additive score for the planner. Positive = account-building value. */
    public double bonus(Method method, GameState state, F2pAioConfig config)
    {
        return bonus(method, state, config, System.currentTimeMillis());
    }

    public double bonus(Method method, GameState state, F2pAioConfig config, long now)
    {
        if (method == null || !config.accountBuilder || "MONEY".equals(config.taskMode)) return 0;
        // Combat and skilling must not be starved by an endless queue of quests and funding.
        if (method.kind == Method.Kind.COMBAT)
        {
            if (lastCombatAt == 0 || now - lastCombatAt > 10 * Tuning.MINUTE)
            { aim = "Combat training: " + method.label; return 60; }
            return 10;
        }
        double value = baseBonus(method, state, config);
        if (value > 0 && skilling(method) && (lastSkillAt == 0 || now - lastSkillAt > 10 * Tuning.MINUTE))
        {
            aim = "Skilling: " + method.skill;
            value += 15;
        }
        return value;
    }

    private double baseBonus(Method method, GameState state, F2pAioConfig config)
    {
        if (method.kind == Method.Kind.GROUND)
        {
            if (state.coins < 150) { aim = "Emergency coins"; return 0; }
            aim = "Skip ground loot; account build active";
            return -70;
        }
        if (method.kind == Method.Kind.QUEST)
        {
            boolean ready = ProgressionPolicy.questReady(method, state)
                && new Requirements().missing(method, state).isEmpty();
            if (ready) { aim = "Ready quest: " + method.label; return 140; }
            if (ProgressionPolicy.questReady(method, state)) { aim = "Prepare quest: " + method.label; return 15; }
            aim = "Quest gated by levels: " + method.label;
            return -20;
        }
        int level = state.levels.getOrDefault(method.skill, 1);
        int next = nextMilestone(level);
        Skill lowest = lowestSkill(state);
        if (method.skill == lowest)
        {
            aim = "Raise " + method.skill + " to " + next;
            return 45;
        }
        if (level < next)
        {
            aim = "Next " + method.skill + " milestone " + next;
            return 20;
        }
        return 5;
    }

    public static int nextMilestone(int level)
    {
        for (int target : MILESTONES) if (level < target) return target;
        return 99;
    }

    public static Skill lowestSkill(GameState state)
    {
        Skill lowest = null;
        int value = Integer.MAX_VALUE;
        for (Skill skill : TRAINABLE)
        {
            int level = state.levels.getOrDefault(skill, 1);
            if (level < value) { value = level; lowest = skill; }
        }
        return lowest;
    }

    private static Set<Skill> trainableSkills()
    {
        Set<Skill> skills = EnumSet.noneOf(Skill.class);
        for (Method method : Methods.all())
            if (method.kind != Method.Kind.QUEST && method.kind != Method.Kind.GROUND
                && method.skill != Skill.OVERALL) skills.add(method.skill);
        return skills;
    }

    /** View helper used by the panel/handoff. */
    public static List<String> milestones()
    {
        return List.of("10", "20", "40", "60", "80", "99");
    }
}
