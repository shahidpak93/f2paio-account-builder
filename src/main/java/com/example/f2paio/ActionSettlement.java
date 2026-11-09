package com.example.f2paio;
/** Bounded asynchronous setup: acknowledgement is not an observed effect. */
public final class ActionSettlement {
    public enum Result { READY, ATTEMPT, WAIT, FAILED }
    private String key="";
    private int attempts;
    private long last;
    private long pauseAt;
    private final int maximum;
    private final long settleMs;
    public ActionSettlement(int maximum,long settleMs) {this.maximum=maximum;this.settleMs=settleMs;}
    public Result poll(String requested,boolean observed,long now) {
        if(pauseAt!=0) {last+=Math.max(0,now-pauseAt);pauseAt=0;}
        if(!requested.equals(key)) {key=requested;attempts=0;last=0;}
        if(observed) {attempts=0;return Result.READY;}
        if(attempts>0 && now-last<settleMs) return Result.WAIT;
        if(attempts>=maximum) return Result.FAILED;
        attempts++;last=now;return Result.ATTEMPT;
    }
    public void pause(long now) {if(pauseAt==0) pauseAt=now;}
    public void reset() {key="";attempts=0;last=0;pauseAt=0;}
}
