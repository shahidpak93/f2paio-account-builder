package com.example.f2paio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Learns the delayed value of a verified goal episode. The executor still owns eligibility. */
final class GoalLearning {
    private static final double DISCOUNT = 0.85;
    private final Path file;
    private final Map<String, Value> values = new HashMap<>();
    private Episode active;
    private Episode pending;
    private double reward;
    private int updates;

    private static final class Value {
        double q;
        int samples;
    }

    private static final class Episode {
        final String key;
        final Method goal;
        final GameState before;
        long began;

        Episode(String key, Method goal, GameState before, long began) {
            this.key = key;
            this.goal = goal;
            this.before = before;
            this.began = began;
        }
    }

    GoalLearning(Path file) {
        this.file = file;
        if (file == null || !Files.exists(file)) return;
        try {
            var type = new com.google.gson.reflect.TypeToken<Map<String,Value>>() { }.getType();
            Map<String, Value> saved = FilesStore.JSON.fromJson(Files.readString(file),type);
            if (saved != null) saved.forEach((key,value) -> {
                if (value != null && Double.isFinite(value.q) && value.samples > 0) values.put(key,value);
            });
        } catch (Exception ex) {
            DebugLog.event("Q_LOAD","Unreadable value history ignored");
        }
    }

    private static String key(Method goal, GameState state, boolean stocked) {
        int level = state.levels.getOrDefault(goal.skill,1);
        int gear = goal.kind == Method.Kind.COMBAT ? Math.max(0,GearCatalog.ownedRank("weapon",goal,state))/100
            : goal.tools.contains("axe") || goal.tools.contains("pickaxe")
                ? Math.max(0,GearCatalog.ownedRank("tool",goal,state))/100 : 0;
        int travel = Math.min(2,state.location.distanceTo(goal.location)/100);
        return goal.id + ":" + level/10 + ":" + gear + ":" + stocked + ":" + travel
            + ":" + Math.min(3,state.questPoints/8) + ":" + state.ironman;
    }

    private double q(String key) {
        Value value = values.get(key);
        return value == null ? 0 : value.q;
    }

    double bonus(Method goal, GameState state, boolean stocked) {
        Value value = values.get(key(goal,state,stocked));
        if (value == null) return 0;
        double confidence = Math.min(1.0,value.samples/4.0);
        return Math.max(-30,Math.min(30,value.q * 8 * confidence));
    }

    void begin(Method goal, GameState state, boolean stocked, long now) {
        active = new Episode(key(goal,state,stocked),goal,state,now);
    }

    void pause(long millis) {
        if (active != null) active.began += millis;
    }

    void finish(GameState after, long now, boolean skipped) {
        if (active == null) return;
        Episode episode = active;
        active = null;
        if (skipped) return;
        long elapsed = Math.max(1,now-episode.began);
        long xp = Math.max(0,OutcomeLearning.trainingXp(episode.goal,after)
            -OutcomeLearning.trainingXp(episode.goal,episode.before));
        int quests = Math.max(0,after.questPoints-episode.before.questPoints);
        boolean completed = episode.goal.kind == Method.Kind.QUEST
            && !ProgressionPolicy.done(episode.before,episode.goal.label)
            && ProgressionPolicy.done(after,episode.goal.label);
        double levelGains = after.levels.entrySet().stream()
            .mapToInt(entry -> Math.max(0,entry.getValue()-episode.before.levels.getOrDefault(entry.getKey(),entry.getValue())))
            .sum();
        reward = Math.min(4,xp / Math.max(120.0,elapsed/1000.0) / 2)
            + Math.min(4,levelGains*0.5) + (completed ? 4+Math.min(3,quests) : 0)
            - Math.min(1,Math.max(0,episode.before.coins-after.coins)/3000.0)
            - (xp == 0 && !completed && levelGains == 0 ? 2 : 0)
            - (after.health <= 0 ? 8 : 0);
        pending = episode;
        if (after.health <= 0) learn(after,List.of(),ignored -> false);
    }

    void learn(GameState after, List<Method> eligible, java.util.function.Predicate<Method> stocked) {
        if (pending == null) return;
        double future = eligible.stream().mapToDouble(goal -> q(key(goal,after,stocked.test(goal)))).max().orElse(0);
        Value value = values.computeIfAbsent(pending.key,ignored -> new Value());
        double target = reward + DISCOUNT * future;
        value.samples++;
        value.q = Math.max(-12,Math.min(12,value.q + 0.30 * (target-value.q)));
        DebugLog.event("Q_UPDATE",pending.goal.id + " reward=" + reward + " next=" + future
            + " q=" + value.q + " samples=" + value.samples);
        pending = null;
        updates++;
        save();
    }

    String summary() { return "Goal Q-learning " + values.size() + " contexts / " + updates + " updates this run"; }

    private void save() {
        if (file == null) return;
        try { FilesStore.write(file,values); }
        catch (Exception ex) { DebugLog.event("Q_SAVE",ex.getClass().getSimpleName()); }
    }
}
