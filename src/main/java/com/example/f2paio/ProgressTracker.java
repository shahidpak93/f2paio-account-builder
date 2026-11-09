package com.example.f2paio;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Skill;

public final class ProgressTracker
{
    public static final class Snapshot
    {
        public long observedAt;
        public long startedAt;
        public long xpGained;
        public int levelUps;
        public long coins;
        public long coinChange;
        public int completedQuests;
        public int newQuests;
        public String position = "Unknown";
        public Map<String, Integer> levels = new LinkedHashMap<>();
        public Map<String, Long> xpBySkill = new LinkedHashMap<>();
        public Map<String, String> quests = new LinkedHashMap<>();
        public java.util.List<ResourceItem> items = java.util.List.of();
        public java.util.List<ResourceItem> equipment = java.util.List.of();
        public int world;
    }

    private final Map<Skill, Integer> initialLevels = new java.util.EnumMap<>(Skill.class);
    private final Map<Skill, Integer> initialXp = new java.util.EnumMap<>(Skill.class);
    private Map<String, String> initialQuests;
    private long initialCoins;
    private long startedAt;
    private volatile Snapshot snapshot = new Snapshot();

    public synchronized void reset()
    {
        initialLevels.clear(); initialXp.clear(); initialQuests = null; snapshot = new Snapshot();
    }
    public Snapshot snapshot() { return snapshot; }

    public synchronized void observe(GameState state)
    {
        if (initialLevels.isEmpty())
        {
            initialLevels.putAll(state.levels); initialXp.putAll(state.experience);
            initialCoins = state.coins; startedAt = System.currentTimeMillis();
            initialQuests = new LinkedHashMap<>(state.quests);
        }
        Snapshot next = new Snapshot(); next.observedAt = System.currentTimeMillis(); next.startedAt = startedAt;
        next.coins = state.coins; next.coinChange = state.coins - initialCoins;
        next.items = state.items; next.equipment = state.equipment; next.world = state.world;
        next.position = state.location.getX() + ", " + state.location.getY() + ", " + state.location.getPlane();
        for (Skill skill : state.levels.keySet())
        {
            int current = state.levels.get(skill);
            next.levels.put(skill.getName(), current);
            long delta = state.experience.getOrDefault(skill, 0) - initialXp.getOrDefault(skill, 0);
            next.xpBySkill.put(skill.getName(), delta); next.xpGained += delta;
            next.levelUps += Math.max(0, current - initialLevels.get(skill));
            Integer old = snapshot.levels.get(skill.getName());
            if (old != null && current > old) DebugLog.event("LEVEL_UP", skill.getName() + " " + old + " -> " + current);
        }
        next.quests.putAll(state.quests);
        state.quests.forEach((name, status) -> {
            if (status.equals("FINISHED"))
            {
                next.completedQuests++;
                if (!"FINISHED".equals(initialQuests.get(name))) next.newQuests++;
                if (snapshot.quests.containsKey(name) && !"FINISHED".equals(snapshot.quests.get(name)))
                    DebugLog.event("QUEST_COMPLETE", name);
            }
        });
        snapshot = next;
    }
}
