package com.example.f2paio;

/** Replayable executor; backend owns game interaction, runner owns order, deadlines and observed effects. */
public final class ChainRunner {
    public enum Result { RUNNING, DONE, FAILED }
    public interface Backend {
        boolean prepare(Method method,GameState state) throws Exception;
        boolean travel(Method method,GameState state) throws Exception;
        boolean act(Method method,GameState state) throws Exception;
        boolean bank(Method method,GameState state) throws Exception;
        boolean sell(Method method,GameState state) throws Exception;
        default void tick(Method method) throws Exception { }
        default void rejected(Method method,long now) { }
        default String failure() { return ""; }
    }
    private final ChainPlan plan;
    private final Backend backend;
    private long started;
    private final java.util.concurrent.atomic.AtomicLong pausedAt=new java.util.concurrent.atomic.AtomicLong(-1);
    private volatile String rejection="";
    private long stepAt,sentAt,lastEffectAt;
    private int index,repeated,verified;
    private GameState before;
    private String status="Preparing chain",failure="";
    private Result result=Result.RUNNING;
    public ChainRunner(ChainPlan plan,Backend backend,long now) {
        if(!ChainPlan.valid(plan)) throw new IllegalArgumentException("Invalid chain contract");
        this.plan=plan.copy(); this.backend=backend; started=now; stepAt=now;
    }
    public Result step(GameState state,long now) throws Exception {
        try { return advanceState(state,now); }
        catch(InterruptedException ex) { throw ex; }
        catch(Exception ex) { DebugLog.error(ex); return fail("BACKEND_EXCEPTION: "+ex.getClass().getSimpleName()); }
    }
    private Result advanceState(GameState state,long now) throws Exception {
        if(result!=Result.RUNNING) return result;
        long pause=pausedAt.getAndSet(-1);
        if(pause>=0) { long delay=Math.max(0,now-pause); started+=delay; stepAt+=delay; sentAt+=delay; lastEffectAt+=delay; }
        if(now-started>12*Tuning.MINUTE) return fail("CHAIN_TIMEOUT");
        if(index>=plan.steps.size()) { result=Result.DONE; return result; }
        ChainPlan.Step step=plan.steps.get(index); Method method=Methods.get(step.method);
        status=plan.id+" step="+(index+1)+"/"+plan.steps.size()+" "+step.op+" "+method.label+" verified="+verified;
        backend.tick(method);
        if(!backend.failure().isEmpty()) return fail("BACKEND: "+backend.failure());
        if(now-stepAt>4*Tuning.MINUTE) return fail("STEP_TIMEOUT: "+step.op);
        switch(step.op) {
            case PREPARE: if(backend.prepare(method,state)) advance(now); break;
            case TRAVEL: if(backend.travel(method,state)) advance(now); break;
            case ACT:
                if(state.busy || state.combat) {
                    // A continuous action (fishing, mining, chopping, cooking) keeps the player busy
                    // while it produces. Do not wait for idle before counting the effect: step into
                    // VERIFY so accumulated XP/products are observed instead of timing the chain out.
                    if(before!=null) advance(now);
                    return result;
                }
                if (step.output!=null && state.count(step.output)>=step.minimum) {
                    index+=2; repeated=0; before=null; stepAt=now; return result;
                }
                if(!backend.prepare(method,state)) return result;
                if(backend.act(method,state)) { before=state; sentAt=now; lastEffectAt=now; advance(now); }
                else if(now-stepAt>30_000) {
                    if(repeated>0 && step.output==null) {
                        DebugLog.event("CHAIN_PARTIAL",method.id+": targets exhausted after "+repeated+" verified actions; continue to settlement");
                        index+=2; repeated=0; before=null; stepAt=now;
                    } else return fail("NO_TARGET: "+method.id);
                }
                break;
            case VERIFY:
                if(!rejection.isEmpty()) { backend.rejected(method,now); return fail("GAME_REJECTED: "+rejection); }
                ChainPlan.Step action = plan.steps.get(index-1);
                if(effect(method,before,state)) {
                    boolean consumedBatch=method.kind==Method.Kind.PROCESS
                        && ProductionRecipes.inputs(method.id).stream().anyMatch(input -> {
                            int minimum=F2pProduction.minimum(method.id,input.id);
                            return before.count(input.name)>=minimum && state.count(input.name)<minimum;
                        });
                    verified++; repeated++; lastEffectAt=now; before=state;
                    DebugLog.event("CHAIN_VERIFIED",method.id+" observed XP/item growth; count="+verified);
                    boolean missingOutput = action.output!=null && state.count(action.output)<action.minimum;
                    boolean continueBatch = !consumedBatch && repeated<action.count && state.inventory<Tuning.INVENTORY_CAPACITY;
                    if (missingOutput && !continueBatch) return fail("MATERIAL_SHORTFALL: "+action.output+" x"+action.minimum);
                    if (continueBatch && (action.output==null || missingOutput)) {
                        index--; stepAt=now;
                    } else { repeated=0; before=null; advance(now); }
                } else if(now-lastEffectAt>=plan.verifySeconds*1000L) {
                    if(repeated>0 && action.output==null) {
                        DebugLog.event("CHAIN_PARTIAL",method.id+": verified="+repeated
                            +" before quiet window; settlement instead of failure");
                        repeated=0; before=null; advance(now);
                    } else {
                        backend.rejected(method,now); return fail("EFFECT_TIMEOUT: "+method.id);
                    }
                }
                break;
            case BANK: if(backend.bank(method,state)) advance(now); break;
            case SELL: if(backend.sell(method,state)) advance(now); break;
            default: return fail("UNKNOWN_STEP");
        }
        if(index>=plan.steps.size()) result=Result.DONE;
        return result;
    }
    public static boolean effect(Method method,GameState before,GameState after) {
        if(before==null) return false;
        boolean products=method.products.stream().mapToInt(after::count).sum()>method.products.stream().mapToInt(before::count).sum();
        return products || (method.kind!=Method.Kind.GROUND
            && after.experience.getOrDefault(method.skill,0)>before.experience.getOrDefault(method.skill,0));
    }
    private void advance(long now) { index++; stepAt=now; }
    private Result fail(String reason) { failure=reason; result=Result.FAILED; return result; }
    public String status() { return status; }
    public String failure() { return failure; }
    ChainPlan.Step failedStep() {
        return result==Result.FAILED && index<plan.steps.size() ? plan.steps.get(index) : null;
    }
    public int verified() { return verified; }
    public void pause(long now) { pausedAt.compareAndSet(-1,now); }
    public void reject(String message) {
        if(index>=plan.steps.size() || plan.steps.get(index).op!=ChainPlan.Op.VERIFY || message==null) return;
        String normalized=message.toLowerCase(java.util.Locale.ROOT);
        if(normalized.contains("you need") || normalized.contains("you can't") || normalized.contains("you cannot")
            || normalized.contains("not enough") || normalized.contains("can't reach") || normalized.contains("not high enough")) rejection=message;
    }
}
