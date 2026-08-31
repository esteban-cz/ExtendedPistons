package dev.estyxq.extendedpistons.path;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compact, iterative path representation. Directions are world-relative and persisted at three bits per segment.
 */
public final class PistonPath {
    private static final String LENGTH_TAG = "PathLength";
    private static final String DATA_TAG = "PathData";

    private final ArrayList<Direction> directions = new ArrayList<>();
    private final ArrayList<BlockPos> positions = new ArrayList<>();
    private final LongOpenHashSet occupied = new LongOpenHashSet();
    private final Long2IntOpenHashMap indices = new Long2IntOpenHashMap();
    private BlockPos base;
    private BlockPos endpoint;

    public PistonPath(BlockPos base, Direction facing) {
        this.base = base.immutable();
        this.endpoint = base.relative(facing);
        this.directions.add(facing);
        this.positions.add(endpoint);
        this.occupied.add(endpoint.asLong());
        this.indices.defaultReturnValue(-1);
        this.indices.put(endpoint.asLong(), 0);
    }

    private PistonPath(BlockPos base, List<Direction> loadedDirections) {
        this.base = base.immutable();
        this.indices.defaultReturnValue(-1);
        rebuild(loadedDirections);
        if (directions.size() != loadedDirections.size()
                || outputPosition().equals(this.base)
                || occupied.contains(outputPosition().asLong())) {
            throw new IllegalArgumentException("Path intersects itself, its base, or its final output");
        }
    }

    public int size() {
        return directions.size();
    }

    public Direction get(int index) {
        return directions.get(index);
    }

    public Direction first() {
        return directions.getFirst();
    }

    public Direction last() {
        return directions.getLast();
    }

    public List<Direction> directions() {
        return Collections.unmodifiableList(directions);
    }

    public BlockPos endpoint() {
        return endpoint;
    }

    public BlockPos outputPosition() {
        return endpoint.relative(last());
    }

    public boolean contains(BlockPos pos) {
        return occupied.contains(pos.asLong());
    }

    public int indexOf(BlockPos pos) {
        return indices.get(pos.asLong());
    }

    public boolean canAppend(Direction direction) {
        BlockPos next = endpoint.relative(direction);
        if (next.equals(base) || occupied.contains(next.asLong())) {
            return false;
        }
        BlockPos newOutput = next.relative(direction);
        return !newOutput.equals(base) && !occupied.contains(newOutput.asLong());
    }

    public boolean append(Direction direction) {
        if (!canAppend(direction)) {
            return false;
        }
        endpoint = endpoint.relative(direction);
        directions.add(direction);
        positions.add(endpoint);
        occupied.add(endpoint.asLong());
        indices.put(endpoint.asLong(), directions.size() - 1);
        return true;
    }

    public boolean removeLast() {
        if (directions.size() == 1) {
            return false;
        }
        Direction removed = directions.removeLast();
        positions.removeLast();
        occupied.remove(endpoint.asLong());
        indices.remove(endpoint.asLong());
        endpoint = endpoint.relative(removed.getOpposite());
        return true;
    }

    public BlockPos positionAt(int index) {
        if (index < 0) {
            return base;
        }
        if (index < positions.size()) {
            return positions.get(index);
        }
        return endpoint.relative(last(), index - directions.size() + 1);
    }

    public byte[] pack() {
        byte[] packed = new byte[(directions.size() * 3 + 7) >>> 3];
        int bitIndex = 0;
        for (Direction direction : directions) {
            int value = direction.get3DDataValue() & 7;
            int byteIndex = bitIndex >>> 3;
            int shift = bitIndex & 7;
            packed[byteIndex] |= (byte) (value << shift);
            if (shift > 5) {
                packed[byteIndex + 1] |= (byte) (value >>> (8 - shift));
            }
            bitIndex += 3;
        }
        return packed;
    }

    public void save(CompoundTag tag) {
        tag.putInt(LENGTH_TAG, directions.size());
        tag.putByteArray(DATA_TAG, pack());
    }

    public static PistonPath load(CompoundTag tag, BlockPos base, Direction fallbackFacing) {
        int length = tag.getInt(LENGTH_TAG);
        byte[] data = tag.getByteArray(DATA_TAG);
        try {
            PistonPath loaded = fromPacked(base, length, data);
            return loaded.first() == fallbackFacing ? loaded : new PistonPath(base, fallbackFacing);
        } catch (IllegalArgumentException ignored) {
            return new PistonPath(base, fallbackFacing);
        }
    }

    /**
     * Decodes the versioned network/storage representation without imposing an artificial path limit.
     * Every structural invariant is checked before the path is exposed to the caller.
     */
    public static PistonPath fromPacked(BlockPos base, int length, byte[] data) {
        long expectedBytes = ((long) length * 3L + 7L) >>> 3;
        if (length < 1 || expectedBytes > Integer.MAX_VALUE || data.length != (int) expectedBytes) {
            throw new IllegalArgumentException("Malformed packed piston path");
        }

        ArrayList<Direction> result = new ArrayList<>(length);
        int bitIndex = 0;
        for (int i = 0; i < length; i++) {
            int byteIndex = bitIndex >>> 3;
            int shift = bitIndex & 7;
            int value = (data[byteIndex] & 0xFF) >>> shift;
            if (shift > 5) {
                value |= (data[byteIndex + 1] & 0xFF) << (8 - shift);
            }
            value &= 7;
            if (value > 5) {
                throw new IllegalArgumentException("Packed path contains an invalid direction");
            }
            result.add(Direction.from3DDataValue(value));
            bitIndex += 3;
        }
        return new PistonPath(base, result);
    }

    private void rebuild(List<Direction> loadedDirections) {
        directions.clear();
        positions.clear();
        occupied.clear();
        indices.clear();
        endpoint = base;
        for (Direction direction : loadedDirections) {
            BlockPos next = endpoint.relative(direction);
            if (next.equals(base) || !occupied.add(next.asLong())) {
                break;
            }
            directions.add(direction);
            positions.add(next);
            indices.put(next.asLong(), directions.size() - 1);
            endpoint = next;
        }
        if (directions.isEmpty()) {
            throw new IllegalArgumentException("A piston path must contain at least one valid segment");
        }
    }
}
