package dev.estyxq.extendedpistons.block;

import com.mojang.serialization.MapCodec;
import dev.estyxq.extendedpistons.block.entity.MovementTransactionBlockEntity;
import dev.estyxq.extendedpistons.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class MovementTransactionBlock extends BaseEntityBlock {
    public static final MapCodec<MovementTransactionBlock> CODEC = simpleCodec(MovementTransactionBlock::new);

    public MovementTransactionBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof MovementTransactionBlockEntity transaction) {
            return transaction.originalState().getShape(level, pos, context);
        }
        return super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof MovementTransactionBlockEntity transaction) {
            return transaction.collisionShape(level, pos, context);
        }
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MovementTransactionBlockEntity transaction) {
            transaction.requestEmergencySettle();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MovementTransactionBlockEntity transaction) {
            transaction.requestEmergencySettle();
        }
        super.wasExploded(level, pos, explosion);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MovementTransactionBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.MOVEMENT_TRANSACTION.get(),
                level.isClientSide ? MovementTransactionBlockEntity::clientTick
                        : MovementTransactionBlockEntity::serverTick);
    }
}
