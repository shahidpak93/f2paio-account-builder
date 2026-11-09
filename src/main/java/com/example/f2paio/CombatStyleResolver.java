package com.example.f2paio;
import net.runelite.api.*;
import net.runelite.client.plugins.microbot.Microbot;
/** Resolve skill styles from live client enums instead of assuming every weapon has four styles. */
public final class CombatStyleResolver {
    public static int choose(String[] styles,Skill target) {
        String wanted=target==Skill.ATTACK?"ACCURATE":target==Skill.STRENGTH?"AGGRESSIVE":"DEFENSIVE";
        for(int i=0;i<Math.min(4,styles.length);i++) if(wanted.equalsIgnoreCase(styles[i])) return i;
        return -1;
    }
    public static int melee(Skill target) {
        return Microbot.getClientThread().runOnClientThreadOptional(()->{
            var client=Microbot.getClient();int type=client.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE);
            int table=client.getEnum(EnumID.WEAPON_STYLES).getIntValue(type);
            if(table<0) return -1;
            int[] entries=client.getEnum(table).getIntVals();String[] styles=new String[entries.length];
            for(int i=0;i<entries.length;i++) styles[i]=client.getStructComposition(entries[i]).getStringValue(ParamID.ATTACK_STYLE_NAME);
            return choose(styles,target);
        }).orElse(-1);
    }
    private CombatStyleResolver(){}
}
