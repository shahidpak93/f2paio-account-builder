package com.example.f2paio;

import java.util.List;
import java.util.Map;

public final class ProductionRecipes
{
    private static final Map<String, List<ItemNeed>> RECIPES = Map.ofEntries(
        Map.entry("smelt_bronze",List.of(new ItemNeed(436,"Copper ore",8),new ItemNeed(438,"Tin ore",8))),
        Map.entry("smelt_iron",List.of(new ItemNeed(440,"Iron ore",10))),
        Map.entry("forge_bronze",List.of(new ItemNeed(2347,"Hammer",1),new ItemNeed(2349,"Bronze bar",8))),
        Map.entry("pray_bones",List.of(new ItemNeed(526,"Bones",8))),
        Map.entry("pray_big_bones",List.of(new ItemNeed(532,"Big bones",8))),
        Map.entry("burn_oak",List.of(new ItemNeed(590,"Tinderbox",1),new ItemNeed(1521,"Oak logs",8))),
        Map.entry("burn_willow",List.of(new ItemNeed(590,"Tinderbox",1),new ItemNeed(1519,"Willow logs",8))),
        Map.entry("burn_maple",List.of(new ItemNeed(590,"Tinderbox",1),new ItemNeed(1517,"Maple logs",8))),
        Map.entry("burn_yew",List.of(new ItemNeed(590,"Tinderbox",1),new ItemNeed(1515,"Yew logs",8))),
        Map.entry("burn_logs",List.of(new ItemNeed(590,"Tinderbox",1),new ItemNeed(1511,"Logs",8))),
        Map.entry("chocolate", List.of(new ItemNeed(1973, "Chocolate bar", 10), new ItemNeed(946, "Knife", 1))),
        Map.entry("string_gold", List.of(new ItemNeed(1673, "Gold amulet (u)", 10), new ItemNeed(1759, "Ball of wool", 10))),
        Map.entry("tan_soft", List.of(new ItemNeed(1739, "Cowhide", 10))),
        Map.entry("tan_hard", List.of(new ItemNeed(1739, "Cowhide", 10))),
        Map.entry("cook_shrimp", List.of(new ItemNeed(317, "Raw shrimps", 10))),
        Map.entry("cook_trout", List.of(new ItemNeed(335, "Raw trout", 10))),
        Map.entry("cook_salmon", List.of(new ItemNeed(331, "Raw salmon", 10))),
        Map.entry("cook_tuna", List.of(new ItemNeed(359, "Raw tuna", 10))),
        Map.entry("cook_lobster", List.of(new ItemNeed(377, "Raw lobster", 10))));

    public static List<ItemNeed> inputs(String id) { return RECIPES.containsKey(id) ? RECIPES.get(id) : F2pProduction.inputs(id); }
    private ProductionRecipes() { }
}
