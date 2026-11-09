package com.example.f2paio;
import java.util.*;
import net.runelite.api.Skill;
/** Progression earns priority; money exists to satisfy the next concrete requirement. */
public final class ProgressionPolicy {
    public static Set<String> reserved(GameState s) {
        Set<String> names=new HashSet<>();
        if(!done(s,"Doric's Quest")) names.addAll(List.of("Clay","Copper ore","Iron ore"));
        if(!done(s,"Sheep Shearer")) names.add("Ball of wool");
        if(!done(s,"Imp Catcher")) names.addAll(List.of("Red bead","Yellow bead","Black bead","White bead"));
        if(!done(s,"The Knight's Sword")) names.addAll(List.of("Iron bar","Redberry pie","Blurite ore"));
        return names;
    }
    public static boolean done(GameState s,String quest) { return "FINISHED".equals(s.quests.get(quest)); }
    public static boolean combatQuest(Method m) {
        return Set.of("vampyre","demon_slayer","dragon_slayer","below_ice","corsair").contains(m.id);
    }
    static Map<Skill,Integer> questLevels(Method quest) {
        if (quest.id.equals("knights_sword")) return Map.of(Skill.MINING,10);
        if (quest.id.equals("dragon_slayer"))
            return Map.of(Skill.ATTACK,40,Skill.STRENGTH,40,Skill.DEFENCE,40,Skill.PRAYER,37);
        return combatQuest(quest) ? Map.of(Skill.ATTACK,15,Skill.STRENGTH,15,Skill.DEFENCE,10) : Map.of();
    }

    static int questPointsRequired(Method quest) {
        switch (quest.id) {
            case "dragon_slayer": return 32;
            case "below_ice": return 16;
            case "black_knights": return 12;
            default: return 0;
        }
    }

    public static boolean questReady(Method method,GameState state) {
        return method.kind!=Method.Kind.QUEST || (state.questPoints>=questPointsRequired(method)
            && questLevels(method).entrySet().stream()
                .allMatch(gate -> state.levels.getOrDefault(gate.getKey(),1)>=gate.getValue()));
    }
    public static double trainingBonus(Method m,GameState s) {
        if(m.kind==Method.Kind.QUEST) {
            if(m.id.equals("restless_ghost") && s.levels.getOrDefault(Skill.PRAYER,1)<10) return 100;
            if(m.id.equals("doric") && s.levels.getOrDefault(Skill.MINING,1)<10) return 100;
            if(m.id.equals("knights_sword") && s.levels.getOrDefault(Skill.SMITHING,1)<29) return 130;
            return 80;
        }
        if(m.id.equals("iron") && s.levels.getOrDefault(Skill.MINING,1)>=15) return 75;
        if(m.id.equals("trout") || m.id.equals("salmon")) return 75;
        if(m.id.equals("willow")) return 65;
        if(m.skill==Skill.MINING && s.levels.getOrDefault(Skill.MINING,1)<10 && !done(s,"The Knight's Sword")) return 40;
        if(m.skill==Skill.PRAYER && s.coins<1000) return -70;
        return 0;
    }
    static boolean trains(Method method) {
        return method.kind != Method.Kind.QUEST && method.kind != Method.Kind.GROUND;
    }

    static int trainingTier(Method method) {
        switch (method.id) {
            case "iron": case "trout": case "willow": return 100;
            case "pray_big_bones": return 15;
            case "chocolate": case "tan_soft": case "tan_hard": return 0;
            default: return method.level;
        }
    }

    static List<Method> frontier(List<Method> candidates) {
        return frontier(candidates, method -> 0);
    }

    static List<Method> frontier(List<Method> candidates, java.util.function.ToDoubleFunction<Method> rate) {
        Map<Skill,Integer> best = new EnumMap<>(Skill.class);
        for (Method method : candidates)
            if (trains(method)) best.merge(method.skill,trainingTier(method),Math::max);
        Map<Skill,Double> topRates = new EnumMap<>(Skill.class);
        for (Method method : candidates)
            if (trains(method) && trainingTier(method)==best.get(method.skill))
                topRates.merge(method.skill,rate.applyAsDouble(method),Math::max);
        Map<Skill,Double> faster = new EnumMap<>(Skill.class);
        for (Method method : candidates) {
            if (!trains(method) || method.level<=1 || trainingTier(method)==best.get(method.skill)) continue;
            double baseline = topRates.get(method.skill), observed = rate.applyAsDouble(method);
            if (baseline>0 && observed>baseline*1.25) faster.merge(method.skill,observed,Math::max);
        }
        return candidates.stream().filter(method -> !trains(method)
            || (faster.containsKey(method.skill)
                ? method.level>1 && rate.applyAsDouble(method)>=faster.get(method.skill)/1.10
                : trainingTier(method)==best.get(method.skill)))
            .collect(java.util.stream.Collectors.toList());
    }

    static boolean hasBetter(Method current,List<Method> candidates,java.util.function.ToDoubleFunction<Method> rate) {
        if (!trains(current)) return false;
        List<Method> finalists = frontier(candidates,rate);
        return !finalists.contains(current) && finalists.stream().anyMatch(method -> trains(method)
            && method.skill==current.skill && (trainingTier(method)>trainingTier(current)
                || (rate.applyAsDouble(current)>0 && rate.applyAsDouble(method)>rate.applyAsDouble(current)*1.25)));
    }

    public static Skill meleeTarget(GameState state,String preset) {
        if(!preset.equals("MAIN") && !preset.equals("MONEY_ALT")) return Skill.STRENGTH;
        int attack=state.levels.getOrDefault(Skill.ATTACK,1),strength=state.levels.getOrDefault(Skill.STRENGTH,1),defence=state.levels.getOrDefault(Skill.DEFENCE,1);
        if(attack<=strength && attack<=defence) return Skill.ATTACK;
        return strength<=defence?Skill.STRENGTH:Skill.DEFENCE;
    }
    private ProgressionPolicy() { }
}
