package com.example.f2paio;
/** Bank representations follow the intended use, never a global always-noted setting. */
public final class BankWithdrawalPolicy {
    public enum Purpose { SALE, TOOL, EQUIPMENT, QUEST, TRAINING }
    public static boolean noted(Purpose purpose,boolean noteable) { return purpose==Purpose.SALE && noteable; }
    public static int saleQuantity(ResourceItem item,long deficit,int unitPrice,int freeSlots) {
        long wanted=Math.max(1,(Math.max(0,deficit)+Math.max(1,unitPrice)-1)/Math.max(1,unitPrice));
        long capacity=(item.noteable || item.stackable)?Integer.MAX_VALUE:Math.max(0,freeSlots);
        return (int)Math.min(Math.min(item.quantity,wanted),capacity);
    }
    private BankWithdrawalPolicy() { }
}
