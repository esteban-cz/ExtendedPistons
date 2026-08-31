package dev.estyxq.extendedpistons.network;

import dev.estyxq.extendedpistons.ExtendedPistons;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record PathDeltaPayload(
        BlockPos base,
        int expectedRevision,
        int newRevision,
        PathOperation operation,
        @Nullable Direction direction) implements CustomPacketPayload {

    public static final Type<PathDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedPistons.MOD_ID, "path_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PathDeltaPayload> STREAM_CODEC =
            StreamCodec.ofMember(PathDeltaPayload::write, PathDeltaPayload::decode);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(base);
        buffer.writeVarInt(expectedRevision);
        buffer.writeVarInt(newRevision);
        buffer.writeEnum(operation);
        buffer.writeByte(direction == null ? -1 : direction.get3DDataValue());
    }

    private static PathDeltaPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos base = buffer.readBlockPos();
        int expectedRevision = buffer.readVarInt();
        int newRevision = buffer.readVarInt();
        int operationId = buffer.readVarInt();
        if (operationId < 0 || operationId >= PathOperation.values().length) {
            throw new DecoderException("Invalid piston path delta operation");
        }
        PathOperation operation = PathOperation.values()[operationId];
        int directionId = buffer.readByte();
        Direction direction = directionId < 0 ? null : directionId <= 5
                ? Direction.from3DDataValue(directionId)
                : null;
        if ((operation == PathOperation.ADD) != (direction != null)
                || newRevision != expectedRevision + 1) {
            throw new DecoderException("Malformed piston path delta");
        }
        return new PathDeltaPayload(base, expectedRevision, newRevision, operation, direction);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
