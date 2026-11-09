package com.example.f2paio;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Converts owned value into coins when a goal cannot be funded.
 *
 * <p>Order: carried products -> banked products (withdraw noted, sell at GE) -> public ground loot.
 * The bank step exists because a fresh F2P account can have a bank full of ores/logs/fish while
 * carrying almost no GP; scavenging public drops should be the last resort, not the first.</p>
 */
public final class FundingTask
{
    private static final Set<String> LOOT = products();
    public static Set<String> products()
    {
        var result = new java.util.HashSet<String>();
        for (Method method : Methods.all()) result.addAll(method.products);
        result.removeIf(name -> name.startsWith("Burnt")); result.add("Coins");
        return Set.copyOf(result);
    }
    private static final List<WorldPoint> SPOTS = List.of(new WorldPoint(3165, 3487, 0),
        new WorldPoint(3104, 3424, 0), new WorldPoint(3257, 3276, 0), new WorldPoint(3232, 3296, 0));
    private static final WorldPoint FALLBACK_BANK = new WorldPoint(3185, 3436, 0);
    private final WorldLootPolicy worlds = new WorldLootPolicy();
    private final TradeService trade = new TradeService();
    private final BankMemory memory;
    private int spot;
    private long emptySince;
    private String status = "Funding supplies";
    private ResourceItem selling;
    private boolean shopFallback;
    private ResourceItem bankSale;
    private long bankSaleAt;
    private long bankSaleBlockedAt;
    private boolean bankSaleBlocked;

    public FundingTask() { this(null); }
    public FundingTask(BankMemory memory) { this.memory = memory; }

    public boolean pendingSale() { return trade.pending() || bankSale != null; }
    public String status() { return status; }

    public void step(GameState state, F2pAioActions actions, long targetCoins, Set<String> reserved)
    {
        if (state.coins >= targetCoins && !trade.pending() && bankSale == null) return;
        reserved = new HashSet<>(reserved);
        reserved.addAll(ProgressionPolicy.reserved(state));
        final Set<String> keep = keepNames(state, reserved);
        ResourceItem sale = selling != null ? selling
            : SaleBatches.group(state.items, LOOT, keep).stream().findFirst().orElse(null);

        // No carried products to sell: liquidate a banked stack before scavenging ground drops.
        if (sale == null && !trade.pending())
        {
            if (bankSale != null) { sellBanked(state, actions); return; }
            if (prepareBankSale(state, actions, targetCoins, keep)) return;
        }

        if (sale != null && selling == null && !trade.pending())
        {
            long value = SaleBatches.group(state.items, LOOT, keep).stream()
                .mapToLong(i -> (long) i.quantity * TradeService.unitPrice(i.id, false)).sum();
            long now = System.currentTimeMillis();
            if (!BuilderBudget.sellBatch(value, Math.max(0, targetCoins - state.coins), state.inventory,
                    emptySince == 0 ? 0 : now - emptySince))
            {
                var nearby = GroundLoot.nearest(LOOT, Tuning.INTERACTION_RANGE, "funding-batch");
                if (nearby != null)
                {
                    emptySince = 0; status = "Funding: build sale batch; collect " + nearby.getName();
                    DebugLog.event("FUNDING_LOOT", nearby.getName() + "; submitted=" + nearby.click("Take"));
                }
                else
                {
                    if (emptySince == 0) emptySince = now;
                    status = "Funding: batch worth " + value + " GP; wait for nearby drops before sale trip";
                }
                return;
            }
        }
        if (sale != null)
        {
            if (shopFallback)
            {
                WorldPoint store = new WorldPoint(3217, 3414, 0);
                status = "Funding: sell public loot to Varrock general store";
                if (state.location.distanceTo(store) > 5) { actions.walk(store); return; }
                if (!net.runelite.client.plugins.microbot.util.shop.Rs2Shop.isOpen())
                    net.runelite.client.plugins.microbot.util.shop.Rs2Shop.openShop("Shop keeper", true);
                else DebugLog.event("FUNDING_SHOP", sale.name + "; submitted=" + Rs2Inventory.sellItem(SaleBatches.physicalId(state.items, sale.id), "50"));
                return;
            }
            status = "Funding: sell collected " + sale.name;
            WorldPoint ge = SPOTS.get(0);
            if (state.location.distanceTo(ge) > 7) { actions.walk(ge); return; }
            selling = sale;
            TradeService.Result result = trade.step(sale.id, sale.name, sale.quantity,
                TradeService.unitPrice(sale.id, false), true);
            status = "Funding: " + trade.status();
            if (result != TradeService.Result.WAITING)
            {
                selling = null;
                if (result == TradeService.Result.FAILED) { shopFallback = true; emptySince = 0; }
            }
            return;
        }
        if (Rs2GrandExchange.isOpen()) { Rs2GrandExchange.closeExchange(); return; }
        if (net.runelite.client.plugins.microbot.util.shop.Rs2Shop.isOpen())
        { net.runelite.client.plugins.microbot.util.shop.Rs2Shop.closeShop(); return; }
        if (!worlds.preparePublic(state)) { status = "Funding: selecting populated F2P loot world"; return; }
        var ground = GroundLoot.nearest(LOOT, Tuning.INTERACTION_RANGE, "funding");
        if (ground != null)
        {
            emptySince = 0; status = "Funding: collect " + ground.getName();
            DebugLog.event("FUNDING_LOOT", ground.getName() + "; submitted=" + ground.click("Take"));
            return;
        }
        WorldPoint destination = SPOTS.get(spot);
        if (state.location.distanceTo(destination) > 8) { status = "Funding: travel to public loot spot " + (spot + 1); actions.walk(destination); return; }
        long now = System.currentTimeMillis();
        if (emptySince == 0) emptySince = now;
        status = "Funding: scanning public drops; need " + Math.max(0, targetCoins - state.coins) + " more coins";
        if (now - emptySince > 30_000) { spot = (spot + 1) % SPOTS.size(); emptySince = 0; }
        DebugLog.event("FUNDING_WAIT", status);
    }

    private Set<String> keepNames(GameState state, Set<String> reserved)
    {
        Set<String> keep = new HashSet<>(reserved);
        for (Method method : Methods.all()) keep.addAll(method.tools);
        for (ResourceItem item : state.equipment) if (item.name != null) keep.add(item.name);
        keep.addAll(Requirements.FOOD);
        keep.add("Coins");
        return keep;
    }

    private boolean prepareBankSale(GameState state, F2pAioActions actions, long targetCoins, Set<String> keep)
    {
        if (memory == null || !memory.known()) return false;
        long now = System.currentTimeMillis();
        if (bankSaleBlocked && now - bankSaleBlockedAt < 5 * Tuning.MINUTE) return false;
        bankSaleBlocked = false;
        long deficit = Math.max(1, targetCoins - state.coins);
        long shortfall = Math.min(100_000, deficit);
        BankSalePlanner.Candidate candidate = BankSalePlanner.choose(memory.items(), LOOT, keep, shortfall,
            Math.max(1, Tuning.INVENTORY_CAPACITY - state.inventory),
            id -> TradeService.unitPrice(id, false));
        if (candidate == null)
        {
            bankSaleBlocked = true; bankSaleBlockedAt = now;
            DebugLog.event("BANK_SALE_NONE", "No banked sale above " + BankSalePlanner.minValue(shortfall) + " GP for a " + deficit + " GP shortfall");
            return false;
        }
        WorldPoint bank = FALLBACK_BANK;
        var nearest = Rs2Bank.getNearestBank();
        if (nearest != null && nearest.getWorldPoint() != null) bank = nearest.getWorldPoint();
        if (state.location.distanceTo(bank) > 6)
        {
            status = "Funding: travel to bank to sell " + candidate.name() + " (" + candidate.value() + " GP)";
            actions.walk(bank); return true;
        }
        if (!Rs2Bank.isOpen())
        {
            boolean submitted = Rs2Bank.openBank();
            DebugLog.event("BANK_OPEN", "for bank sale; submitted=" + submitted);
            return submitted || Rs2Bank.isOpen();
        }
        memory.observe();
        if (!Rs2Bank.isWithdrawAs(true))
        {
            boolean noted = Rs2Bank.setWithdrawAs(true);
            DebugLog.event("BANK_SALE_NOTE_MODE", "withdraw as note; submitted=" + noted);
            return true;
        }
        boolean submitted = Rs2Bank.withdrawX(candidate.id(), candidate.quantity());
        DebugLog.event("BANK_SALE_WITHDRAW", candidate.name() + " quantity=" + candidate.quantity()
            + " expected=" + candidate.value() + " GP; submitted=" + submitted);
        if (!submitted) { bankSaleBlocked = true; bankSaleBlockedAt = now; return false; }
        bankSale = new ResourceItem(candidate.id(), candidate.name(), candidate.quantity());
        bankSaleAt = now;
        status = "Funding: sell banked " + candidate.name();
        return true;
    }

    private void sellBanked(GameState state, F2pAioActions actions)
    {
        long now = System.currentTimeMillis();
        boolean carried = state.items.stream().anyMatch(i -> i.canonicalId() == bankSale.id && i.quantity > 0);
        if (!carried)
        {
            if (now - bankSaleAt > 20_000) { DebugLog.event("BANK_SALE_MISSING", bankSale.name + " not observed in inventory; aborting"); bankSale = null; bankSaleBlocked = true; bankSaleBlockedAt = now; return; }
            status = "Funding: withdraw banked " + bankSale.name;
            if (!Rs2Bank.isOpen())
            {
                var nearest = Rs2Bank.getNearestBank();
                WorldPoint bank = nearest != null && nearest.getWorldPoint() != null ? nearest.getWorldPoint() : FALLBACK_BANK;
                if (state.location.distanceTo(bank) > 6) { actions.walk(bank); return; }
                Rs2Bank.openBank(); return;
            }
            if (!Rs2Bank.isWithdrawAs(true)) { Rs2Bank.setWithdrawAs(true); return; }
            boolean submitted = Rs2Bank.withdrawX(bankSale.id, bankSale.quantity);
            DebugLog.event("BANK_SALE_RETRY", bankSale.name + "; submitted=" + submitted);
            bankSaleAt = now;
            return;
        }
        if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return; }
        WorldPoint ge = SPOTS.get(0);
        if (state.location.distanceTo(ge) > 7) { status = "Funding: travel to GE to sell banked " + bankSale.name; actions.walk(ge); return; }
        TradeService.Result result = trade.step(bankSale.id, bankSale.name, bankSale.quantity,
            TradeService.unitPrice(bankSale.id, false), true);
        status = "Funding bank sale: " + trade.status();
        if (result == TradeService.Result.COLLECTED)
        {
            DebugLog.event("BANK_SALE_DONE", bankSale.name + " converted to coins");
            bankSale = null;
        }
        else if (result == TradeService.Result.FAILED)
        {
            DebugLog.event("BANK_SALE_FAILED", bankSale.name + " could not be sold; falling back to ground loot");
            bankSale = null; bankSaleBlocked = true; bankSaleBlockedAt = now;
        }
    }
}
