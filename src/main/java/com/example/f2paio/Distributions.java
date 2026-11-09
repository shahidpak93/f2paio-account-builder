package com.example.f2paio;

import java.util.Random;

public final class Distributions
{
    private Distributions() { }

    public static double lognormal(Random random, double median, double sigma)
    {
        return median * Math.exp(sigma * random.nextGaussian());
    }

    public static double exGaussian(Random random, double mu, double sigma, double tau)
    {
        return Math.max(1, mu + sigma * random.nextGaussian()
            - tau * Math.log(Math.max(1e-12, random.nextDouble())));
    }

    public static long latency(Random random, double speed, double attention)
    {
        return Math.min(4_000, Math.max(180,
            Math.round(exGaussian(random, 190, 55, 120) / (speed * attention))));
    }
}
