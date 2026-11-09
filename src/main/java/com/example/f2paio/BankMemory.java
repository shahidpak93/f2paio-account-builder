package com.example.f2paio;

import java.util.List;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

public final class BankMemory
{
    private volatile List<ResourceItem> items = List.of();
    private volatile long checkedAt;
    private long lastAttemptAt;
    private String signature="";

    public void observe()
    {
        if (!Rs2Bank.isOpen()) return;
        long now=System.currentTimeMillis();
        if(now-lastAttemptAt<1_000) return;
        lastAttemptAt=now;
        var snapshot=net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(() ->
            Rs2Bank.getAll().filter(i -> i.getQuantity()>0)
                .map(i -> new ResourceItem(i.getId(),i.getName(),i.getQuantity(),i.isNoted(),i.getUnNotedId(),i.getNotedId()!=i.getUnNotedId(),i.isStackable()))
                .collect(java.util.stream.Collectors.toUnmodifiableList())).orElse(null);
        if(!update(snapshot,now)) return;
        String current=items.stream().map(i->i.id+"x"+i.quantity).sorted().collect(java.util.stream.Collectors.joining(","));
        if(!current.equals(signature)) {signature=current;DebugLog.event("BANK_SCAN","Observed "+items.size()+" bank stacks");}
    }
    boolean update(List<ResourceItem> snapshot,long now) {
        if(snapshot==null) return false;
        items=List.copyOf(snapshot);checkedAt=now;return true;
    }

    public boolean known() { return checkedAt > 0; }
    public List<ResourceItem> items() { return items; }
    public int count(String name) { return items.stream().filter(i -> name.equalsIgnoreCase(i.name)).mapToInt(i -> i.quantity).sum(); }
}
