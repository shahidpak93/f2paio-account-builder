package com.example.f2paio;
import java.util.List;
import java.util.function.IntToLongFunction;
/** Do not convert an unaffordable training choice into an open-ended funding goal. */
public final class BuilderBudget {
    public static boolean affordable(Method method,GameState state,List<ResourceItem> bank,IntToLongFunction price) {
        return affordable(new Requirements().missing(method,state),state,bank,price);
    }

    static boolean affordable(List<ItemNeed> needs,GameState state,List<ResourceItem> bank,IntToLongFunction price) {
        long cash=state.coins+bank.stream().filter(i -> i.id==995).mapToLong(i -> i.quantity).sum();
        long cost=0;
        for(ItemNeed need:needs) {
            if(need.id==995) { cost+=Math.max(0,need.quantity-state.coins); continue; }
            long stored=bank.stream().filter(i -> Requirements.bankMatch(need,i,state)).mapToLong(i -> i.quantity).sum();
            long carriedNotes=state.items.stream().filter(i -> i.noted && i.canonicalId()==need.id).mapToLong(i -> i.quantity).sum();
            long deficit=Math.max(0,need.quantity-stored-carriedNotes);
            if(state.ironman && deficit>0 && need.id!=1351) return false;
            // A tiny bootstrap for basic gathering tools is bounded; production inputs get none.
            if((need.id==1351 || need.id==1265) && deficit>0) cash+=150;
            cost+=deficit*Math.max(1,price.applyAsLong(need.id));
        }
        return cost<=cash;
    }
    public static boolean sellBatch(long value,long shortfall,int slots,long idleMs) {
        return slots>=24 || value>=Math.max(250,shortfall) || idleMs>=60_000;
    }
    private BuilderBudget() { }
}
