package dev.estyxq.extendedpistons.block.entity;

import dev.estyxq.extendedpistons.block.TransactionPhase;
import dev.estyxq.extendedpistons.movement.MovingEntityPusher;
import dev.estyxq.extendedpistons.registry.ModBlockEntities;
import dev.estyxq.extendedpistons.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class MovementTransactionBlockEntity extends BlockEntity {
    private BlockState originalState = Blocks.AIR.defaultBlockState();
    private BlockState finalState = Blocks.AIR.defaultBlockState();
    private BlockState renderState = Blocks.AIR.defaultBlockState();
    private BlockPos owner = BlockPos.ZERO;
    private long transactionId;
    private TransactionPhase phase = TransactionPhase.PREPARING;
    private Direction renderDirection = Direction.NORTH;
    private boolean renderAtDestination;
    private long startGameTime;
    private int duration = 1;
    private boolean initialized;
    private int collisionProgress;
    private int clientEntityProgress = -1;
    private static long clientPushGameTime = Long.MIN_VALUE;
    private static final Map<MovingEntityPusher.EntityMove, Double> CLIENT_MOVED = new HashMap<>();

    public MovementTransactionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOVEMENT_TRANSACTION.get(), pos, state);
    }

    public void configure(BlockState originalState, BlockState finalState, BlockState renderState,
                          BlockPos owner, long transactionId, TransactionPhase phase,
                          Direction renderDirection, boolean renderAtDestination,
                          long startGameTime, int duration) {
        this.originalState = originalState;
        this.finalState = finalState;
        this.renderState = renderState;
        this.owner = owner.immutable();
        this.transactionId = transactionId;
        this.phase = phase;
        this.renderDirection = renderDirection;
        this.renderAtDestination = renderAtDestination;
        this.startGameTime = startGameTime;
        this.duration = Math.max(1, duration);
        this.initialized = true;
        this.collisionProgress = 0;
        this.clientEntityProgress = -1;
        setChanged();
        sync();
    }

    public BlockState originalState() {
        return originalState;
    }

    public BlockState finalState() {
        return finalState;
    }

    public BlockState renderState() {
        return renderState;
    }

    /**
     * Returns the moving head geometry for this point in the cell transition.
     * A retracting head switches to its compact connector during the second
     * half, revealing the destination elbow instead of drawing a straight rod
     * through it. The connection property keeps the plate-side and base-side
     * arms independent, so a corner remains an actual elbow while it travels.
     */
    public BlockState renderStateAtProgress(double linearProgress) {
        if (!renderState.is(ModBlocks.EXTENDED_PISTON_HEAD.get())) return renderState;
        boolean extendingHead = renderAtDestination
                || finalState.is(ModBlocks.PISTON_SHAFT.get());
        boolean retractingHead = originalState.is(ModBlocks.EXTENDED_PISTON_HEAD.get())
                && !finalState.is(ModBlocks.PISTON_SHAFT.get());
        boolean shortHead = extendingHead ? linearProgress <= 0.5D
                : retractingHead && linearProgress >= 0.5D;
        BlockState movingHead = renderState.setValue(
                dev.estyxq.extendedpistons.block.ExtendedPistonHeadBlock.SHORT, shortHead);
        if (extendingHead && !renderAtDestination
                && originalState.is(ModBlocks.EXTENDED_PISTON_HEAD.get())
                && linearProgress >= 0.5D) {
            // The old head cell supplies a stationary support elbow. Once the
            // plate crosses the midpoint, turn its moving base-side arm back
            // toward that support instead of carrying the source elbow all the
            // way into the destination and snapping only when it settles.
            movingHead = movingHead.setValue(
                    dev.estyxq.extendedpistons.block.ExtendedPistonHeadBlock.CONNECTION,
                    renderDirection.getOpposite());
        }
        if (retractingHead && linearProgress < 0.5D
                && originalState.hasProperty(
                dev.estyxq.extendedpistons.block.ExtendedPistonHeadBlock.CONNECTION)) {
            // Keep contact with the source-side shaft during the first half.
            // The destination support elbow takes over as the head crosses the
            // cell midpoint, where the connector visibly turns toward the base.
            movingHead = movingHead.setValue(
                    dev.estyxq.extendedpistons.block.ExtendedPistonHeadBlock.CONNECTION,
                    originalState.getValue(
                            dev.estyxq.extendedpistons.block.ExtendedPistonHeadBlock.CONNECTION));
        }
        return movingHead;
    }

    public Direction renderDirection() {
        return renderDirection;
    }

    public boolean renderAtDestination() {
        return renderAtDestination;
    }

    public long startGameTime() {
        return startGameTime;
    }

    public int duration() {
        return duration;
    }

    public long transactionId() {
        return transactionId;
    }

    /** Collision follows the same eased trajectory as the rendered payload. */
    public VoxelShape collisionShape(BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!initialized) return Shapes.empty();

        VoxelShape support = supportState().isAir() ? Shapes.empty()
                : supportState().getCollisionShape(level, pos, context);
        if (renderState.isAir() || MovingEntityPusher.ignoresMovingCollision(renderDirection)) {
            return support;
        }

        int progressTicks = level instanceof Level transactionLevel && transactionLevel.isClientSide
                ? Math.max(0, clientEntityProgress) : collisionProgress;
        double progress = MovingEntityPusher.easedProgress(progressTicks, duration);
        double offset = renderAtDestination ? progress - 1.0D : progress;
        var normal = renderDirection.getNormal();
        BlockState movingState = renderStateAtProgress(
                (double) progressTicks / Math.max(1, duration));
        VoxelShape moving = movingState.getCollisionShape(level, pos, context).move(
                normal.getX() * offset, normal.getY() * offset, normal.getZ() * offset);
        return support.isEmpty() ? moving : Shapes.or(support, moving);
    }

    public void setCollisionProgress(int progress) {
        collisionProgress = Math.max(0, Math.min(duration, progress));
        setChanged();
    }

    private BlockState supportState() {
        if (originalState.is(ModBlocks.EXTENDED_PISTON_HEAD.get())
                && finalState.is(ModBlocks.PISTON_SHAFT.get())) {
            return finalState;
        }
        if (originalState.is(ModBlocks.PISTON_SHAFT.get())
                && finalState.is(ModBlocks.EXTENDED_PISTON_HEAD.get())) {
            return originalState;
        }
        return Blocks.AIR.defaultBlockState();
    }

    public void markCommitted() {
        phase = TransactionPhase.COMMITTED;
        setChanged();
        sync();
    }

    public void requestEmergencySettle() {
        if (level == null || level.isClientSide || !initialized) {
            return;
        }
        if (level.isLoaded(owner)
                && level.getBlockEntity(owner) instanceof ExtendedPistonBlockEntity piston
                && piston.ownsTransaction(transactionId)) {
            piston.emergencySettle();
        } else {
            settleSelf();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  MovementTransactionBlockEntity transaction) {
        if (!transaction.initialized || !level.isLoaded(transaction.owner)) {
            return;
        }
        if (level.getBlockEntity(transaction.owner) instanceof ExtendedPistonBlockEntity piston
                && piston.ownsTransaction(transaction.transactionId)) {
            return;
        }
        transaction.settleSelf();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  MovementTransactionBlockEntity transaction) {
        if (!transaction.initialized) return;
        long gameTime = level.getGameTime();
        if (clientPushGameTime != gameTime) {
            clientPushGameTime = gameTime;
            CLIENT_MOVED.clear();
        }
        int nextProgress = (int) Math.min(transaction.duration,
                Math.max(0L, gameTime - transaction.startGameTime));
        if (transaction.clientEntityProgress < 0) {
            // A modpack client can receive the transaction after several server
            // ticks. Replay the complete sweep from zero: overlap-based motion
            // moves an already-synchronized entity only as far as necessary.
            transaction.clientEntityProgress = 0;
        }
        if (nextProgress <= transaction.clientEntityProgress) return;
        MovingEntityPusher.pushCell(level, pos, transaction.renderState,
                transaction.renderDirection, transaction.renderAtDestination,
                MovingEntityPusher.easedProgress(transaction.clientEntityProgress,
                        transaction.duration),
                MovingEntityPusher.easedProgress(nextProgress, transaction.duration),
                CLIENT_MOVED);
        // Vanilla advances the moving block's collision only after entities have
        // been displaced. Keeping this assignment after pushCell prevents the
        // payload from occupying the player first.
        transaction.clientEntityProgress = nextProgress;
        transaction.collisionProgress = nextProgress;
    }

    private void settleSelf() {
        if (level == null) {
            return;
        }
        BlockState target = phase == TransactionPhase.COMMITTED ? finalState : originalState;
        level.setBlock(worldPosition, target, Block.UPDATE_ALL);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("OriginalState", NbtUtils.writeBlockState(originalState));
        tag.put("FinalState", NbtUtils.writeBlockState(finalState));
        tag.put("RenderState", NbtUtils.writeBlockState(renderState));
        tag.putLong("Owner", owner.asLong());
        tag.putLong("TransactionId", transactionId);
        tag.putString("Phase", phase.name());
        tag.putByte("RenderDirection", (byte) renderDirection.get3DDataValue());
        tag.putBoolean("RenderAtDestination", renderAtDestination);
        tag.putLong("StartGameTime", startGameTime);
        tag.putInt("Duration", duration);
        tag.putBoolean("Initialized", initialized);
        tag.putInt("CollisionProgress", collisionProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        originalState = NbtUtils.readBlockState(blocks, tag.getCompound("OriginalState"));
        finalState = NbtUtils.readBlockState(blocks, tag.getCompound("FinalState"));
        renderState = NbtUtils.readBlockState(blocks, tag.getCompound("RenderState"));
        owner = BlockPos.of(tag.getLong("Owner"));
        transactionId = tag.getLong("TransactionId");
        try {
            phase = TransactionPhase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException ignored) {
            phase = TransactionPhase.PREPARING;
        }
        int direction = tag.getByte("RenderDirection");
        renderDirection = direction >= 0 && direction <= 5
                ? Direction.from3DDataValue(direction) : Direction.NORTH;
        renderAtDestination = tag.getBoolean("RenderAtDestination");
        startGameTime = tag.getLong("StartGameTime");
        duration = Math.max(1, tag.getInt("Duration"));
        initialized = tag.getBoolean("Initialized");
        collisionProgress = Math.max(0, Math.min(duration, tag.getInt("CollisionProgress")));
        clientEntityProgress = -1;
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

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
