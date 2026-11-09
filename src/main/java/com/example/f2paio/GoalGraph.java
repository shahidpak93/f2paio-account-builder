package com.example.f2paio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class GoalGraph
{
    public List<Method> eligible(GameState state, F2pAioConfig config, Set<String> quarantine)
    {
        List<Method> choices = new ArrayList<>();
        List<String> ids = config.autonomous ? Methods.all().stream().map(m -> m.id)
            .collect(java.util.stream.Collectors.toList()) : config.enabledMethods;
        for (String id : ids)
        {
            Method method = Methods.get(id);
            if(method.kind==Method.Kind.COMBAT && state.health<70 && state.items.stream().noneMatch(i->!i.noted && Requirements.FOOD.contains(i.name))) continue;
            if(!config.allowPvp && method.id.equals("pvp_loot")) continue;
            if(config.accountBuilder && method.kind==Method.Kind.GROUND && !config.taskMode.equals("MONEY")) continue;
            if(config.accountBuilder && !config.taskMode.equals("MONEY")
                && Set.of("chocolate","tan_soft","tan_hard").contains(method.id)) continue;
            if(!ProgressionPolicy.questReady(method,state) || !CombatPolicy.ready(method,state)) continue;
            if (method.kind == Method.Kind.QUEST && "FINISHED".equals(state.quests.get(method.label))) continue;
            if (method.id.equals("pvp_loot") && (!state.equipment.isEmpty()
                || state.coins > 100 || state.items.stream().anyMatch(i -> i.id != 995)) && !state.pvpWorld) continue;
            if (quarantine.contains(id) || Methods.learnedBlocked(id)) continue;
            if (method.kind != Method.Kind.QUEST && method.kind != Method.Kind.GROUND && (state.levels.getOrDefault(method.skill,1) < method.level
                || state.levels.getOrDefault(method.kind==Method.Kind.COMBAT && method.skill==net.runelite.api.Skill.ATTACK
                    ? ProgressionPolicy.meleeTarget(state,config.preset) : method.skill,1) >= (config.autonomous ? 99 : config.targetLevel))) continue;
            if ((config.taskMode.equals("SKILLER") || config.preset.equals("SKILL_PURE"))
                && (method.kind == Method.Kind.COMBAT || method.id.equals("witch") || method.id.equals("imp_catcher"))) continue;
            if ((config.preset.equals("PURE") || config.preset.equals("ZERKER"))
                && method.kind == Method.Kind.COMBAT) continue;
            if (config.taskMode.equals("QUESTER") && method.kind != Method.Kind.QUEST) continue;
            if (config.taskMode.equals("PVM") && method.kind != Method.Kind.COMBAT) continue;
            if (config.taskMode.equals("MONEY") && method.kind == Method.Kind.QUEST) continue;
            choices.add(method);
        }
        return choices;
    }

    public Method choose(List<Method> methods, String previous, String preferred,
        Random random, Bandit bandit)
    {
        if (methods.isEmpty()) return null;
        if (previous.isEmpty())
            for (Method method : methods) if (method.id.equals(preferred)) return method;
        List<Method> ranked = new ArrayList<>(methods);
        java.util.Map<String, Double> scores = new java.util.HashMap<>();
        for (Method method : ranked)
            scores.put(method.id, bandit.score(method.id, random)
                + (method.id.equals(preferred) ? 0.15 : 0) - (method.id.equals(previous) ? 0.5 : 0));
        ranked.sort(Comparator.comparingDouble((Method m) -> scores.get(m.id)).reversed());
        return ranked.get(0);
    }
}
