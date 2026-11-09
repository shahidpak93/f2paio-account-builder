package com.example.f2paio;
import java.util.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
/** Adapter uses the same native interaction layer as the existing controller. */
public final class ChainGameBackend implements ChainRunner.Backend {
    private final F2pAioActions actions;
    private final F2pAioConfig config;
    private final SupplyService supplies;
    private final WorldLootPolicy worlds=new WorldLootPolicy();
    private final EquipmentPolicy gear=new EquipmentPolicy();
    private final ProductionActions production=new ProductionActions();
    private final BankTask bank=new BankTask();
    private final FundingTask seller;
    private final Set<String> reserved;
    private long saleCoins=-1;
    private String blocked="";
    public ChainGameBackend(F2pAioActions actions,F2pAioConfig config,SupplyService supplies,FundingTask seller,ChainPlan plan) {
        this.actions=actions; this.config=config; this.supplies=supplies; this.seller=seller;
        reserved=plan.steps.stream().map(step -> step.output).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    }
    public boolean prepare(Method m,GameState state) {
        if(state.levels.getOrDefault(m.skill,1)<m.level) { blocked="Insufficient level for chain capability "+m.id; return false; }
        if(Methods.learnedBlocked(m.id)) { blocked="Learned method quarantined"; return false; }
        if(state.inventory>=Tuning.INVENTORY_CAPACITY-2) {
            // Clear leftovers from abandoned goals first; only then bank this goal's products.
            if(bank.depositUnrelated(state,m,reserved)) return false;
            if (m.products.stream().anyMatch(reserved::contains)) {
                if (state.inventory>=Tuning.INVENTORY_CAPACITY) {
                    blocked="No space for chain inputs or outputs";
                    return false;
                }
            } else if(m.products.stream().mapToInt(state::count).sum()>0) { bank.step(m,config); return false; }
        }
        return supplies.step(m,state,actions) && ToolPolicy.hasTools(m,state) && gear.step(m,state);
    }
    public boolean travel(Method m,GameState state) {
        if(!worlds.prepare(m,state)) return false;
        F2pProduction.Spec spec=F2pProduction.get(m.id);
        if (spec!=null && spec.altar()) {
            if (spec.inside(state)) return true;
            if (state.location.distanceTo(m.location)<=6) production.enterAltar(spec,state);
            else actions.walk(m.location,6);
            return false;
        }
        int radius=m.id.startsWith("burn_")?1:Tuning.TRAVEL_RADIUS;
        if(actions.localTarget(m) || state.location.distanceTo(m.location)<=radius) return true;
        actions.walk(m.location,radius); return false;
    }
    public boolean act(Method m,GameState state) throws Exception {
        if(Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return false; }
        if(Rs2Shop.isOpen()) { Rs2Shop.closeShop(); return false; }
        if(Rs2GrandExchange.isOpen()) { Rs2GrandExchange.closeExchange(); return false; }
        if(!new Requirements().missing(m,state).isEmpty()) return false;
        return m.kind==Method.Kind.OBJECT ? actions.object(m) : m.kind==Method.Kind.GROUND ? actions.ground(m)
            : m.kind==Method.Kind.PROCESS ? production.step(m,state) : actions.npc(m);
    }
    public boolean bank(Method m,GameState state) {
        F2pProduction.Spec spec=F2pProduction.get(m.id);
        if (spec!=null && spec.inside(state)) {
            production.leaveAltar();
            return false;
        }
        if(m.products.stream().mapToInt(state::count).sum()==0 && !Rs2Bank.isOpen()) return true;
        bank.step(m,config); return false;
    }
    public boolean sell(Method m,GameState state) {
        if(saleCoins<0) saleCoins=state.coins;
        if(m.products.stream().mapToInt(state::total).sum()==0 && !seller.pendingSale()) return state.coins>saleCoins;
        Set<String> reserved=new HashSet<>();
        for(ResourceItem item:state.items) if(!m.products.contains(item.name)) reserved.add(item.name);
        seller.step(state,actions,Long.MAX_VALUE,reserved); return false;
    }
    public void tick(Method m) { if(m.kind==Method.Kind.PROCESS) production.continuePrompt(m); }
    public void rejected(Method m,long now) {
        if(m.kind==Method.Kind.GROUND) GroundLoot.reject(m.id,now);
        Methods.recordLearnedOutcome(m.id,false);
    }
    public String failure() { return blocked.isEmpty()?supplies.failure():blocked; }
    public void close() { supplies.reset(); }
}
