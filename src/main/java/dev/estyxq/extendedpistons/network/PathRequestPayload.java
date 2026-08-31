package dev.estyxq.extendedpistons.network;

import dev.estyxq.extendedpistons.ExtendedPistons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PathRequestPayload(BlockPos base) implements CustomPacketPayload {
    public static final Type<PathRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedPistons.MOD_ID, "path_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PathRequestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PathRequestPayload::base, PathRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
