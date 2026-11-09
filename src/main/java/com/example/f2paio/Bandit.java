package com.example.f2paio;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Account-local contextual Thompson sampling. XP and cash evidence remain separate. */
public final class Bandit
{
    static final class Arm {
        double success = 2, failure = 2, xpReward, cashReward;
        long observations;
    }
    private Map<String, Arm> arms = new HashMap<>();
    private final Path file;
    private GameState context;
    public Bandit() { this(null); }
    public Bandit(Path file) {
        this.file = file;
        if (file != null && Files.exists(file)) try {
            var type = new com.google.gson.reflect.TypeToken<Map<String, Arm>>() { }.getType();
            Map<String, Arm> loaded = FilesStore.JSON.fromJson(Files.readString(file), type);
            if (loaded != null) loaded.forEach((key, arm) -> {
                if (arm != null && Double.isFinite(arm.success) && Double.isFinite(arm.failure)
                    && arm.success >= 1 && arm.failure >= 1 && Double.isFinite(arm.xpReward)
                    && Double.isFinite(arm.cashReward)) arms.put(key, arm);
            });
        } catch (Exception ex) { DebugLog.event("LEARNING_LOAD", "Invalid history ignored: " + ex.getClass().getSimpleName()); }
    }
    public void context(GameState state) { context = state; }
    private String key(String id) {
        if (context == null) return id;
        try {
            Method method = Methods.get(id);
            return id + ":level=" + context.levels.getOrDefault(method.skill,1)/10
                + ":cash=" + (context.coins < 500 ? "low" : "funded");
        } catch (IllegalArgumentException ex) { return id; }
    }
    private Arm arm(String id) { return arms.computeIfAbsent(key(id), ignored -> new Arm()); }
    public double score(String id, Random random) {
        Arm arm = arm(id);
        double a = gamma(arm.success,random), b = gamma(arm.failure,random);
        return a/(a+b) + Math.min(.1, arm.xpReward/Math.max(1,arm.observations)/3000)
            + Math.min(.1, arm.cashReward/Math.max(1,arm.observations)/5000);
    }
    public void reward(String id, boolean progress) { reward(id,progress,0,0); }
    public void reward(String id, boolean progress, long xpDelta, long coinDelta) {
        Arm arm = arm(id);
        if (progress || xpDelta > 0 || coinDelta > 0) arm.success += 1;
        else arm.failure += 1;
        arm.xpReward += Math.min(10000,Math.max(0,xpDelta));
        arm.cashReward += Math.min(10000,Math.max(0,coinDelta));
        arm.observations++;
        if (arm.success + arm.failure > 500) { arm.success = 2+(arm.success-2)*.5; arm.failure = 2+(arm.failure-2)*.5; }
        save();
    }
    public void penalize(String id) { reward(id,false); }
    public void save() {
        if (file == null) return;
        try { FilesStore.write(file,arms); }
        catch (Exception ex) { DebugLog.event("LEARNING_SAVE", ex.getClass().getSimpleName()); }
    }
    long observations(String id) { return arm(id).observations; }
    private static double gamma(double shape, Random random) {
        double d=shape-1.0/3, c=1/Math.sqrt(9*d);
        for (;;) {
            double x=random.nextGaussian(), v=1+c*x;
            if (v<=0) continue;
            v=v*v*v; double u=random.nextDouble();
            if (u<1-.0331*x*x*x*x || Math.log(u)<.5*x*x+d*(1-v+Math.log(v))) return d*v;
        }
    }
}
