package com.example.f2paio;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Watchdog
{
    public enum Level { OK, RETRY, REROUTE, ABANDON, LOGOUT, KILL }
    private final Path stop;
    private volatile long progressAt;
    private String signature = "";
    private String dialogue = "";
    private int repeats;

    public Watchdog(Path stop, long now)
    {
        this.stop = stop;
        progressAt = now;
    }

    public void observe(String value, long now)
    {
        if (!signature.equals(value))
        {
            signature = value;
            progressAt = now;
            repeats = 0;
        }
    }

    public void dialogue(String value)
    {
        value = value == null ? "" : value;
        repeats = dialogue.equals(value) ? repeats + 1 : 1;
        dialogue = value;
    }

    public void pause(long now)
    {
        progressAt = now;
    }

    public boolean killed()
    {
        return Files.exists(stop);
    }

    public Level level(long now)
    {
        if (killed()) return Level.KILL;
        long idle = Math.max(0, now - progressAt);
        if (idle >= Tuning.LOGOUT_MS) return Level.LOGOUT;
        if (idle >= Tuning.ABANDON_MS || repeats >= Tuning.DIALOGUE_REPEAT_CAP) return Level.ABANDON;
        if (idle >= Tuning.REROUTE_MS) return Level.REROUTE;
        if (idle >= Tuning.RETRY_MS) return Level.RETRY;
        return Level.OK;
    }
}
