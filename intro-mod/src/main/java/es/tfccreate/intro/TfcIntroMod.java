package es.tfccreate.intro;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Intro cinemática la primera vez que se entra a cada mundo + Códice con una tecla. Solo cliente. */
@Mod(value = TfcIntroMod.MODID, dist = Dist.CLIENT)
public class TfcIntroMod {
    public static final String MODID = "tfccreate_intro";

    /** GLFW_KEY_Y = 89 */
    public static final KeyMapping CODEX_KEY = new KeyMapping(
            "key.tfccreate_intro.codex", InputConstants.Type.KEYSYM, 89, "key.categories.tfccreate_intro");

    private static String pendingWorld = null;
    private static int waitTicks = 0;

    public TfcIntroMod(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(CODEX_KEY));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> onLogin());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> onTick());
    }

    private static void onLogin() {
        String id = worldId();
        if (id != null && !seen().contains(id)) {
            pendingWorld = id;
            waitTicks = 0;
        }
    }

    private static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            pendingWorld = null;
            while (CODEX_KEY.consumeClick()) { }
            return;
        }
        if (pendingWorld != null && mc.screen == null) {
            // deja que el mundo termine de aparecer antes de lanzar la intro
            if (++waitTicks >= 40) {
                markSeen(pendingWorld);
                pendingWorld = null;
                mc.setScreen(new IntroScreen(true));
            }
        }
        while (CODEX_KEY.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new CodexScreen(0));
        }
    }

    // ---- mundos ya vistos -------------------------------------------------------------

    private static Path store() {
        return FMLPaths.CONFIGDIR.get().resolve("tfccreate_intro-vistos.txt");
    }

    private static String worldId() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.getSingleplayerServer() != null) {
                return "sp:" + mc.getSingleplayerServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath().normalize();
            }
            if (mc.getCurrentServer() != null) return "mp:" + mc.getCurrentServer().ip;
        } catch (Exception ignored) { }
        return null;
    }

    private static Set<String> seen() {
        Set<String> s = new HashSet<>();
        try {
            if (Files.exists(store())) s.addAll(Files.readAllLines(store(), StandardCharsets.UTF_8));
        } catch (IOException ignored) { }
        return s;
    }

    private static void markSeen(String id) {
        try {
            Set<String> s = seen();
            if (s.add(id)) Files.write(store(), s, StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }
}
