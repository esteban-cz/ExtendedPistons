package dev.estyxq.extendedpistons.network;

import dev.estyxq.extendedpistons.ExtendedPistons;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PathSyncFragmentPayload(
        BlockPos base,
        int revision,
        int segmentCount,
        int fragmentIndex,
        int fragmentCount,
        byte[] data) implements CustomPacketPayload {

    public static final int FRAGMENT_BYTES = 16 * 1024;
    public static final Type<PathSyncFragmentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedPistons.MOD_ID, "path_sync_fragment"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PathSyncFragmentPayload> STREAM_CODEC =
            StreamCodec.ofMember(PathSyncFragmentPayload::write, PathSyncFragmentPayload::decode);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(base);
        buffer.writeVarInt(revision);
        buffer.writeVarInt(segmentCount);
        buffer.writeVarInt(fragmentIndex);
        buffer.writeVarInt(fragmentCount);
        buffer.writeByteArray(data);
    }

    private static PathSyncFragmentPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos base = buffer.readBlockPos();
        int revision = buffer.readVarInt();
        int segments = buffer.readVarInt();
        int index = buffer.readVarInt();
        int count = buffer.readVarInt();
        byte[] data = buffer.readByteArray(FRAGMENT_BYTES);
        validateHeader(segments, index, count, data.length);
        return new PathSyncFragmentPayload(base, revision, segments, index, count, data);
    }

    public static void validateHeader(int segments, int index, int count, int dataLength) {
        long packedBytes = ((long) segments * 3L + 7L) >>> 3;
        long expectedFragments = Math.max(1L, (packedBytes + FRAGMENT_BYTES - 1L) / FRAGMENT_BYTES);
        if (segments < 1 || packedBytes > Integer.MAX_VALUE || count != expectedFragments
                || index < 0 || index >= count) {
            throw new DecoderException("Malformed piston path fragment header");
        }
        int expectedLength = index == count - 1
                ? (int) (packedBytes - (long) index * FRAGMENT_BYTES)
                : FRAGMENT_BYTES;
        if (dataLength != expectedLength) {
            throw new DecoderException("Malformed piston path fragment length");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
