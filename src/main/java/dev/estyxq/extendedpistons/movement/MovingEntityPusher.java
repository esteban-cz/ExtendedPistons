package dev.estyxq.extendedpistons.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMath;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared server/client collision sweep for an animated transaction cell. */
public final class MovingEntityPusher {
    private static final double SEPARATION_EPSILON = 0.01D;
    private static final double SUPPORT_TOLERANCE = 0.125D;
    private static final ThreadLocal<Direction> NOCLIP_DIRECTION = new ThreadLocal<>();

    private MovingEntityPusher() {
    }

    public static double easedProgress(int elapsedTicks, int duration) {
        double linear = Math.clamp((double) elapsedTicks / Math.max(1, duration), 0.0D, 1.0D);
        return linear * linear * (3.0D - 2.0D * linear);
    }

    public static void pushCell(Level level, BlockPos position, BlockState renderState,
                                Direction renderDirection, boolean renderAtDestination,
                                double previousProgress, double currentProgress,
                                Map<EntityMove, Double> moved) {
        double progressDelta = currentProgress - previousProgress;
        if (renderState.isAir() || progressDelta <= 0.0D) return;

        var normal = renderDirection.getNormal();
        double renderOffset = renderAtDestination ? previousProgress - 1.0D : previousProgress;
        Vec3 initialOffset = new Vec3(normal.getX() * renderOffset,
                normal.getY() * renderOffset, normal.getZ() * renderOffset);

        // Match vanilla's piston sweep: query the volume newly occupied during
        // this tick, calculate the actual overlap along the movement axis, then
        // add a tiny separation so rounding cannot leave feet inside a payload.
        List<AABB> previousBoxes = new ArrayList<>();
        List<AABB> movementAreas = new ArrayList<>();
        AABB searchArea = null;
        for (AABB localShape : renderState.getCollisionShape(level, position).toAabbs()) {
            AABB previousBox = localShape.move(position).move(initialOffset);
            previousBoxes.add(previousBox);
            AABB movementArea = PistonMath.getMovementArea(
                    previousBox, renderDirection, progressDelta);
            movementAreas.add(movementArea);
            AABB completeSweep = previousBox.minmax(movementArea);
            searchArea = searchArea == null ? completeSweep : searchArea.minmax(completeSweep);
        }

        // A player standing exactly on an upward-moving payload only touches its
        // top face; depending on entity/block-entity tick order, that contact can
        // be a few floating-point units outside the strict swept-volume overlap.
        // Carry such supported riders by the same delta first. The ordinary
        // overlap pass below remains responsible for entities inside or in front
        // of the moving shape. This keeps the payload and rider in one coherent
        // movement instead of expelling the rider after collision has advanced.
        if (renderDirection == Direction.UP && !previousBoxes.isEmpty()) {
            carryUpwardRiders(level, previousBoxes, progressDelta, moved);
        }
        if (searchArea != null) {
            for (Entity entity : level.getEntities((Entity) null, searchArea,
                    candidate -> !candidate.isSpectator()
                            && candidate.getPistonPushReaction() != PushReaction.IGNORE)) {
                double requiredMovement = 0.0D;
                AABB entityBox = entity.getBoundingBox();
                for (AABB movementArea : movementAreas) {
                    if (movementArea.intersects(entityBox)) {
                        requiredMovement = Math.max(requiredMovement,
                                movementDistance(movementArea, renderDirection, entityBox));
                    }
                }
                if (requiredMovement > 0.0D) {
                    moveRemaining(renderDirection, entity,
                            Math.min(requiredMovement, progressDelta) + SEPARATION_EPSILON,
                            progressDelta > 0.5D, moved);
                }
            }
        }

        if (renderState.is(Blocks.HONEY_BLOCK) && renderDirection.getAxis().isHorizontal()) {
            AABB topDrag = new AABB(position).move(initialOffset).move(0.0D, 1.0D, 0.0D)
                    .expandTowards(normal.getX() * progressDelta,
                            normal.getY() * progressDelta, normal.getZ() * progressDelta)
                    .inflate(0.01D, 0.1D, 0.01D);
            for (Entity entity : level.getEntities((Entity) null, topDrag,
                    candidate -> !candidate.isSpectator() && candidate.onGround()
                            && candidate.getPistonPushReaction() != PushReaction.IGNORE)) {
                moveRemaining(renderDirection, entity, progressDelta,
                        progressDelta > 0.5D, moved);
            }
        }
    }

    private static void carryUpwardRiders(Level level, List<AABB> previousBoxes,
                                           double progressDelta,
                                           Map<EntityMove, Double> moved) {
        AABB riderSearch = null;
        for (AABB box : previousBoxes) {
            AABB surface = new AABB(box.minX, box.maxY - SUPPORT_TOLERANCE, box.minZ,
                    box.maxX, box.maxY + SUPPORT_TOLERANCE, box.maxZ);
            riderSearch = riderSearch == null ? surface : riderSearch.minmax(surface);
        }
        if (riderSearch == null) return;

        for (Entity entity : level.getEntities((Entity) null, riderSearch,
                candidate -> !candidate.isSpectator()
                        && candidate.getPistonPushReaction() != PushReaction.IGNORE
                        && candidate.getDeltaMovement().y <= 0.1D)) {
            AABB entityBox = entity.getBoundingBox();
            double supportingTop = Double.NEGATIVE_INFINITY;
            for (AABB box : previousBoxes) {
                if (isSupportedBySurface(box, entityBox)) {
                    supportingTop = Math.max(supportingTop, box.maxY);
                }
            }
            if (supportingTop == Double.NEGATIVE_INFINITY) continue;

            double desiredMovement = riderMovement(
                    supportingTop, entityBox.minY, progressDelta);
            moveRemaining(Direction.UP, entity, desiredMovement,
                    desiredMovement > 0.5D, moved);
            entity.resetFallDistance();
            entity.setOnGround(true);
        }
    }

    static boolean isSupportedBySurface(AABB surface, AABB entityBox) {
        double verticalGap = entityBox.minY - surface.maxY;
        return verticalGap >= -SUPPORT_TOLERANCE
                && verticalGap <= SUPPORT_TOLERANCE
                && entityBox.maxX > surface.minX + 1.0E-7D
                && entityBox.minX < surface.maxX - 1.0E-7D
                && entityBox.maxZ > surface.minZ + 1.0E-7D
                && entityBox.minZ < surface.maxZ - 1.0E-7D;
    }

    static double riderMovement(double supportingTop, double entityBottom,
                                double progressDelta) {
        double embeddedDepth = Math.max(0.0D, supportingTop - entityBottom);
        return progressDelta + embeddedDepth
                + (embeddedDepth > 0.0D ? SEPARATION_EPSILON : 0.0D);
    }

    /** Omits this moving shape while the entity is being expelled through it. */
    public static boolean ignoresMovingCollision(Direction direction) {
        return NOCLIP_DIRECTION.get() == direction;
    }

    static double movementDistance(AABB movingArea, Direction direction, AABB entityBox) {
        return switch (direction) {
            case EAST -> movingArea.maxX - entityBox.minX;
            case WEST -> entityBox.maxX - movingArea.minX;
            case UP -> movingArea.maxY - entityBox.minY;
            case DOWN -> entityBox.maxY - movingArea.minY;
            case SOUTH -> movingArea.maxZ - entityBox.minZ;
            case NORTH -> entityBox.maxZ - movingArea.minZ;
        };
    }

    private static void moveRemaining(Direction direction, Entity entity, double desiredDistance,
                                      boolean catchUpSweep, Map<EntityMove, Double> moved) {
        EntityMove key = new EntityMove(entity.getId(), direction);
        double remaining = recordRequiredMovement(moved, key, desiredDistance);
        if (remaining <= 1.0E-7D) return;

        // A head, shaft and payload can all sweep the same entity in one curved
        // transaction. Apply the largest required distance independent of block-
        // entity tick order, rather than letting the first (possibly smaller)
        // overlap suppress the payload's remaining push.
        moveEntity(direction, entity, remaining, catchUpSweep);
    }

    static double recordRequiredMovement(Map<EntityMove, Double> moved, EntityMove key,
                                         double desiredDistance) {
        double alreadyMoved = moved.getOrDefault(key, 0.0D);
        if (desiredDistance <= alreadyMoved) return 0.0D;
        moved.put(key, desiredDistance);
        return desiredDistance - alreadyMoved;
    }

    private static void moveEntity(Direction direction, Entity entity, double distance,
                                   boolean catchUpSweep) {
        Direction previous = NOCLIP_DIRECTION.get();
        NOCLIP_DIRECTION.set(direction);
        var normal = direction.getNormal();
        try {
            // Minecraft caps accumulated PISTON movement to roughly 0.51 blocks
            // per game tick. A delayed client update (or ticksPerSegment=1) can
            // legitimately need a larger catch-up sweep, so use ordinary
            // collision-aware movement for that case instead of stopping halfway.
            MoverType moverType = catchUpSweep ? MoverType.SELF : MoverType.PISTON;
            entity.move(moverType, new Vec3(normal.getX() * distance,
                    normal.getY() * distance, normal.getZ() * distance));
        } finally {
            if (previous == null) NOCLIP_DIRECTION.remove();
            else NOCLIP_DIRECTION.set(previous);
        }
    }

    public record EntityMove(int entityId, Direction direction) {
    }
}
