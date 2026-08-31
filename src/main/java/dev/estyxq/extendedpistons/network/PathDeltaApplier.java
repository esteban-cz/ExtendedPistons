package dev.estyxq.extendedpistons.network;

import dev.estyxq.extendedpistons.path.PistonPath;

public final class PathDeltaApplier {
    private PathDeltaApplier() {
    }

    public static Result apply(PistonPath path, int currentRevision, PathDeltaPayload delta) {
        if (currentRevision != delta.expectedRevision()) return Result.STALE;
        if (delta.newRevision() != delta.expectedRevision() + 1) return Result.INVALID;
        boolean changed = delta.operation() == PathOperation.ADD
                ? delta.direction() != null && path.append(delta.direction())
                : delta.direction() == null && path.removeLast();
        return changed ? Result.APPLIED : Result.INVALID;
    }

    public enum Result {
        APPLIED,
        STALE,
        INVALID
    }
}
