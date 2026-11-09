package com.example.f2paio;
/** A world transition is not an account logout; bound the grace period instead of killing the task. */
public final class SessionTransitions {
    private long started=-1;
    public boolean waiting(net.runelite.api.GameState state,long now) {
        boolean transition=state==net.runelite.api.GameState.HOPPING || state==net.runelite.api.GameState.LOADING
            || state==net.runelite.api.GameState.CONNECTION_LOST || state==net.runelite.api.GameState.LOGGING_IN;
        if(!transition) { started=-1; return false; }
        if(started<0) started=now;
        return now-started<30_000;
    }
}
