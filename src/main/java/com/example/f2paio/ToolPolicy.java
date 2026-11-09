package com.example.f2paio;

import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

public final class ToolPolicy
{
    private static final Map<Integer, Integer> AXES = Map.of(
        1351, 1, 1349, 1, 1353, 6, 1361, 11, 1355, 21, 1357, 31, 1359, 41);
    private static final Map<Integer, Integer> PICKS = Map.of(
        1265, 1, 1267, 1, 1269, 6, 12297, 11, 1273, 21, 1271, 31, 1275, 41);
    private static final Map<String, Integer> OTHER = Map.of("Small fishing net", 303,
        "Fly fishing rod", 309, "Fishing rod", 307, "Feather", 314, "Fishing bait", 313,
        "Lobster pot", 301, "Harpoon", 311);

    public static boolean usable(String tool, int itemId, int level)
    {
        Map<Integer, Integer> allowed = tool.equals("axe") ? AXES : tool.equals("pickaxe") ? PICKS : Map.of();
        return allowed.containsKey(itemId) && level >= allowed.get(itemId);
    }

    public static boolean has(String tool, GameState state)
    {
        if (tool.equals("axe") || tool.equals("pickaxe"))
        {
            int level = state.levels.get(tool.equals("axe") ? Skill.WOODCUTTING : Skill.MINING);
            return java.util.stream.Stream.concat(state.items.stream(), state.equipment.stream())
                .anyMatch(item -> !item.noted && usable(tool, item.id, level));
        }
        Integer id = OTHER.get(tool);
        return id != null && state.items.stream().anyMatch(item -> !item.noted && item.id == id && item.quantity > 0);
    }

    public static boolean hasTools(Method method, GameState state)
    {
        return method.tools.stream().allMatch(tool -> has(tool, state));
    }

    private ToolPolicy() { }
}
