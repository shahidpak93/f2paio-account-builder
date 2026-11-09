package com.example.f2paio;

import java.nio.file.Files;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/** Offline checks for account knowledge, one-time rewards, safe combat and category balance. */
public final class AccountKnowledgeTests
{
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    private static GameState state(Map<String, String> quests, List<ResourceItem> items)
    {
        var levels = new EnumMap<Skill, Integer>(Skill.class);
        var experience = new EnumMap<Skill, Integer>(Skill.class);
        for (Skill s : Skill.values()) if (s != Skill.OVERALL) { levels.put(s, 1); experience.put(s, 0); }
        levels.put(Skill.ATTACK, 5);
        GameState state = new GameState("test", new WorldPoint(3222, 3218, 0), 0, 1, false, false, 100,
            false, levels, experience, quests, 0);
        state.items = items;
        return state;
    }

    static void run() throws Exception
    {
        var ledger = FilesStore.ROOT.resolve("test-unlocks.json");
        Files.deleteIfExists(ledger);
        GameState empty = state(Map.of(), List.of());

        // One-time rewards are known, persisted and never repeated.
        AccountKnowledge knowledge = new AccountKnowledge(ledger);
        check(knowledge.available(empty).stream().anyMatch(r -> r.id.equals("stronghold_of_security")),
            "Stronghold of Security is a known one-time opportunity");
        check(knowledge.doneRewards().isEmpty(), "Nothing is claimed on a fresh ledger");
        knowledge.record("stronghold_of_security");
        check(knowledge.done("stronghold_of_security"), "Claim is recorded");
        AccountKnowledge reloaded = new AccountKnowledge(ledger);
        check(reloaded.done("stronghold_of_security") && reloaded.available(empty).isEmpty(),
            "Claim survives restart and is no longer offered");

        // Owning the reward item also counts as claimed.
        Files.deleteIfExists(ledger);
        AccountKnowledge byItem = new AccountKnowledge(ledger);
        GameState boots = state(Map.of(), List.of(new ResourceItem(9005, "Fancy boots", 1)));
        byItem.refresh(boots);
        check(byItem.done("stronghold_of_security"), "Owning the boots marks the one-time reward done");

        // Quests are one-time; farmable methods may repeat.
        check(!AccountKnowledge.farmable(Methods.get("cooks")), "Quest content is one-time");
        check(AccountKnowledge.farmable(Methods.get("trees")) && AccountKnowledge.farmable(Methods.get("shrimp")),
            "Skilling and gathering are farmable and repeatable");

        // Safe combat works with fists and no food on a broke account.
        check(new Requirements().missing(Methods.get("chickens"), empty).isEmpty(),
            "Chickens need no food, weapon or armour");
        check(!CombatPolicy.needsFood(Methods.get("chickens")), "Safe melee has no food requirement");
        var rangedMissing = new Requirements().missing(Methods.get("range_chickens"), empty);
        check(rangedMissing.stream().noneMatch(n -> "Trout".equals(n.name)),
            "Safe ranged training does not demand food");
        check(rangedMissing.stream().anyMatch(n -> "Shortbow".equals(n.name) || n.name.startsWith("Bronze") || n.name.startsWith("Shortbow")),
            "Ranged training still needs a bow");

        // Combat gets a turn when it has been ignored, then lower priority after a pick.
        var config = new F2pAioConfig();
        var curriculum = new Curriculum();
        GameState combatState = state(Map.of(), List.of());
        check(curriculum.bonus(Methods.get("chickens"), combatState, config, 0) == 60,
            "Ignored combat category receives a strong boost");
        curriculum.note(Methods.get("chickens"), 1_000);
        check(curriculum.bonus(Methods.get("chickens"), combatState, config, 2_000) == 10,
            "Combat is not over-prioritised immediately after a session");

        // Unlocks and readiness are visible.
        GameState unlocked = state(Map.of("Rune Mysteries", "FINISHED"), List.of());
        check(new AccountKnowledge(ledger).unlocked(unlocked).contains("Rune essence mine teleport"),
            "Quest unlocks are recognised");
        check(new AccountKnowledge(ledger).combatReady(empty), "Safe combat is reported ready on a fresh account");
        check(new AccountKnowledge(ledger).skillingReady(empty), "Basic skilling is reported ready");

        Files.deleteIfExists(ledger);
        System.out.println("PASS " + checks + " account-knowledge/one-time/combat cases");
    }
}
