package com.example.f2paio;

import java.util.Comparator;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.http.api.worlds.WorldType;

public final class WorldLootPolicy
{
    private int pendingWorld;
    private long requestedAt;
    private long settledAt;

    public boolean prepare(Method method, GameState state)
    {
        if (!method.id.equals("pvp_loot"))
        {
            if (method.kind == Method.Kind.GROUND) return preparePublic(state);
            if (!state.pvpWorld) return true;
            var result = Microbot.getWorldService().getWorlds();
            var ordinary = result == null ? null : select(result.getWorlds(), false);
            if (ordinary != null) Microbot.hopToWorld(ordinary.getId());
            return false;
        }
        if (state.pvpWorld) return true;
        if (!state.equipment.isEmpty() || state.coins > 100 || state.items.stream().anyMatch(i -> i.id != 995))
        {
            DebugLog.event("PVP_SKIP", "Public PvP scavenging requires empty gear/inventory and at most 100 coins");
            return false;
        }
        var result = Microbot.getWorldService().getWorlds();
        if (result == null) return false;
        var world = select(result.getWorlds(), true);
        if (world != null)
            DebugLog.event("WORLD_HOP", "Most populated eligible F2P PvP world=" + world.getId()
                + "; players=" + world.getPlayers() + "; submitted=" + Microbot.hopToWorld(world.getId()));
        return false;
    }

    public boolean preparePublic(GameState state)
    {
        long now = System.currentTimeMillis();
        if (pendingWorld != 0) {
            if (state.world == pendingWorld && !state.pvpWorld) {
                DebugLog.event("WORLD_READY", "Public loot world=" + state.world);
                pendingWorld = 0; settledAt = now; return true;
            }
            if (now - requestedAt < 20_000) return false;
            pendingWorld = 0;
        }
        if (!state.pvpWorld && now - settledAt < 120_000) return true;
        var result = Microbot.getWorldService().getWorlds();
        if (result == null) {
            DebugLog.event("WORLD_WAIT", "World list unavailable; cannot rank public loot worlds");
            return false;
        }
        var world = select(result.getWorlds(), false);
        if (world == null) return false;
        if (world.getId() == state.world && !state.pvpWorld) { settledAt = now; return true; }
        boolean submitted = Microbot.hopToWorld(world.getId());
        DebugLog.event("WORLD_HOP", "Public-drop method: highest population eligible ordinary F2P world="
            + world.getId() + "; players=" + world.getPlayers() + "; from=" + state.world + "; submitted=" + submitted);
        if (submitted) { pendingWorld = world.getId(); requestedAt = now; }
        return false;
    }

    static net.runelite.http.api.worlds.World select(java.util.List<net.runelite.http.api.worlds.World> worlds, boolean pvp)
    {
        return worlds.stream().filter(w -> w.getTypes().contains(WorldType.PVP) == pvp
            && !w.getTypes().contains(WorldType.MEMBERS) && !w.getTypes().contains(WorldType.HIGH_RISK)
            && !w.getTypes().contains(WorldType.DEADMAN) && !w.getTypes().contains(WorldType.SKILL_TOTAL)
            && !w.getTypes().contains(WorldType.BETA_WORLD) && !w.getTypes().contains(WorldType.QUEST_SPEEDRUNNING)
            && w.getPlayers() > 0 && w.getPlayers() < 1_950)
            .max(Comparator.comparingInt(w -> w.getPlayers())).orElse(null);
    }
}
