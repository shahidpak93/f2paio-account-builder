package com.example.f2paio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class F2pAioScript extends Script
{
    private static final Logger LOG = LoggerFactory.getLogger(F2pAioScript.class);
    private final AtomicBoolean active = new AtomicBoolean();
    private volatile String status = "Stopped";
    private volatile Engine engine;
    private volatile Metrics metrics;
    private volatile AgentBus agent;
    private SafetyMonitor safety;
    private Scheduler scheduler;
    private Watchdog watchdog;
    private F2pAioConfig config;
    private Persona persona;
    private Random random;
    private String accountId;
    private long logoutDeadline;
    private boolean logoutPending;
    private boolean previousAutorun;
    private long retryAt;
    private long errorLogAt;
    private final ProgressTracker progress = new ProgressTracker();
    private RuntimeObserver observer;
    private volatile long lastCycleAt;
    private volatile String gate = "";
    private long observedHeartbeatAt;
    private final SessionTransitions transitions=new SessionTransitions();
    private volatile String chatAction="";
    private volatile String lastSuggestedAction="NONE";
    private long lastMonitorAt;
    private volatile boolean monitorInFlight;

    public synchronized boolean run(F2pAioConfig requested, String key) throws IOException
    {
        if (active.get()) return false;
        requested.validate();
        GameState state = GameState.capture();
        if (state == null) throw new IllegalArgumentException("Log in before starting");
        if (Files.exists(FilesStore.ROOT.resolve("STOP")))
            throw new IllegalArgumentException("STOP file exists. Remove it before starting");
        config = requested.copy();
        config.autonomous = true;
        config.taskMode = "AIO";
        persona = Persona.load(state.account, config.personalitySeed);
        accountId = persona.id;
        Path cooldown = FilesStore.ROOT.resolve("cooldowns").resolve(accountId + ".json");
        if (Files.exists(cooldown))
        {
            Long until = FilesStore.read(cooldown, Long.class);
            if (until != null && System.currentTimeMillis() < until)
            {
                status="Scheduled break active until "+java.time.Instant.ofEpochMilli(until).atZone(java.time.ZoneId.systemDefault()).toLocalTime();
                DebugLog.event("START_DEFERRED",status); return false;
            }
        }
        random = new Random(persona.seed ^ System.nanoTime());
        long now = System.currentTimeMillis();
        watchdog = new Watchdog(FilesStore.ROOT.resolve("STOP"), now);
        scheduler = new Scheduler(random, persona, config, now);
        metrics = new Metrics();
        var motion = Recorder.latest();
        agent = new AgentBus(config, key);
        engine = new Engine(config, persona, random, metrics, watchdog,
            new SharedStore(FilesStore.ROOT.resolve("shared")), agent, new HumanInput(motion, random));
        logoutPending = false;
        retryAt = 0; errorLogAt = 0;
        // Never force Microbot.enableAutoRunOn=false here. Disabling it removed the inherited
        // Script.run() auto-run and the walker's requestAutoRun(), so every trip was walked.
        // Microbot's own guard already skips the orb while a blocking interface is open, and
        // RunEnergy adds a long-trip policy on top. The value is captured only so shutdown can
        // leave Microbot exactly as it was found.
        previousAutorun = Microbot.enableAutoRunOn;
        active.set(true);
        resume();
        RunEnergy.sessionStart(); CombatTraining.reset();
        AntiblockerTuner.apply();
        status = "Running";
        progress.reset(); progress.observe(state); lastCycleAt = now; gate = "";
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::cycle,
            Tuning.POLL_MS, Tuning.POLL_MS, TimeUnit.MILLISECONDS);
        safety = new SafetyMonitor(() -> {
            if (!active.get()) return;
            if (watchdog.killed()) stop("Kill switch");
            else if (net.runelite.client.plugins.microbot.ScriptPauseController.isPaused()) {
                long pausedAt=System.currentTimeMillis(); watchdog.pause(pausedAt);
                if(engine!=null) engine.pause(pausedAt);
            }
        });
        observer = new RuntimeObserver(this::observe);
        DebugLog.event("START", "version=" + Tuning.VERSION + " autonomous=true; task selection uses live levels/tools/supplies");
        LOG.info("F2P AIO session started");
        return true;
    }

    private void cycle()
    {
        if (!active.get()) return;
        try
        {
            if (watchdog.killed()) { stop("Kill switch"); return; }
            long now = System.currentTimeMillis();
            lastCycleAt = now;
            String queued = chatAction; chatAction = "";
            if (!queued.isEmpty()) applySafeAction(queued);
            monitorTick(now);
            if (now < retryAt) return;
            if (logoutPending)
            {
                if (!Microbot.isLoggedIn()) { stop("Session ended; logged out"); return; }
                if (now > logoutDeadline) { stop("Session ended; logout not confirmed"); return; }
                Rs2Player.logoutWithoutAutoLoginStep();
                return;
            }
            var gameState=Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getGameState())
                .orElse(net.runelite.api.GameState.LOGIN_SCREEN);
            if(transitions.waiting(gameState,now)) {
                watchdog.pause(now); if(engine!=null) engine.pause(now); gate="Waiting for world/connection transition"; return;
            }
            if (!Microbot.isLoggedIn()) { stop("Logged out or connection transition timed out"); return; }
            if (scheduler.ended(now) || !Scheduler.withinHours(config.activeFrom, config.activeTo, LocalTime.now()))
            {
                long breakMinutes = Math.min(config.breakMax,
                    Math.max(1, Math.round(Distributions.lognormal(random, config.breakMax * 0.6, 0.25))));
                FilesStore.write(FilesStore.ROOT.resolve("cooldowns").resolve(accountId + ".json"),
                    now + breakMinutes * Tuning.MINUTE);
                logoutPending = true; logoutDeadline = now + Tuning.RETRY_MS;
                status = "Session ended; logging out for break";
                return;
            }
            if (!super.run())
            {
                watchdog.pause(now); if(engine!=null) engine.pause(now); gate = "Microbot blocking event or input ownership gate";
                DebugLog.event("BLOCKED", gate); return;
            }
            gate = "";
            if (!scheduler.ready(now, persona, config))
            {
                if (scheduler.idle(now)) { watchdog.pause(now); if(engine!=null) engine.pause(now); status = "Short idle break"; }
                return;
            }
            GameState state = GameState.capture();
            if (state == null) return;
            if (!accountId.equals(Persona.hash(state.account.trim().toLowerCase(java.util.Locale.ROOT))))
            {
                stop("Account changed; restart for the new account"); return;
            }
            engine.step(state, now);
            status = engine.status();
            Microbot.status = "F2P AIO: " + status;
            if (engine.complete())
            {
                if (engine.logoutRequested())
                {
                    logoutPending = true; logoutDeadline = now + Tuning.RETRY_MS;
                }
                else stop(status);
            }
        }
        catch (Exception ex)
        {
            if (ex instanceof InterruptedException || Thread.currentThread().isInterrupted())
            {
                Thread.currentThread().interrupt(); stop("Interrupted"); return;
            }
            metrics.errors++;
            DebugLog.error(ex);
            status = "Retry: " + ex.getClass().getSimpleName();
            long now = System.currentTimeMillis();
            retryAt = now + Tuning.ACTION_SETTLE_MS;
            if (now >= errorLogAt)
            {
                LOG.warn("F2P AIO cycle failed: {}", ex.getClass().getSimpleName());
                errorLogAt = now + Tuning.RETRY_MS;
            }
        }
    }

    public String status()
    {
        if (!active.get()) return status;
        if (!gate.isEmpty()) return gate;
        return engine == null ? status : engine.status();
    }
    public boolean active() { return active.get(); }
    public String planView() { return engine==null?"Start auto to read the account and build a plan":engine.planView(); }
    public String learning() { return engine==null?"Learning starts with your account snapshot":engine.learning(); }
    public ProgressTracker.Snapshot progress() { return progress.snapshot(); }
    public java.util.List<ResourceItem> bankItems() { return engine == null ? java.util.List.of() : engine.bankItems(); }
    public void skip() { if (engine != null && active.get()) engine.requestSkip(); }
    public void resume()
    {
        // Start/Resume is explicit user intent; later manual pauses remain respected.
        net.runelite.client.plugins.microbot.ScriptPauseController.setPaused(false);
        Microbot.pauseAllScripts.set(false);
        DebugLog.event("RESUME", "Explicit Start/Resume cleared the manual and legacy global script pause flags");
    }
    public String aiContext() { return engine == null ? "{}" : engine.aiContext(); }
    public void setLastSuggestedAction(String action) { lastSuggestedAction = action == null ? "NONE" : action; }
    public String lastSuggestedAction() { return lastSuggestedAction; }
    public void queueSafeAction(String action)
    {
        if (!AiBridge.allowed(action)) return;
        chatAction = action.toUpperCase(java.util.Locale.ROOT);
        DebugLog.event("AI_ACTION_QUEUED", chatAction);
    }
    private void applySafeAction(String action)
    {
        try
        {
            switch (action)
            {
                case "RESUME": resume(); break;
                case "SKIP_GOAL": case "REPLAN": if (engine != null) engine.requestSkip(); break;
                case "BANK_LEFTOVERS": if (engine != null) engine.requestCleanup(); break;
                case "RESTART_QUEST": if (engine != null) engine.requestQuestRecovery(); break;
                case "CLOSE_INTERFACE":
                    if (net.runelite.client.plugins.microbot.util.bank.Rs2Bank.isOpen())
                        net.runelite.client.plugins.microbot.util.bank.Rs2Bank.closeBank();
                    if (net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange.isOpen())
                        net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange.closeExchange();
                    if (net.runelite.client.plugins.microbot.util.shop.Rs2Shop.isOpen())
                        net.runelite.client.plugins.microbot.util.shop.Rs2Shop.closeShop();
                    break;
                default: return;
            }
            DebugLog.event("AI_ACTION", action + " applied by the controller");
        }
        catch (Exception ex) { DebugLog.event("AI_ACTION_FAIL", action + " " + ex.getClass().getSimpleName()); }
    }
    private void monitorTick(long now)
    {
        if (!active.get() || engine == null || monitorInFlight || now - lastMonitorAt < 30_000) return;
        lastMonitorAt = now; monitorInFlight = true;
        final String context = engine.aiContext();
        java.util.concurrent.CompletableFuture.supplyAsync(() -> AiBridge.monitor(context)).whenComplete((m, error) ->
        {
            monitorInFlight = false;
            if (m == null) return;
            DebugLog.event("AI_MONITOR", m.severity + " action=" + m.action + " " + m.note);
            if (!"NONE".equals(m.action) && AiBridge.allowed(m.action))
            {
                chatAction = m.action;
                DebugLog.event("AI_ACTION_AUTO", "monitor requested " + m.action);
            }
        });
    }
    public void gameMessage(String message)
    {
        if (!active.get() || engine == null) return;
        engine.feedback(message);
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("you need") || lower.contains("you can't") || lower.contains("you cannot")
            || lower.contains("don't have") || lower.contains("not high enough") || lower.contains("not enough"))
            DebugLog.event("GAME_REJECTION", message);
    }

    private void observe()
    {
        if (!active.get()) return;
        if (net.runelite.client.plugins.microbot.ScriptPauseController.isPaused())
            gate = "Paused in Microbot: use its Resume scripts control";
        else if (net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman())
            gate = "Waiting: human has mouse/keyboard ownership";
        else if (Microbot.pauseAllScripts.get()) gate = "Microbot pauseAllScripts is set";
        GameState state = GameState.capture();
        if (state == null) { DebugLog.event("BLOCKED", "No logged-in player snapshot"); return; }
        if (!accountId.equals(Persona.hash(state.account.trim().toLowerCase(java.util.Locale.ROOT)))) return;
        progress.observe(state);
        long now = System.currentTimeMillis();
        if(engine!=null && (net.runelite.client.plugins.microbot.ScriptPauseController.isPaused()
            || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman() || Microbot.pauseAllScripts.get())) engine.pause(now);
        if (now - observedHeartbeatAt >= 10_000)
        {
            observedHeartbeatAt = now;
            DebugLog.event("HEARTBEAT", "status=" + status() + "; position=" + state.location
                + "; inventory=" + state.inventory + "; busy=" + state.busy + "; combat=" + state.combat
                + "; run=" + (state.runEnabled ? "on" : "off") + " " + state.runPercent() + "%"
                + "; lastCycleAgeMs=" + (now - lastCycleAt) + "; xpGained=" + progress.snapshot().xpGained);
            try { FilesStore.write(FilesStore.ROOT.resolve("progress-latest.json"), progress.snapshot()); }
            catch (IOException ex) { DebugLog.error(ex); }
        }
    }

    public synchronized void stop(String reason)
    {
        if (!active.compareAndSet(true, false)) return;
        status = reason;
        if (safety != null) safety.close();
        if (observer != null) observer.close();
        super.shutdown();
        Microbot.enableAutoRunOn = previousAutorun;
        RunEnergy.reset();
        AntiblockerTuner.restore();
        if (agent != null) agent.close();
        boolean interrupted = Thread.interrupted();
        try
        {
            try { if (engine != null) engine.close(); }
            catch (IOException ex) { LOG.warn("F2P AIO lease release failed: {}", ex.getClass().getSimpleName()); }
            try { if (metrics != null) metrics.finish(reason); }
            catch (IOException ex) { LOG.warn("F2P AIO session save failed: {}", ex.getClass().getSimpleName()); }
            try
            {
                FilesStore.write(FilesStore.ROOT.resolve("progress-latest.json"), progress.snapshot());
                if (metrics != null) FilesStore.write(FilesStore.ROOT.resolve("sessions")
                    .resolve(metrics.started + "-progress.json"), progress.snapshot());
            }
            catch (IOException ex) { DebugLog.error(ex); }
        }
        finally { if (interrupted) Thread.currentThread().interrupt(); }
        LOG.info("F2P AIO session stopped");
        DebugLog.event("STOP", reason);
    }

    @Override public void shutdown() { stop("Stopped by user"); }
}
