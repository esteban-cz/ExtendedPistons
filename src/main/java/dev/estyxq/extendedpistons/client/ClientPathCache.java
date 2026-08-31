package dev.estyxq.extendedpistons.client;

import dev.estyxq.extendedpistons.ExtendedPistons;
import dev.estyxq.extendedpistons.block.ExtendedPistonBlock;
import dev.estyxq.extendedpistons.network.PathDeltaPayload;
import dev.estyxq.extendedpistons.network.PathDeltaApplier;
import dev.estyxq.extendedpistons.network.PathOperation;
import dev.estyxq.extendedpistons.network.PathRequestPayload;
import dev.estyxq.extendedpistons.network.PathSyncFragmentPayload;
import dev.estyxq.extendedpistons.path.PistonPath;
import dev.estyxq.extendedpistons.path.PathTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Client-only spatial cache. A full path is traversed once when a revision arrives;
 * rendering thereafter performs constant-time lookups only for nearby chunks.
 */
public final class ClientPathCache {
    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static final Map<BlockPos, Assembly> ASSEMBLIES = new HashMap<>();
    private static final Map<Long, List<Segment>> BY_CHUNK = new HashMap<>();
    private static final Set<BlockPos> REQUESTED = new HashSet<>();

    private ClientPathCache() {
    }

    public static void acceptFragment(PathSyncFragmentPayload fragment) {
        Entry current = ENTRIES.get(fragment.base());
        if (current != null && current.revision() > fragment.revision()) {
            return;
        }
        Assembly assembly = ASSEMBLIES.get(fragment.base());
        if (assembly == null || assembly.revision != fragment.revision()) {
            assembly = new Assembly(fragment.revision(), fragment.segmentCount(), fragment.fragmentCount());
            ASSEMBLIES.put(fragment.base().immutable(), assembly);
        }
        if (assembly.segmentCount != fragment.segmentCount()
                || assembly.parts.length != fragment.fragmentCount()
                || assembly.parts[fragment.fragmentIndex()] != null) {
            return;
        }
        assembly.parts[fragment.fragmentIndex()] = Arrays.copyOf(fragment.data(), fragment.data().length);
        assembly.received++;
        if (assembly.received != assembly.parts.length) {
            return;
        }

        int byteCount = (int) ((((long) assembly.segmentCount * 3L) + 7L) >>> 3);
        byte[] packed = new byte[byteCount];
        int offset = 0;
        for (byte[] part : assembly.parts) {
            System.arraycopy(part, 0, packed, offset, part.length);
            offset += part.length;
        }
        ASSEMBLIES.remove(fragment.base());
        REQUESTED.remove(fragment.base());
        try {
            PistonPath path = PistonPath.fromPacked(fragment.base(), assembly.segmentCount, packed);
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && level.isLoaded(fragment.base())
                    && level.getBlockState(fragment.base()).getBlock() instanceof ExtendedPistonBlock
                    && level.getBlockState(fragment.base()).getValue(ExtendedPistonBlock.FACING) != path.first()) {
                throw new IllegalArgumentException("Path facing disagrees with its piston base");
            }
            replace(fragment.base(), path, fragment.revision());
        } catch (IllegalArgumentException exception) {
            ExtendedPistons.LOGGER.warn("Rejected malformed path sync for {}: {}",
                    fragment.base(), exception.getMessage());
        }
    }

    public static void acceptDelta(PathDeltaPayload delta) {
        Entry entry = ENTRIES.get(delta.base());
        if (entry == null) {
            request(delta.base());
            return;
        }
        PathDeltaApplier.Result result = PathDeltaApplier.apply(entry.path(), entry.revision(), delta);
        if (result != PathDeltaApplier.Result.APPLIED) {
            request(delta.base());
            return;
        }
        replace(delta.base(), entry.path(), delta.newRevision());
    }

    public static Target raycastEndpoint(Vec3 eye, Vec3 end) {
        Target nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return null;
        }
        BlockHitResult worldHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        double worldHitDistance = worldHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE : eye.distanceToSqr(worldHit.getLocation());
        for (Map.Entry<BlockPos, Entry> mapEntry : ENTRIES.entrySet()) {
            BlockPos base = mapEntry.getKey();
            Entry entry = mapEntry.getValue();
            BlockPos endpoint = entry.path().endpoint();
            if (!level.isLoaded(base) || !level.isLoaded(endpoint)
                    || !(level.getBlockState(base).getBlock() instanceof ExtendedPistonBlock)) {
                continue;
            }
            BlockHitResult hit = PathTargeting.clipEndpoint(eye, end, endpoint);
            if (hit == null || !hit.getBlockPos().equals(endpoint)) {
                continue;
            }
            double distance = eye.distanceToSqr(hit.getLocation());
            // A solid block closer to the camera owns the crosshair. This keeps
            // virtual endpoints behind neighboring pistons from being selected.
            if (distance > worldHitDistance + 1.0E-7D) {
                continue;
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = new Target(base, entry.revision(), hit);
            }
        }
        return nearest;
    }

    public static void requestIfMissing(BlockPos base) {
        if (!ENTRIES.containsKey(base)) {
            request(base);
        }
    }

    public static void invalidate(BlockPos base) {
        ASSEMBLIES.remove(base);
        REQUESTED.remove(base);
        remove(base);
    }

    public static void pruneInvalid(ClientLevel level) {
        ENTRIES.keySet().stream()
                .filter(base -> level.isLoaded(base)
                        && !(level.getBlockState(base).getBlock() instanceof ExtendedPistonBlock))
                .toList()
                .forEach(ClientPathCache::invalidate);
    }

    public static void forEachVisibleChunk(int centerChunkX, int centerChunkZ, int radius,
                                           Consumer<Segment> consumer) {
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                List<Segment> segments = BY_CHUNK.get(ChunkPos.asLong(x, z));
                if (segments != null) {
                    segments.forEach(consumer);
                }
            }
        }
    }

    public static void removeBaseChunk(ChunkPos chunkPos) {
        List<BlockPos> removals = ENTRIES.keySet().stream()
                .filter(base -> new ChunkPos(base).equals(chunkPos))
                .toList();
        removals.forEach(ClientPathCache::remove);
    }

    public static void clear() {
        ENTRIES.clear();
        ASSEMBLIES.clear();
        BY_CHUNK.clear();
        REQUESTED.clear();
    }

    private static void request(BlockPos base) {
        if (REQUESTED.add(base.immutable())) {
            PacketDistributor.sendToServer(new PathRequestPayload(base));
        }
    }

    private static void replace(BlockPos base, PistonPath path, int revision) {
        remove(base);
        Set<Long> chunkKeys = new HashSet<>();
        BlockPos cursor = base;
        for (int index = 0; index < path.size(); index++) {
            Direction incoming = path.get(index);
            cursor = cursor.relative(incoming);
            Direction outgoing = index + 1 < path.size() ? path.get(index + 1) : null;
            Segment segment = new Segment(base.immutable(), cursor.immutable(), incoming, outgoing,
                    index == path.size() - 1);
            long chunkKey = ChunkPos.asLong(cursor.getX() >> 4, cursor.getZ() >> 4);
            BY_CHUNK.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(segment);
            chunkKeys.add(chunkKey);
        }
        ENTRIES.put(base.immutable(), new Entry(path, revision, chunkKeys));
    }

    private static void remove(BlockPos base) {
        Entry old = ENTRIES.remove(base);
        if (old == null) {
            return;
        }
        for (long chunkKey : old.chunkKeys()) {
            List<Segment> segments = BY_CHUNK.get(chunkKey);
            if (segments != null) {
                segments.removeIf(segment -> segment.base().equals(base));
                if (segments.isEmpty()) {
                    BY_CHUNK.remove(chunkKey);
                }
            }
        }
    }

    public record Target(BlockPos base, int revision, BlockHitResult hit) {
    }

    public record Segment(BlockPos base, BlockPos position, Direction incoming,
                          Direction outgoing, boolean finalHead) {
    }

    private record Entry(PistonPath path, int revision, Set<Long> chunkKeys) {
    }

    private static final class Assembly {
        private final int revision;
        private final int segmentCount;
        private final byte[][] parts;
        private int received;

        private Assembly(int revision, int segmentCount, int fragmentCount) {
            this.revision = revision;
            this.segmentCount = segmentCount;
            this.parts = new byte[fragmentCount][];
        }
    }
}
