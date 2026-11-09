package com.example.f2paio;

import java.time.LocalTime;
import java.util.Random;

public final class Scheduler
{
    private final Random random;
    private final long started;
    private final long sessionEnds;
    private long nextAction;
    private long idleUntil;
    private long nextDrift;

    public Scheduler(Random random, Persona persona, F2pAioConfig config, long now)
    {
        this.random = random;
        started = now;
        double planned = Math.min(config.sessionMax, 20 + persona.endurance * 120);
        sessionEnds = now + Math.round(planned * Tuning.MINUTE);
        nextDrift = now + 6 * Tuning.MINUTE;
    }

    public boolean ended(long now) { return now >= sessionEnds; }
    public boolean idle(long now) { return now < idleUntil; }

    public boolean ready(long now, Persona persona, F2pAioConfig config)
    {
        if (now < nextAction || idle(now)) return false;
        if (now >= nextDrift)
        {
            nextDrift = now + (long) Math.min(18 * Tuning.MINUTE,
                Distributions.lognormal(random, 10 * Tuning.MINUTE, 0.3));
            if (random.nextDouble() < config.idlePct / 100.0)
            {
                idleUntil = now + (long) Math.min(93_000, Distributions.lognormal(random, 30_000, 0.5));
                return false;
            }
        }
        double warmup = 0.68 + 0.32 * Math.min(1, (now - started) / 90_000.0);
        nextAction = now + Distributions.latency(random, persona.speed, warmup);
        return true;
    }

    public static boolean withinHours(int from, int to, LocalTime time)
    {
        int hour = time.getHour();
        return from < to ? hour >= from && hour < to : hour >= from || hour < to;
    }
}
