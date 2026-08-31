package dev.estyxq.extendedpistons.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record DestroyedCell(BlockPos position, BlockState state) {
}
