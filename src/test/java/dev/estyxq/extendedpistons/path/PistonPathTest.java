package dev.estyxq.extendedpistons.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PistonPathTest {
    @Test
    void defaultPathHasOnePermanentFacingSegment() {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.NORTH);

        assertEquals(1, path.size());
        assertEquals(Direction.NORTH, path.first());
        assertEquals(new BlockPos(0, 0, -1), path.endpoint());
        assertFalse(path.removeLast());
    }

    @Test
    void packedPathRoundTripsAcrossByteBoundariesAndCorners() {
        PistonPath path = new PistonPath(new BlockPos(17, 42, -9), Direction.EAST);
        for (Direction direction : List.of(Direction.UP, Direction.SOUTH, Direction.SOUTH,
                Direction.WEST, Direction.DOWN, Direction.WEST, Direction.NORTH)) {
            assertTrue(path.append(direction));
        }

        byte[] packed = path.pack();
        PistonPath decoded = PistonPath.fromPacked(new BlockPos(17, 42, -9), path.size(), packed);

        assertEquals(path.directions(), decoded.directions());
        assertEquals(path.endpoint(), decoded.endpoint());
        assertArrayEquals(packed, decoded.pack());
    }

    @Test
    void rejectsBaseAndSelfIntersectionsIncludingFinalOutput() {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.EAST);
        assertFalse(path.canAppend(Direction.WEST));
        assertTrue(path.append(Direction.NORTH));
        assertFalse(path.canAppend(Direction.SOUTH));

        PistonPath outputCollision = new PistonPath(BlockPos.ZERO, Direction.EAST);
        assertTrue(outputCollision.append(Direction.EAST));
        assertTrue(outputCollision.append(Direction.NORTH));
        assertTrue(outputCollision.append(Direction.NORTH));
        assertTrue(outputCollision.append(Direction.WEST));
        assertFalse(outputCollision.canAppend(Direction.SOUTH));

        byte[] eastThenWest = pack(Direction.EAST, Direction.WEST);
        assertThrows(IllegalArgumentException.class,
                () -> PistonPath.fromPacked(BlockPos.ZERO, 2, eastThenWest));
    }

    @Test
    void rejectsMalformedPackedLengthsAndDirectionCodes() {
        assertThrows(IllegalArgumentException.class,
                () -> PistonPath.fromPacked(BlockPos.ZERO, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> PistonPath.fromPacked(BlockPos.ZERO, 3, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> PistonPath.fromPacked(BlockPos.ZERO, 1, new byte[]{7}));
    }

    @Test
    void veryLongPathUsesIterativeOperations() {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.EAST);
        for (int index = 1; index < 100_000; index++) {
            assertTrue(path.append(Direction.EAST));
        }

        assertEquals(100_000, path.size());
        assertEquals(new BlockPos(100_000, 0, 0), path.endpoint());
        assertEquals(new BlockPos(100_001, 0, 0), path.outputPosition());
        PistonPath decoded = PistonPath.fromPacked(BlockPos.ZERO, path.size(), path.pack());
        assertEquals(path.endpoint(), decoded.endpoint());
    }

    private static byte[] pack(Direction... directions) {
        byte[] result = new byte[(directions.length * 3 + 7) >>> 3];
        int bit = 0;
        for (Direction direction : directions) {
            int value = direction.get3DDataValue();
            int byteIndex = bit >>> 3;
            int shift = bit & 7;
            result[byteIndex] |= (byte) (value << shift);
            if (shift > 5) {
                result[byteIndex + 1] |= (byte) (value >>> (8 - shift));
            }
            bit += 3;
        }
        return result;
    }
}
