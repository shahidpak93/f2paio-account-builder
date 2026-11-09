package com.example.f2paio;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/** Offline checks for the leftovers fix and for continuous-action chain verification. */
public final class RuntimeFixTests
{
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    private static GameState state(boolean busy, int logs, int woodcutXp)
    {
        var levels = new EnumMap<Skill, Integer>(Skill.class);
        var experience = new EnumMap<Skill, Integer>(Skill.class);
        for (Skill s : Skill.values()) if (s != Skill.OVERALL) { levels.put(s, 1); experience.put(s, 0); }
        levels.put(Skill.WOODCUTTING, 50);
        experience.put(Skill.WOODCUTTING, woodcutXp);
        GameState state = new GameState("test", new WorldPoint(3192, 3223, 0), woodcutXp, 1, busy,
            false, 100, false, levels, experience, Map.of(), 0);
        state.items = logs > 0 ? List.of(new ResourceItem(1511, "Logs", logs)) : List.of();
        return state;
    }

    private static final class FakeBackend implements ChainRunner.Backend
    {
        int acts;
        public boolean prepare(Method method, GameState state) { return true; }
        public boolean travel(Method method, GameState state) { return true; }
        public boolean act(Method method, GameState state) { acts++; return true; }
        public boolean bank(Method method, GameState state) { return true; }
        public boolean sell(Method method, GameState state) { return true; }
        public String failure() { return ""; }
    }

    static void run() throws Exception
    {
        // ---- leftovers / inventory cleanup ----
        GameState inventory = state(false, 1, 0);
        inventory.items = List.of(new ResourceItem(1511, "Logs", 3), new ResourceItem(317, "Raw shrimps", 4),
            new ResourceItem(303, "Small fishing net", 1), new ResourceItem(333, "Trout", 2),
            new ResourceItem(995, "Coins", 100));
        Set<String> keep = InventoryCleanup.keepFor(Methods.get("trees"), inventory, Set.of());
        List<ResourceItem> junk = InventoryCleanup.unrelated(inventory.items, keep);
        check(junk.size() == 4, "Leftovers include other activity tools and unneeded coins");
        check(junk.stream().anyMatch(i -> "Logs".equals(i.name)) && junk.stream().anyMatch(i -> "Raw shrimps".equals(i.name))
            && junk.stream().anyMatch(i -> "Small fishing net".equals(i.name))
            && junk.stream().anyMatch(i -> "Coins".equals(i.name)),
            "Logs, raw shrimps, another activity's net and unneeded coins are treated as leftovers");
        check(keep.contains("Trout") && !keep.contains("Coins") && !keep.contains("Small fishing net"),
            "Only the current activity's tools plus food are protected; coins are banked");

        // ---- continuous action: XP growth across busy frames must verify, not time out ----
        Method trees = Methods.get("trees");
        FakeBackend backend = new FakeBackend();
        ChainRunner runner = new ChainRunner(ChainPlan.seed(trees), backend, 0);
        GameState start = state(false, 0, 0);
        runner.step(start, 0);              // PREPARE
        runner.step(start, 10);             // TRAVEL
        runner.step(start, 20);             // ACT (submits)
        GameState grown = state(true, 1, 100);
        runner.step(grown, 1_000);          // VERIFY: first observed growth
        check(runner.verified() == 1, "First continuous effect is verified");
        runner.step(grown, 2_000);          // ACT while busy: step into VERIFY instead of idling
        ChainRunner.Result waiting = runner.step(grown, 3_000);
        check(waiting == ChainRunner.Result.RUNNING, "Quiet window keeps the chain running");
        ChainRunner.Result partial = runner.step(grown, 36_000);
        check(partial == ChainRunner.Result.RUNNING, "Verified progress past the quiet window continues to settlement");
        ChainRunner.Result done = runner.step(grown, 37_000);   // BANK
        check(done == ChainRunner.Result.DONE, "Chain settles instead of reporting a false failure");
        check(runner.failure().isEmpty(), "No failure recorded after partial progress");

        // ---- no effect at all still fails, so a broken chain cannot loop forever ----
        FakeBackend idleBackend = new FakeBackend();
        ChainRunner idle = new ChainRunner(ChainPlan.seed(trees), idleBackend, 0);
        GameState idleState = state(false, 0, 0);
        idle.step(idleState, 0);
        idle.step(idleState, 10);
        idle.step(idleState, 20);
        ChainRunner.Result failed = idle.step(idleState, 31_000);
        check(failed == ChainRunner.Result.FAILED, "Chain with zero observed effects fails");
        check(idle.failure().startsWith("EFFECT_TIMEOUT"), "Failure reason is an effect timeout");

        System.out.println("PASS " + checks + " inventory/continuous-chain recovery cases");
    }
}
