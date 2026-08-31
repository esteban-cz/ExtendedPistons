package dev.estyxq.extendedpistons.movement;

import dev.estyxq.extendedpistons.block.entity.PistonPartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/** Only unresolved recovery work is ticked; valid static shaft/head entities stay idle. */
public final class OrphanPartRecovery {
    private static final int CHECKS_PER_TICK = 512;
    private static final Set<Entry> PENDING = new LinkedHashSet<>();

    private OrphanPartRecovery() {
    }

    public static void enqueue(ResourceKey<Level> dimension, BlockPos pos) {
        PENDING.add(new Entry(dimension, pos.immutable()));
    }

    public static void remove(ResourceKey<Level> dimension, BlockPos pos) {
        PENDING.remove(new Entry(dimension, pos));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        int checked = 0;
        Iterator<Entry> iterator = PENDING.iterator();
        while (iterator.hasNext() && checked++ < CHECKS_PER_TICK) {
            Entry entry = iterator.next();
            var level = event.getServer().getLevel(entry.dimension());
            if (level == null || !level.isLoaded(entry.position())) {
                iterator.remove();
                continue;
            }
            if (!(level.getBlockEntity(entry.position()) instanceof PistonPartBlockEntity part)
                    || part.checkOwnership()) {
                iterator.remove();
            }
        }
    }

    private record Entry(ResourceKey<Level> dimension, BlockPos position) {
    }
}
