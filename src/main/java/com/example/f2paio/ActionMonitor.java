package com.example.f2paio;

public final class ActionMonitor
{
    public enum Result { NONE, WAITING, CONFIRMED, FAILED }
    private volatile Method method;
    private long sentAt;
    private long xp;
    private int products;
    private volatile String rejection = "";

    public void sent(Method task, GameState state, long now)
    {
        sentAt = now; xp = observedXp(task,state);
        products = task.products.stream().mapToInt(state::count).sum(); rejection = ""; method = task;
    }

    public void feedback(String message)
    {
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        if (method != null && (normalized.contains("you need") || normalized.contains("you can't")
            || normalized.contains("you cannot") || normalized.contains("don't have")
            || normalized.contains("not high enough") || normalized.contains("not enough"))) rejection = message;
    }

    public Result observe(GameState state, long now)
    {
        if (method == null) return Result.NONE;
        if (!rejection.isEmpty()) { method = null; return Result.FAILED; }
        if (method.products.stream().mapToInt(state::count).sum() > products
            || (method.kind != Method.Kind.GROUND && observedXp(method,state) > xp)
            || (method.kind != Method.Kind.GROUND && method.kind != Method.Kind.COMBAT
                && !Methods.isLearned(method.id) && state.animating))
        {
            method = null; return Result.CONFIRMED;
        }
        long deadline = method.kind == Method.Kind.COMBAT ? 30_000 : 12_000;
        if (now - sentAt >= deadline) {
            method = null; rejection = "No verified progress within " + deadline/1_000 + " seconds";
            return Result.FAILED;
        }
        return Result.WAITING;
    }

    private static long observedXp(Method method,GameState state) {
        return OutcomeLearning.trainingXp(method,state);
    }

    public String reason() { return rejection; }
    public void clear() { method = null; rejection = ""; }
}
