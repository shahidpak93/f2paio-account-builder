package com.example.f2paio;

import java.util.Set;

/**
 * Combat availability rules.
 *
 * <p>Safe low-level training (chickens, cows) must not require food or armour: a fresh account with
 * no GP should still be able to train Attack. Ranged/magic training still needs its weapon and
 * ammunition/runes, and higher-risk combat keeps the food requirement.</p>
 */
public final class CombatPolicy
{
    private static final Set<String> SAFE_MELEE = Set.of("chickens", "cows");
    private static final Set<String> SAFE_RANGED = Set.of("range_chickens");
    private static final Set<String> SAFE_MAGIC = Set.of("mage_chickens");

    public static boolean safe(Method method)
    {
        return method != null && method.kind == Method.Kind.COMBAT
            && (SAFE_MELEE.contains(method.id) || SAFE_RANGED.contains(method.id) || SAFE_MAGIC.contains(method.id)
                || method.id.equals("range_cows") || method.id.equals("mage_cows"));
    }

    public static boolean ready(Method method, GameState state) {
        if (method.kind != Method.Kind.COMBAT || method.level < 15) return true;
        int floor = method.level >= 30 ? 20 : 8;
        return state.levels.getOrDefault(net.runelite.api.Skill.HITPOINTS, 10) >= floor + 2
            && state.levels.getOrDefault(net.runelite.api.Skill.DEFENCE, 1) >= floor
            && (method.skill != net.runelite.api.Skill.ATTACK
                || state.levels.getOrDefault(net.runelite.api.Skill.STRENGTH, 1) >= floor);
    }

    public static boolean needsFood(Method method)
    {
        return method != null && (method.kind == Method.Kind.COMBAT || ProgressionPolicy.combatQuest(method))
            && !safe(method);
    }

    /** Fists are fine for safe melee; ranged/magic still need their weapon and ammo/runes. */
    public static boolean needsGear(Method method)
    {
        if (method == null || method.kind != Method.Kind.COMBAT) return false;
        return !SAFE_MELEE.contains(method.id);
    }

    private CombatPolicy() { }
}
