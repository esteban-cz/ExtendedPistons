package dev.estyxq.extendedpistons.movement;

import org.jetbrains.annotations.Nullable;

public record PlanResult(PlanStatus status, @Nullable StepPlan plan) {
    public static PlanResult ready(StepPlan plan) {
        return new PlanResult(PlanStatus.READY, plan);
    }

    public static PlanResult blocked() {
        return new PlanResult(PlanStatus.BLOCKED, null);
    }

    public static PlanResult unloaded() {
        return new PlanResult(PlanStatus.UNLOADED, null);
    }
}
