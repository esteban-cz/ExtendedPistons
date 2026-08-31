package dev.estyxq.extendedpistons.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Shared client/server hit geometry for the virtual path endpoint. */
public final class PathTargeting {
    private static final AABB ENDPOINT_BOX = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    private PathTargeting() {
    }

    public static BlockHitResult clipEndpoint(Vec3 eye, Vec3 end, BlockPos endpoint) {
        // The exact one-block endpoint cube is the complete interaction surface.
        // There is deliberately no magnetic margin: the crosshair must intersect
        // a real face and that face alone determines an appended segment.
        return Shapes.create(ENDPOINT_BOX).clip(eye, end, endpoint);
    }
}
