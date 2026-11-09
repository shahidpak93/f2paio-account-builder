package com.example.f2paio;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What may stay in the inventory when the bot banks or switches goals.
 *
 * <p>Before 0.9.3 only the current goal's products were deposited. Items left over from an
 * abandoned goal (raw fish from a failed fishing chain, ore from a quest detour) stayed in the
 * inventory forever, wasting slots and confusing the next supply scan. This class separates
 * "keep" from "junk" once, so every banking path uses the same rule.</p>
 */
public final class InventoryCleanup
{
    /** Build the keep-set for a goal: tool names, food, coins and its requirements. */
    public static Set<String> keepFor(Method current, GameState state, Set<String> reserved)
    {
        Set<String> keep = new HashSet<>(reserved);
        if (current != null)
        {
            keep.addAll(current.tools);
            keep.addAll(ProductionRecipes.inputs(current.id).stream().map(i -> i.name)
                .collect(java.util.stream.Collectors.toList()));
            if (current.kind == Method.Kind.QUEST) keep.addAll(new Quests().requirements(current.id).keySet());
        }
        keep.addAll(ProgressionPolicy.reserved(state));
        keep.addAll(Requirements.FOOD);
        for (ResourceItem item : state.equipment) if (item.name != null) keep.add(item.name);
        // Coins are only carried when the activity itself needs them (quest purchase, tanning, etc.).
        // Otherwise they are banked and the supply service withdraws them when a purchase is required.
        if (current != null && current.kind == Method.Kind.QUEST) keep.add("Coins");
        return keep;
    }

    /** Items that are neither coins nor protected; candidates for banking. */
    public static List<ResourceItem> unrelated(List<ResourceItem> items, Set<String> keep)
    {
        List<ResourceItem> result = new ArrayList<>();
        for (ResourceItem item : items)
        {
            if (item == null || item.quantity <= 0) continue;
            if (item.name == null || keep.contains(item.name)) continue;
            result.add(item);
        }
        return result;
    }

    private InventoryCleanup() { }
}
