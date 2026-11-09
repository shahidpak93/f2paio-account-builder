package com.example.f2paio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Requirements
{
    public static final java.util.Set<String> FOOD = java.util.Set.of("Shrimps", "Anchovies", "Sardine", "Herring", "Cooked chicken", "Cooked meat", "Bread", "Trout", "Salmon", "Pike", "Tuna", "Lobster", "Bass", "Swordfish");
    private static final Map<String, Integer> IDS = Map.ofEntries(
        Map.entry("Redberry pie",2325), Map.entry("Iron bar",2351), Map.entry("Bucket of milk", 1927), Map.entry("Pot of flour", 1933), Map.entry("Egg", 1944),
        Map.entry("Ball of wool", 1759), Map.entry("Clay", 434), Map.entry("Copper ore", 436),
        Map.entry("Iron ore", 440), Map.entry("Eye of newt", 221), Map.entry("Onion", 1957),
        Map.entry("Burnt meat", 2146), Map.entry("Small fishing net", 303), Map.entry("Fly fishing rod", 309),
        Map.entry("Fishing rod", 307), Map.entry("Fishing bait", 313), Map.entry("Feather", 314),
        Map.entry("Lobster pot", 301), Map.entry("Harpoon", 311), Map.entry("Trout", 333),
        Map.entry("Cadava berries", 753), Map.entry("White apron", 1005), Map.entry("Spade", 952),
        Map.entry("Coins", 995), Map.entry("Red bead", 1470), Map.entry("Yellow bead", 1472),
        Map.entry("Black bead", 1474), Map.entry("White bead", 1476),
        Map.entry("Blue dye", 1767), Map.entry("Orange dye", 1769), Map.entry("Green dye", 1771));
    /** Own item requirements for a quest, used when the helper reports no tradeable gap. */
    public List<ItemNeed> questNeeds(String questId, GameState state)
    {
        List<ItemNeed> needs = new ArrayList<>();
        new Quests().requirements(questId).forEach((name, count) -> {
            Integer id = IDS.get(name); if (id == null) return;
            int have = state.items.stream().filter(i -> !i.noted && i.id == id).mapToInt(i -> i.quantity).sum();
            int deficit = count - have;
            if (deficit > 0) needs.add(new ItemNeed(id, name, deficit));
        });
        return needs;
    }

    static boolean bankMatch(ItemNeed need, ResourceItem item, GameState state) {
        if (need.id==1351) return ToolPolicy.usable("axe",item.canonicalId(),state.levels.getOrDefault(net.runelite.api.Skill.WOODCUTTING,1));
        if (need.id==1265) return ToolPolicy.usable("pickaxe",item.canonicalId(),state.levels.getOrDefault(net.runelite.api.Skill.MINING,1));
        return item.canonicalId()==need.id || (need.id==333 && FOOD.contains(item.name));
    }

    public List<ItemNeed> missing(Method method, GameState state)
    {
        List<ItemNeed> needs = new ArrayList<>();
        for (ItemNeed input : ProductionRecipes.inputs(method.id))
            if (state.items.stream().filter(i -> !i.noted && i.id==input.id).mapToInt(i -> i.quantity).sum()
                < F2pProduction.minimum(method.id,input.id)) needs.add(input);
        F2pProduction.Spec spec=F2pProduction.get(method.id);
        if (spec!=null && spec.altar() && state.count(spec.accessName)==0 && !spec.equipped(state))
            needs.add(new ItemNeed(spec.accessId,spec.accessName,1));
        if (method.id.startsWith("tan_") && state.coins < 30) needs.add(new ItemNeed(995, "Coins", 30));
        for (String tool : method.tools)
        {
            if (ToolPolicy.has(tool, state)) continue;
            if (tool.equals("axe")) needs.add(new ItemNeed(1351, "Bronze axe", 1));
            else if (tool.equals("pickaxe")) needs.add(new ItemNeed(1265, "Bronze pickaxe", 1));
            else if (IDS.containsKey(tool)) needs.add(new ItemNeed(IDS.get(tool), tool,
                tool.equals("Feather") || tool.equals("Fishing bait") ? 100 : 1));
        }
        if (method.kind == Method.Kind.QUEST)
        {
            if ("NOT_STARTED".equals(state.quests.get(method.label)))
                new Quests().requirements(method.id).forEach((name, count) -> {
                    int deficit = count - state.items.stream().filter(i -> !i.noted && i.id == IDS.get(name)).mapToInt(i -> i.quantity).sum();
                    if (deficit > 0) needs.add(new ItemNeed(IDS.get(name), name, name.equals("Coins") ? count : deficit));
                });
        }
        if (method.kind == Method.Kind.COMBAT || ProgressionPolicy.combatQuest(method))
        {
            if (CombatPolicy.needsFood(method))
            {
                int food = state.items.stream().filter(i -> !i.noted && FOOD.contains(i.name)).mapToInt(i -> i.quantity).sum();
                if (food < 5) needs.add(new ItemNeed(333, "Trout", 10 - food));
            }
            // Safe melee (chickens/cows) works with fists: no weapon, armour or food required.
            if (method.kind != Method.Kind.COMBAT || CombatPolicy.needsGear(method))
                needs.addAll(GearCatalog.essentials(method,state));
        }
        if (List.of("lobster", "tuna", "swordfish").contains(method.id) && state.coins < 60)
            needs.add(new ItemNeed(995, "Coins", 60));
        return needs;
    }
}
