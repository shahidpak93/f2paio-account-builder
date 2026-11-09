package com.example.f2paio;
import java.util.*;
/** Optional upgrades use banked gear first and a bounded disposable budget; they never fund an endless shopping goal. */
public final class GearUpgrades {
    private final SupplyService supply;
    private final BankMemory bank;
    private long next;
    private ItemNeed active;
    private String status="";
    public GearUpgrades(BankMemory bank,SupplyService supply) { this.bank=bank; this.supply=supply; }
    public boolean step(Method goal,GameState state,F2pAioActions actions,F2pAioConfig config,long now) {
        if(goal.kind!=Method.Kind.COMBAT && !goal.tools.contains("axe") && !goal.tools.contains("pickaxe")) return true;
        if(active!=null) {
            List<ItemNeed> missing=state.items.stream().anyMatch(i->i.id==active.id && !i.noted)?List.of():List.of(active);
            boolean done=supply.step(goal,state,actions,missing); status="Upgrade: "+supply.status();
            if(done || !supply.failure().isEmpty()) { active=null; supply.reset(); next=now+120_000; }
            return done;
        }
        if(now<next || !bank.known()) return true;
        next=now+30_000;
        long wealth=state.coins+bank.count("Coins"),budget=Math.min(25_000,Math.max(0,(wealth-config.gearReserveCoins)/4));
        List<GearCatalog.Entry> choices=GearCatalog.eligible(goal,state);
        for(String slot:List.of("tool","weapon","body","legs","head","shield","neck","hands","feet","cape")) {
            int owned=GearCatalog.ownedRank(slot,goal,state);
            var better = choices.stream().filter(e -> e.slot.equals(slot) && e.rank>owned)
                .collect(java.util.stream.Collectors.toList());
            var upgrade = better.stream().filter(e -> bank.items().stream().anyMatch(i -> i.id==e.id && i.quantity>0))
                .max(Comparator.comparingInt(e->e.rank)).orElse(null);
            if (upgrade==null && !state.ironman) upgrade=better.stream().filter(e -> TradeService.quoted(e.id)
                    && worthBuying(slot,owned,e.rank,TradeService.unitPrice(e.id,true),budget))
                .max(Comparator.comparingInt(e->e.rank)).orElse(null);
            if(upgrade!=null) {
                active=new ItemNeed(upgrade.id,upgrade.name,1); status="Upgrade: "+upgrade.name+"; bank stock before purchase";
                DebugLog.event("GEAR_UPGRADE","Eligible "+upgrade.name+" slot="+slot+" budget="+budget+"; bank first");
                return false;
            }
        }
        return true;
    }
    static boolean worthBuying(String slot,int owned,int candidate,long price,long budget) {
        if (candidate <= owned || price <= 0 || price > budget) return false;
        if (slot.equals("weapon") || slot.equals("tool")) return candidate-owned>=100;
        if (slot.equals("body") || slot.equals("legs") || slot.equals("head") || slot.equals("shield"))
            return candidate-owned>=100 && (candidate-owned>=200 || price<=budget/3);
        return slot.equals("neck") && owned<0 && price<=budget/5;
    }

    void reset() { active=null; next=0; status=""; }

    public String status() { return status.isEmpty()?"Planning eligible loadout upgrade":status; }
}
