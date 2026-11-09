package com.example.f2paio;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

public final class BankTask
{

    public boolean step(Method method, F2pAioConfig config)
    {
        if (config.inventoryMode.equals("DROP"))
        {
            var item = Rs2Inventory.get(i -> method.products.contains(i.getName()));
            if (item != null) Rs2Inventory.interact(item.getId(), "Drop");
            return item != null;
        }
        if (!Rs2Bank.isOpen())
        {
            var bank = Rs2Bank.getNearestBank();
            if (bank == null) bank = BankLocation.VARROCK_EAST;
            if (net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation()
                .distanceTo(bank.getWorldPoint()) > Tuning.INTERACTION_RANGE)
            {
                DebugLog.event("BANK_TRAVEL", "target=" + bank.getWorldPoint());
                var result = Rs2Walker.walkWithState(bank.getWorldPoint(), Tuning.TRAVEL_RADIUS);
                DebugLog.event("BANK_TRAVEL_RESULT", result.name());
            }
            else DebugLog.event("BANK_OPEN", "result=" + Rs2Bank.openBank());
            return true;
        }
        int count = productCount(method);
        if (count > 0)
        {
            var item = Rs2Inventory.get(i -> method.products.contains(i.getName()));
            if (item != null) DebugLog.event("BANK_DEPOSIT", item.getName() + " result=" + Rs2Bank.depositAll(item.getId()));
            return true;
        }
        Rs2Bank.closeBank();
        return false;
    }

    /**
     * Deposit one item that is neither a tool, food, coin nor needed by the current goal.
     * This is what clears leftovers from abandoned goals instead of letting them fill the inventory.
     */
    public boolean depositUnrelated(GameState state, Method current, java.util.Set<String> reserved)
    {
        java.util.Set<String> keep = InventoryCleanup.keepFor(current, state, reserved);
        ResourceItem junk = InventoryCleanup.unrelated(state.items, keep).stream().findFirst().orElse(null);
        if (junk == null) return false;
        if (!Rs2Bank.isOpen())
        {
            var bank = Rs2Bank.getNearestBank();
            if (bank == null) bank = BankLocation.VARROCK_EAST;
            if (net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation()
                .distanceTo(bank.getWorldPoint()) > Tuning.INTERACTION_RANGE)
            {
                DebugLog.event("BANK_TRAVEL", "junk cleanup target=" + bank.getWorldPoint());
                Rs2Walker.walkWithState(bank.getWorldPoint(), Tuning.TRAVEL_RADIUS);
            }
            else Rs2Bank.openBank();
            return true;
        }
        boolean result = Rs2Bank.depositAll(junk.id);
        DebugLog.event("BANK_DEPOSIT_JUNK", junk.name + " result=" + result
            + "; kept tools/food/current-goal inputs");
        return true;
    }

    public int productCount(Method method)
    {
        return Rs2Inventory.count(item -> method.products.contains(item.getName()));
    }

}
