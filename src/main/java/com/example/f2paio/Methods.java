package com.example.f2paio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

// F2P subset adapted from Microbot-Hub WoodcuttingTree, Rocks and Fish; see SOURCES.md.
public final class Methods
{
    private static final Map<String, Method> ALL = new LinkedHashMap<>();
    private static final java.util.Set<String> BUILTIN = new java.util.HashSet<>();
    private static final SkillProbation PROBATION = new SkillProbation(FilesStore.ROOT.resolve("learned-verification.json"));
    static
    {
        add("trees", "Trees - Lumbridge", Method.Kind.OBJECT, Skill.WOODCUTTING, 1,
            "Tree|Evergreen", "Chop down", 3192, 3223, "Logs", "axe");
        add("oak", "Oak - Draynor", Method.Kind.OBJECT, Skill.WOODCUTTING, 15,
            "Oak|Oak tree", "Chop down", 3103, 3279, "Oak logs", "axe");
        add("willow", "Willow - Draynor", Method.Kind.OBJECT, Skill.WOODCUTTING, 30,
            "Willow|Willow tree", "Chop down", 3087, 3237, "Willow logs", "axe");
        add("yew", "Yew - Edgeville", Method.Kind.OBJECT, Skill.WOODCUTTING, 60,
            "Yew|Yew tree", "Chop down", 3087, 3468, "Yew logs", "axe");
        add("copper", "Copper - Varrock southeast", Method.Kind.OBJECT, Skill.MINING, 1,
            "Copper rocks", "Mine", 3285, 3363, "Copper ore", "pickaxe");
        add("tin", "Tin - Varrock southeast", Method.Kind.OBJECT, Skill.MINING, 1,
            "Tin rocks", "Mine", 3285, 3363, "Tin ore", "pickaxe");
        add("iron", "Iron - Varrock southeast", Method.Kind.OBJECT, Skill.MINING, 15,
            "Iron rocks", "Mine", 3286, 3368, "Iron ore", "pickaxe");
        add("shrimp", "Shrimp - Lumbridge swamp", Method.Kind.FISHING, Skill.FISHING, 1,
            "Fishing spot", "Net", 3241, 3152, "Raw shrimps|Raw anchovies", "Small fishing net");
        add("trout", "Trout - Barbarian Village", Method.Kind.FISHING, Skill.FISHING, 20,
            "Rod Fishing spot|Fishing spot", "Lure", 3104, 3424, "Raw trout|Raw salmon",
            "Fly fishing rod|Feather");
        add("chickens", "Chickens - Lumbridge", Method.Kind.COMBAT, Skill.ATTACK, 1,
            "Chicken", "Attack", 3232, 3296, "Feather|Bones|Raw chicken", "");
        add("cows", "Cows - Lumbridge", Method.Kind.COMBAT, Skill.ATTACK, 5,
            "Cow|Cow calf", "Attack", 3257, 3276, "Cowhide|Bones|Raw beef", "");
        quest("cooks", "Cook's Assistant", 3208, 3213);
        quest("sheep", "Sheep Shearer", 3189, 3272);
        quest("doric", "Doric's Quest", 2951, 3450);
        quest("witch", "Witch's Potion", 2967, 3205);
        quest("rune_mysteries", "Rune Mysteries", 3210, 3220);
        quest("romeo", "Romeo & Juliet", 3213, 3423);
        quest("pirates", "Pirate's Treasure", 3051, 3252);
        quest("imp_catcher", "Imp Catcher", 3108, 3150);
        add("clay", "Clay - Varrock west", Method.Kind.OBJECT, Skill.MINING, 1,
            "Clay rocks", "Mine", 3181, 3372, "Clay", "pickaxe");
        add("silver", "Silver - Varrock west", Method.Kind.OBJECT, Skill.MINING, 20,
            "Silver rocks", "Mine", 3181, 3372, "Silver ore", "pickaxe");
        add("coal", "Coal - Barbarian Village", Method.Kind.OBJECT, Skill.MINING, 30,
            "Coal rocks", "Mine", 3082, 3420, "Coal", "pickaxe");
        add("gold", "Gold - Al Kharid", Method.Kind.OBJECT, Skill.MINING, 40,
            "Gold rocks", "Mine", 3295, 3285, "Gold ore", "pickaxe");
        add("mithril", "Mithril - Al Kharid", Method.Kind.OBJECT, Skill.MINING, 55,
            "Mithril rocks", "Mine", 3302, 3314, "Mithril ore", "pickaxe");
        add("adamant", "Adamantite - Al Kharid", Method.Kind.OBJECT, Skill.MINING, 70,
            "Adamantite rocks", "Mine", 3300, 3317, "Adamantite ore", "pickaxe");
        add("sardine", "Sardines - Lumbridge coast", Method.Kind.FISHING, Skill.FISHING, 5,
            "Fishing spot", "Bait", 3242, 3150, "Raw sardine|Raw herring", "Fishing rod|Fishing bait");
        add("herring", "Herring - Draynor", Method.Kind.FISHING, Skill.FISHING, 10,
            "Fishing spot", "Bait", 3086, 3230, "Raw herring|Raw sardine", "Fishing rod|Fishing bait");
        add("pike", "Pike - Barbarian Village", Method.Kind.FISHING, Skill.FISHING, 25,
            "Fishing spot", "Bait", 3104, 3424, "Raw pike", "Fishing rod|Fishing bait");
        add("lobster", "Lobsters - Karamja", Method.Kind.FISHING, Skill.FISHING, 40,
            "Fishing spot", "Cage", 2924, 3178, "Raw lobster", "Lobster pot");
        add("tuna", "Tuna - Karamja", Method.Kind.FISHING, Skill.FISHING, 35,
            "Fishing spot", "Harpoon", 2924, 3178, "Raw tuna|Raw swordfish", "Harpoon");
        add("swordfish", "Swordfish - Karamja", Method.Kind.FISHING, Skill.FISHING, 50,
            "Fishing spot", "Harpoon", 2924, 3178, "Raw tuna|Raw swordfish", "Harpoon");
        ground("loot_ashes", "Collect ashes - Grand Exchange", 3165, 3487, "Ashes");
        ground("loot_cowhide", "Collect cowhides - Lumbridge", 3257, 3276, "Cowhide");
        ground("loot_bones", "Collect bones - chicken farm", 3232, 3296, "Bones");
        ground("loot_feathers", "Collect feathers - chicken farm", 3232, 3296, "Feather");
        ground("loot_fish", "Collect discarded fish - Barbarian Village", 3104, 3424, "Raw trout|Raw salmon|Trout|Salmon");
        ground("loot_beef", "Collect raw beef - Lumbridge", 3257, 3276, "Raw beef");
        ground("loot_chicken", "Collect raw chicken - Lumbridge", 3232, 3296, "Raw chicken");
        ground("pvp_loot", "Public PvP loot - populated F2P world", 3165, 3487,
            "Coins|Ashes|Bones|Trout|Salmon|Lobster|Swordfish|Steel arrow|Mithril arrow|Adamant arrow|Rune arrow");
        process("chocolate", "Grind chocolate bars", Skill.COOKING, 1, 3185, 3436, "Chocolate dust");
        process("string_gold", "String gold amulets", Skill.CRAFTING, 1, 3185, 3436, "Gold amulet");
        process("tan_soft", "Tan cowhides - soft leather", Skill.CRAFTING, 1, 3273, 3192, "Leather");
        process("tan_hard", "Tan cowhides - hard leather", Skill.CRAFTING, 1, 3273, 3192, "Hard leather");
        process("cook_shrimp", "Cook shrimps - Al Kharid", Skill.COOKING, 1, 3274, 3181, "Shrimps|Burnt shrimp");
        process("cook_trout", "Cook trout - Al Kharid", Skill.COOKING, 15, 3274, 3181, "Trout|Burnt fish");
        process("cook_salmon", "Cook salmon - Al Kharid", Skill.COOKING, 25, 3274, 3181, "Salmon|Burnt fish");
        process("cook_tuna", "Cook tuna - Al Kharid", Skill.COOKING, 30, 3274, 3181, "Tuna|Burnt fish");
        process("cook_lobster", "Cook lobsters - Al Kharid", Skill.COOKING, 40, 3274, 3181, "Lobster|Burnt lobster");
        quest("restless_ghost", "The Restless Ghost", 3243, 3208);
        quest("x_marks", "X Marks the Spot", 3228, 3241);
        quest("knights_sword", "The Knight's Sword", 2976, 3342);
        quest("goblin_diplomacy", "Goblin Diplomacy", 2956, 3506);
        quest("ernest", "Ernest the Chicken", 3110, 3330);
        quest("vampyre", "Vampyre Slayer", 3098, 3268);
        quest("demon_slayer", "Demon Slayer", 3204, 3424);
        quest("black_knights", "Black Knights' Fortress", 2958, 3335);
        quest("prince_ali", "Prince Ali Rescue", 3293, 3163);
        quest("below_ice", "Below Ice Mountain", 3001, 3436);
        quest("corsair", "The Corsair Curse", 3029, 3274);
        quest("dragon_slayer", "Dragon Slayer I", 3190, 3362);
        add("range_chickens", "Ranged - Lumbridge chickens", Method.Kind.COMBAT, Skill.RANGED, 1,
            "Chicken", "Attack", 3230,3296, "Feather|Raw chicken|Bones", "");
        add("mage_chickens", "Magic - Lumbridge chickens", Method.Kind.COMBAT, Skill.MAGIC, 1,
            "Chicken", "Attack", 3230,3296, "Feather|Raw chicken|Bones", "");
        process("pray_bones", "Prayer - bury bones", Skill.PRAYER, 1,3165,3487, "");
        process("burn_logs", "Firemaking - normal logs", Skill.FIREMAKING, 1,3173,3497, "");
        for (Skill style : List.of(Skill.ATTACK, Skill.RANGED, Skill.MAGIC)) {
            String prefix = style == Skill.RANGED ? "range_" : style == Skill.MAGIC ? "mage_" : "";
            if (style != Skill.ATTACK)
                add(prefix+"cows", "Cows - Lumbridge ("+style+")", Method.Kind.COMBAT, style, 5,
                    "Cow|Cow calf", "Attack", 3257, 3276, "Cowhide|Bones|Raw beef", "");
            add(prefix+"giant_frogs", "Giant frogs - Lumbridge swamp ("+style+")", Method.Kind.COMBAT, style, 15,
                "Giant frog", "Attack", 3196, 3170, "Big bones", "");
            add(prefix+"hill_giants", "Hill giants - Giants' Plateau ("+style+")", Method.Kind.COMBAT, style, 30,
                "Hill Giant", "Attack", 3380, 3152, "Big bones|Limpwurt root", "");
        }
        process("burn_oak", "Firemaking - oak logs", Skill.FIREMAKING, 15,3173,3497,"");
        process("burn_willow", "Firemaking - willow logs", Skill.FIREMAKING, 30,3173,3497,"");
        process("burn_maple", "Firemaking - maple logs", Skill.FIREMAKING, 45,3173,3497,"");
        process("burn_yew", "Firemaking - yew logs", Skill.FIREMAKING, 60,3173,3497,"");
        process("pray_big_bones", "Prayer - bury big bones", Skill.PRAYER, 1,3165,3487,"");
        process("smelt_bronze","Smithing - smelt bronze bars",Skill.SMITHING,1,3273,3186,"Bronze bar");
        process("smelt_iron","Smithing - smelt iron bars",Skill.SMITHING,15,3273,3186,"Iron bar");
        process("forge_bronze","Smithing - forge bronze daggers",Skill.SMITHING,1,3188,3425,"Bronze dagger");
        for (F2pProduction.Spec spec : F2pProduction.all())
            process(spec.id,spec.label,spec.skill,spec.level,spec.x,spec.y,String.join("|",spec.products));
        BUILTIN.addAll(ALL.keySet());
        loadLearnedSkills();
    }

    public static final class LearnedSkillDto
    {
        public String id;
        public String label;
        public String kind;
        public String skill;
        public int level;
        public String names;
        public String action;
        public int x, y;
        public String products;
        public String tools;
    }

    public static synchronized void loadLearnedSkills()
    {
        java.nio.file.Path file = FilesStore.ROOT.resolve("learned-skills.json");
        if (!java.nio.file.Files.exists(file)) return;
        try
        {
            String json = java.nio.file.Files.readString(file);
            LearnedSkillDto[] dtos = new com.google.gson.Gson().fromJson(json, LearnedSkillDto[].class);
            if (dtos != null)
            {
                for (LearnedSkillDto d : dtos)
                {
                    try {
                        Method m = new Method(d.id,d.label,Method.Kind.valueOf(d.kind),Skill.valueOf(d.skill),d.level,
                            d.names.split("\\|"),d.action,new WorldPoint(d.x,d.y,0),splitList(d.products),splitList(d.tools));
                        if (SkillProbation.valid(m) && !BUILTIN.contains(m.id)) ALL.put(m.id,m);
                    } catch (RuntimeException ex) { DebugLog.event("SKILL_INVALID", "Discarded invalid saved proposal"); }
                }
            }
        }
        catch (Exception ex) { DebugLog.error(ex); }
    }

    public static synchronized void saveLearnedSkill(Method m)
    {
        if (!SkillProbation.valid(m) || BUILTIN.contains(m.id)) {
            DebugLog.event("SKILL_INVALID", "Proposal rejected by executor/precondition schema"); return;
        }
        ALL.put(m.id, m);
        java.nio.file.Path file = FilesStore.ROOT.resolve("learned-skills.json");
        try
        {
            List<LearnedSkillDto> list = new java.util.ArrayList<>();
            if (java.nio.file.Files.exists(file))
            {
                String existing = java.nio.file.Files.readString(file);
                LearnedSkillDto[] dtos = new com.google.gson.Gson().fromJson(existing, LearnedSkillDto[].class);
                if (dtos != null) java.util.Collections.addAll(list, dtos);
            }
            LearnedSkillDto dto = new LearnedSkillDto();
            dto.id = m.id; dto.label = m.label; dto.kind = m.kind.name(); dto.skill = m.skill.name();
            dto.level = m.level; dto.names = String.join("|", m.names); dto.action = m.action;
            dto.x = m.location.getX(); dto.y = m.location.getY();
            dto.products = String.join("|", m.products); dto.tools = String.join("|", m.tools);
            list.removeIf(d -> d.id.equals(dto.id));
            list.add(dto);
            java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(list));
            DebugLog.event("SKILL_LEARNED", "Proposed skill registered on probation: " + m.label + " (" + m.id + ")");
        }
        catch (Exception ex) { DebugLog.error(ex); }
    }

    private static void add(String id, String label, Method.Kind kind, Skill skill, int level,
        String names, String action, int x, int y, String products, String tools)
    {
        ALL.put(id, new Method(id, label, kind, skill, level, names.split("\\|"), action,
            new WorldPoint(x, y, 0), splitList(products), splitList(tools)));
    }

    public static List<String> splitList(String value)
    {
        return value == null || value.isEmpty() ? List.of() : List.of(value.split("\\|"));
    }

    private static void quest(String id, String label, int x, int y)
    {
        add(id, label, Method.Kind.QUEST, Skill.COOKING, 1, "", "Talk-to", x, y, "", "");
    }

    private static void ground(String id, String label, int x, int y, String products)
    {
        add(id, label, Method.Kind.GROUND, Skill.ATTACK, 1, products, "Take", x, y, products, "");
    }

    private static void process(String id, String label, Skill skill, int level, int x, int y, String products)
    {
        add(id, label, Method.Kind.PROCESS, skill, level, "", "", x, y, products, "");
    }

    public static Method get(String id)
    {
        Method value = ALL.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown method: " + id);
        return value;
    }

    public static boolean isLearned(String id) { return ALL.containsKey(id) && !BUILTIN.contains(id); }
    public static boolean learnedBlocked(String id) { return isLearned(id) && "QUARANTINED".equals(PROBATION.status(id)); }
    public static List<String> learnedIds() { return ALL.keySet().stream().filter(Methods::isLearned).sorted().collect(java.util.stream.Collectors.toList()); }
    public static String learnedStatus(String id) { return isLearned(id) ? PROBATION.status(id) : ""; }
    public static void recordLearnedOutcome(String id, boolean confirmed) {
        if (isLearned(id)) PROBATION.observe(id,confirmed);
    }
    public static List<Method> all() { return List.copyOf(ALL.values()); }
    public static List<String> knownIds() { return new java.util.ArrayList<>(ALL.keySet()); }
    private Methods() { }
}
