package dev.estyxq.extendedpistons.movement;

import java.util.List;

public record StepPlan(
        List<MovementCell> cells,
        List<DestroyedCell> destroyed,
        int targetHeadIndex,
        boolean extending,
        boolean pullingPayload) {
}
