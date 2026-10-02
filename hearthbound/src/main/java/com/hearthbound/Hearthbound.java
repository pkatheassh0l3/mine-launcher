package com.hearthbound;

import com.hearthbound.command.HearthboundCommand;
import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBClientConfig;
import com.hearthbound.config.HBConfig;
import com.hearthbound.data.HBData;
import com.hearthbound.event.CommonEvents;
import com.hearthbound.network.Net;
import com.hearthbound.registry.ModRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hearthbound: Living Villages.
 * <p>
 * Villages of original cultures that settle the world and grow on their own, building
 * block by block. Players earn reputation with each village, take contracts, trade with
 * coins, hire companions and level up a light RPG character (backgrounds and skills).
 * Everything is data-driven (cultures, buildings, contracts, trades) and configurable,
 * with optional age-based progression through Ascension: Vanilla Ages.
 */
@Mod(Hearthbound.MOD_ID)
public final class Hearthbound {
    public static final String MOD_ID = "hearthbound";
    public static final Logger LOGGER = LoggerFactory.getLogger("Hearthbound");

    public Hearthbound(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, HBConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, HBClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, com.hearthbound.config.HBCommonConfig.SPEC);

        ModRegistry.register(modBus);
        modBus.addListener(Net::register);
        modBus.addListener(this::commonSetup);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(this::addReloadListeners);
        bus.addListener(this::registerCommands);
        CommonEvents.register(bus);

        Compat.init(modBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.hearthbound.client.HBClient.init(modBus, container);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Hearthbound ready. Ascension integration: {}", Compat.ascension() ? "enabled" : "not installed");
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new HBData.Loader());
    }

    private void registerCommands(RegisterCommandsEvent event) {
        HearthboundCommand.register(event.getDispatcher());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
