package com.example.f2paio;

/** A dispatch acknowledgement never proves that purchased items reached inventory. */
public final class PurchaseReceipt {
    public enum State { WAITING, VERIFIED, FAILED }
    private final long before;
    private final long expected;
    private long deadline;
    public PurchaseReceipt(long before,long expected,long now) {
        this.before=before; this.expected=Math.max(1,expected); deadline=now+8_000;
    }
    public State observe(long quantity,long now) {
        if(quantity-before>=expected) return State.VERIFIED;
        return now>=deadline?State.FAILED:State.WAITING;
    }
}
