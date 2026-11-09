package com.example.f2paio;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

@SuppressWarnings("removal")
public final class Quests
{
    private final Dialogue dialogue = new Dialogue();

    public Quest quest(String id)
    {
        switch (id)
        {
            case "cooks": return Quest.COOKS_ASSISTANT;
            case "sheep": return Quest.SHEEP_SHEARER;
            case "doric": return Quest.DORICS_QUEST;
            case "witch": return Quest.WITCHS_POTION;
            case "rune_mysteries": return Quest.RUNE_MYSTERIES;
            case "romeo": return Quest.ROMEO__JULIET;
            case "pirates": return Quest.PIRATES_TREASURE;
            case "imp_catcher": return Quest.IMP_CATCHER;
            case "restless_ghost": return Quest.THE_RESTLESS_GHOST;
            case "x_marks": return Quest.X_MARKS_THE_SPOT;
            case "knights_sword": return Quest.THE_KNIGHTS_SWORD;
            case "goblin_diplomacy": return Quest.GOBLIN_DIPLOMACY;
            case "ernest": return Quest.ERNEST_THE_CHICKEN;
            case "vampyre": return Quest.VAMPYRE_SLAYER;
            case "demon_slayer": return Quest.DEMON_SLAYER;
            case "black_knights": return Quest.BLACK_KNIGHTS_FORTRESS;
            case "prince_ali": return Quest.PRINCE_ALI_RESCUE;
            case "below_ice": return Quest.BELOW_ICE_MOUNTAIN;
            case "corsair": return Quest.THE_CORSAIR_CURSE;
            case "dragon_slayer": return Quest.DRAGON_SLAYER_I;
            default: throw new IllegalArgumentException("Unknown quest");
        }
    }

    public QuestState state(String id)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
            () -> quest(id).getState(Microbot.getClient())).orElse(null);
    }

    public Map<String, Integer> requirements(String id)
    {
        Map<String, Integer> items = new LinkedHashMap<>();
        switch (id)
        {
            case "cooks":
                items.put("Bucket of milk", 1); items.put("Pot of flour", 1); items.put("Egg", 1); break;
            case "sheep": items.put("Ball of wool", 20); break;
            case "doric":
                items.put("Clay", 6); items.put("Copper ore", 4); items.put("Iron ore", 2); break;
            case "witch":
                items.put("Eye of newt", 1); items.put("Onion", 1); items.put("Burnt meat", 1); break;
            case "romeo": items.put("Cadava berries", 1); break;
            case "pirates": items.put("White apron", 1); items.put("Spade", 1); items.put("Coins", 70); break;
            case "imp_catcher":
                items.put("Red bead", 1); items.put("Yellow bead", 1);
                items.put("Black bead", 1); items.put("White bead", 1); break;
            case "knights_sword": items.put("Redberry pie",1); items.put("Iron bar",2); break;
            case "x_marks": items.put("Spade",1); break;
            case "restless_ghost": break;
            case "goblin_diplomacy":
                items.put("Blue dye", 1); items.put("Orange dye", 1); items.put("Green dye", 1); break;
            case "ernest": break;
            case "vampyre": break;
            case "demon_slayer": break;
            case "black_knights": break;
            case "prince_ali": break;
            case "below_ice": break;
            case "corsair": break;
            case "dragon_slayer": break;
            case "rune_mysteries": break;
            default: throw new IllegalArgumentException("Unknown quest");
        }
        return items;
    }

    public String missing(String id)
    {
        StringBuilder result = new StringBuilder();
        requirements(id).forEach((name, quantity) -> {
            if (Rs2Inventory.count(name) < quantity) result.append(quantity).append(' ').append(name).append("; ");
        });
        return result.toString();
    }

    public boolean step(Method method, Watchdog watchdog)
    {
        String[] options;
        String npc;
        switch (method.id)
        {
            case "cooks": npc = "Cook"; options = new String[]{"What's wrong?", "Can I help?",
                "I'm always happy to help a cook in distress.", "Yes", "I have brought"}; break;
            case "sheep": npc = "Fred the Farmer"; options = new String[]{"I'm looking for a quest",
                "Yes, okay. I can do that.", "I need to talk to you about shearing these sheep!", "I'm back", "I have some balls of wool", "Yes"}; break;
            case "doric": npc = "Doric"; options = new String[]{"I wanted to use your anvils.",
                "Yes, I will get you the materials.", "I've brought", "Yes"}; break;
            case "witch": npc = "Hetty"; options = new String[]{"I am in search of a quest.",
                "Yes, help me become one with my darker side.", "Yes", "I have"}; break;
            default: throw new IllegalArgumentException("Unknown quest");
        }
        if (dialogue.step(watchdog, options)) return true;
        if (Rs2Dialogue.hasSelectAnOption()) return false;
        if (method.id.equals("witch") && state(method.id) == QuestState.IN_PROGRESS)
        {
            int stage = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getVarpValue(67)).orElse(-1);
            if (stage == 2) return Rs2GameObject.interact("Cauldron", "Drink From", false);
            if (!Rs2Inventory.contains("Rat's tail"))
            {
                if (net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem
                    .interact("Rat's tail", "Take", Tuning.INTERACTION_RANGE)) return true;
                return Rs2Npc.interact("Rat", "Attack");
            }
        }
        return Rs2Npc.interact(npc, "Talk-to");
    }
}
