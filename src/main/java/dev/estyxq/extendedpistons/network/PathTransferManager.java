package dev.estyxq.extendedpistons.network;

import dev.estyxq.extendedpistons.block.entity.ExtendedPistonBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Drains full-path fragments under a fixed per-player, per-tick byte budget. */
public final class PathTransferManager {
    private static final int BYTES_PER_PLAYER_PER_TICK = 64 * 1024;
    private static final Map<UUID, PlayerQueue> PENDING = new HashMap<>();

    private PathTransferManager() {
    }

    public static void queueFull(ServerPlayer player, ExtendedPistonBlockEntity piston) {
        byte[] packed = piston.path().pack();
        int fragmentCount = Math.max(1,
                (packed.length + PathSyncFragmentPayload.FRAGMENT_BYTES - 1)
                        / PathSyncFragmentPayload.FRAGMENT_BYTES);
        TransferKey key = new TransferKey(piston.getBlockPos().immutable(), piston.pathRevision());
        PlayerQueue playerQueue = PENDING.computeIfAbsent(player.getUUID(), ignored -> new PlayerQueue());
        if (!playerQueue.transfers.add(key)) {
            return;
        }
        for (int index = 0; index < fragmentCount; index++) {
            int start = index * PathSyncFragmentPayload.FRAGMENT_BYTES;
            int end = Math.min(packed.length, start + PathSyncFragmentPayload.FRAGMENT_BYTES);
            byte[] fragment = Arrays.copyOfRange(packed, start, end);
            PathSyncFragmentPayload payload = new PathSyncFragmentPayload(
                    piston.getBlockPos(), piston.pathRevision(), piston.path().size(),
                    index, fragmentCount, fragment);
            playerQueue.payloads.addLast(new QueuedPayload(payload, fragment.length, key,
                    index == fragmentCount - 1));
        }
    }

    /**
     * Seeds clients that already track the area when a piston is placed into an
     * existing chunk. ChunkWatchEvent only covers pistons that existed when the
     * chunk was sent, so relying on it alone leaves newly placed pistons invisible
     * to the path editor until the player reloads the chunk.
     */
    public static void queueFullNearby(ServerLevel level, ExtendedPistonBlockEntity piston) {
        Vec3 center = Vec3.atCenterOf(piston.getBlockPos());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= 512.0D * 512.0D) {
                queueFull(player, piston);
            }
        }
    }

    public static void broadcastDelta(ServerLevel level, ExtendedPistonBlockEntity piston,
                                      int expectedRevision, PathOperation operation,
                                      @Nullable Direction direction) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(piston.getBlockPos()),
                new PathDeltaPayload(piston.getBlockPos(), expectedRevision, piston.pathRevision(),
                        operation, direction));
    }

    public static void invalidateBase(ServerLevel level, BlockPos base) {
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != level) continue;
            PlayerQueue queue = entry.getValue();
            queue.payloads.removeIf(payload -> payload.key().base().equals(base));
            queue.transfers.removeIf(key -> key.base().equals(base));
            if (queue.payloads.isEmpty()) {
                iterator.remove();
            }
        }
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(base),
                new PathInvalidatePayload(base));
    }

    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        for (var blockEntity : event.getChunk().getBlockEntities().values()) {
            if (blockEntity instanceof ExtendedPistonBlockEntity piston) {
                queueFull(event.getPlayer(), piston);
            }
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            PlayerQueue queue = entry.getValue();
            int budget = BYTES_PER_PLAYER_PER_TICK;
            while (!queue.payloads.isEmpty()) {
                QueuedPayload next = queue.payloads.peekFirst();
                if (budget < next.bytes() && budget != BYTES_PER_PLAYER_PER_TICK) {
                    break;
                }
                queue.payloads.removeFirst();
                PacketDistributor.sendToPlayer(player, next.payload());
                budget -= next.bytes();
                if (next.last()) {
                    queue.transfers.remove(next.key());
                }
            }
            if (queue.payloads.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    private record TransferKey(net.minecraft.core.BlockPos base, int revision) {
    }

    private record QueuedPayload(CustomPacketPayload payload, int bytes, TransferKey key, boolean last) {
    }

    private static final class PlayerQueue {
        private final ArrayDeque<QueuedPayload> payloads = new ArrayDeque<>();
        private final Set<TransferKey> transfers = new HashSet<>();
    }
}
