package com.example.f2paio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;

public final class TradeService
{
    public enum Result { WAITING, COLLECTED, FAILED }
    private static final Map<Integer, Long> PRICE_AT = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> PRICE_CACHE = new ConcurrentHashMap<>();

    private GrandExchangeSlots slot;
    private int itemId;
    private String name;
    private long placedAt;
    private boolean collected;
    private boolean selling;
    private long beforeQuantity;
    private PurchaseReceipt receipt;
    private final Map<Integer,Long> purchaseBlockedUntil = new java.util.HashMap<>();
    private long inventoryQuantity(int id) {
        return net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.items()
            .filter(i -> i.getId()==id || i.getUnNotedId()==id).mapToLong(i -> i.getQuantity()).sum();
    }

    private String status = "Idle";

    public String status() { return status; }
    public boolean pending() { return slot != null; }

    public static int unitPrice(int id, boolean buy)
    {
        Long cached = PRICE_CACHE.get(id);
        long base = cached != null && System.currentTimeMillis()-PRICE_AT.getOrDefault(id,0L)<300_000 ? cached : 0L;
        if (base <= 0)
        {
            try
            {
                if (Microbot.getClientThread() != null && Microbot.getItemManager() != null)
                {
                    base = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                        try
                        {
                            return (long) Microbot.getItemManager().getItemPrice(id);
                        }
                        catch (Throwable t)
                        {
                            return 0L;
                        }
                    }).orElse(0L);
                    if (base > 0)
                    {
                        PRICE_CACHE.put(id, base); PRICE_AT.put(id,System.currentTimeMillis());
                    }
                }
            }
            catch (Throwable ignored)
            {
                base = 0L;
            }
        }
        if (base <= 0) base = id == 1351 ? 50 : 100;
        return quote(base,buy);
    }

    public static int quote(long base,boolean buy) {
        return (int)Math.max(1,Math.min(Integer.MAX_VALUE,buy?Math.ceil(base*1.25+5):Math.floor(base*.8)));
    }
    public static boolean quoted(int id) { return PRICE_CACHE.containsKey(id) && System.currentTimeMillis()-PRICE_AT.getOrDefault(id,0L)<300_000; }

    public Result step(int id, String item, int quantity, int price, boolean sell)
    {
        if (receipt != null) {
            PurchaseReceipt.State observed=receipt.observe(inventoryQuantity(itemId),System.currentTimeMillis());
            if(observed==PurchaseReceipt.State.WAITING) { status="Verify purchased inventory: "+name; return Result.WAITING; }
            if(observed==PurchaseReceipt.State.FAILED) {
                status="Collection acknowledged but purchased item absent: "+name+"; buying blocked for 10 minutes";
                DebugLog.event("GE_RECEIPT_FAILED",status); purchaseBlockedUntil.put(itemId,System.currentTimeMillis()+600_000);
                OfferJournal.state(slot.name(),"RECEIPT_FAILED");clear(); return Result.FAILED;
            }
            OfferJournal.state(slot.name(),"RECEIPT_VERIFIED");
            DebugLog.event("GE_RECEIPT_VERIFIED",name+"; actual inventory quantity increased");
            clear(); return Result.COLLECTED;
        }
        if(slot==null && !sell && purchaseBlockedUntil.getOrDefault(id,0L)>System.currentTimeMillis()) {
            status="Purchase blocked after unverified collection: "+item; return Result.FAILED;
        }
        if (!Rs2GrandExchange.isOpen())
        {
            status = "Open Grand Exchange"; Rs2GrandExchange.openExchange(); return Result.WAITING;
        }
        if (slot == null)
        {
            if (Rs2GrandExchange.findSlotForItem(id, sell) != null)
            {
                status = "Existing offer for " + item + " preserved; use another method";
                DebugLog.event("GE_BLOCKED", status); return Result.FAILED;
            }
            GrandExchangeSlots available = Rs2GrandExchange.getAvailableSlot();
            if (available == null) { status = "No free GE slot; existing offers preserved"; return Result.FAILED; }
            beforeQuantity=inventoryQuantity(id); selling=sell;
            boolean submitted = sell ? Rs2GrandExchange.sellItem(item, quantity, price)
                : Rs2GrandExchange.buyItem(item, price, quantity);
            DebugLog.event("GE_SUBMIT", (sell ? "SELL " : "BUY ") + quantity + " x " + item
                + " at " + price + "; submitted=" + submitted);
            if (!submitted) return Result.FAILED;
            OfferJournal.submitted(available.name(),id,item,quantity,price,sell);
            slot = available; itemId = id; name = item; placedAt = System.currentTimeMillis(); collected = false;
            status = "Waiting for " + (sell ? "sale: " : "purchase: ") + item;
            return Result.WAITING;
        }
        var details = Rs2GrandExchange.getOfferDetails(slot);
        if (details == null || details.getItemId() != itemId)
        {
            if (collected) { clear(); return Result.COLLECTED; }
            status = "Offer not observed in owned slot"; clear(); return Result.FAILED;
        }
        if (details.isCompleted())
        {
            boolean done = Rs2GrandExchange.collectOffer(slot, false);
            DebugLog.event("GE_COLLECT", name + "; result=" + done + "; filled=" + details.getQuantitySold());
            if (done) {
                OfferJournal.state(slot.name(),"COLLECTION_ACKNOWLEDGED");
                collected=true;
                if(!selling) {
                    receipt=new PurchaseReceipt(beforeQuantity,details.getQuantitySold(),System.currentTimeMillis());
                    status="Verify purchased inventory: "+name; return Result.WAITING;
                }
                clear(); return Result.COLLECTED;
            }
            return Result.WAITING;
        }
        if (System.currentTimeMillis() - placedAt > 90_000)
        {
            DebugLog.event("GE_TIMEOUT", "Cancelling only owned offer for " + name);
            Rs2GrandExchange.abortOffer(name, false); clear(); return Result.FAILED;
        }
        return Result.WAITING;
    }

    public void cancelOwned()
    {
        if (slot != null)
        {
            var offer = Rs2GrandExchange.getOfferDetails(slot);
            if (offer != null && offer.getItemId() == itemId) Rs2GrandExchange.abortOffer(name, false);
            clear();
        }
    }

    private void clear() { slot = null; itemId = -1; name = null; receipt=null; }
}
