package com.example.f2paio;

import java.util.List;
import java.util.Set;
import java.util.function.IntToLongFunction;

/**
 * Choose which banked stack to liquidate when the account has no GP.
 *
 * <p>Conservative by design: only allow-listed (bot-produced) item names are candidates, reserved
 * names are never touched, coins are excluded, and a stack must clear a minimum value before a bank
 * trip is worth it. Pure function so it can be unit-tested without a client.</p>
 */
public final class BankSalePlanner
{
    /** Java 11 target: a small value object instead of a record. */
    public static final class Candidate
    {
        private final int id;
        private final String name;
        private final int quantity;
        private final long value;

        Candidate(int id, String name, int quantity, long value)
        {
            this.id = id; this.name = name; this.quantity = quantity; this.value = value;
        }

        public int id() { return id; }
        public String name() { return name; }
        public int quantity() { return quantity; }
        public long value() { return value; }
    }

    public static Candidate choose(List<ResourceItem> bank, Set<String> allowed, Set<String> keep,
        long shortfall, int freeSlots, IntToLongFunction sellPrice)
    {
        Candidate best = null;
        for (ResourceItem item : bank)
        {
            if (item == null || item.id == 995 || item.quantity <= 0) continue;
            if (item.name == null || !allowed.contains(item.name) || keep.contains(item.name)) continue;
            int id = item.canonicalId();
            long unit = sellPrice.applyAsLong(id);
            if (unit <= 0) continue;
            int quantity = BankWithdrawalPolicy.saleQuantity(item, shortfall, (int) unit, freeSlots);
            if (quantity <= 0) continue;
            long value = unit * quantity;
            if (value < minValue(shortfall)) continue;
            if (best == null || value > best.value()) best = new Candidate(id, item.name, quantity, value);
        }
        return best;
    }

    static long minValue(long shortfall) { return 250; }

    private BankSalePlanner() { }
}
