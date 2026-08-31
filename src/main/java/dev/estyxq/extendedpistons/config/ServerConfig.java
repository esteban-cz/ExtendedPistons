package dev.estyxq.extendedpistons.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue EXTENDED_PISTON_PUSH_LIMIT;
    public static final ModConfigSpec.IntValue TICKS_PER_SEGMENT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("movement");
        EXTENDED_PISTON_PUSH_LIMIT = builder
                .comment("Maximum number of blocks an Extended Piston may move in one step. This does not limit path length.")
                .translation("extendedpistons.config.extendedPistonPushLimit")
                .defineInRange("extendedPistonPushLimit", 12, 1, Integer.MAX_VALUE);
        TICKS_PER_SEGMENT = builder
                .comment("Number of game ticks used to traverse one configured path segment.")
                .translation("extendedpistons.config.ticksPerSegment")
                .defineInRange("ticksPerSegment", 4, 1, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }
}
