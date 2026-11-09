package com.example.f2paio;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SafetyMonitor implements AutoCloseable
{
    private final ScheduledExecutorService executor;

    public SafetyMonitor(Runnable poll)
    {
        // Script's executor skips all work during a manual pause, including auxiliary jobs.
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "F2PAIO-kill-monitor"); thread.setDaemon(true); return thread;
        });
        executor.scheduleWithFixedDelay(poll, Tuning.POLL_MS, Tuning.POLL_MS, TimeUnit.MILLISECONDS);
    }

    @Override public void close() { executor.shutdown(); }
}
