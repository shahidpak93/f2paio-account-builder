package com.example.f2paio;

import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;

public final class SupplyService
{
    private final BankMemory bank;
    private final Requirements requirements = new Requirements();
    private final FundingTask funding;
    private final TradeService trade = new TradeService();
    private ItemNeed active;
    private int attempts;
    public boolean accountBuilder;
    private long fundingStarted;
    private long nextAt;
    private String status = "Checking requirements";
    private String failed = "";

    public SupplyService(BankMemory bank) { this.bank = bank; this.funding = new FundingTask(bank); }
    public String status() { return status; }
    public String failure() { return failed; }

    public boolean step(Method goal, GameState state, F2pAioActions actions)
    {
        return step(goal, state, actions, null);
    }

    public boolean step(Method goal, GameState state, F2pAioActions actions, List<ItemNeed> override)
    {
        bank.observe();
        List<ItemNeed> missing = override == null ? requirements.missing(goal, state) : override;
        if (missing.isEmpty() && !trade.pending())
        {
            if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
            if (Rs2Shop.isOpen()) Rs2Shop.closeShop();
            if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
            active = null; failed = ""; return true;
        }
        if(trade.pending() && active!=null) {
            TradeService.Result result=trade.step(active.id,active.name,active.quantity,TradeService.unitPrice(active.id,true),false);
            status="Settle owned supply offer: "+trade.status();
            if(result==TradeService.Result.COLLECTED) { active=null; attempts=0; }
            else if(result==TradeService.Result.FAILED && ++attempts>3) failed="Owned supply offer could not settle";
            return false;
        }
        if (state.inventory >= Tuning.INVENTORY_CAPACITY && !missing.isEmpty())
        {
            status = "Free inventory space by banking unrelated items before acquiring supplies";
            if (!Rs2Bank.isOpen()) { openBank(state, actions); return false; }
            var keep = new java.util.HashSet<String>(goal.tools);
            for (ItemNeed input : ProductionRecipes.inputs(goal.id)) keep.add(input.name);
            if (goal.kind == Method.Kind.QUEST) keep.addAll(new Quests().requirements(goal.id).keySet());
            for (ItemNeed need : missing) keep.add(need.name);
            ResourceItem deposit = state.items.stream().filter(i -> i.id != 995 && !keep.contains(i.name)
                && !ToolPolicy.usable("axe", i.id, state.levels.get(Skill.WOODCUTTING))
                && !ToolPolicy.usable("pickaxe", i.id, state.levels.get(Skill.MINING))
                && (goal.kind != Method.Kind.COMBAT || (!EquipmentPolicy.isWeapon(i.name)
                    && (i.name == null || (!i.name.endsWith(" platebody") && !i.name.endsWith(" platelegs"))))))
                .findFirst().orElse(null);
            if (deposit == null) { failed = "Required loadout does not fit; choose a different goal"; return false; }
            DebugLog.event("FREE_SLOT", "Bank " + deposit.name + "; submitted=" + Rs2Bank.depositAll(deposit.id));
            nextAt = System.currentTimeMillis() + 1_500; return false;
        }
        if (System.currentTimeMillis() < nextAt) return false;
        if (active == null || (!trade.pending() && missing.stream().noneMatch(n -> n.id == active.id)))
        {
            active = missing.get(0); attempts = 0;
            DebugLog.event("SUBGOAL", "Before " + goal.label + ": obtain " + active);
        }
        // Convert already-owned notes rather than repeatedly purchasing an unusable form.
        ResourceItem note=state.items.stream().filter(i -> i.noted && i.canonicalId()==active.id).findFirst().orElse(null);
        if(note!=null) {
            status="Bank: convert noted supply to usable items: "+active.name;
            if(!Rs2Bank.isOpen()) { openBank(state,actions); return false; }
            DebugLog.event("SUPPLY_UNNOTE",active.name+"; deposit notes="+Rs2Bank.depositAll(note.id));
            nextAt=System.currentTimeMillis()+1_500; return false;
        }
        int unit = active.id == 995 ? 1 : TradeService.unitPrice(active.id, true);
        if (!trade.pending())
            for (ItemNeed updated : missing) if (updated.id == active.id) { active = updated; break; }
        long cost = active.id == 995 ? active.quantity : (long) unit * active.quantity;
        if (!bank.known())
        {
            status = "Inspect bank before purchasing " + active.name;
            openBank(state, actions); return false;
        }
        ResourceItem stored = bank.items().stream().filter(item -> {
            return Requirements.bankMatch(active,item,state);
        }).findFirst().orElse(null);
        if (stored != null)
        {
            status = "Bank: withdraw " + active;
            if (!Rs2Bank.isOpen()) { openBank(state, actions); return false; }
            bank.observe();
            if (!Rs2Bank.hasWithdrawAsItem()) { Rs2Bank.setWithdrawAs(false); return false; }
            boolean result = Rs2Bank.withdrawX(stored.id, Math.min(active.quantity, stored.quantity));
            DebugLog.event("WITHDRAW", stored.name + "; requested=" + active.quantity + "; submitted=" + result);
            nextAt = System.currentTimeMillis() + 2_000; return false;
        }
        SupplyPolicy.Next decision = SupplyPolicy.next(false, bank.known(), 0,
            bank.count("Coins"), state.coins, cost);
        if (decision == SupplyPolicy.Next.WITHDRAW_COINS)
        {
            status = "Bank: withdraw coins for supplies";
            if (!Rs2Bank.isOpen()) { openBank(state, actions); return false; }
            Rs2Bank.withdrawX(995, (int) Math.min(bank.count("Coins"), Math.max(1, cost - state.coins)));
            bank.observe(); nextAt = System.currentTimeMillis() + 2_000; return false;
        }
        java.util.Set<String> reserved = new java.util.HashSet<>(goal.tools);
        reserved.addAll(ProgressionPolicy.reserved(state));
        for (ItemNeed input : ProductionRecipes.inputs(goal.id)) reserved.add(input.name);
        if (goal.kind == Method.Kind.QUEST) reserved.addAll(new Quests().requirements(goal.id).keySet());
        if (goal.kind == Method.Kind.COMBAT) reserved.addAll(Requirements.FOOD);
        if (decision == SupplyPolicy.Next.EARN_COINS && !trade.pending())
        {
            boolean saleHeld = state.items.stream().anyMatch(i -> FundingTask.products().contains(i.name)
                && i.id != 995 && !reserved.contains(i.name));
            ResourceItem storedSale = bank.items().stream().filter(i -> FundingTask.products().contains(i.name)
                && i.id != 995 && !reserved.contains(i.name)).findFirst().orElse(null);
            if (!saleHeld && storedSale != null && state.inventory < Tuning.INVENTORY_CAPACITY)
            {
                status = "Funding: use surplus banked " + storedSale.name + " before scavenging";
                if (!Rs2Bank.isOpen()) { openBank(state, actions); return false; }
                boolean notes=BankWithdrawalPolicy.noted(BankWithdrawalPolicy.Purpose.SALE,storedSale.noteable);
                if(!Rs2Bank.isWithdrawAs(notes)) { Rs2Bank.setWithdrawAs(notes); return false; }
                int amount=BankWithdrawalPolicy.saleQuantity(storedSale,Math.max(1,cost-state.coins),
                    TradeService.unitPrice(storedSale.canonicalId(),false),Tuning.INVENTORY_CAPACITY-state.inventory);
                DebugLog.event("FUNDING_WITHDRAW","Sale stock "+storedSale.name+" quantity="+amount+" noted="+notes);
                if(amount>0) Rs2Bank.withdrawX(storedSale.id,amount);
                nextAt = System.currentTimeMillis() + 2_000; return false;
            }
        }
        if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return false; }
        if (active.id == 1351 && state.coins >= 16 && (state.coins < cost || state.ironman) && !trade.pending())
        {
            status = "Cheaper supply route: buy bronze axe from Bob for shop price";
            WorldPoint bob = new WorldPoint(3230, 3203, 0);
            if (state.location.distanceTo(bob) > 5) actions.walk(bob);
            else if (!Rs2Shop.isOpen()) Rs2Shop.openShop("Bob", true);
            else if (!Rs2Shop.buyItem("Bronze axe", "1") && ++attempts > 4)
                failed = "Bob has no obtainable bronze axe";
            nextAt = System.currentTimeMillis() + 2_000; return false;
        }
        if (decision == SupplyPolicy.Next.EARN_COINS && !trade.pending())
        {
            if (fundingStarted == 0) fundingStarted = System.currentTimeMillis();
            if (System.currentTimeMillis() - fundingStarted > 180_000)
            {
                failed = "Funding could not obtain " + active.name + " within three minutes; defer " + goal.label;
                status = failed;
                DebugLog.event("FUNDING_TIMEOUT", failed);
                return false;
            }
            funding.step(state, actions, cost, reserved); status = funding.status(); return false;
        }
        if (attempts >= 3 && !trade.pending())
        {
            if (active.id == 1351 && state.coins >= 16)
            {
                status = "GE unavailable: obtain bronze axe at Bob's shop";
                WorldPoint bob = new WorldPoint(3230, 3203, 0);
                if (state.location.distanceTo(bob) > 5) actions.walk(bob);
                else if (!Rs2Shop.isOpen()) Rs2Shop.openShop("Bob", true);
                else Rs2Shop.buyItem("Bronze axe", "1");
                nextAt = System.currentTimeMillis() + 3_000;
                if (++attempts > 7) failed = "Unable to obtain " + active.name + " after GE and shop attempts";
                return false;
            }
            failed = "Supply acquisition failed for " + active.name; status = failed; return false;
        }
        if(state.ironman) {failed="Supply route unavailable: Ironman cannot use the GE; choose supported self-supply";status=failed;return false;}
        WorldPoint ge = new WorldPoint(3165, 3487, 0);
        status = "GE: obtain " + active + " (budget " + cost + " coins)";
        if (state.location.distanceTo(ge) > 6) { actions.walk(ge); return false; }
        TradeService.Result result = trade.step(active.id, active.name, active.quantity, unit, false);
        status = "GE: " + trade.status();
        if (result == TradeService.Result.FAILED)
        {
            attempts++; nextAt = System.currentTimeMillis() + 5_000;
            DebugLog.event("SUPPLY_RETRY", active.name + "; attempt=" + attempts);
        }
        else if (result == TradeService.Result.COLLECTED)
        {
            // Only the following live inventory snapshot can satisfy the requirement.
            active = null; nextAt = System.currentTimeMillis() + 1_200;
        }
        return false;
    }

    void inspectBank(GameState state,F2pAioActions actions) {
        bank.observe();
        if (!bank.known()) openBank(state,actions);
    }

    private void openBank(GameState state, F2pAioActions actions)
    {
        var nearest = Rs2Bank.getNearestBank();
        if (nearest != null && state.location.distanceTo(nearest.getWorldPoint()) > 6)
            actions.walk(nearest.getWorldPoint());
        else
        {
            boolean submitted = Rs2Bank.openBank();
            DebugLog.event("BANK_OPEN", "for supply scan; submitted=" + submitted);
            bank.observe();
        }
    }

    public void reset()
    {
        if (!trade.pending()) active = null;
        failed = ""; attempts = 0; nextAt = 0; fundingStarted=0;
    }
}
