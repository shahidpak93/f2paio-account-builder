package com.example.f2paio;
/** Phase-independent bounded progress monitoring. Travel has its own generous deadline. */
public final class IncidentDetector {
    private String goal="", inventory="", quests="";
    private long progressAt,stationaryAt,xp,coins,pauseAt;
    private int losses;
    public void pause(long now) {if(pauseAt==0) pauseAt=now;}
    public String observe(String id,GameState s,long now) {
        if(pauseAt!=0) {long delta=Math.max(0,now-pauseAt); progressAt+=delta;stationaryAt+=delta;pauseAt=0;}
        String q=s.quests.toString();
        String inv=s.items.stream().map(i->i.id+"x"+i.quantity).sorted().collect(java.util.stream.Collectors.joining(","));
        if(!id.equals(goal)) {goal=id;progressAt=stationaryAt=now;xp=s.xp;coins=s.coins;inventory=inv;quests=q;losses=0;return null;}
        if(s.xp>xp || !q.equals(quests)) {progressAt=now;losses=0;}
        if(s.coins<coins) losses++;
        if(!inv.equals(inventory) || s.moving || s.animating || s.combat) stationaryAt=now;
        xp=s.xp;coins=s.coins;quests=q;inventory=inv;
        if(losses>=3 && now-progressAt>60_000) return "repeated_coin_loss_without_account_progress";
        if(now-progressAt>360_000) return "no_xp_or_quest_progress_for_six_minutes";
        if(now-stationaryAt>120_000) return "unchanged_inventory_and_idle_for_two_minutes";
        return null;
    }
    public void reset() {goal="";pauseAt=0;}
}
