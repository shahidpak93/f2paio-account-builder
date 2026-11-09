package com.example.f2paio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntToLongFunction;

/** Backward chaining over registered producers, with bounded search and explicit material effects. */
final class RecipeChains {
    private static final int MAX_PRODUCERS = 3;
    private static final int MAX_ALTERNATIVES = 4;

    private final GameState state;
    private final List<ResourceItem> bank;
    private final Set<String> excluded;
    private final IntToLongFunction price;

    private RecipeChains(GameState state,List<ResourceItem> bank,Set<String> excluded,IntToLongFunction price) {
        this.state=state;
        this.bank=bank;
        this.excluded=excluded;
        this.price=price;
    }

    static ChainPlan plan(Method goal, GameState state, List<ResourceItem> bank,
        Set<String> excluded, IntToLongFunction price) {
        return new RecipeChains(state,bank,excluded,price).variants(goal).stream().findFirst().orElse(null);
    }

    List<ChainPlan> variants(Method goal) {
        if (goal.kind != Method.Kind.PROCESS) return List.of();
        List<Route> routes = expand(goal,new Route(),Set.of(goal.id),0);
        routes.removeIf(route -> route.blocks.isEmpty() || !BuilderBudget.affordable(route.direct,state,bank,price));
        routes.sort(Comparator.comparingLong(route -> cost(route,goal)));
        List<ChainPlan> plans = new ArrayList<>();
        for (Route route : routes) {
            ChainPlan plan = new ChainPlan();
            plan.goal=goal.id;
            plan.origin="recipe-dependency";
            for (Block block : route.blocks) plan.addBlock(block.method,Math.min(16,block.need.quantity+4),block.need);
            plan.addBlock(goal,Math.min(10,route.blocks.get(route.blocks.size()-1).need.quantity));
            plan.steps.add(new ChainPlan.Step(ChainPlan.Op.BANK,goal.id,1));
            plan.id=ChainPlan.contentId(plan);
            if (ChainPlan.valid(plan) && plans.stream().noneMatch(existing -> existing.id.equals(plan.id))) plans.add(plan);
            if (plans.size()>=MAX_ALTERNATIVES) break;
        }
        return plans;
    }

    static List<ChainPlan> variants(Method goal, GameState state, List<ResourceItem> bank,
        Set<String> excluded, IntToLongFunction price) {
        return new RecipeChains(state,bank,excluded,price).variants(goal);
    }

    private long cost(Route route, Method goal) {
        long distance = 0;
        var position = state.location;
        for (Block block : route.blocks) {
            distance += position.distanceTo(block.method.location) + block.need.quantity*4L;
            if (block.method.kind==Method.Kind.GROUND) distance += 100;
            position = block.method.location;
        }
        return distance + position.distanceTo(goal.location)
            + route.direct.stream().filter(need -> !owned(need)).mapToLong(need -> Math.min(10_000,price.applyAsLong(need.id))/20).sum();
    }

    private List<Route> expand(Method method, Route start, Set<String> visiting, int depth) {
        List<Route> routes = List.of(start);
        for (ItemNeed need : new Requirements().missing(method,state)) {
            List<Route> next = new ArrayList<>();
            for (Route route : routes) {
                Route direct = route.copy();
                direct.direct.add(need);
                next.add(direct);
                if (depth>=MAX_PRODUCERS || owned(need) || route.blocks.size()>=MAX_PRODUCERS
                    || ProductionRecipes.inputs(method.id).stream().noneMatch(input -> input.id==need.id)) continue;
                for (Method source : producers(need,visiting,method)) {
                    Set<String> active = new HashSet<>(visiting);
                    active.add(source.id);
                    for (Route prepared : expand(source,route.copy(),active,depth+1)) {
                        if (prepared.blocks.size()>=MAX_PRODUCERS) continue;
                        prepared.blocks.add(new Block(source,need));
                        next.add(prepared);
                    }
                }
            }
            next.sort(Comparator.comparing((Route route) -> !BuilderBudget.affordable(route.direct,state,bank,price))
                .thenComparingLong(route -> cost(route,method)));
            routes = new ArrayList<>(next.subList(0,Math.min(next.size(),12)));
        }
        return new ArrayList<>(routes);
    }

    private boolean owned(ItemNeed need) {
        return state.items.stream().anyMatch(i -> i.canonicalId()==need.id)
            || bank.stream().anyMatch(i -> Requirements.bankMatch(need,i,state));
    }

    private List<Method> producers(ItemNeed need, Set<String> visiting, Method consumer) {
        return Methods.all().stream().filter(ChainPlan::supported)
            .filter(method -> !method.id.equals("pvp_loot") && (!state.ironman || method.kind!=Method.Kind.GROUND)
                && method.products.contains(need.name)
                && state.levels.getOrDefault(method.skill,1)>=method.level && !excluded.contains(method.id)
                && !visiting.contains(method.id) && !Methods.learnedBlocked(method.id))
            .sorted(Comparator.comparingInt(method -> method.location.distanceTo(state.location)
                + method.location.distanceTo(consumer.location)+(method.kind==Method.Kind.GROUND?100:0)))
            .limit(MAX_ALTERNATIVES).collect(java.util.stream.Collectors.toList());
    }

    private static final class Block {
        final Method method;
        final ItemNeed need;
        Block(Method method, ItemNeed need) { this.method=method; this.need=need; }
    }

    private static final class Route {
        final List<Block> blocks = new ArrayList<>();
        final List<ItemNeed> direct = new ArrayList<>();
        Route copy() {
            Route copy = new Route();
            copy.blocks.addAll(blocks);
            copy.direct.addAll(direct);
            return copy;
        }
    }
}
