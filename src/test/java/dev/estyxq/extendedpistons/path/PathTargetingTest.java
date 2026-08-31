package dev.estyxq.extendedpistons.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class PathTargetingTest {
    @Test
    void exactEndpointFaceIsReported() {
        var hit = PathTargeting.clipEndpoint(
                new Vec3(0.5D, 2.0D, 0.5D), new Vec3(0.5D, -1.0D, 0.5D), BlockPos.ZERO);
        assertEquals(Direction.UP, hit.getDirection());
    }

    @Test
    void rayImmediatelyAboveEndpointDoesNotSelectIt() {
        var hit = PathTargeting.clipEndpoint(
                new Vec3(0.5D, 1.2D, -2.0D), new Vec3(0.5D, 1.2D, 2.0D), BlockPos.ZERO);
        assertNull(hit);
    }

    @Test
    void distantRayDoesNotSelectEndpoint() {
        var hit = PathTargeting.clipEndpoint(
                new Vec3(0.5D, 1.4D, -2.0D), new Vec3(0.5D, 1.4D, 2.0D), BlockPos.ZERO);
        assertNull(hit);
    }
}
