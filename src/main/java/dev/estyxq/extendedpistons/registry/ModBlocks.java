package dev.estyxq.extendedpistons.registry;

import dev.estyxq.extendedpistons.ExtendedPistons;
import dev.estyxq.extendedpistons.block.ExtendedPistonBlock;
import dev.estyxq.extendedpistons.block.ExtendedStickyPistonBlock;
import dev.estyxq.extendedpistons.block.ExtendedPistonHeadBlock;
import dev.estyxq.extendedpistons.block.MovementTransactionBlock;
import dev.estyxq.extendedpistons.block.PistonShaftBlock;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ExtendedPistons.MOD_ID);

    public static final DeferredBlock<ExtendedPistonBlock> EXTENDED_PISTON = BLOCKS.register(
            "extended_piston",
            () -> new ExtendedPistonBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.PISTON)));

    public static final DeferredBlock<ExtendedStickyPistonBlock> EXTENDED_STICKY_PISTON = BLOCKS.register(
            "extended_sticky_piston",
            () -> new ExtendedStickyPistonBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STICKY_PISTON)));

    public static final DeferredBlock<PistonShaftBlock> PISTON_SHAFT = BLOCKS.register(
            "piston_shaft", () -> new PistonShaftBlock(technicalProperties(Blocks.PISTON)));

    public static final DeferredBlock<ExtendedPistonHeadBlock> EXTENDED_PISTON_HEAD = BLOCKS.register(
            "extended_piston_head", () -> new ExtendedPistonHeadBlock(
                    technicalProperties(Blocks.PISTON_HEAD)));

    public static final DeferredBlock<MovementTransactionBlock> MOVEMENT_TRANSACTION = BLOCKS.register(
            "movement_transaction", () -> new MovementTransactionBlock(
                    technicalProperties(Blocks.MOVING_PISTON)));

    private static BlockBehaviour.Properties technicalProperties(net.minecraft.world.level.block.Block source) {
        // Full-copy preserves the vanilla material, sound and hardness, but it also
        // copies state-dependent predicates. The piston base predicates read the
        // vanilla EXTENDED property and crash when evaluated on our shaft states.
        // Technical parts are partial shapes, so all three full-cube predicates are
        // explicitly replaced after copying.
        return BlockBehaviour.Properties.ofFullCopy(source)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion()
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    private ModBlocks() {
    }
}
