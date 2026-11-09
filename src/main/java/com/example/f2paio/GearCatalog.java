package com.example.f2paio;
import java.util.*;
import net.runelite.api.Skill;
/** Standard F2P loadouts: requirements are distinct from cosmetic/material names. */
public final class GearCatalog {
    public static final class Entry {
        public final int id,level,defence,rank; public final String name,slot,profile; public final boolean dragonQuest;
        Entry(int id,String name,String slot,String profile,int level,int defence,int rank,boolean quest) {
            this.id=id;this.name=name;this.slot=slot;this.profile=profile;this.level=level;this.defence=defence;this.rank=rank;dragonQuest=quest;
        }
        public boolean eligible(GameState s) {
            Skill skill=profile.equals("ranged")?Skill.RANGED:profile.equals("magic")?Skill.MAGIC:profile.equals("axe")?Skill.WOODCUTTING:profile.equals("pickaxe")?Skill.MINING:Skill.ATTACK;
            return s.levels.getOrDefault(skill,1)>=level && s.levels.getOrDefault(Skill.DEFENCE,1)>=defence
                && (!dragonQuest || ProgressionPolicy.done(s,"Dragon Slayer I") || ProgressionPolicy.done(s,"Dragon Slayer"));
        }
    }
    private static final List<Entry> ITEMS=new ArrayList<>();
    static {
        String[] metals={"Bronze","Iron","Steel","Black","Mithril","Adamant","Rune"};
        int[] levels={1,1,5,10,20,30,40}, axes={1351,1349,1353,1361,1355,1357,1359},picks={1265,1267,1269,12297,1273,1271,1275};
        int[] toolLevels={1,1,6,11,21,31,41},scims={1321,1323,1325,1327,1329,1331,1333};
        int[] bodies={1117,1115,1119,1125,1121,1123,1127},legs={1075,1067,1069,1077,1071,1073,1079};
        int[] helms={1155,1153,1157,1165,1159,1161,1163},shields={1189,1191,1193,1195,1197,1199,1201};
        for(int i=0;i<metals.length;i++) {
            int rank=i+1;
            add(scims[i],metals[i]+" scimitar","weapon","melee",levels[i],0,rank*100,false);
            add(bodies[i],metals[i]+" platebody","body","melee",1,levels[i],rank*100,i==6);
            add(legs[i],metals[i]+" platelegs","legs","melee",1,levels[i],rank*100,false);
            add(helms[i],metals[i]+" full helm","head","melee",1,levels[i],rank*100,false);
            add(shields[i],metals[i]+" kiteshield","shield","melee",1,levels[i],rank*100,false);
            add(axes[i],metals[i]+" axe","tool","axe",toolLevels[i],0,rank*100,false);
            add(picks[i],metals[i]+" pickaxe","tool","pickaxe",toolLevels[i],0,rank*100,false);
        }
        add(1113,"Rune chainbody","body","melee",1,40,690,false);
        add(841,"Shortbow","weapon","ranged",1,0,100,false); add(843,"Oak shortbow","weapon","ranged",5,0,200,false);
        add(849,"Willow shortbow","weapon","ranged",20,0,300,false); add(853,"Maple shortbow","weapon","ranged",30,0,400,false);
        add(882,"Bronze arrow","ammo","ranged",1,0,100,false); add(884,"Iron arrow","ammo","ranged",1,0,110,false);
        add(886,"Steel arrow","ammo","ranged",5,0,200,false); add(888,"Mithril arrow","ammo","ranged",20,0,300,false);
        add(890,"Adamant arrow","ammo","ranged",30,0,400,false);
        add(1129,"Leather body","body","ranged",1,1,100,false); add(1131,"Hardleather body","body","ranged",1,10,200,false);
        add(1133,"Studded body","body","ranged",20,20,300,false); add(1135,"Green d'hide body","body","ranged",40,40,400,true);
        add(1095,"Leather chaps","legs","ranged",1,1,100,false); add(1097,"Studded chaps","legs","ranged",20,1,200,false);
        add(1099,"Green d'hide chaps","legs","ranged",40,1,300,false); add(1167,"Leather cowl","head","ranged",1,1,100,false);
        add(1169,"Coif","head","ranged",20,1,200,false); add(1065,"Green d'hide vambraces","hands","ranged",40,1,200,false);
        add(1063,"Leather vambraces","hands","ranged",1,1,100,false);
        add(1381,"Staff of air","weapon","magic",1,0,100,false); add(577,"Wizard robe","body","magic",1,1,100,false);
        add(579,"Wizard hat","head","magic",1,1,100,false);
        for(String profile:List.of("melee","ranged","magic")) {
            add(1731,"Amulet of power","neck",profile,1,1,100,false);
            add(1061,"Leather boots","feet",profile,1,1,100,false);
            if(!profile.equals("ranged")) add(1059,"Leather gloves","hands",profile,1,1,100,false);
            add(1007,"Red cape","cape",profile,1,1,100,false);
        }
        add(1725,"Amulet of strength","neck","melee",1,1,110,false);
    }
    private static void add(int id,String name,String slot,String profile,int level,int defence,int rank,boolean quest) {
        ITEMS.add(new Entry(id,name,slot,profile,level,defence,rank,quest));
    }
    public static String profile(Method m) {
        if(m.tools.contains("axe")) return "axe"; if(m.tools.contains("pickaxe")) return "pickaxe";
        return m.skill==Skill.RANGED?"ranged":m.skill==Skill.MAGIC?"magic":"melee";
    }
    public static List<Entry> eligible(Method method,GameState state) {
        String profile=profile(method);
        int bow=ITEMS.stream().filter(e->e.profile.equals("ranged") && e.slot.equals("weapon") && e.eligible(state)
            && java.util.stream.Stream.concat(state.items.stream(),state.equipment.stream()).anyMatch(i->!i.noted && i.id==e.id))
            .mapToInt(e->e.rank).max().orElse(100);
        return ITEMS.stream().filter(e->e.profile.equals(profile) && e.eligible(state))
            .filter(e->!e.slot.equals("ammo") || e.rank<=Math.max(110,bow)).collect(java.util.stream.Collectors.toList());
    }
    public static int ownedRank(String slot,Method m,GameState s) {
        return eligible(m,s).stream().filter(e->e.slot.equals(slot) && java.util.stream.Stream.concat(s.items.stream(),s.equipment.stream())
            .anyMatch(i->!i.noted && i.id==e.id && i.quantity>0)).mapToInt(e->e.rank).max().orElse(-1);
    }
    public static List<ItemNeed> essentials(Method m,GameState s) {
        String profile=profile(m); List<ItemNeed> result=new ArrayList<>();
        for(String slot:List.of("weapon","body","legs")) if(ownedRank(slot,m,s)<0) {
            eligible(m,s).stream().filter(e->e.slot.equals(slot)).min(Comparator.comparingInt(e->e.rank))
                .ifPresent(e->result.add(new ItemNeed(e.id,e.name,1)));
        }
        if(profile.equals("ranged") && ownedRank("ammo",m,s)<0) result.add(new ItemNeed(882,"Bronze arrow",100));
        if(profile.equals("magic")) {
            int rune=CombatTraining.runeId(s);
            if(s.items.stream().noneMatch(i->i.id==rune && !i.noted && i.quantity>0))
                result.add(new ItemNeed(rune,rune==560?"Death rune":rune==562?"Chaos rune":"Mind rune",rune==558?100:10));
        }
        return result;
    }
    private GearCatalog() { }
}
