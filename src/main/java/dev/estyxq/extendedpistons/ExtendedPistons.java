package dev.estyxq.extendedpistons;

import com.mojang.logging.LogUtils;
import dev.estyxq.extendedpistons.config.ServerConfig;
import dev.estyxq.extendedpistons.network.ExtendedNetwork;
import dev.estyxq.extendedpistons.network.PathTransferManager;
import dev.estyxq.extendedpistons.movement.OrphanPartRecovery;
import dev.estyxq.extendedpistons.registry.ModBlockEntities;
import dev.estyxq.extendedpistons.registry.ModBlocks;
import dev.estyxq.extendedpistons.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(ExtendedPistons.MOD_ID)
public final class ExtendedPistons {
    public static final String MOD_ID = "extendedpistons";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExtendedPistons(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ExtendedNetwork::register);
        modBus.addListener(this::addCreativeTabContents);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MOD_ID + "-server.toml");

        NeoForge.EVENT_BUS.addListener(PathTransferManager::onChunkSent);
        NeoForge.EVENT_BUS.addListener(PathTransferManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(PathTransferManager::onLogout);
        NeoForge.EVENT_BUS.addListener(OrphanPartRecovery::onServerTick);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            dev.estyxq.extendedpistons.client.ClientEvents.init(modBus);
        }
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.EXTENDED_PISTON.get());
            event.accept(ModItems.EXTENDED_STICKY_PISTON.get());
            event.accept(ModItems.PISTON_PATH_TOOL.get());
        }
    }
}
