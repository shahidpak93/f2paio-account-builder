package com.example.f2paio;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/** Offline coverage for the run-orb policy: no client, no clicks, pure decisions. */
public final class RunEnergyTests
{
    private static int checks;

    private static void check(boolean ok, String message)
    {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static GameState state(int level)
    {
        var levels = new EnumMap<Skill, Integer>(Skill.class);
        var experience = new EnumMap<Skill, Integer>(Skill.class);
        for (Skill skill : Skill.values())
            if (skill != Skill.OVERALL) { levels.put(skill, level); experience.put(skill, 0); }
        return new GameState("test", new WorldPoint(3286, 3363, 0), 0, 1, false, false, 100,
            false, levels, experience, Map.of(), 1000);
    }

    static void run()
    {
        // Already running: never click again.
        check(RunEnergy.decide(true, false, true, 9_000, 40) == RunEnergy.Decision.NONE,
            "Run already on is left alone");
        // The reported bug: a long trip with energy must turn the orb on.
        check(RunEnergy.decide(true, false, false, 3_000, 40) == RunEnergy.Decision.ENABLE,
            "Long trip above the arm threshold turns run on");
        // Short repositioning does not justify an orb click.
        check(RunEnergy.decide(true, false, false, 9_000, 5) == RunEnergy.Decision.NONE,
            "Short repositioning sends no run click");
        // Low energy walks first instead of clicking a dead orb.
        check(RunEnergy.decide(true, false, false, 1_500, 40) == RunEnergy.Decision.HOLD,
            "Long trip below the arm threshold is walked first");
        // Dialogue/bank/shop/GE focus must never be stolen by the orb click.
        check(RunEnergy.decide(true, true, false, 9_000, 40) == RunEnergy.Decision.HOLD,
            "Blocking interface suppresses the run click");
        // No client action while logged out.
        check(RunEnergy.decide(false, false, false, 9_000, 40) == RunEnergy.Decision.NONE,
            "Logged-out session sends no click");
        // Boundaries are inclusive and documented by the test.
        check(RunEnergy.decide(true, false, false, RunEnergy.ARM_AT, RunEnergy.MIN_TRIP_TILES)
            == RunEnergy.Decision.ENABLE, "Arm threshold and trip distance are inclusive");
        check(RunEnergy.decide(true, false, false, RunEnergy.ARM_AT - 1, RunEnergy.MIN_TRIP_TILES)
            == RunEnergy.Decision.HOLD, "One energy unit below the threshold walks");
        check(RunEnergy.decide(true, false, false, RunEnergy.ARM_AT, RunEnergy.MIN_TRIP_TILES - 1)
            == RunEnergy.Decision.NONE, "One tile below the trip minimum does not click");
        check(RunEnergy.percent(0) == 0 && RunEnergy.percent(7_500) == 75 && RunEnergy.percent(20_000) == 100,
            "Raw energy converts to a clamped percentage");

        // GameState exposes the live snapshot fields the policy and heartbeat use.
        GameState state = state(1);
        check(!state.runEnabled && state.runPercent() == 0, "GameState defaults to no run snapshot");
        state.runEnergy = 5_000;
        check(state.runPercent() == 50, "GameState exposes run energy as a percentage");
        state.runEnabled = true;
        check(RunEnergy.summary(state).contains("50%") && RunEnergy.summary(state).contains("on"),
            "Travel status summarises run energy and orb state");

        // Out of energy means the same distance is walked, so far goals lose a bounded amount.
        var near = new Method("near", "near", Method.Kind.OBJECT, Skill.MINING, 1,
            new String[] { "Copper rocks" }, "Mine", state.location,
            List.of("Copper ore"), List.of("pickaxe"));
        var far = new Method("far", "far", Method.Kind.OBJECT, Skill.MINING, 1,
            new String[] { "Copper rocks" }, "Mine", new WorldPoint(2980, 3230, 0),
            List.of("Copper ore"), List.of("pickaxe"));
        var choices = List.of(near, far);
        var planner = new AutonomousPlanner();
        GameState fresh = state(1);
        fresh.runEnergy = 9_000;
        GameState tired = state(1);
        tired.runEnergy = 0;
        double farFresh = planner.score(far, choices, fresh, "", null, null);
        double farTired = planner.score(far, choices, tired, "", null, null);
        check(farTired < farFresh, "Out-of-energy account pays extra travel cost on a far goal");
        check(Math.abs(planner.score(near, choices, fresh, "", null, null)
            - planner.score(near, choices, tired, "", null, null)) < 0.01,
            "Nearby goal travel cost is unaffected by energy");
        check(farFresh - farTired <= 15.01, "Extra travel cost stays bounded");

        System.out.println("PASS " + checks + " run-energy policy cases");
    }
}
