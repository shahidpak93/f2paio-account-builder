package com.example.f2paio;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class Metrics
{
    public long started = System.currentTimeMillis();
    public long ended;
    public long xp;
    public int attempts;
    public int taskSwitches;
    public int errors;
    public Map<String, Integer> stalls = new HashMap<>();
    private transient long initialXp = -1;
    private transient long lastAction;
    private transient long intervals;
    private transient double mean;
    private transient double m2;
    public double actionIntervalCv;
    public String stopReason;

    public synchronized void observe(GameState state)
    {
        if (initialXp < 0) initialXp = state.xp;
        xp = state.xp - initialXp;
    }

    public synchronized void action(long now)
    {
        attempts++;
        if (lastAction > 0)
        {
            double interval = now - lastAction;
            intervals++;
            double delta = interval - mean;
            mean += delta / intervals;
            m2 += delta * (interval - mean);
            actionIntervalCv = intervals > 1 && mean > 0 ? Math.sqrt(m2 / (intervals - 1)) / mean : 0;
        }
        lastAction = now;
    }

    public synchronized void stall(Watchdog.Level level)
    {
        stalls.merge(level.name(), 1, Integer::sum);
    }

    public synchronized void finish(String reason) throws IOException
    {
        if (ended != 0) return;
        ended = System.currentTimeMillis();
        stopReason = reason;
        Path file = FilesStore.ROOT.resolve("sessions").resolve(
            Instant.ofEpochMilli(started).toString().replace(':', '-') + ".json");
        FilesStore.write(file, this);
    }
}
