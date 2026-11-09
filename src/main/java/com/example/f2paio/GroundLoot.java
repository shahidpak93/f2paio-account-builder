package com.example.f2paio;

import java.util.Collection;
import net.runelite.api.TileItem;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;

/** Public-drop eligibility follows native Ground Items TAKEABLE semantics. */
public final class GroundLoot
{
    private static long loggedAt;
    private static final java.util.Map<String,Long> blocked=new java.util.HashMap<>();
    private static final java.util.Map<String,String> submitted=new java.util.HashMap<>();
    private static String key(Rs2TileItemModel item) {
        return Microbot.getClient().getWorld()+":"+item.getId()+":"+item.getTile().getWorldLocation();
    }
    public static void submitted(String purpose,Rs2TileItemModel item) {
        Microbot.getClientThread().runOnClientThreadOptional(() -> { synchronized(blocked) { submitted.put(purpose,key(item)); } return true; });
    }
    public static void reject(String purpose,long now) {
        synchronized(blocked) { String key=submitted.remove(purpose); if(key!=null) blocked.put(key,now+120_000); }
        DebugLog.event("LOOT_BACKOFF",purpose+": rejected item/tile excluded for two minutes");
    }
    private GroundLoot() { }

    static boolean eligible(int ownership, boolean privateItem, int accountType)
    {
        if (ownership == TileItem.OWNERSHIP_SELF || ownership == TileItem.OWNERSHIP_GROUP) return true;
        if (privateItem) return false;
        return ownership != TileItem.OWNERSHIP_OTHER || accountType == 0;
    }

    static boolean matches(Collection<String> products, String name)
    {
        if (name == null) return false;
        String normalized = name.replaceAll("<[^>]*>", "").trim();
        return products.stream().anyMatch(p -> p.equalsIgnoreCase(normalized));
    }

    public static Rs2TileItemModel nearest(Collection<String> products, int radius, String purpose)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            int accountType = client.getVarbitValue(VarbitID.IRONMAN);
            int[] counts = new int[3];
            StringBuilder rejected = new StringBuilder();
            var item = Microbot.getRs2TileItemCache().query().within(radius).where(i -> {
                synchronized(blocked) {
                    blocked.values().removeIf(until -> until<=System.currentTimeMillis());
                    if(blocked.containsKey(key(i))) return false;
                }
                counts[0]++;
                if (!matches(products, i.getName())) return false;
                counts[1]++;
                boolean allowed = eligible(i.getOwnership(), i.isPrivate(), accountType);
                if (!allowed) {
                    counts[2]++;
                    if (rejected.length() < 300) rejected.append(i.getName()).append("#").append(i.getId())
                        .append(" ownership=").append(i.getOwnership()).append(" private=").append(i.isPrivate()).append("; ");
                }
                return allowed;
            }).nearestReachableOnClientThread();
            long now = System.currentTimeMillis();
            if (now - loggedAt >= 10_000) {
                loggedAt = now;
                DebugLog.event("LOOT_SCAN", "purpose=" + purpose + "; world=" + client.getWorld()
                    + "; radius=" + radius + "; scanned=" + counts[0] + "; matching=" + counts[1]
                    + "; restricted=" + counts[2] + "; accountType=" + accountType
                    + "; selected=" + (item == null ? "none" : item.getName() + "#" + item.getId())
                    + "; rejected=" + rejected);
            }
            return item;
        }).orElse(null);
    }
}
