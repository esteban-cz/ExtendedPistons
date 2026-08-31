package dev.estyxq.extendedpistons.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.estyxq.extendedpistons.path.PistonPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkValidationTest {
    @Test
    void validatesFragmentGeometryWithoutAllocatingClaimedTransfer() {
        PathSyncFragmentPayload.validateHeader(1, 0, 1, 1);
        PathSyncFragmentPayload.validateHeader(50_000, 1, 2,
                ((50_000 * 3 + 7) >>> 3) - PathSyncFragmentPayload.FRAGMENT_BYTES);

        assertThrows(DecoderException.class,
                () -> PathSyncFragmentPayload.validateHeader(0, 0, 1, 0));
        assertThrows(DecoderException.class,
                () -> PathSyncFragmentPayload.validateHeader(1, 1, 1, 1));
        assertThrows(DecoderException.class,
                () -> PathSyncFragmentPayload.validateHeader(50_000, 0, 1, 16_384));
        assertThrows(DecoderException.class,
                () -> PathSyncFragmentPayload.validateHeader(1, 0, 1, 2));
    }

    @Test
    void rejectsStaleAndStructurallyInvalidDeltas() {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.EAST);
        PathDeltaPayload add = new PathDeltaPayload(BlockPos.ZERO, 4, 5,
                PathOperation.ADD, Direction.NORTH);
        assertEquals(PathDeltaApplier.Result.STALE, PathDeltaApplier.apply(path, 3, add));
        assertEquals(PathDeltaApplier.Result.APPLIED, PathDeltaApplier.apply(path, 4, add));
        assertEquals(2, path.size());

        PathDeltaPayload badRevision = new PathDeltaPayload(BlockPos.ZERO, 5, 9,
                PathOperation.REMOVE, null);
        assertEquals(PathDeltaApplier.Result.INVALID,
                PathDeltaApplier.apply(path, 5, badRevision));
    }
}
