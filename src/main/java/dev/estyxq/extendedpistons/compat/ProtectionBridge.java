package dev.estyxq.extendedpistons.compat;

import dev.estyxq.extendedpistons.ExtendedPistons;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.HashSet;

/** Optional, reflection-isolated Open Parties and Claims integration. */
public final class ProtectionBridge {
    private static final String MOD_ID = "openpartiesandclaims";
    private static boolean initialized;
    private static boolean failed;
    private static boolean loggedFailure;
    private static Method getApi;
    private static Method getProtection;
    private static Method crossChunkCheck;

    private ProtectionBridge() {
    }

    public static boolean canAffect(ServerLevel level, BlockPos base,
                                    Iterable<BlockPos> affectedPositions) {
        if (!ModList.get().isLoaded(MOD_ID)) return true;
        if (!initialize()) return false;
        try {
            Object api = getApi.invoke(null, level.getServer());
            Object protection = getProtection.invoke(api);
            ChunkPos source = new ChunkPos(base);
            HashSet<Long> checked = new HashSet<>();
            for (BlockPos position : affectedPositions) {
                ChunkPos target = new ChunkPos(position);
                if (!checked.add(target.toLong())) continue;
                boolean protectedAgainst = (boolean) crossChunkCheck.invoke(protection,
                        level, target, level, source, true, true, true);
                if (protectedAgainst) return false;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            fail(exception);
            return false;
        }
    }

    private static synchronized boolean initialize() {
        if (initialized) return !failed;
        initialized = true;
        try {
            Class<?> apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Class<?> protectionClass = Class.forName(
                    "xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
            getApi = apiClass.getMethod("get", net.minecraft.server.MinecraftServer.class);
            getProtection = apiClass.getMethod("getChunkProtection");
            crossChunkCheck = protectionClass.getMethod("onPosAffectedByAnotherPos",
                    ServerLevel.class, ChunkPos.class, ServerLevel.class, ChunkPos.class,
                    boolean.class, boolean.class, boolean.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            fail(exception);
            return false;
        }
    }

    private static void fail(Throwable throwable) {
        failed = true;
        if (!loggedFailure) {
            loggedFailure = true;
            ExtendedPistons.LOGGER.error(
                    "Open Parties and Claims is present but its protection bridge could not initialize; "
                            + "Extended Piston motion is disabled fail-closed", throwable);
        }
    }
}
