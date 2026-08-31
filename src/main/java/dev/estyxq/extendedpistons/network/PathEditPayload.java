package dev.estyxq.extendedpistons.network;

import dev.estyxq.extendedpistons.ExtendedPistons;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

public record PathEditPayload(
        BlockPos base,
        InteractionHand hand,
        int expectedRevision,
        PathOperation operation,
        @Nullable Direction direction) implements CustomPacketPayload {

    public static final Type<PathEditPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedPistons.MOD_ID, "path_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PathEditPayload> STREAM_CODEC =
            StreamCodec.ofMember(PathEditPayload::write, PathEditPayload::decode);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(base);
        buffer.writeEnum(hand);
        buffer.writeVarInt(expectedRevision);
        buffer.writeEnum(operation);
        buffer.writeByte(direction == null ? -1 : direction.get3DDataValue());
    }

    private static PathEditPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos base = buffer.readBlockPos();
        InteractionHand hand = readEnum(buffer, InteractionHand.values(), "hand");
        int revision = buffer.readVarInt();
        PathOperation operation = readEnum(buffer, PathOperation.values(), "operation");
        int directionId = buffer.readByte();
        Direction direction = directionId < 0 ? null : directionId <= 5
                ? Direction.from3DDataValue(directionId)
                : null;
        if ((operation == PathOperation.ADD) != (direction != null)) {
            throw new DecoderException("Invalid direction for piston path edit");
        }
        return new PathEditPayload(base, hand, revision, operation, direction);
    }

    private static <T> T readEnum(RegistryFriendlyByteBuf buffer, T[] values, String name) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("Invalid " + name + " in piston path edit");
        }
        return values[ordinal];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
