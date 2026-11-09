package com.example.f2paio;
import java.util.List;
import net.runelite.api.TileItem;
public final class LootRegressionTests {
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    static void run() {
        check(GroundLoot.eligible(TileItem.OWNERSHIP_OTHER, false, 0), "Main can take public other-player fish");
        check(!GroundLoot.eligible(TileItem.OWNERSHIP_OTHER, true, 0), "Private other-player drop rejected");
        check(!GroundLoot.eligible(TileItem.OWNERSHIP_OTHER, false, 1), "Ironman cannot take other-player fish");
        check(GroundLoot.eligible(TileItem.OWNERSHIP_SELF, true, 1), "Own private drop remains takeable");
        check(GroundLoot.eligible(TileItem.OWNERSHIP_GROUP, true, 4), "Group-owned drop remains takeable");
        check(GroundLoot.eligible(TileItem.OWNERSHIP_NONE, false, 1), "Public world spawn remains takeable");
        check(GroundLoot.matches(List.of("Raw trout", "Raw salmon"), "<col=ff0000>raw TROUT</col>"), "Normalized fish names");
        check(!GroundLoot.matches(List.of("Raw trout"), "Trout"), "Do not confuse raw and cooked products");
        check(!GroundLoot.matches(List.of("Raw trout"), null), "Unknown name rejected");
        check(GameState.quantity(List.of(new ResourceItem(995,"Coins",848), new ResourceItem(1511,"Logs",1)),995)==848,
            "Coin stack quantity must be 848, not one inventory slot");
        check(GameState.quantity(List.of(),995)==0, "Missing coins are zero");
        var quiet = world(301, 120, java.util.EnumSet.noneOf(net.runelite.http.api.worlds.WorldType.class));
        var busy = world(308, 1800, java.util.EnumSet.noneOf(net.runelite.http.api.worlds.WorldType.class));
        var full = world(316, 2000, java.util.EnumSet.noneOf(net.runelite.http.api.worlds.WorldType.class));
        var members = world(302, 1900, java.util.EnumSet.of(net.runelite.http.api.worlds.WorldType.MEMBERS));
        var pvp = world(371, 1900, java.util.EnumSet.of(net.runelite.http.api.worlds.WorldType.PVP));
        check(WorldLootPolicy.select(List.of(quiet,busy,full,members,pvp),false).getId()==308,
            "Public fish looting picks most populated eligible normal F2P world, excluding members/PvP/full");
        check(WorldLootPolicy.select(List.of(members,pvp),false)==null,"No ordinary eligible world yields no target");
        System.out.println("PASS 13 loot/coin/world regression cases");
    }
    private static net.runelite.http.api.worlds.World world(int id,int players,
        java.util.EnumSet<net.runelite.http.api.worlds.WorldType> types) {
        return net.runelite.http.api.worlds.World.builder().id(id).players(players).types(types).build();
    }
}
