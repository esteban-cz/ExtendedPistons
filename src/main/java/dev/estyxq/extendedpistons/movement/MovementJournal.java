package dev.estyxq.extendedpistons.movement;

import dev.estyxq.extendedpistons.block.TransactionPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** The base-owned durable record for a two-phase single-cell movement. */
public final class MovementJournal {
    private final long id;
    private TransactionPhase phase;
    private final StepPlan plan;
    private final long startGameTime;
    private final int duration;
    private boolean destroyEffectsApplied;
    private int entityProgress;

    public MovementJournal(long id, TransactionPhase phase, StepPlan plan,
                           long startGameTime, int duration) {
        this.id = id;
        this.phase = phase;
        this.plan = plan;
        this.startGameTime = startGameTime;
        this.duration = Math.max(1, duration);
    }

    public long id() {
        return id;
    }

    public TransactionPhase phase() {
        return phase;
    }

    public void commit() {
        phase = TransactionPhase.COMMITTED;
    }

    public boolean shouldFinishForward() {
        return phase == TransactionPhase.COMMITTED;
    }

    public StepPlan plan() {
        return plan;
    }

    public long startGameTime() {
        return startGameTime;
    }

    public int duration() {
        return duration;
    }

    public boolean destroyEffectsApplied() {
        return destroyEffectsApplied;
    }

    public void markDestroyEffectsApplied() {
        destroyEffectsApplied = true;
    }

    public int entityProgress() {
        return entityProgress;
    }

    public void setEntityProgress(int entityProgress) {
        this.entityProgress = Math.max(0, Math.min(duration, entityProgress));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Id", id);
        tag.putString("Phase", phase.name());
        tag.putInt("TargetHeadIndex", plan.targetHeadIndex());
        tag.putBoolean("Extending", plan.extending());
        tag.putBoolean("PullingPayload", plan.pullingPayload());
        tag.putLong("StartGameTime", startGameTime);
        tag.putInt("Duration", duration);
        tag.putBoolean("DestroyEffectsApplied", destroyEffectsApplied);
        tag.putInt("EntityProgress", entityProgress);
        ListTag cells = new ListTag();
        for (MovementCell cell : plan.cells()) {
            CompoundTag cellTag = new CompoundTag();
            cellTag.putLong("Position", cell.position().asLong());
            cellTag.putInt("Order", cell.trajectoryOrder());
            cellTag.put("Original", NbtUtils.writeBlockState(cell.originalState()));
            cellTag.put("Final", NbtUtils.writeBlockState(cell.finalState()));
            cellTag.put("Render", NbtUtils.writeBlockState(cell.renderState()));
            cellTag.putByte("Direction", (byte) cell.renderDirection().get3DDataValue());
            cellTag.putBoolean("RenderAtDestination", cell.renderAtDestination());
            cells.add(cellTag);
        }
        tag.put("Cells", cells);
        ListTag destroyed = new ListTag();
        for (DestroyedCell cell : plan.destroyed()) {
            CompoundTag destroyedTag = new CompoundTag();
            destroyedTag.putLong("Position", cell.position().asLong());
            destroyedTag.put("State", NbtUtils.writeBlockState(cell.state()));
            destroyed.add(destroyedTag);
        }
        tag.put("Destroyed", destroyed);
        return tag;
    }

    public static MovementJournal load(CompoundTag tag, HolderLookup.Provider registries) {
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        ArrayList<MovementCell> cells = new ArrayList<>();
        ListTag cellTags = tag.getList("Cells", Tag.TAG_COMPOUND);
        for (int index = 0; index < cellTags.size(); index++) {
            CompoundTag cell = cellTags.getCompound(index);
            int directionId = cell.getByte("Direction");
            Direction direction = directionId >= 0 && directionId <= 5
                    ? Direction.from3DDataValue(directionId) : Direction.NORTH;
            cells.add(new MovementCell(
                    BlockPos.of(cell.getLong("Position")), cell.getInt("Order"),
                    NbtUtils.readBlockState(blocks, cell.getCompound("Original")),
                    NbtUtils.readBlockState(blocks, cell.getCompound("Final")),
                    NbtUtils.readBlockState(blocks, cell.getCompound("Render")),
                    direction, cell.getBoolean("RenderAtDestination")));
        }
        ArrayList<DestroyedCell> destroyed = new ArrayList<>();
        ListTag destroyedTags = tag.getList("Destroyed", Tag.TAG_COMPOUND);
        for (int index = 0; index < destroyedTags.size(); index++) {
            CompoundTag cell = destroyedTags.getCompound(index);
            destroyed.add(new DestroyedCell(BlockPos.of(cell.getLong("Position")),
                    NbtUtils.readBlockState(blocks, cell.getCompound("State"))));
        }
        TransactionPhase phase;
        try {
            phase = TransactionPhase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException ignored) {
            phase = TransactionPhase.PREPARING;
        }
        StepPlan plan = new StepPlan(List.copyOf(cells), List.copyOf(destroyed),
                tag.getInt("TargetHeadIndex"), tag.getBoolean("Extending"),
                tag.getBoolean("PullingPayload"));
        MovementJournal journal = new MovementJournal(tag.getLong("Id"), phase, plan,
                tag.getLong("StartGameTime"), tag.getInt("Duration"));
        journal.destroyEffectsApplied = tag.getBoolean("DestroyEffectsApplied");
        journal.setEntityProgress(tag.getInt("EntityProgress"));
        return journal;
    }
}
