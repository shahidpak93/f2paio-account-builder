package com.example.f2paio;
import net.runelite.api.*;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
/** Progression style switches are explicit so melee does not silently train Attack forever. */
public final class CombatTraining {
    private static final ActionSettlement settle=new ActionSettlement(3,5_000);
    private static String failed="";
    public static String failure(){return failed;}
    public static void pause(long now){settle.pause(now);}
    public static void reset(){settle.reset();failed="";}
    private static ActionSettlement.Result observe(GameState s,String target,boolean observed) {
        String weapon=s.equipment.stream().map(i->Integer.toString(i.id)).sorted().collect(java.util.stream.Collectors.joining(","));
        var result=settle.poll(s.account+":"+weapon+":"+target,observed,System.currentTimeMillis());
        if(result==ActionSettlement.Result.FAILED) failed="Setup failed: combat style not observed after three attempts ("+target+")";
        return result;
    }
    public static boolean step(Method m,GameState state,String preset) {
        failed="";
        if(m.kind!=Method.Kind.COMBAT) return true;
        if(m.skill==Skill.MAGIC) {
            var current=net.runelite.client.plugins.microbot.util.magic.Rs2Magic.getCurrentAutoCastSpell();
            int rune=runeId(state);
            var wanted=rune==560?Rs2CombatSpells.WIND_BLAST:rune==562?Rs2CombatSpells.WIND_BOLT:Rs2CombatSpells.WIND_STRIKE;
            var result=observe(state,"autocast="+wanted,current==wanted);
            if(result==ActionSettlement.Result.READY) return true;
            if(result!=ActionSettlement.Result.ATTEMPT) return false;
            DebugLog.event("TRAIN_STYLE","Magic autocast "+wanted);
            Rs2Combat.setAutoCastSpell(wanted,false);
            return false; // verify on the next live snapshot
        }
        int arrow=state.equipment.stream().filter(i->i.name!=null && i.name.endsWith(" arrow")).mapToInt(i->i.id).findFirst().orElse(882);
        int desired=m.skill==Skill.RANGED?rangedStyle(state.levels.getOrDefault(Skill.RANGED,1),arrow)
            :CombatStyleResolver.melee(ProgressionPolicy.meleeTarget(state,preset));
        if(desired<0 || desired>3) {failed="Setup failed: current weapon has no verified style for the target skill";return false;}
        int selected=Microbot.getClientThread().runOnClientThreadOptional(()->Microbot.getClient().getVarpValue(VarPlayer.ATTACK_STYLE)).orElse(-1);
        var result=observe(state,"style="+desired,selected==desired);
        if(result==ActionSettlement.Result.READY) return true;
        if(result!=ActionSettlement.Result.ATTEMPT) return false;
        var style=desired==0?WidgetInfo.COMBAT_STYLE_ONE:desired==1?WidgetInfo.COMBAT_STYLE_TWO:desired==2?WidgetInfo.COMBAT_STYLE_THREE:WidgetInfo.COMBAT_STYLE_FOUR;
        DebugLog.event("TRAIN_STYLE",m.skill==Skill.RANGED?"Ranged rapid":"Melee target="+ProgressionPolicy.meleeTarget(state,preset));
        net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.COMBAT);
        Rs2Combat.setAttackStyle(style); return false;
    }
    public static int runeId(GameState s) {
        int level=s.levels.getOrDefault(Skill.MAGIC,1);
        if(level>=41 && s.items.stream().anyMatch(i->!i.noted && i.id==560 && i.quantity>0)) return 560;
        if(level>=17 && s.items.stream().anyMatch(i->!i.noted && i.id==562 && i.quantity>0)) return 562;
        return level>=41 && s.coins>=20000?560:level>=17 && s.coins>=5000?562:558;
    }
    public static int rangedStyle(int level,int arrow) {
        if(arrow==884 && level>=2 && level<=4) return 0;
        if(arrow==882 && level>=3 && level<=5) return 0;
        return 1;
    }
    private CombatTraining() { }
}
