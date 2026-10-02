package com.hearthbound.client.render;

import com.hearthbound.Hearthbound;
import com.hearthbound.config.HBClientConfig;
import com.hearthbound.entity.SettlerEntity;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Skins the player drops into {@code config/hearthbound/skins/<culture>/<role>/} (or {@code any/}).
 * They are loaded as dynamic textures on every resource reload (F3+T), so any skin downloaded
 * from the internet can be used locally without making a resource pack. 64×32 skins are
 * padded to 64×64.
 */
public final class ClientSkins implements ResourceManagerReloadListener {
    private static final Map<String, List<ResourceLocation>> POOLS = new HashMap<>();
    private static final List<ResourceLocation> REGISTERED = new ArrayList<>();

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("hearthbound").resolve("skins");
    }

    @Override
    public void onResourceManagerReload(ResourceManager rm) {
        load();
    }

    public static void load() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation rl : REGISTERED) mc.getTextureManager().release(rl);
        REGISTERED.clear();
        POOLS.clear();
        Path root = dir();
        try {
            Files.createDirectories(root);
            Path readme = root.resolve("LEEME_README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        Hearthbound - custom settler skins / aspectos propios
                        ====================================================
                        config/hearthbound/skins/<culture>/<role>/*.png   (64x64 or 64x32 Minecraft skins)
                        config/hearthbound/skins/<culture>/any/*.png      (any job)

                        <culture> = valdoran, sylvaran, durnhal, solarys, brumaverde, hrimfell
                        <role>    = villager, elder, builder, farmer, smith, merchant, guard, innkeeper, mage, priest

                        Press F3+T in game to reload. / Pulsa F3+T para recargar.
                        Only visible to you (client side). / Solo lo ves tu (lado cliente).
                        """);
            }
            int n = 0;
            try (Stream<Path> walk = Files.walk(root, 3)) {
                for (Path p : walk.toList()) {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!Files.isRegularFile(p) || !name.endsWith(".png")) continue;
                    Path rel = root.relativize(p);
                    if (rel.getNameCount() != 3) continue;
                    String key = rel.getName(0).toString().toLowerCase(Locale.ROOT) + "/" + rel.getName(1).toString().toLowerCase(Locale.ROOT);
                    try (InputStream in = Files.newInputStream(p)) {
                        NativeImage img = pad(NativeImage.read(in));
                        if (img == null) continue;
                        ResourceLocation rl = Hearthbound.id("dynamic_skin/" + (n++));
                        mc.getTextureManager().register(rl, new DynamicTexture(img));
                        REGISTERED.add(rl);
                        POOLS.computeIfAbsent(key, k -> new ArrayList<>()).add(rl);
                    } catch (Exception ex) {
                        Hearthbound.LOGGER.warn("Skipping skin {}: {}", p, ex.getMessage());
                    }
                }
            }
            if (n > 0) Hearthbound.LOGGER.info("Loaded {} custom settler skins", n);
        } catch (Exception ex) {
            Hearthbound.LOGGER.warn("Could not read {}: {}", root, ex.toString());
        }
    }

    /** Converts a 64×32 legacy skin to 64×64; anything else that is not 64×64 is rejected. */
    private static NativeImage pad(NativeImage src) {
        if (src.getWidth() == 64 && src.getHeight() == 64) return src;
        if (src.getWidth() != 64 || src.getHeight() != 32) {
            src.close();
            return null;
        }
        NativeImage out = new NativeImage(64, 64, true);
        for (int x = 0; x < 64; x++) for (int y = 0; y < 32; y++) out.setPixelRGBA(x, y, src.getPixelRGBA(x, y));
        src.close();
        return out;
    }

    /** A custom skin for this settler, or null. Stable per entity. */
    public static ResourceLocation pick(SettlerEntity e, String role) {
        if (POOLS.isEmpty() || !HBClientConfig.CUSTOM_SKINS.get()) return null;
        String skin = e.skin().toLowerCase(Locale.ROOT);
        List<ResourceLocation> pool = new ArrayList<>(POOLS.getOrDefault(skin + "/" + role, List.of()));
        pool.addAll(POOLS.getOrDefault(skin + "/any", List.of()));
        if (pool.isEmpty()) return null;
        int h = Math.abs(e.getUUID().hashCode());
        double share = HBClientConfig.CUSTOM_SKIN_SHARE.get();
        if ((h % 1000) / 1000.0 >= share) return null;
        return pool.get((h / 1000) % pool.size());
    }
}
