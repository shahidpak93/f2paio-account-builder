package com.example.f2paio;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@SuppressWarnings("deprecation")
public final class GameState
{
    private static Map<String, String> questCache = Map.of();
    private static long questReadAt;
    private static String questAccount = "";
    public final String account;
    public final WorldPoint location;
    public final long xp;
    public final int inventory;
    public final boolean busy;
    public final boolean combat;
    public final double health;
    public final boolean members;
    public final Map<Skill, Integer> levels;
    public final Map<Skill, Integer> experience;
    public final Map<String, String> quests;
    public final long coins;
    public java.util.List<ResourceItem> items = java.util.List.of();
    public java.util.List<ResourceItem> equipment = java.util.List.of();
    public boolean animating;
    public boolean moving;
    public int world;
    public int questPoints;
    public java.util.Map<String,Boolean> rewardFlags=java.util.Map.of();
    public boolean pvpWorld;
    public boolean ironman;
    public int attackStyle=-1,weaponType=-1;
    /** Raw client run energy, 0..10000. */
    public int runEnergy;
    /** Whether the run orb is currently on. */
    public boolean runEnabled;

    public int runPercent() { return RunEnergy.percent(runEnergy); }

    GameState(String account, WorldPoint location, long xp, int inventory,
        boolean busy, boolean combat, double health, boolean members, Map<Skill, Integer> levels,
        Map<Skill, Integer> experience, Map<String, String> quests, long coins)
    {
        this.account = account;
        this.location = location;
        this.xp = xp;
        this.inventory = inventory;
        this.busy = busy;
        this.combat = combat;
        this.health = health;
        this.members = members;
        this.levels = Map.copyOf(levels);
        this.experience = Map.copyOf(experience); this.quests = Map.copyOf(quests); this.coins = coins;
    }

    public static GameState capture()
    {
        if (!Microbot.isLoggedIn()) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            var player = client.getLocalPlayer();
            if (player == null || player.getName() == null || player.getWorldLocation() == null) return null;
            Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
            Map<Skill, Integer> experience = new EnumMap<>(Skill.class);
            long xp = 0;
            for (Skill skill : Skill.values())
            {
                if (skill == Skill.OVERALL) continue;
                levels.put(skill, client.getRealSkillLevel(skill));
                xp += client.getSkillExperience(skill);
                experience.put(skill, client.getSkillExperience(skill));
            }
            long now = System.currentTimeMillis();
            if (!questAccount.equals(player.getName()) || now - questReadAt >= 10_000)
            {
                Map<String, String> quests = new java.util.LinkedHashMap<>();
                for (net.runelite.api.Quest quest : net.runelite.api.Quest.values())
                    quests.put(quest.getName(), quest.getState(client).name());
                questCache = Map.copyOf(quests); questReadAt = now; questAccount = player.getName();
            }
            var inventoryItems = Rs2Inventory.items().map(i -> new ResourceItem(i.getId(), i.getName(), i.getQuantity(), i.isNoted(), i.getUnNotedId(), i.getNotedId()!=i.getUnNotedId(),i.isStackable()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
            GameState result = new GameState(player.getName(), player.getWorldLocation(), xp,
                Rs2Inventory.count(), Rs2Player.isAnimating() || Rs2Player.isMoving()
                    || Rs2Player.isInteracting(), Rs2Player.isInCombat(),
                Rs2Player.getHealthPercentage(), client.getWorldType().contains(net.runelite.api.WorldType.MEMBERS),
                levels, experience, questCache, quantity(inventoryItems, 995));
            result.items = inventoryItems;
            result.equipment = net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.all()
                .map(i -> new ResourceItem(i.getId(), i.getName(), i.getQuantity(), i.isNoted(), i.getUnNotedId(), i.getNotedId()!=i.getUnNotedId(),i.isStackable()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
            result.animating = player.getAnimation() != -1; result.moving = Rs2Player.isMoving();
            result.questPoints=client.getVarpValue(net.runelite.api.VarPlayer.QUEST_POINTS);
            result.rewardFlags=java.util.Map.of(
                "sos_flap",client.getVarbitValue(net.runelite.api.gameval.VarbitID.SOS_EMOTE_FLAP)>0,
                "sos_doh",client.getVarbitValue(net.runelite.api.gameval.VarbitID.SOS_EMOTE_DOH)>0,
                "sos_idea",client.getVarbitValue(net.runelite.api.gameval.VarbitID.SOS_EMOTE_IDEA)>0,
                "sos_stamp",client.getVarbitValue(net.runelite.api.gameval.VarbitID.SOS_EMOTE_STAMP)>0);
            result.attackStyle=client.getVarpValue(net.runelite.api.VarPlayer.ATTACK_STYLE);
            result.weaponType=client.getVarbitValue(net.runelite.api.Varbits.EQUIPPED_WEAPON_TYPE);
            result.ironman=client.getVarbitValue(net.runelite.api.gameval.VarbitID.IRONMAN)!=0;
            result.world = client.getWorld(); result.pvpWorld = client.getWorldType().contains(net.runelite.api.WorldType.PVP);
            result.runEnergy = client.getEnergy();
            result.runEnabled = client.getVarpValue(RunEnergy.RUN_VARP) == 1;
            return result;
        }).orElse(null);
    }

    static long quantity(java.util.List<ResourceItem> items, int id)
    {
        return items.stream().filter(i -> i.id == id).mapToLong(i -> i.quantity).sum();
    }

    public String progressSignature()
    {
        return location.toString() + ":" + xp + ":" + inventory;
    }

    public int total(String name) { return items.stream().filter(i -> name.equalsIgnoreCase(i.name)).mapToInt(i -> i.quantity).sum(); }

    public int count(String name)
    {
        return items.stream().filter(i -> !i.noted && name.equalsIgnoreCase(i.name)).mapToInt(i -> i.quantity).sum();
    }
}
