package com.example.f2paio;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class RuntimeObserver implements AutoCloseable
{
    private final ScheduledExecutorService executor;

    public RuntimeObserver(Runnable observation)
    {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "F2PAIO-observer"); t.setDaemon(true); return t;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { observation.run(); }
            catch (Exception ex) { DebugLog.error(ex); }
        }, 0, 2, TimeUnit.SECONDS);
    }

    @Override public void close() { executor.shutdownNow(); }
}
