package dev.estyxq.extendedpistons.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public record MovementCell(
        BlockPos position,
        int trajectoryOrder,
        BlockState originalState,
        BlockState finalState,
        BlockState renderState,
        Direction renderDirection,
        boolean renderAtDestination) {
}
