package dev.estyxq.extendedpistons.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

public final class ExtendedStickyPistonBlock extends ExtendedPistonBlock {
    public static final MapCodec<ExtendedStickyPistonBlock> CODEC = simpleCodec(ExtendedStickyPistonBlock::new);

    public ExtendedStickyPistonBlock(Properties properties) {
        super(true, properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
