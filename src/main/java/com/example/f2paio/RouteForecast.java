package com.example.f2paio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToLongFunction;
import net.runelite.api.coords.WorldPoint;

/** Compare executable chain branches against the same observed account snapshot. */
final class RouteForecast {
    private static final WorldPoint EXCHANGE = new WorldPoint(3165,3487,0);
    private final GameState state;
    private final List<ResourceItem> bank;
    private final IntToLongFunction prices;
    private final Map<String,Assessment> estimates = new HashMap<>();

    static final class Assessment {
        final double score;
        final String explanation;

        Assessment(double score,String explanation) {
            this.score=score; this.explanation=explanation;
        }
    }

    RouteForecast(GameState state,List<ResourceItem> bank,IntToLongFunction prices) {
        this.state=state; this.bank=List.copyOf(bank); this.prices=prices;
    }

    Assessment estimate(ChainPlan plan) {
        return estimates.computeIfAbsent(plan.id,ignored -> project(plan));
    }

    private Assessment project(ChainPlan plan) {
        WorldPoint position=state.location;
        Map<String,Integer> outputs=new HashMap<>();
        var steps=new java.util.ArrayList<String>();
        long gp=0;
        double travel=0, work=0;
        int bankStops=0, marketStops=0;
        for (ChainPlan.Step step:plan.steps) {
            Method method=Methods.get(step.method);
            switch (step.op) {
                case PREPARE:
                    boolean withdraw=false, buy=false;
                    for (ItemNeed need:new Requirements().missing(method,state)) {
                        if (need.id==995) continue;
                        int made=outputs.getOrDefault(need.name,0);
                        int minimum=F2pProduction.minimum(method.id,need.id);
                        if (made>=minimum) {
                            outputs.put(need.name,made-minimum);
                            continue;
                        }
                        long stored=bank.stream().filter(item -> Requirements.bankMatch(need,item,state))
                            .mapToLong(item -> item.quantity).sum();
                        long noted=state.items.stream().filter(item -> item.noted && item.canonicalId()==need.id)
                            .mapToLong(item -> item.quantity).sum();
                        withdraw |= stored+noted>0;
                        long missing=Math.max(0,need.quantity-stored-noted);
                        if (missing>0) {
                            buy=true;
                            gp+=missing*Math.max(1,prices.applyAsLong(need.id));
                        }
                    }
                    if (withdraw) { bankStops++; travel+=12; }
                    if (buy) {
                        marketStops++; travel+=position.distanceTo(EXCHANGE)*0.6+35;
                        position=EXCHANGE;
                    }
                    break;
                case TRAVEL:
                    travel+=position.distanceTo(method.location)*(state.runEnergy>=RunEnergy.ARM_AT?0.45:0.65);
                    position=method.location;
                    break;
                case ACT:
                    steps.add(method.id);
                    if (step.output!=null) {
                        work+=step.minimum*(method.kind==Method.Kind.FISHING?5:3);
                        outputs.merge(step.output,step.minimum,Integer::sum);
                    } else if (!method.id.equals(plan.goal)) {
                        work+=Math.min(step.count,12)*3;
                    }
                    break;
                case BANK:
                    if (!method.products.isEmpty()) { bankStops++; travel+=12; }
                    break;
                default:
                    break;
            }
        }
        // Compare the cost to get to the same goal batch, independent of the runner's action cap.
        double gpWeight=state.coins<1_000?0.06:0.02;
        double score=travel+work+gp*gpWeight;
        String explanation=String.join(" -> ",steps)+" | travel/setup "+Math.round(travel)
            +" | prerequisite work "+Math.round(work)+" | bank "+bankStops+" | GE "+marketStops+" | estimated GP "+gp;
        return new Assessment(score,explanation);
    }
}
