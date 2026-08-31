package dev.estyxq.extendedpistons.compat;

import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.Set;

public final class CompatibilityGuard {
    private static final String MOONLIGHT_PISTON_CALLBACK =
            "net.mehvahdjukaar.moonlight.api.block.IPistonMotionReact";

    private CompatibilityGuard() {
    }

    public static boolean canMove(Block block) {
        return !implementsNamedInterface(block.getClass(), MOONLIGHT_PISTON_CALLBACK, new HashSet<>());
    }

    private static boolean implementsNamedInterface(Class<?> type, String interfaceName,
                                                     Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) return false;
        for (Class<?> implemented : type.getInterfaces()) {
            if (implemented.getName().equals(interfaceName)
                    || implementsNamedInterface(implemented, interfaceName, visited)) return true;
        }
        return implementsNamedInterface(type.getSuperclass(), interfaceName, visited);
    }
}
