package com.hearthbound.client;

import com.hearthbound.Hearthbound;
import com.hearthbound.client.gui.CharacterScreen;
import com.hearthbound.client.render.SettlerModel;
import com.hearthbound.client.render.SettlerRenderer;
import com.hearthbound.config.HBClientConfig;
import com.hearthbound.entity.SettlerEntity;
import com.hearthbound.registry.ModRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client bootstrap: renderers, keys, HUD layers and the config screen. */
public final class HBClient {
    public static final KeyMapping CHARACTER = new KeyMapping("key.hearthbound.character", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.hearthbound");

    private HBClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(HBClient::renderers);
        modBus.addListener(HBClient::layers);
        modBus.addListener(HBClient::keys);
        modBus.addListener(HBClient::gui);
        modBus.addListener(HBClient::configLoad);
        modBus.addListener(HBClient::configReload);
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent.class,
                e -> e.registerReloadListener(new com.hearthbound.client.render.ClientSkins()));
        NeoForge.EVENT_BUS.addListener(HBClient::tick);
        NeoForge.EVENT_BUS.addListener(HBClient::logout);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.VILLAGE_GOLEM.get(), com.hearthbound.client.render.VillageGolemRenderer::new);
        e.registerEntityRenderer(ModRegistry.SETTLER.get(), SettlerRenderer::new);
    }

    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(SettlerModel.LAYER, SettlerModel::create);
    }

    private static void keys(RegisterKeyMappingsEvent e) {
        e.register(CHARACTER);
    }

    private static void gui(RegisterGuiLayersEvent e) {
        e.registerAboveAll(Hearthbound.id("hud"), Hud::render);
    }

    private static void configLoad(ModConfigEvent.Loading e) {
        applyConfig(e);
    }

    private static void configReload(ModConfigEvent.Reloading e) {
        applyConfig(e);
    }

    private static void applyConfig(ModConfigEvent e) {
        if (e.getConfig().getSpec() == HBClientConfig.SPEC) {
            try {
                SettlerEntity.showRoleInName = HBClientConfig.SHOW_ROLE_IN_NAME.get();
            } catch (Exception ignored) {
            }
        }
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        while (CHARACTER.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            // ON_KEY: first press shows the tracked quest, a second press opens the character sheet
            if (HBClientConfig.TRACKER_MODE.get() == HBClientConfig.TrackerMode.ON_KEY && HBClientConfig.TRACKER.get()
                    && Hud.hasTracked() && !Hud.trackerShown()) {
                Hud.showTracker();
            } else {
                Hud.hideTracker();
                mc.setScreen(new CharacterScreen());
            }
        }
    }

    private static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientData.reset();
        Hud.leftVillage();
        SettlerRenderer.clearCache();
    }
}
