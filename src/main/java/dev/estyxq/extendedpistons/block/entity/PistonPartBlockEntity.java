package dev.estyxq.extendedpistons.block.entity;

import dev.estyxq.extendedpistons.block.ExtendedPistonHeadBlock;
import dev.estyxq.extendedpistons.movement.MovementPlanner;
import dev.estyxq.extendedpistons.registry.ModBlockEntities;
import dev.estyxq.extendedpistons.movement.OrphanPartRecovery;
import dev.estyxq.extendedpistons.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class PistonPartBlockEntity extends BlockEntity {
    private BlockPos owner = BlockPos.ZERO;
    private boolean initialized;

    public PistonPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PISTON_PART.get(), pos, state);
    }

    public void configure(BlockPos owner) {
        this.owner = owner.immutable();
        initialized = true;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            OrphanPartRecovery.remove(serverLevel.dimension(), worldPosition);
        }
    }

    public boolean isOwnedBy(BlockPos expectedOwner) {
        return initialized && owner.equals(expectedOwner);
    }

    public void requestEmergencySettle() {
        if (level == null || level.isClientSide || !initialized || !level.isLoaded(owner)) return;
        if (level.getBlockEntity(owner) instanceof ExtendedPistonBlockEntity piston) {
            piston.emergencySettle();
        } else {
            level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    public boolean checkOwnership() {
        if (level == null || !initialized || !level.isLoaded(owner)) return false;
        if (level.getBlockEntity(owner) instanceof ExtendedPistonBlockEntity piston
                && piston.ownsPart(worldPosition)) {
            migrateHeadConnection(piston);
            return true;
        }
        level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /** Repairs heads saved by 1.0.7, before the explicit elbow property existed. */
    private void migrateHeadConnection(ExtendedPistonBlockEntity piston) {
        BlockState state = getBlockState();
        if (!state.is(ModBlocks.EXTENDED_PISTON_HEAD.get())) return;
        int index = piston.path().indexOf(worldPosition);
        if (index < 0) return;
        BlockState expected = ModBlocks.EXTENDED_PISTON_HEAD.get().stateFor(
                MovementPlanner.directionAfter(piston.path(), index),
                MovementPlanner.headConnection(piston.path(), index),
                state.getValue(ExtendedPistonHeadBlock.STICKY));
        if (!state.equals(expected)) {
            level.setBlock(worldPosition, expected,
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            OrphanPartRecovery.enqueue(serverLevel.dimension(), worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Owner", owner.asLong());
        tag.putBoolean("Initialized", initialized);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = BlockPos.of(tag.getLong("Owner"));
        initialized = tag.getBoolean("Initialized");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
