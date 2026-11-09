package com.example.f2paio;

import java.util.HashSet;
import java.util.Set;

/** Rebuild a failed supply branch once using a fresh snapshot and previously observed materials. */
final class ChainRepair {
    private final Set<String> attempted = new HashSet<>();
    private String blockedProducer;
    private String incident="";

    boolean alternative(ChainPlan failed, ChainPlan.Step step, String reason) {
        if (failed==null || step==null || !attempted.isEmpty()) return false;
        boolean preparing=step.op==ChainPlan.Op.PREPARE || step.op==ChainPlan.Op.ACT && reason.startsWith("BACKEND:");
        boolean producer=!step.method.equals(failed.goal);
        boolean effectFailure=reason.startsWith("NO_TARGET:") || reason.startsWith("EFFECT_TIMEOUT:")
            || reason.startsWith("MATERIAL_SHORTFALL:");
        if (!(preparing || producer && effectFailure)) return false;
        attempted.add(failed.id);
        if (producer) blockedProducer=step.method;
        incident=reason;
        return true;
    }

    Set<String> excluded(Set<String> base) {
        if (blockedProducer==null) return base;
        Set<String> result=new HashSet<>(base);
        result.add(blockedProducer);
        return result;
    }

    boolean accepts(ChainPlan plan) {
        return !attempted.contains(plan.id) && !attempted.contains(plan.parent)
            && (blockedProducer==null || plan.steps.stream().noneMatch(step -> step.method.equals(blockedProducer)));
    }

    String incident() { return incident; }

    void clear() {
        attempted.clear();
        blockedProducer=null;
        incident="";
    }
}
