package dev.estyxq.extendedpistons.movement;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MovingEntityPusherTest {
    @Test
    void upwardOverlapUsesMovingTopFace() {
        AABB movingArea = new AABB(0.0D, 1.0D, 0.0D, 1.0D, 1.5D, 1.0D);
        AABB entity = new AABB(0.25D, 1.2D, 0.25D, 0.75D, 3.0D, 0.75D);

        assertEquals(0.3D,
                MovingEntityPusher.movementDistance(movingArea, Direction.UP, entity),
                1.0E-9D);
    }

    @Test
    void downwardOverlapUsesMovingBottomFace() {
        AABB movingArea = new AABB(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D);
        AABB entity = new AABB(0.25D, 0.2D, 0.25D, 0.75D, 0.8D, 0.75D);

        assertEquals(0.3D,
                MovingEntityPusher.movementDistance(movingArea, Direction.DOWN, entity),
                1.0E-9D);
    }

    @Test
    void largerPayloadOverlapAddsRemainderAfterHead() {
        var moved = new HashMap<MovingEntityPusher.EntityMove, Double>();
        var key = new MovingEntityPusher.EntityMove(7, Direction.UP);

        assertEquals(0.2D, MovingEntityPusher.recordRequiredMovement(moved, key, 0.2D), 1.0E-9D);
        assertEquals(0.8D, MovingEntityPusher.recordRequiredMovement(moved, key, 1.0D), 1.0E-9D);
        assertEquals(0.0D, MovingEntityPusher.recordRequiredMovement(moved, key, 0.4D), 1.0E-9D);
    }

    @Test
    void exactTopContactRidesByTheSameDelta() {
        assertEquals(0.34375D,
                MovingEntityPusher.riderMovement(4.0D, 4.0D, 0.34375D),
                1.0E-9D);
    }

    @Test
    void shallowEmbeddingIsCorrectedBeforeCollisionAdvances() {
        assertEquals(0.40375D,
                MovingEntityPusher.riderMovement(4.0D, 3.95D, 0.34375D),
                1.0E-9D);
    }

    @Test
    void standingSurfaceRequiresHorizontalOverlap() {
        AABB surface = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        assertEquals(true, MovingEntityPusher.isSupportedBySurface(surface,
                new AABB(0.25D, 1.0D, 0.25D, 0.75D, 2.8D, 0.75D)));
        assertEquals(false, MovingEntityPusher.isSupportedBySurface(surface,
                new AABB(1.01D, 1.0D, 0.25D, 1.51D, 2.8D, 0.75D)));
    }
}
