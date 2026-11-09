package com.example.f2paio;

import javax.swing.SwingUtilities;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.coords.WorldPoint;

/** Delegates to the installed native GE Flipper and the user's existing Copilot session. */
public final class FlipTask {
    public enum Result { FLIPPING, DONE, FAILED }
    private Plugin owned;
    private volatile boolean starting, failed, finishing, stopRequested;
    private long began;
    private String status="Native Copilot integration idle";
    public String status() { return status; }
    public Result step(GameState state,F2pAioActions actions,long reserve) {
        if(failed) return Result.FAILED;
        if(owned!=null) {
            if(starting) return Result.FLIPPING;
            if(!Microbot.getPluginManager().isActive(owned)) { owned=null; return Result.DONE; }
            if(System.currentTimeMillis()-began>10*Tuning.MINUTE || state.coins<reserve) cancel();
            status=finishing ? "Native flipper finishing owned orders; awaiting stop" : "Native GE Flipper owns trading; Copilot supplies suggestions";
            return Result.FLIPPING;
        }
        if(state.coins<reserve) return Result.DONE;
        Plugin nativePlugin=Microbot.getPluginManager().getPlugins().stream()
            .filter(p -> p.getClass().getName().equals("net.runelite.client.plugins.microbot.geflipper.FlipperPlugin"))
            .findFirst().orElse(null);
        boolean copilot=Microbot.getPluginManager().getPlugins().stream().anyMatch(p ->
            (p.getClass().getSimpleName().equals("FlippingCopilotPlugin") || p.getClass().getSimpleName().equals("FlipAssistPlugin"))
                && Microbot.getPluginManager().isActive(p));
        if(nativePlugin==null || !copilot || Microbot.getPluginManager().isActive(nativePlugin)) {
            status="Native flipper unavailable, Copilot inactive, or trading already externally owned";
            DebugLog.event("FLIP_UNAVAILABLE",status); return Result.FAILED;
        }
        if(state.location.distanceTo(new WorldPoint(3165,3487,0))>7) {
            status="Travel to GE for native Copilot session"; actions.walk(new WorldPoint(3165,3487,0)); return Result.FLIPPING;
        }
        // Verify Finish API before acquiring control; never copy or inspect subscription credentials.
        try { nativePlugin.getClass().getDeclaredMethod("requestFinish"); }
        catch(ReflectiveOperationException ex) { status="Unsupported native flipper Finish API"; return Result.FAILED; }
        owned=nativePlugin; starting=true; began=System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> {
            try {
                Microbot.getPluginManager().setPluginEnabled(nativePlugin,true);
                if(!Microbot.getPluginManager().startPlugin(nativePlugin)) failed=true;
            } catch(Exception ex) { failed=true; DebugLog.error(ex); }
            finally { starting=false; if(stopRequested) cancel(); }
        });
        DebugLog.event("FLIP_DELEGATE","Native GE Flipper started with existing Copilot configuration; AIO actions suspended");
        return Result.FLIPPING;
    }
    public void cancel() {
        stopRequested=true;
        Plugin plugin=owned;
        if(plugin==null || starting || finishing) return;
        finishing=true;
        SwingUtilities.invokeLater(() -> {
            try {
                var finish=plugin.getClass().getDeclaredMethod("requestFinish");
                finish.setAccessible(true); finish.invoke(plugin);
                DebugLog.event("FLIP_FINISH","Requested native graceful Finish; awaiting owned-order settlement");
            } catch(ReflectiveOperationException ex) { DebugLog.error(ex); status="Native Finish failed; trading remains delegated"; }
        });
    }
    public void reset() { if(owned==null) { failed=false; finishing=false; stopRequested=false; began=0; } }
}
