package dev.estyxq.extendedpistons.movement;

import dev.estyxq.extendedpistons.block.ExtendedPistonHeadBlock;
import dev.estyxq.extendedpistons.block.PistonShaftBlock;
import dev.estyxq.extendedpistons.compat.CompatibilityGuard;
import dev.estyxq.extendedpistons.compat.ProtectionBridge;
import dev.estyxq.extendedpistons.path.PistonPath;
import dev.estyxq.extendedpistons.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves movement by trajectory index, not by one global direction. A block at
 * q(n) always maps to q(n+1), so consecutive blocks naturally take different
 * cardinal displacement vectors when their chain crosses a corner.
 */
public final class MovementPlanner {
    private MovementPlanner() {
    }

    public static PlanStatus preflightExtension(ServerLevel level, PistonPath path,
                                                int currentHeadIndex, int pushLimit,
                                                boolean stickyHead) {
        Long2ObjectOpenHashMap<BlockState> virtual = new Long2ObjectOpenHashMap<>();
        for (int head = currentHeadIndex; head < path.size() - 1; head++) {
            PlanResult result = planExtension(level, path, head, pushLimit, stickyHead, virtual);
            if (result.status() != PlanStatus.READY) {
                return result.status();
            }
            if (!ProtectionBridge.canAffect(level, path.positionAt(-1),
                    result.plan().cells().stream().map(MovementCell::position).toList())) {
                return PlanStatus.BLOCKED;
            }
            for (MovementCell cell : result.plan().cells()) {
                virtual.put(cell.position().asLong(), cell.finalState());
            }
        }
        return PlanStatus.READY;
    }

    public static PlanResult planExtension(ServerLevel level, PistonPath path, int headIndex,
                                           int pushLimit, boolean stickyHead) {
        return planExtension(level, path, headIndex, pushLimit, stickyHead, null);
    }

    private static PlanResult planExtension(ServerLevel level, PistonPath path, int headIndex,
                                            int pushLimit, boolean stickyHead,
                                            Long2ObjectOpenHashMap<BlockState> virtual) {
        int targetIndex = headIndex + 1;
        ArrayList<PayloadMove> trajectoryMoves = new ArrayList<>();
        ArrayList<DestroyedCell> destroyed = new ArrayList<>();
        LongOpenHashSet scanned = new LongOpenHashSet();
        int index = targetIndex;

        while (true) {
            BlockPos source = path.positionAt(index);
            PlanStatus sourceStatus = positionStatus(level, source);
            if (sourceStatus != PlanStatus.READY) {
                return new PlanResult(sourceStatus, null);
            }
            if (!scanned.add(source.asLong())) {
                return PlanResult.blocked();
            }
            BlockState state = stateAt(level, source, virtual);
            if (state.isAir()) {
                break;
            }
            if (isTechnical(state) || !CompatibilityGuard.canMove(state.getBlock())
                    || hasUnsupportedBlockEntity(level, source, virtual)) {
                return PlanResult.blocked();
            }
            PushReaction reaction = state.getPistonPushReaction();
            if (reaction == PushReaction.DESTROY) {
                destroyed.add(new DestroyedCell(source.immutable(), state));
                break;
            }
            Direction displacement = directionAfter(path, index);
            BlockPos destination = source.relative(displacement);
            PlanStatus destinationStatus = positionStatus(level, destination);
            if (destinationStatus != PlanStatus.READY) {
                return new PlanResult(destinationStatus, null);
            }
            if (!PistonBaseBlock.isPushable(state, level, source, displacement, true, displacement)) {
                return PlanResult.blocked();
            }
            trajectoryMoves.add(new PayloadMove(source.immutable(), destination.immutable(), state,
                    displacement, index));
            if (trajectoryMoves.size() > pushLimit) {
                return PlanResult.blocked();
            }
            index++;
        }

        LinkedHashMap<Long, PayloadMove> moveMap = new LinkedHashMap<>();
        for (PayloadMove move : trajectoryMoves) moveMap.put(move.source().asLong(), move);
        PlanStatus attachmentStatus = expandStickyAttachments(level, virtual, moveMap, destroyed,
                pushLimit, true, null);
        if (attachmentStatus != PlanStatus.READY) return new PlanResult(attachmentStatus, null);
        ArrayList<PayloadMove> moves = new ArrayList<>(moveMap.values());
        HashMap<Long, Long> destinationOwners = new HashMap<>();
        for (PayloadMove move : moves) {
            Long prior = destinationOwners.putIfAbsent(move.destination().asLong(), move.source().asLong());
            if (prior != null && prior != move.source().asLong()) return PlanResult.blocked();
        }

        LinkedHashMap<Long, MutableCell> cells = new LinkedHashMap<>();
        for (PayloadMove move : moves) {
            MutableCell source = ensure(cells, level, virtual, move.source(), move.index());
            ensure(cells, level, virtual, move.destination(), move.index() + 1);
            source.finalState = Blocks.AIR.defaultBlockState();
            source.renderState = move.state();
            source.renderDirection = move.direction();
        }
        for (DestroyedCell destroyedCell : destroyed) {
            ensure(cells, level, virtual, destroyedCell.position(), index).finalState =
                    Blocks.AIR.defaultBlockState();
        }
        // Destination writes are applied after every source has been cleared. Overlapping
        // chain cells therefore receive the payload from q(n-1), not AIR from q(n).
        for (PayloadMove move : moves) {
            ensure(cells, level, virtual, move.destination(), move.index() + 1).finalState = move.state();
        }

        Direction headMovement = headIndex < 0 ? path.first() : directionAfter(path, headIndex);
        Direction headFacing = directionAfter(path, targetIndex);
        Direction headConnection = headConnection(path, targetIndex);
        BlockState headState = ModBlocks.EXTENDED_PISTON_HEAD.get()
                .stateFor(headFacing, headConnection, stickyHead);
        BlockPos target = path.positionAt(targetIndex);
        MutableCell targetCell = ensure(cells, level, virtual, target, targetIndex);
        targetCell.finalState = headState;
        if (headIndex < 0 && targetCell.originalState.isAir()) {
            // Enter the first cell facing the direction actually travelled. If
            // q(0) is a corner, rotation happens only after reaching its center.
            targetCell.renderState = ModBlocks.EXTENDED_PISTON_HEAD.get()
                    .stateFor(headMovement, headMovement.getOpposite(), stickyHead);
            targetCell.renderDirection = headMovement;
            targetCell.renderAtDestination = true;
        }

        if (headIndex >= 0) {
            BlockPos oldHead = path.positionAt(headIndex);
            MutableCell oldHeadCell = ensure(cells, level, virtual, oldHead, headIndex);
            Direction shaftOutgoing = headIndex + 1 < path.size() ? path.get(headIndex + 1) : null;
            oldHeadCell.finalState = ModBlocks.PISTON_SHAFT.get()
                    .stateFor(path.get(headIndex), shaftOutgoing);
            oldHeadCell.renderState = oldHeadCell.originalState;
            oldHeadCell.renderDirection = headMovement;
            oldHeadCell.renderAtDestination = false;
        }

        return PlanResult.ready(new StepPlan(freeze(cells), List.copyOf(destroyed),
                targetIndex, true, false));
    }

    public static PlanResult planRetraction(ServerLevel level, PistonPath path, int headIndex,
                                            int pushLimit, boolean sticky,
                                            boolean payloadAlreadyCaptured) {
        if (headIndex < 0) {
            return PlanResult.blocked();
        }
        BlockPos headPos = path.positionAt(headIndex);
        PlanStatus headStatus = positionStatus(level, headPos);
        if (headStatus != PlanStatus.READY) {
            return new PlanResult(headStatus, null);
        }
        Direction retractingHeadFacing = retractionHeadFacing(path, headIndex);
        Direction headPullDirection = retractingHeadFacing.getOpposite();
        int targetHeadIndex = headIndex - 1;
        Direction retractingConnection = headConnection(path, targetHeadIndex);
        LinkedHashMap<Long, MutableCell> cells = new LinkedHashMap<>();
        MutableCell headCell = ensure(cells, level, null, headPos, headIndex);
        if (!headCell.originalState.is(ModBlocks.EXTENDED_PISTON_HEAD.get())) {
            return PlanResult.blocked();
        }
        headCell.finalState = Blocks.AIR.defaultBlockState();
        // On retraction the plate faces away from the cell it is entering. Its
        // independent base-side arm already uses the destination head's incoming
        // direction, so a corner travels as an elbow instead of a straight rod.
        headCell.renderState = ModBlocks.EXTENDED_PISTON_HEAD.get()
                .stateFor(retractingHeadFacing, retractingConnection, sticky);
        headCell.renderDirection = headPullDirection;

        boolean pulling = false;
        ArrayList<DestroyedCell> destroyed = new ArrayList<>();
        if (sticky) {
            BlockPos payloadSource = path.positionAt(headIndex + 1);
            Direction payloadPullDirection = directionAfter(path, headIndex).getOpposite();
            PlanStatus payloadStatus = positionStatus(level, payloadSource);
            if (payloadStatus != PlanStatus.READY) {
                return new PlanResult(payloadStatus, null);
            }
            BlockState payload = level.getBlockState(payloadSource);
            if (!payload.isAir() && !isTechnical(payload)
                    && CompatibilityGuard.canMove(payload.getBlock())
                    && !hasUnsupportedBlockEntity(level, payloadSource, null)
                    && payload.getPistonPushReaction() != PushReaction.DESTROY
                    && PistonBaseBlock.isPushable(payload, level, payloadSource,
                    payloadPullDirection, false, payloadPullDirection.getOpposite())) {
                pulling = true;
                LinkedHashMap<Long, PayloadMove> moveMap = new LinkedHashMap<>();
                PayloadMove directPayload = new PayloadMove(payloadSource.immutable(), headPos.immutable(),
                        payload, payloadPullDirection, headIndex + 1);
                moveMap.put(payloadSource.asLong(), directPayload);
                PlanStatus attachmentStatus = expandStickyAttachments(level, null, moveMap,
                        destroyed, pushLimit, false, headPos);
                if (attachmentStatus != PlanStatus.READY) {
                    return new PlanResult(attachmentStatus, null);
                }
                HashMap<Long, Long> destinationOwners = new HashMap<>();
                for (PayloadMove move : moveMap.values()) {
                    Long prior = destinationOwners.putIfAbsent(move.destination().asLong(),
                            move.source().asLong());
                    if (prior != null && prior != move.source().asLong()) return PlanResult.blocked();
                    MutableCell source = ensure(cells, level, null, move.source(), move.index());
                    ensure(cells, level, null, move.destination(), move.index() - 1);
                    source.finalState = Blocks.AIR.defaultBlockState();
                    source.renderState = move.state();
                    source.renderDirection = move.direction();
                }
                for (DestroyedCell destroyedCell : destroyed) {
                    ensure(cells, level, null, destroyedCell.position(), headIndex)
                            .finalState = Blocks.AIR.defaultBlockState();
                }
                for (PayloadMove move : moveMap.values()) {
                    ensure(cells, level, null, move.destination(), move.index() - 1)
                            .finalState = move.state();
                }
            } else if (payloadAlreadyCaptured) {
                // The captured payload was changed externally. Retraction remains safe but stops pulling.
                pulling = false;
            }
        }

        if (targetHeadIndex >= 0) {
            BlockPos newHeadPos = path.positionAt(targetHeadIndex);
            PlanStatus targetStatus = positionStatus(level, newHeadPos);
            if (targetStatus != PlanStatus.READY) {
                return new PlanResult(targetStatus, null);
            }
            MutableCell newHead = ensure(cells, level, null, newHeadPos, targetHeadIndex);
            if (!newHead.originalState.is(ModBlocks.PISTON_SHAFT.get())) {
                return PlanResult.blocked();
            }
            newHead.finalState = ModBlocks.EXTENDED_PISTON_HEAD.get().stateFor(
                    directionAfter(path, targetHeadIndex),
                    headConnection(path, targetHeadIndex), sticky);
        }
        return PlanResult.ready(new StepPlan(freeze(cells), List.copyOf(destroyed),
                targetHeadIndex, false, pulling));
    }

    public static Direction directionAfter(PistonPath path, int index) {
        return index + 1 < path.size() ? path.get(index + 1) : path.last();
    }

    /** Direction from a head cell toward the preceding shaft or piston base. */
    public static Direction headConnection(PistonPath path, int index) {
        return (index >= 0 ? path.get(index) : path.first()).getOpposite();
    }

    static Direction retractionHeadFacing(PistonPath path, int headIndex) {
        if (headIndex < 0) {
            throw new IllegalArgumentException("A retraction head must be on the configured path");
        }
        return headIndex == 0 ? path.first() : directionAfter(path, headIndex - 1);
    }

    private static PlanStatus positionStatus(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return PlanStatus.BLOCKED;
        }
        return level.isLoaded(pos) ? PlanStatus.READY : PlanStatus.UNLOADED;
    }

    private static BlockState stateAt(ServerLevel level, BlockPos pos,
                                      Long2ObjectOpenHashMap<BlockState> virtual) {
        if (virtual != null && virtual.containsKey(pos.asLong())) {
            return virtual.get(pos.asLong());
        }
        return level.getBlockState(pos);
    }

    private static boolean hasUnsupportedBlockEntity(ServerLevel level, BlockPos pos,
                                                     Long2ObjectOpenHashMap<BlockState> virtual) {
        return (virtual == null || !virtual.containsKey(pos.asLong())) && level.getBlockEntity(pos) != null;
    }

    private static boolean isTechnical(BlockState state) {
        return state.is(ModBlocks.EXTENDED_PISTON_HEAD.get())
                || state.is(ModBlocks.PISTON_SHAFT.get())
                || state.is(ModBlocks.MOVEMENT_TRANSACTION.get());
    }

    /**
     * Iteratively expands NeoForge sticky components. Every source position has exactly
     * one displacement vector; encountering the same component through a different
     * corner vector fails closed instead of tearing or duplicating it.
     */
    private static PlanStatus expandStickyAttachments(ServerLevel level,
                                                      Long2ObjectOpenHashMap<BlockState> virtual,
                                                      LinkedHashMap<Long, PayloadMove> moves,
                                                      ArrayList<DestroyedCell> destroyed,
                                                      int pushLimit, boolean extending,
                                                      BlockPos ignoredTechnicalDestination) {
        ArrayDeque<PayloadMove> queue = new ArrayDeque<>(moves.values());
        LongOpenHashSet inspected = new LongOpenHashSet();
        while (!queue.isEmpty()) {
            PayloadMove anchor = queue.removeFirst();
            if (!inspected.add(anchor.source().asLong()) || !anchor.state().isStickyBlock()) continue;
            for (Direction side : Direction.values()) {
                // Match vanilla's branching rule: a sticky payload does not reach
                // backward into the piston (or into blocks already behind the push
                // front). Without this exclusion a slime/honey block directly in
                // front of the base would try to move the base itself.
                if (side == anchor.direction().getOpposite()) continue;
                BlockPos attachedPos = anchor.source().relative(side);
                PlanStatus status = positionStatus(level, attachedPos);
                if (status != PlanStatus.READY) return status;
                BlockState attached = stateAt(level, attachedPos, virtual);
                if (attachedPos.equals(ignoredTechnicalDestination) && isTechnical(attached)) continue;
                if (attached.isAir() || !anchor.state().canStickTo(attached)) continue;
                PayloadMove existing = moves.get(attachedPos.asLong());
                if (existing != null) {
                    if (existing.direction() != anchor.direction()) return PlanStatus.BLOCKED;
                    continue;
                }
                status = addPushLine(level, virtual, attachedPos, anchor.direction(), anchor.index(),
                        moves, destroyed, queue, pushLimit, extending);
                if (status != PlanStatus.READY) return status;
            }
        }
        return PlanStatus.READY;
    }

    private static PlanStatus addPushLine(ServerLevel level,
                                          Long2ObjectOpenHashMap<BlockState> virtual,
                                          BlockPos first, Direction displacement, int order,
                                          LinkedHashMap<Long, PayloadMove> moves,
                                          ArrayList<DestroyedCell> destroyed,
                                          ArrayDeque<PayloadMove> stickyQueue,
                                          int pushLimit, boolean extending) {
        BlockPos source = first;
        int lineOrder = order;
        LongOpenHashSet line = new LongOpenHashSet();
        while (true) {
            PlanStatus sourceStatus = positionStatus(level, source);
            if (sourceStatus != PlanStatus.READY) return sourceStatus;
            if (!line.add(source.asLong())) return PlanStatus.BLOCKED;
            PayloadMove existing = moves.get(source.asLong());
            if (existing != null) return existing.direction() == displacement
                    ? PlanStatus.READY : PlanStatus.BLOCKED;
            BlockState state = stateAt(level, source, virtual);
            if (state.isAir()) return PlanStatus.READY;
            if (isTechnical(state) || !CompatibilityGuard.canMove(state.getBlock())
                    || hasUnsupportedBlockEntity(level, source, virtual)
                    || state.getPistonPushReaction() == PushReaction.DESTROY
                    || !PistonBaseBlock.isPushable(state, level, source,
                    displacement, extending, displacement)) return PlanStatus.BLOCKED;

            BlockPos destination = source.relative(displacement);
            PlanStatus destinationStatus = positionStatus(level, destination);
            if (destinationStatus != PlanStatus.READY) return destinationStatus;
            PayloadMove added = new PayloadMove(source.immutable(), destination.immutable(),
                    state, displacement, lineOrder++);
            moves.put(source.asLong(), added);
            stickyQueue.addLast(added);
            if (moves.size() > pushLimit) return PlanStatus.BLOCKED;

            if (moves.containsKey(destination.asLong())) return PlanStatus.READY;
            BlockState destinationState = stateAt(level, destination, virtual);
            if (destinationState.isAir()) return PlanStatus.READY;
            if (destinationState.getPistonPushReaction() == PushReaction.DESTROY) {
                boolean known = destroyed.stream().anyMatch(cell -> cell.position().equals(destination));
                if (!known) destroyed.add(new DestroyedCell(destination.immutable(), destinationState));
                return PlanStatus.READY;
            }
            source = destination;
        }
    }

    private static MutableCell ensure(Map<Long, MutableCell> cells, ServerLevel level,
                                      Long2ObjectOpenHashMap<BlockState> virtual,
                                      BlockPos pos, int order) {
        return cells.computeIfAbsent(pos.asLong(), ignored ->
                new MutableCell(pos.immutable(), order, stateAt(level, pos, virtual)));
    }

    private static List<MovementCell> freeze(Map<Long, MutableCell> cells) {
        return cells.values().stream()
                .sorted(Comparator.comparingInt((MutableCell cell) -> cell.order).reversed())
                .map(MutableCell::freeze)
                .toList();
    }

    private record PayloadMove(BlockPos source, BlockPos destination, BlockState state,
                               Direction direction, int index) {
    }

    private static final class MutableCell {
        private final BlockPos position;
        private final int order;
        private final BlockState originalState;
        private BlockState finalState = Blocks.AIR.defaultBlockState();
        private BlockState renderState = Blocks.AIR.defaultBlockState();
        private Direction renderDirection = Direction.NORTH;
        private boolean renderAtDestination;

        private MutableCell(BlockPos position, int order, BlockState originalState) {
            this.position = position;
            this.order = order;
            this.originalState = originalState;
        }

        private MovementCell freeze() {
            return new MovementCell(position, order, originalState, finalState, renderState,
                    renderDirection, renderAtDestination);
        }
    }
}
