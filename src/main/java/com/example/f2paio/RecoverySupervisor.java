package com.example.f2paio;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** Bounded recovery proposals; deterministic controller still owns preconditions and replanning. */
public final class RecoverySupervisor {
    private CompletableFuture<String> pending;
    public String recover(Method current,GameState state,long now,long emptySince,int failures,AgentBus agent,
        WorldLootPolicy worlds,F2pAioActions actions,Random random) {
        if(pending!=null && pending.isDone()) {
            String action=pending.getNow(null); pending=null;
            if(action!=null) {
                DebugLog.event("SELF_HEAL","Recovery="+action+" goal="+current.id);
                if(action.equals("HOP_WORLD")) { worlds.preparePublic(state); return "WAIT"; }
                if(action.equals("STEP_RANDOM")) {
                    var loc=state.location;
                    actions.walk(new net.runelite.api.coords.WorldPoint(loc.getX()+(random.nextBoolean()?3:-3),loc.getY()+(random.nextBoolean()?3:-3),loc.getPlane()));
                    return "WAIT";
                }
                return "REPLAN";
            }
        }
        if(pending==null && ((emptySince>0 && now-emptySince>20_000) || failures>=2)) {
            var logs=DebugLog.recent();
            pending=agent.diagnose(logs.subList(Math.max(0,logs.size()-8),logs.size()),"pos="+state.location+" goal="+current.id+" inv="+state.inventory);
        }
        return "NONE";
    }
}
