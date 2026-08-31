package dev.estyxq.extendedpistons.registry;

import dev.estyxq.extendedpistons.ExtendedPistons;
import dev.estyxq.extendedpistons.item.PistonPathToolItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExtendedPistons.MOD_ID);

    public static final DeferredItem<BlockItem> EXTENDED_PISTON = ITEMS.register(
            "extended_piston", () -> new BlockItem(ModBlocks.EXTENDED_PISTON.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> EXTENDED_STICKY_PISTON = ITEMS.register(
            "extended_sticky_piston", () -> new BlockItem(ModBlocks.EXTENDED_STICKY_PISTON.get(), new Item.Properties()));
    public static final DeferredItem<PistonPathToolItem> PISTON_PATH_TOOL = ITEMS.register(
            "piston_path_tool", () -> new PistonPathToolItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
