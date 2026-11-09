package com.example.f2paio;

import java.util.List;
import net.runelite.api.Skill;

/** Work backwards from nearby unfinished quest milestones to currently executable training. */
final class UnlockPlanner {
    static final class Route {
        final Method quest;
        final Skill skill;
        final int level, distance;
        final List<Method> steps;

        Route(Method quest,Skill skill,int level,int distance,List<Method> steps) {
            this.quest=quest; this.skill=skill; this.level=level; this.distance=distance; this.steps=steps;
        }
    }

    static Route next(List<Method> candidates, GameState state, F2pAioConfig config) {
        if (!config.autonomous || !config.taskMode.equals("AIO")) return null;
        Route best = null;
        for (Method quest : Methods.all()) {
            if (quest.kind!=Method.Kind.QUEST || !"NOT_STARTED".equals(state.quests.get(quest.label))
                || state.questPoints<ProgressionPolicy.questPointsRequired(quest)) continue;
            var gates = ProgressionPolicy.questLevels(quest);
            int distance = gates.entrySet().stream()
                .mapToInt(gate -> Math.max(0,gate.getValue()-state.levels.getOrDefault(gate.getKey(),1))).sum();
            if (distance==0 || distance>12 || (best!=null && distance>=best.distance)) continue;
            for (var gate : gates.entrySet()) {
                if (state.levels.getOrDefault(gate.getKey(),1)>=gate.getValue()) continue;
                var steps=candidates.stream().filter(ProgressionPolicy::trains)
                    .filter(method -> skill(method,state,config)==gate.getKey())
                    .collect(java.util.stream.Collectors.toList());
                if (!steps.isEmpty()) {
                    best=new Route(quest,gate.getKey(),gate.getValue(),distance,steps);
                    break;
                }
            }
        }
        return best;
    }

    private static Skill skill(Method method,GameState state,F2pAioConfig config) {
        return method.kind==Method.Kind.COMBAT && method.skill==Skill.ATTACK
            ? ProgressionPolicy.meleeTarget(state,config.preset) : method.skill;
    }

    private UnlockPlanner() { }
}
