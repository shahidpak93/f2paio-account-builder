package com.example.f2paio;

public final class SupplyPolicy
{
    public enum Next { READY, INSPECT_BANK, WITHDRAW_ITEM, WITHDRAW_COINS, EARN_COINS, BUY }

    public static Next next(boolean satisfied, boolean bankKnown, int storedQuantity,
        long bankCoins, long carriedCoins, long purchaseCost)
    {
        if (satisfied) return Next.READY;
        if (!bankKnown) return Next.INSPECT_BANK;
        if (storedQuantity > 0) return Next.WITHDRAW_ITEM;
        if (carriedCoins < purchaseCost && bankCoins > 0) return Next.WITHDRAW_COINS;
        if (carriedCoins < purchaseCost) return Next.EARN_COINS;
        return Next.BUY;
    }

    private SupplyPolicy() { }
}
