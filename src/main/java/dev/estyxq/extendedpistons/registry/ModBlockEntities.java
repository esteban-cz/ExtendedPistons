package dev.estyxq.extendedpistons.registry;

import dev.estyxq.extendedpistons.ExtendedPistons;
import dev.estyxq.extendedpistons.block.entity.ExtendedPistonBlockEntity;
import dev.estyxq.extendedpistons.block.entity.MovementTransactionBlockEntity;
import dev.estyxq.extendedpistons.block.entity.PistonPartBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ExtendedPistons.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtendedPistonBlockEntity>> EXTENDED_PISTON =
            BLOCK_ENTITIES.register("extended_piston", () -> BlockEntityType.Builder.of(
                    ExtendedPistonBlockEntity::new,
                    ModBlocks.EXTENDED_PISTON.get(),
                    ModBlocks.EXTENDED_STICKY_PISTON.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MovementTransactionBlockEntity>> MOVEMENT_TRANSACTION =
            BLOCK_ENTITIES.register("movement_transaction", () -> BlockEntityType.Builder.of(
                    MovementTransactionBlockEntity::new,
                    ModBlocks.MOVEMENT_TRANSACTION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PistonPartBlockEntity>> PISTON_PART =
            BLOCK_ENTITIES.register("piston_part", () -> BlockEntityType.Builder.of(
                    PistonPartBlockEntity::new,
                    ModBlocks.PISTON_SHAFT.get(), ModBlocks.EXTENDED_PISTON_HEAD.get()).build(null));

    private ModBlockEntities() {
    }
}
