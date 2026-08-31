package dev.estyxq.extendedpistons.network;

import dev.estyxq.extendedpistons.ExtendedPistons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Removes every cached path generation associated with a destroyed base. */
public record PathInvalidatePayload(BlockPos base) implements CustomPacketPayload {
    public static final Type<PathInvalidatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedPistons.MOD_ID, "path_invalidate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PathInvalidatePayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, PathInvalidatePayload::base,
                    PathInvalidatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
