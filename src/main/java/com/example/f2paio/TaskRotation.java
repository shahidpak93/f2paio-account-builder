package com.example.f2paio;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TaskRotation
{
    private net.runelite.api.Skill lastSkill;
    private int consecutive;
    private long skillSince;
    private boolean skilling(Method method) { return method.kind != Method.Kind.QUEST && method.kind != Method.Kind.GROUND; }
    public void selected(Method method, long now) {
        if (!skilling(method)) { lastSkill=null; consecutive=0; return; }
        if (lastSkill == method.skill) consecutive++;
        else { lastSkill=method.skill; consecutive=1; skillSince=now; }
    }
    public java.util.List<Method> diversify(java.util.List<Method> choices, long now, long limit) {
        if (lastSkill == null || (consecutive<2 && now-skillSince<limit)) return choices;
        var different = choices.stream().filter(m -> !skilling(m) || m.skill!=lastSkill)
            .collect(java.util.stream.Collectors.toList());
        return different.isEmpty() ? choices : different;
    }
    private final Map<String, Long> until = new HashMap<>();
    public void exclude(String id, long now, long duration) { until.put(id, now + duration); }
    public void excludeSkill(Method method, long now, long duration)
    {
        if (method.kind == Method.Kind.QUEST || method.kind == Method.Kind.GROUND) return;
        for (Method candidate : Methods.all())
            if (candidate.skill == method.skill && candidate.kind != Method.Kind.QUEST && candidate.kind != Method.Kind.GROUND)
                exclude(candidate.id, now, duration);
    }
    public Set<String> excluded(long now)
    {
        until.values().removeIf(expiry -> expiry <= now);
        return Set.copyOf(until.keySet());
    }
}
