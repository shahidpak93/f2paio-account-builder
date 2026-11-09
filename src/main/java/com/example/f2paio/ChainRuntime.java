package com.example.f2paio;
import java.util.Random;
/** Per-account planning/execution/verification loop, shared by synthesized and AI-proposed chains. */
public final class ChainRuntime {
    private final ChainLibrary library;
    private final Random random;
    private final ChainRepair repair=new ChainRepair();
    private ChainRunner runner;
    private ChainPlan plan;
    private ChainGameBackend backend;
    private long began,xp,coins;
    private String status="No active chain",failure="",route="";
    public ChainRuntime(String accountHash,Random random) {
        this.random=random;
        library=new ChainLibrary(FilesStore.ROOT.resolve("learning").resolve(accountHash+"-chains.json"));
    }
    public ChainRunner.Result step(Method goal,GameState state,long now,F2pAioActions actions,F2pAioConfig config,SupplyService supplies,FundingTask seller,
        BankMemory bank,java.util.Set<String> excluded) throws Exception {
        if(runner==null) {
            java.util.function.IntToLongFunction price=id -> TradeService.unitPrice(id,true);
            boolean building=config.accountBuilder && !config.taskMode.equals("MONEY");
            if (state.busy || state.combat) return ChainRunner.Result.RUNNING;
            var allowed=repair.excluded(excluded);
            boolean compare=building && goal.kind==Method.Kind.PROCESS;
            var recipes=compare ? RecipeChains.variants(goal,state,bank.items(),allowed,price) : java.util.List.<ChainPlan>of();
            boolean direct=!building || BuilderBudget.affordable(goal,state,bank.items(),price);
            RouteForecast forecast=compare ? new RouteForecast(state,bank.items(),price) : null;
            plan=library.choose(goal,random,now,recipes,p -> repair.accepts(p) && ("recipe-dependency".equals(p.origin)
                ? recipes.stream().anyMatch(recipe -> recipe.id.equals(p.id) || recipe.id.equals(p.parent)
                    && sameMaterialOrder(recipe,p))
                : direct && p.steps.stream().allMatch(s -> stepEligible(s,goal,state,bank,allowed,price))),forecast);
            if (plan==null) {
                failure=repair.incident().isEmpty()?"No affordable, eligible chain outside quarantine"
                    :"No alternative after "+repair.incident();
                status=failure;
                return ChainRunner.Result.FAILED;
            }
            if (forecast!=null) route=forecast.estimate(plan).explanation;
            backend=new ChainGameBackend(actions,config,supplies,seller,plan); runner=new ChainRunner(plan,backend,now);
            began=now; xp=state.xp; coins=state.coins;
        }
        ChainRunner.Result result=runner.step(state,now); status=runner.status(); failure=runner.failure();
        if(result!=ChainRunner.Result.RUNNING) {
            library.observe(plan,result==ChainRunner.Result.DONE,runner.verified(),now-began,state.xp-xp,state.coins-coins,failure,random,now);
            if(result==ChainRunner.Result.DONE) Methods.recordLearnedOutcome(goal.id,true);
            else if (config.accountBuilder && !config.taskMode.equals("MONEY") && goal.kind==Method.Kind.PROCESS
                && repair.alternative(plan,runner.failedStep(),failure)) {
                status="Repairing "+goal.label+" after "+failure;
                DebugLog.event("CHAIN_REPAIR",status+"; use current inventory and a different branch");
                clearRunner();
                return ChainRunner.Result.RUNNING;
            } else if(failure.startsWith("NO_TARGET") || failure.startsWith("STEP_TIMEOUT")) Methods.recordLearnedOutcome(goal.id,false);
        }
        return result;
    }
    private static boolean sameMaterialOrder(ChainPlan original,ChainPlan variant) {
        if (original.steps.size()!=variant.steps.size()) return false;
        for (int i=0;i<original.steps.size();i++) {
            ChainPlan.Step before=original.steps.get(i), after=variant.steps.get(i);
            if (before.op!=after.op || !before.method.equals(after.method)
                || !java.util.Objects.equals(before.output,after.output) || before.minimum!=after.minimum) return false;
        }
        return true;
    }

    private boolean stepEligible(ChainPlan.Step step,Method goal,GameState state,BankMemory bank,
        java.util.Set<String> excluded,java.util.function.IntToLongFunction price) {
        Method method=Methods.get(step.method);
        return method.id.equals(goal.id) || (!excluded.contains(method.id) && !method.id.equals("pvp_loot")
            && !Methods.learnedBlocked(method.id) && state.levels.getOrDefault(method.skill,1)>=method.level
            && BuilderBudget.affordable(method,state,bank.items(),price));
    }

    public void pause(long now) { if(runner!=null) runner.pause(now); }
    public void feedback(String message) { if(runner!=null) runner.reject(message); }
    public String status() { return status; }
    public String failure() { return failure; }
    public String view() {
        ChainPlan snapshot=plan;
        if(snapshot==null) return "No active chain.\n"+library.summary();
        StringBuilder text=new StringBuilder("Goal: "+snapshot.goal+"\nChain: "+snapshot.id
            +"\nOrigin: "+snapshot.origin+" | generation "+snapshot.generation+" | parent "+snapshot.parent
            +"\n"+status+"\n"+route+"\n\n");
        int number=1;
        for(ChainPlan.Step step:snapshot.steps) text.append(number++).append(". ").append(step.op).append(" ")
            .append(step.method).append(step.op==ChainPlan.Op.ACT?" (up to "+step.count+" verified actions)":"").append("\n");
        return text.toString();
    }
    public ChainLibrary library() { return library; }
    private void clearRunner() { if(backend!=null) backend.close(); runner=null; plan=null; backend=null; }
    public void reset() { clearRunner(); repair.clear(); failure=""; route=""; }
}
