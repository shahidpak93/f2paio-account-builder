package com.example.f2paio;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Scores live readiness and level gaps without regional lock-in. */
public final class AutonomousPlanner
{
    public Method choose(List<Method> eligible, GameState state, String previous)
    {
        return choose(eligible, state, previous, null, null);
    }

    public Method choose(List<Method> eligible, GameState state, String previous, Bandit bandit, Random random)
    {
        java.util.Map<Method,Double> scores = new java.util.HashMap<>();
        List<Method> choices = ProgressionPolicy.frontier(eligible);
        for (Method method : choices) scores.put(method,score(method,choices,state,previous,bandit,random));
        return choices.stream().max(Comparator.comparingDouble(scores::get)).orElse(null);
    }

    double score(Method method, List<Method> eligible, GameState state, String previous, Bandit bandit, Random random)
    {
        int missing = new Requirements().missing(method, state).size();
        double banditWeight = (bandit != null && random != null) ? (bandit.score(method.id, random) * 35.0) : 0;

        // Completed quests are filtered before scoring; missing supplies reduce readiness
        if (method.kind == Method.Kind.QUEST)
        {
            double base = (missing == 0 ? 500 : state.coins >= 500 ? 180 : 70)
                + (method.id.equals("cooks") ? 15 : 0)
                - method.location.distanceTo(state.location) * 0.02;
            return base + banditWeight + ProgressionPolicy.trainingBonus(method,state);
        }

        // Ground scavenging is strictly for emergency early-game bootstrapping (<150 coins)
        if (method.kind == Method.Kind.GROUND)
        {
            if (state.coins >= 150) return 20 - (method.id.equals(previous) ? 25 : 0);
            double base = 130 - (method.id.equals("pvp_loot") ? 20 : 0)
                - Math.min(25, method.location.distanceTo(state.location) * 0.03)
                - (method.id.equals(previous) ? 30 : 0);
            return base + banditWeight;
        }

        // Standard skilling and production
        int currentLevel = state.levels.getOrDefault(method.skill, 1);
        int bestTier = eligible.stream().filter(m -> m.skill == method.skill && ProgressionPolicy.trains(m))
            .mapToInt(ProgressionPolicy::trainingTier).max().orElse(1);

        double score = 100 - currentLevel + (ProgressionPolicy.trainingTier(method) == bestTier ? 25 : -20);
        // Travel is walked when the account has no run energy, so the same distance costs
        // more wall-clock time. The penalty stays bounded and only tilts close choices.
        double travelWeight = state.runEnergy < RunEnergy.ARM_AT ? 0.025 : 0.015;
        score -= Math.min(15, method.location.distanceTo(state.location) * travelWeight);

        if (method.id.equals(previous)) score -= 15;
        if (missing > 0) score -= 35;
        return score + banditWeight + ProgressionPolicy.trainingBonus(method,state);
    }
}
