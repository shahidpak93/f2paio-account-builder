package com.example.f2paio;
import java.util.concurrent.CompletableFuture;
/** Alternate bounded method and chain drafting; no provider is needed for local learning/evolution. */
public final class DiscoveryCoordinator {
    private CompletableFuture<Method> method;
    private CompletableFuture<ChainPlan> chain;
    private long next;
    private boolean draftMethod;
    public void poll(GameState state,long now,boolean enabled,AgentBus agent,ChainLibrary library) {
        if(method!=null && method.isDone()) { Method m=method.getNow(null); method=null; if(m!=null) Methods.saveLearnedSkill(m); }
        if(chain!=null && chain.isDone()) { ChainPlan p=chain.getNow(null); chain=null; if(p!=null) library.propose(p); }
        if(!enabled || now<next || method!=null || chain!=null) return;
        if(draftMethod) method=agent.discoverSkill(state,Methods.knownIds());
        else chain=agent.discoverChain(state,library.knownIds());
        draftMethod=!draftMethod; next=now+45*Tuning.MINUTE;
    }
}
