package com.example.f2paio;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntToLongFunction;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/** Offline checks for bank liquidation and the account-building curriculum. */
public final class BankCurriculumTests
{
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    private static GameState state(Map<Skill, Integer> levels, Map<String, String> quests, long coins)
    {
        var lv = new EnumMap<Skill, Integer>(Skill.class);
        var xp = new EnumMap<Skill, Integer>(Skill.class);
        for (Skill s : Skill.values()) if (s != Skill.OVERALL) { lv.put(s, levels.getOrDefault(s, 1)); xp.put(s, 0); }
        return new GameState("test", new WorldPoint(3222, 3218, 0), 0, 1, false, false, 100,
            false, lv, xp, quests, coins);
    }

    static void run()
    {
        // ---- bank liquidation ----
        var bank = List.of(new ResourceItem(995, "Coins", 5000), new ResourceItem(1511, "Logs", 100),
            new ResourceItem(1265, "Bronze pickaxe", 1), new ResourceItem(440, "Iron ore", 20));
        Set<String> allowed = Set.of("Logs", "Iron ore", "Bronze pickaxe");
        IntToLongFunction prices = id -> id == 1511 ? 50 : id == 440 ? 400 : id == 1265 ? 30 : 0;
        var pick = BankSalePlanner.choose(bank, allowed, Set.of("Bronze pickaxe"), 500, 20, prices);
        check(pick != null && "Iron ore".equals(pick.name()), "Highest-value bank stack is chosen");
        check(pick.quantity() == 2 && pick.value() == 800, "Quantity covers the shortfall and value is reported");
        check(BankSalePlanner.choose(List.of(new ResourceItem(1511, "Logs", 1)), allowed, Set.of(), 500, 20, prices) == null,
            "Stack below the minimum liquidation value is ignored");
        check(BankSalePlanner.choose(bank, allowed, Set.of("Logs", "Iron ore", "Bronze pickaxe"), 500, 20, prices) == null,
            "Reserved and tool names are never sold");
        check(BankSalePlanner.choose(List.of(new ResourceItem(995, "Coins", 9999)), Set.of("Coins"), Set.of(), 500, 20, prices) == null,
            "Coins are never a sale candidate");
        var small = BankSalePlanner.choose(List.of(new ResourceItem(1511, "Logs", 100)), allowed, Set.of(), 300, 20, prices);
        check(small != null && small.quantity() == 6, "Quantity is limited by the GP shortfall");

        // ---- curriculum ----
        var levels = new EnumMap<Skill, Integer>(Skill.class);
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) levels.put(skill, 50);
        levels.put(Skill.WOODCUTTING, 5); levels.put(Skill.MINING, 9); levels.put(Skill.FISHING, 3);
        var config = new F2pAioConfig();
        GameState rich = state(levels, Map.of(), 1000);
        var curriculum = new Curriculum();
        check(curriculum.bonus(Methods.get("cooks"), rich, config) >= 140, "Ready quest gets top curriculum priority");
        check(curriculum.bonus(Methods.get("shrimp"), rich, config) == 60, "Lowest trainable skill activity is boosted (45 + 15 first-session skilling)");
        check(curriculum.bonus(Methods.get("trees"), rich, config) == 35, "Other skills get the next-milestone bonus plus the first-session skilling boost");
        check(curriculum.bonus(Methods.get("loot_ashes"), rich, config) < 0, "Ground loot is penalised once emergency coins are not needed");
        GameState poor = state(levels, Map.of(), 50);
        check(curriculum.bonus(Methods.get("loot_ashes"), poor, config) == 0, "Ground loot allowed below 150 coins");
        check(curriculum.bonus(Methods.get("dragon_slayer"), rich, config) < 0, "Far-gated combat quest is deprioritised");
        var money = new F2pAioConfig(); money.accountBuilder = false; money.taskMode = "MONEY";
        check(curriculum.bonus(Methods.get("trees"), rich, money) == 0, "Money mode disables curriculum shaping");
        check(Curriculum.nextMilestone(1) == 10 && Curriculum.nextMilestone(10) == 20
            && Curriculum.nextMilestone(41) == 60 && Curriculum.nextMilestone(99) == 99,
            "Milestone ladder boundaries are exact");

        System.out.println("PASS " + checks + " bank-sale/curriculum cases");
    }
}
