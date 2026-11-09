package com.example.f2paio;
import java.util.*;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
/** Select the strongest eligible standard loadout for the active combat style, excluding notes. */
public final class EquipmentPolicy {
    private int effectiveStrength,strengthBonus;
    private final ActionSettlement settle=new ActionSettlement(3,5_000);
    private String failed="";
    private String pendingKey="";
    private int pendingId;
    private final Set<String> blocked=new HashSet<>();
    public String failure(){return failed;}
    public void pause(long now){settle.pause(now);}
    public static boolean strengthIncreasesHit(int effective,int otherBonus) {
        return (int)(.5+effective*(otherBonus+74)/640.0)>(int)(.5+effective*(otherBonus+70)/640.0);
    }
    public boolean step(Method method,GameState state) {
        failed="";
        if(pendingId>0 && state.equipment.stream().anyMatch(i->i.id==pendingId)) {settle.poll(pendingKey,true,System.currentTimeMillis());pendingId=0;}
        boolean tool=method.tools.contains("axe") || method.tools.contains("pickaxe");
        if(method.kind!=Method.Kind.COMBAT && !tool) return true;
        var eligible=GearCatalog.eligible(method,state);
        effectiveStrength=state.levels.getOrDefault(Skill.STRENGTH,1)+8; strengthBonus=0;
        var thread=net.runelite.client.plugins.microbot.Microbot.getClientThread();
        if(thread!=null && GearCatalog.profile(method).equals("melee")) {
            int[] context=thread.runOnClientThreadOptional(()->{
                var client=net.runelite.client.plugins.microbot.Microbot.getClient();
                double prayer=client.isPrayerActive(net.runelite.api.Prayer.ULTIMATE_STRENGTH)?1.15
                    :client.isPrayerActive(net.runelite.api.Prayer.SUPERHUMAN_STRENGTH)?1.10
                    :client.isPrayerActive(net.runelite.api.Prayer.BURST_OF_STRENGTH)?1.05:1.0;
                int style=client.getVarpValue(net.runelite.api.VarPlayer.ATTACK_STYLE);
                int effective=(int)Math.floor(client.getBoostedSkillLevel(Skill.STRENGTH)*prayer)+8+(style==1?3:style==2?1:0);
                int bonus=0;
                for(ResourceItem item:state.equipment) {
                    var stats=net.runelite.client.plugins.microbot.Microbot.getItemManager().getItemStats(item.id);
                    if(stats!=null && stats.getEquipment()!=null && stats.getEquipment().getSlot()!=2) bonus+=stats.getEquipment().getStr();
                }
                return new int[]{effective,bonus};
            }).orElse(new int[]{effectiveStrength,0});
            effectiveStrength=context[0]; strengthBonus=context[1];
        }
        for(String slot:List.of("tool","weapon","ammo","body","legs","head","shield","neck","hands","feet","cape")) {
            var candidate=eligible.stream().filter(e->e.slot.equals(slot) && state.items.stream().anyMatch(i->!i.noted && i.id==e.id))
                .max(Comparator.comparingDouble(e->score(e,method,state))).orElse(null);
            double worn=eligible.stream().filter(e->e.slot.equals(slot) && state.equipment.stream().anyMatch(i->i.id==e.id))
                .mapToDouble(e->score(e,method,state)).max().orElse(-1);
            if(candidate==null || score(candidate,method,state)<=worn) continue;
            if(tool && state.levels.getOrDefault(Skill.ATTACK,1)<wieldLevel(candidate.rank)) return true;
            String key=state.account+":"+slot+":"+candidate.id;
            if(blocked.contains(key)) continue;
            var result=settle.poll(key,false,System.currentTimeMillis());
            if(result==ActionSettlement.Result.FAILED) {
                if(slot.equals("weapon") || slot.equals("ammo")) {failed="Setup failed: equipment not observed: "+candidate.name;return false;}
                blocked.add(key);DebugLog.event("EQUIP_DEFERRED",candidate.name+" optional upgrade not observed; retain worn gear");continue;
            }
            if(result!=ActionSettlement.Result.ATTEMPT) return false;
            pendingKey=key;pendingId=candidate.id;
            DebugLog.event("EQUIP",candidate.name+" slot="+slot+" profile="+GearCatalog.profile(method));
            Rs2Inventory.interact(candidate.id,slot.equals("weapon") || slot.equals("tool") || slot.equals("ammo")?"Wield":"Wear");
            return false;
        }
        return true;
    }
    private static int wieldLevel(int rank) { int[] levels={1,1,5,10,20,30,40}; return levels[Math.max(0,Math.min(6,rank/100-1))]; }
    private double score(GearCatalog.Entry e,Method method,GameState state) {
        if(e.slot.equals("neck") && GearCatalog.profile(method).equals("melee")) {
            boolean maxHit=strengthIncreasesHit(effectiveStrength,strengthBonus);
            return e.id==1725?(maxHit?120:90):100;
        }
        return e.rank;
    }
    static boolean isWeapon(String name) {
        return name!=null && (name.endsWith(" scimitar") || name.endsWith(" sword") || name.endsWith(" dagger") || name.endsWith(" mace"));
    }
    static boolean canEquipArmour(String name,GameState state) {
        return tier(name)<=state.levels.getOrDefault(Skill.DEFENCE,1) && (!"Rune platebody".equals(name)
            || ProgressionPolicy.done(state,"Dragon Slayer I") || ProgressionPolicy.done(state,"Dragon Slayer"));
    }
    static int tier(String name) {
        if(name==null) return 999;
        if(name.startsWith("Bronze") || name.startsWith("Iron")) return 1;
        if(name.startsWith("Steel")) return 5; if(name.startsWith("Black")) return 10;
        if(name.startsWith("Mithril")) return 20; if(name.startsWith("Adamant")) return 30;
        if(name.startsWith("Rune")) return 40; return 999;
    }
}
