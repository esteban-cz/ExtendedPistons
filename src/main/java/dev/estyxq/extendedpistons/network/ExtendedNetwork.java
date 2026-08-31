package dev.estyxq.extendedpistons.network;

import dev.estyxq.extendedpistons.block.entity.ExtendedPistonBlockEntity;
import dev.estyxq.extendedpistons.client.ClientPathCache;
import dev.estyxq.extendedpistons.registry.ModItems;
import dev.estyxq.extendedpistons.path.PathTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ExtendedNetwork {
    public static final String PROTOCOL_VERSION = "8";

    private ExtendedNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(PathEditPayload.TYPE, PathEditPayload.STREAM_CODEC, ExtendedNetwork::handleEdit);
        registrar.playToServer(PathRequestPayload.TYPE, PathRequestPayload.STREAM_CODEC, ExtendedNetwork::handleRequest);
        registrar.playToClient(PathSyncFragmentPayload.TYPE, PathSyncFragmentPayload.STREAM_CODEC,
                ExtendedNetwork::handleSyncFragment);
        registrar.playToClient(PathDeltaPayload.TYPE, PathDeltaPayload.STREAM_CODEC, ExtendedNetwork::handleDelta);
        registrar.playToClient(PathInvalidatePayload.TYPE, PathInvalidatePayload.STREAM_CODEC,
                ExtendedNetwork::handleInvalidate);
    }

    private static void handleEdit(PathEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !player.getItemInHand(payload.hand()).is(ModItems.PISTON_PATH_TOOL.get())
                || !level.isLoaded(payload.base())
                || !(level.getBlockEntity(payload.base()) instanceof ExtendedPistonBlockEntity piston)) {
            return;
        }

        if (payload.expectedRevision() != piston.pathRevision()) {
            player.displayClientMessage(Component.translatable("message.extendedpistons.path.stale"), true);
            PathTransferManager.queueFull(player, piston);
            return;
        }

        BlockPos endpoint = piston.path().endpoint();
        BlockPos editedPosition = payload.operation() == PathOperation.ADD
                ? endpoint.relative(payload.direction()) : endpoint;
        if (!player.mayBuild() || !player.mayInteract(level, payload.base())
                || !player.mayInteract(level, endpoint)
                || !player.mayInteract(level, editedPosition)) {
            player.displayClientMessage(Component.translatable("message.extendedpistons.path.denied"), true);
            return;
        }
        if (!canRaycastEndpoint(player, endpoint, payload.direction())) {
            player.displayClientMessage(Component.translatable("message.extendedpistons.path.too_far"), true);
            return;
        }

        int oldRevision = piston.pathRevision();
        boolean changed = payload.operation() == PathOperation.ADD
                ? piston.tryAppend(payload.direction())
                : piston.tryRemoveLast();
        if (!changed) {
            String key = payload.operation() == PathOperation.REMOVE && piston.path().size() == 1
                    ? "message.extendedpistons.path.minimum"
                    : "message.extendedpistons.path.invalid";
            player.displayClientMessage(Component.translatable(key), true);
            return;
        }

        PathTransferManager.broadcastDelta(level, piston, oldRevision, payload.operation(), payload.direction());
        String key = payload.operation() == PathOperation.ADD
                ? "message.extendedpistons.path.added"
                : "message.extendedpistons.path.removed";
        player.displayClientMessage(Component.translatable(key, piston.path().size()), true);
    }

    private static boolean canRaycastEndpoint(ServerPlayer player, BlockPos endpoint,
                                               net.minecraft.core.Direction expectedFace) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        BlockHitResult hit = PathTargeting.clipEndpoint(eye, end, endpoint);
        BlockHitResult worldHit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double worldHitDistance = worldHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE : eye.distanceToSqr(worldHit.getLocation());
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(endpoint)
                && eye.distanceToSqr(hit.getLocation()) <= worldHitDistance + 1.0E-7D
                && (expectedFace == null || hit.getDirection() == expectedFace);
    }

    private static void handleRequest(PathRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level
                && level.isLoaded(payload.base())
                && player.distanceToSqr(Vec3.atCenterOf(payload.base())) <= 512.0D * 512.0D
                && level.getBlockEntity(payload.base()) instanceof ExtendedPistonBlockEntity piston) {
            PathTransferManager.queueFull(player, piston);
        }
    }

    private static void handleSyncFragment(PathSyncFragmentPayload payload, IPayloadContext context) {
        ClientPathCache.acceptFragment(payload);
    }

    private static void handleDelta(PathDeltaPayload payload, IPayloadContext context) {
        ClientPathCache.acceptDelta(payload);
    }

    private static void handleInvalidate(PathInvalidatePayload payload, IPayloadContext context) {
        ClientPathCache.invalidate(payload.base());
    }
}
