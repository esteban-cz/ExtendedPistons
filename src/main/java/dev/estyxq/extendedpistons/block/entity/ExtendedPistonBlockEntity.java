package dev.estyxq.extendedpistons.block.entity;

import dev.estyxq.extendedpistons.block.ExtendedPistonBlock;
import dev.estyxq.extendedpistons.block.MovementState;
import dev.estyxq.extendedpistons.block.TransactionPhase;
import dev.estyxq.extendedpistons.config.ServerConfig;
import dev.estyxq.extendedpistons.compat.ProtectionBridge;
import dev.estyxq.extendedpistons.movement.DestroyedCell;
import dev.estyxq.extendedpistons.movement.MovementCell;
import dev.estyxq.extendedpistons.movement.MovementJournal;
import dev.estyxq.extendedpistons.movement.MovementPlanner;
import dev.estyxq.extendedpistons.movement.MovingEntityPusher;
import dev.estyxq.extendedpistons.movement.PlanResult;
import dev.estyxq.extendedpistons.movement.PlanStatus;
import dev.estyxq.extendedpistons.movement.StepPlan;
import dev.estyxq.extendedpistons.network.PathTransferManager;
import dev.estyxq.extendedpistons.path.PistonPath;
import dev.estyxq.extendedpistons.registry.ModBlockEntities;
import dev.estyxq.extendedpistons.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class ExtendedPistonBlockEntity extends BlockEntity {
    private static final int BLOCKED_RETRY_TICKS = 20;
    private static final int TRANSACTION_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON;

    private PistonPath path;
    private MovementState movementState = MovementState.RETRACTED;
    private int headIndex = -1;
    private int segmentProgress;
    private int pathRevision;
    private boolean desiredPowered;
    private boolean stickyPayloadActive;
    private boolean stickyCaptureAttempted;
    private int blockedRetry;
    private long transactionSequence;
    @Nullable
    private MovementJournal activeJournal;

    public ExtendedPistonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXTENDED_PISTON.get(), pos, state);
        Direction facing = state.hasProperty(ExtendedPistonBlock.FACING)
                ? state.getValue(ExtendedPistonBlock.FACING)
                : Direction.NORTH;
        path = new PistonPath(pos, facing);
    }

    public PistonPath path() { return path; }
    public MovementState movementState() { return movementState; }
    public int headIndex() { return headIndex; }
    public int segmentProgress() { return segmentProgress; }
    public int pathRevision() { return pathRevision; }
    public boolean desiredPowered() { return desiredPowered; }

    public boolean ownsTransaction(long transactionId) {
        return activeJournal != null && activeJournal.id() == transactionId;
    }

    public boolean ownsPart(BlockPos partPos) {
        int index = path.indexOf(partPos);
        return index >= 0 && index <= headIndex;
    }

    public boolean isEditable() {
        return movementState == MovementState.RETRACTED && !desiredPowered && activeJournal == null;
    }

    public void resetPath(Direction facing) {
        path = new PistonPath(worldPosition, facing);
        pathRevision++;
        changedAndSync();
        if (level instanceof ServerLevel serverLevel) {
            PathTransferManager.queueFullNearby(serverLevel, this);
        }
    }

    public boolean tryAppend(Direction direction) {
        if (!isEditable() || level == null) return false;
        BlockPos target = path.endpoint().relative(direction);
        if (!level.isLoaded(target) || !level.isInWorldBounds(target)
                || !level.getWorldBorder().isWithinBounds(target)
                || !level.getBlockState(target).isAir() || !path.append(direction)) return false;
        pathRevision++;
        changedAndSync();
        return true;
    }

    public boolean tryRemoveLast() {
        if (!isEditable() || !path.removeLast()) return false;
        pathRevision++;
        changedAndSync();
        return true;
    }

    public void setDesiredPowered(boolean powered) {
        if (desiredPowered == powered) return;
        desiredPowered = powered;
        if (level instanceof ServerLevel serverLevel) {
            setBaseState(serverLevel, true, getBlockState().getValue(ExtendedPistonBlock.EXTENDED));
        }
        changedAndSync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ExtendedPistonBlockEntity piston) {
        if (level instanceof ServerLevel serverLevel) piston.tickServer(serverLevel);
    }

    private void tickServer(ServerLevel level) {
        if (activeJournal != null) {
            tickJournal(level);
            return;
        }
        switch (movementState) {
            case RETRACTED -> {
                headIndex = -1;
                stickyPayloadActive = false;
                stickyCaptureAttempted = false;
                if (desiredPowered) {
                    movementState = MovementState.PREFLIGHTING;
                    changedAndSync();
                } else deactivate(level, false);
            }
            case PREFLIGHTING -> preflight(level);
            case EXTENDING -> {
                if (!desiredPowered) {
                    movementState = MovementState.RETRACTING;
                    changedAndSync();
                } else if (headIndex >= path.size() - 1) {
                    movementState = MovementState.EXTENDED;
                    deactivate(level, true);
                } else beginExtensionStep(level);
            }
            case EXTENDED -> {
                headIndex = path.size() - 1;
                if (!desiredPowered) {
                    movementState = MovementState.RETRACTING;
                    changedAndSync();
                } else deactivate(level, true);
            }
            case RETRACTING -> {
                if (desiredPowered) {
                    movementState = MovementState.PREFLIGHTING;
                    changedAndSync();
                } else if (headIndex < 0) {
                    movementState = MovementState.RETRACTED;
                    stickyPayloadActive = false;
                    stickyCaptureAttempted = false;
                    deactivate(level, false);
                } else beginRetractionStep(level);
            }
            case BLOCKED -> tickBlocked(level);
            case RECOVERING -> recoverWithoutJournal(level);
        }
    }

    private void preflight(ServerLevel level) {
        PlanStatus status = MovementPlanner.preflightExtension(level, path, headIndex,
                ServerConfig.EXTENDED_PISTON_PUSH_LIMIT.get(), isSticky());
        if (status == PlanStatus.UNLOADED) return;
        if (status == PlanStatus.BLOCKED) {
            movementState = MovementState.BLOCKED;
            blockedRetry = BLOCKED_RETRY_TICKS;
            changedAndSync();
            return;
        }
        movementState = MovementState.EXTENDING;
        changedAndSync();
        beginExtensionStep(level);
    }

    private void beginExtensionStep(ServerLevel level) {
        handlePlanResult(level, MovementPlanner.planExtension(level, path, headIndex,
                ServerConfig.EXTENDED_PISTON_PUSH_LIMIT.get(), isSticky()), true);
    }

    private void beginRetractionStep(ServerLevel level) {
        handlePlanResult(level, MovementPlanner.planRetraction(level, path, headIndex,
                ServerConfig.EXTENDED_PISTON_PUSH_LIMIT.get(),
                isSticky() && (!stickyCaptureAttempted || stickyPayloadActive),
                stickyPayloadActive), false);
    }

    private void handlePlanResult(ServerLevel level, PlanResult result, boolean extending) {
        if (result.status() == PlanStatus.UNLOADED) return;
        if (result.status() == PlanStatus.BLOCKED) {
            if (extending) {
                movementState = MovementState.BLOCKED;
                blockedRetry = BLOCKED_RETRY_TICKS;
            } else emergencySettle();
            changedAndSync();
            return;
        }
        installTransaction(level, result.plan());
    }

    private void installTransaction(ServerLevel level, StepPlan plan) {
        if (!ProtectionBridge.canAffect(level, worldPosition,
                plan.cells().stream().map(MovementCell::position).toList())) {
            movementState = plan.extending() ? MovementState.BLOCKED : MovementState.RECOVERING;
            changedAndSync();
            return;
        }
        for (MovementCell cell : plan.cells()) {
            if (!level.isLoaded(cell.position())
                    || !level.getBlockState(cell.position()).equals(cell.originalState())
                    || (level.getBlockEntity(cell.position()) != null
                    && !(level.getBlockEntity(cell.position()) instanceof PistonPartBlockEntity part
                    && part.isOwnedBy(worldPosition) && ownsPart(cell.position())))) {
                movementState = plan.extending() ? MovementState.BLOCKED : MovementState.RECOVERING;
                changedAndSync();
                return;
            }
        }
        long id = (++transactionSequence << 20) ^ level.getGameTime() ^ worldPosition.asLong();
        int duration = ServerConfig.TICKS_PER_SEGMENT.get();
        activeJournal = new MovementJournal(id, TransactionPhase.PREPARING, plan,
                level.getGameTime(), duration);
        segmentProgress = 0;
        setBaseState(level, true, headIndex >= 0 || plan.extending());
        changedAndSync();

        for (MovementCell cell : plan.cells()) {
            if (!level.setBlock(cell.position(), ModBlocks.MOVEMENT_TRANSACTION.get().defaultBlockState(),
                    TRANSACTION_FLAGS)
                    || !(level.getBlockEntity(cell.position()) instanceof MovementTransactionBlockEntity transaction)) {
                rollbackPreparing(level);
                return;
            }
            transaction.configure(cell.originalState(), cell.finalState(), cell.renderState(),
                    worldPosition, id, TransactionPhase.PREPARING, cell.renderDirection(),
                    cell.renderAtDestination(), activeJournal.startGameTime(), duration);
        }

        activeJournal.commit();
        setChanged();
        for (MovementCell cell : plan.cells()) {
            if (level.getBlockEntity(cell.position()) instanceof MovementTransactionBlockEntity transaction
                    && transaction.transactionId() == id) transaction.markCommitted();
        }
        applyDestroyEffects(level);
        level.playSound(null, worldPosition,
                plan.extending() ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT,
                SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.15F);
        changedAndSync();
    }

    private void applyDestroyEffects(ServerLevel level) {
        if (activeJournal == null || activeJournal.destroyEffectsApplied()) return;
        activeJournal.markDestroyEffectsApplied();
        setChanged();
        for (DestroyedCell destroyed : activeJournal.plan().destroyed()) {
            Block.dropResources(destroyed.state(), level, destroyed.position());
            level.levelEvent(null, 2001, destroyed.position(), Block.getId(destroyed.state()));
            level.gameEvent(GameEvent.BLOCK_DESTROY, Vec3.atCenterOf(destroyed.position()),
                    GameEvent.Context.of(destroyed.state()));
        }
    }

    private void tickJournal(ServerLevel level) {
        if (!allJournalCellsLoaded(level)) return;
        if (activeJournal.phase() == TransactionPhase.PREPARING) {
            rollbackPreparing(level);
            movementState = MovementState.RECOVERING;
            changedAndSync();
            return;
        }
        applyDestroyEffects(level);
        long elapsed = Math.max(0L, level.getGameTime() - activeJournal.startGameTime());
        int nextProgress = (int) Math.min(activeJournal.duration(), elapsed);
        int priorEntityProgress = activeJournal.entityProgress();
        if (nextProgress > priorEntityProgress) {
            pushEntities(level, activeJournal.plan(),
                    MovingEntityPusher.easedProgress(priorEntityProgress, activeJournal.duration()),
                    MovingEntityPusher.easedProgress(nextProgress, activeJournal.duration()));
            updateTransactionCollisionProgress(level, activeJournal.plan(), nextProgress);
            activeJournal.setEntityProgress(nextProgress);
            setChanged();
        }
        segmentProgress = nextProgress;
        if (elapsed >= activeJournal.duration()) settleCommitted(level);
    }

    private boolean allJournalCellsLoaded(ServerLevel level) {
        for (MovementCell cell : activeJournal.plan().cells()) {
            if (!level.isLoaded(cell.position())) return false;
        }
        return true;
    }

    private void settleCommitted(ServerLevel level) {
        MovementJournal journal = activeJournal;
        settleCells(level, journal.plan().cells(), true);
        headIndex = journal.plan().targetHeadIndex();
        stickyPayloadActive = journal.plan().pullingPayload();
        boolean wasExtending = journal.plan().extending();
        activeJournal = null;
        segmentProgress = 0;
        if (wasExtending) {
            stickyCaptureAttempted = false;
            if (!desiredPowered) movementState = MovementState.RETRACTING;
            else if (headIndex >= path.size() - 1) movementState = MovementState.EXTENDED;
            else movementState = MovementState.EXTENDING;
        } else if (desiredPowered) {
            stickyCaptureAttempted = isSticky();
            movementState = MovementState.PREFLIGHTING;
        }
        else if (headIndex < 0) {
            movementState = MovementState.RETRACTED;
            stickyPayloadActive = false;
            stickyCaptureAttempted = false;
            setBaseState(level, true, false);
        } else {
            stickyCaptureAttempted = isSticky();
            movementState = MovementState.RETRACTING;
        }
        changedAndSync();
        if ((movementState == MovementState.RETRACTED && !desiredPowered)
                || (movementState == MovementState.EXTENDED && desiredPowered)) {
            deactivate(level, movementState == MovementState.EXTENDED);
        }
    }

    private void rollbackPreparing(ServerLevel level) {
        if (activeJournal == null) return;
        settleCells(level, activeJournal.plan().cells(), false);
        activeJournal = null;
        segmentProgress = 0;
        changedAndSync();
    }

    private void settleCells(ServerLevel level, java.util.List<MovementCell> cells, boolean forward) {
        for (MovementCell cell : cells) {
            level.setBlock(cell.position(), forward ? cell.finalState() : cell.originalState(),
                    TRANSACTION_FLAGS);
            if (level.getBlockEntity(cell.position()) instanceof PistonPartBlockEntity part) {
                part.configure(worldPosition);
            }
        }
        for (MovementCell cell : cells) {
            BlockState state = level.getBlockState(cell.position());
            BlockState shaped = Block.updateFromNeighbourShapes(state, level, cell.position());
            if (shaped != state) {
                level.setBlock(cell.position(), shaped, TRANSACTION_FLAGS);
                state = shaped;
            }
            level.updateNeighborsAt(cell.position(), cell.originalState().getBlock());
            level.updateNeighborsAt(cell.position(), state.getBlock());
        }
    }

    private void pushEntities(ServerLevel level, StepPlan plan,
                              double previousProgress, double currentProgress) {
        Map<MovingEntityPusher.EntityMove, Double> moved = new HashMap<>();
        for (MovementCell cell : plan.cells()) {
            MovingEntityPusher.pushCell(level, cell.position(), cell.renderState(),
                    cell.renderDirection(), cell.renderAtDestination(),
                    previousProgress, currentProgress, moved);
        }
    }

    private void updateTransactionCollisionProgress(ServerLevel level, StepPlan plan, int progress) {
        for (MovementCell cell : plan.cells()) {
            if (level.getBlockEntity(cell.position()) instanceof MovementTransactionBlockEntity transaction
                    && transaction.transactionId() == activeJournal.id()) {
                transaction.setCollisionProgress(progress);
            }
        }
    }

    private void tickBlocked(ServerLevel level) {
        if (!desiredPowered) {
            if (headIndex >= 0) {
                movementState = MovementState.RETRACTING;
                changedAndSync();
            } else {
                movementState = MovementState.RETRACTED;
                deactivate(level, false);
            }
        } else if (--blockedRetry <= 0) {
            blockedRetry = BLOCKED_RETRY_TICKS;
            movementState = MovementState.PREFLIGHTING;
            changedAndSync();
        }
    }

    private void recoverWithoutJournal(ServerLevel level) {
        cleanupLoadedTechnicalParts(level);
        headIndex = -1;
        stickyPayloadActive = false;
        stickyCaptureAttempted = false;
        movementState = desiredPowered ? MovementState.PREFLIGHTING : MovementState.RETRACTED;
        setBaseState(level, true, false);
        changedAndSync();
    }

    public void emergencySettle() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (activeJournal != null && allJournalCellsLoaded(serverLevel)) {
            boolean forward = activeJournal.phase() == TransactionPhase.COMMITTED;
            settleCells(serverLevel, activeJournal.plan().cells(), forward);
            if (forward) headIndex = activeJournal.plan().targetHeadIndex();
            activeJournal = null;
        }
        cleanupLoadedTechnicalParts(serverLevel);
        headIndex = -1;
        stickyPayloadActive = false;
        stickyCaptureAttempted = false;
        movementState = MovementState.RETRACTED;
        setBaseState(serverLevel, true, false);
        changedAndSync();
    }

    public void baseRemoved() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        // A base position can be reused immediately. Remove both queued fragments
        // and the live client cache before a replacement piston publishes revision 1.
        PathTransferManager.invalidateBase(serverLevel, worldPosition);
        if (activeJournal != null && allJournalCellsLoaded(serverLevel)) {
            boolean forward = activeJournal.phase() == TransactionPhase.COMMITTED;
            settleCells(serverLevel, activeJournal.plan().cells(), forward);
            if (forward) headIndex = activeJournal.plan().targetHeadIndex();
            activeJournal = null;
        }
        cleanupLoadedTechnicalParts(serverLevel);
    }

    private void cleanupLoadedTechnicalParts(ServerLevel level) {
        // Only cells at or behind the committed head can belong to this piston.
        // Checking the persisted owner prevents overlapping pistons from deleting
        // each other's technical blocks during emergency cleanup.
        for (int index = 0; index <= headIndex; index++) {
            BlockPos pos = path.positionAt(index);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if ((state.is(ModBlocks.PISTON_SHAFT.get())
                    || state.is(ModBlocks.EXTENDED_PISTON_HEAD.get()))
                    && level.getBlockEntity(pos) instanceof PistonPartBlockEntity part
                    && part.isOwnedBy(worldPosition)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private boolean isSticky() {
        return getBlockState().getBlock() instanceof ExtendedPistonBlock piston && piston.isSticky();
    }

    private void deactivate(ServerLevel level, boolean extended) {
        setBaseState(level, false, extended);
        setChanged();
    }

    private void setBaseState(ServerLevel level, boolean active, boolean extended) {
        BlockState state = getBlockState();
        if (state.hasProperty(ExtendedPistonBlock.ACTIVE)
                && (state.getValue(ExtendedPistonBlock.ACTIVE) != active
                || state.getValue(ExtendedPistonBlock.EXTENDED) != extended)) {
            level.setBlock(worldPosition, state.setValue(ExtendedPistonBlock.ACTIVE, active)
                    .setValue(ExtendedPistonBlock.EXTENDED, extended), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        path.save(tag);
        tag.putString("MovementState", movementState.name());
        tag.putInt("HeadIndex", headIndex);
        tag.putInt("SegmentProgress", segmentProgress);
        tag.putInt("PathRevision", pathRevision);
        tag.putBoolean("DesiredPowered", desiredPowered);
        tag.putBoolean("StickyPayloadActive", stickyPayloadActive);
        tag.putBoolean("StickyCaptureAttempted", stickyCaptureAttempted);
        tag.putInt("BlockedRetry", blockedRetry);
        tag.putLong("TransactionSequence", transactionSequence);
        if (activeJournal != null) tag.put("ActiveJournal", activeJournal.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Direction facing = getBlockState().hasProperty(ExtendedPistonBlock.FACING)
                ? getBlockState().getValue(ExtendedPistonBlock.FACING) : Direction.NORTH;
        path = PistonPath.load(tag, worldPosition, facing);
        loadClientMotion(tag);
        stickyPayloadActive = tag.getBoolean("StickyPayloadActive");
        stickyCaptureAttempted = tag.getBoolean("StickyCaptureAttempted");
        blockedRetry = tag.getInt("BlockedRetry");
        transactionSequence = tag.getLong("TransactionSequence");
        activeJournal = tag.contains("ActiveJournal", Tag.TAG_COMPOUND)
                ? MovementJournal.load(tag.getCompound("ActiveJournal"), registries) : null;
        if (activeJournal != null) movementState = MovementState.RECOVERING;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("MovementState", movementState.name());
        tag.putInt("HeadIndex", headIndex);
        tag.putInt("SegmentProgress", segmentProgress);
        tag.putInt("PathRevision", pathRevision);
        tag.putBoolean("DesiredPowered", desiredPowered);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadClientMotion(tag);
    }

    private void loadClientMotion(CompoundTag tag) {
        try { movementState = MovementState.valueOf(tag.getString("MovementState")); }
        catch (IllegalArgumentException ignored) { movementState = MovementState.RECOVERING; }
        headIndex = tag.getInt("HeadIndex");
        segmentProgress = tag.getInt("SegmentProgress");
        pathRevision = tag.getInt("PathRevision");
        desiredPowered = tag.getBoolean("DesiredPowered");
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }
}
