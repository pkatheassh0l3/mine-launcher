package com.hearthbound.client.render;

import com.hearthbound.Hearthbound;
import com.hearthbound.entity.SettlerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Texture priority:
 * <ol>
 *   <li>the player's own skins in {@code config/hearthbound/skins} ({@link ClientSkins})</li>
 *   <li>the skin the server picked from the culture's pool ({@code "skins"} in the culture file)</li>
 *   <li>{@code textures/entity/settler/<skin>/<role>_<m|f>.png}, then the culture's villager, then Valdor's</li>
 * </ol>
 */
public class SettlerRenderer extends HumanoidMobRenderer<SettlerEntity, SettlerModel> {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    public SettlerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SettlerModel(ctx.bakeLayer(SettlerModel.LAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(SettlerEntity e) {
        String role = e.isCompanion() ? "guard" : e.role().id();
        ResourceLocation custom = ClientSkins.pick(e, role);
        if (custom != null) return custom;
        String chosen = e.texture();
        if (!chosen.isEmpty()) {
            ResourceLocation rl = CACHE.computeIfAbsent("tex:" + chosen, k -> {
                ResourceLocation p = ResourceLocation.tryParse(chosen);
                return p != null && exists(p) ? p : null;
            });
            if (rl != null) return rl;
        }
        String g = e.female() ? "f" : "m";
        String key = e.skin() + "/" + role + "_" + g;
        return CACHE.computeIfAbsent(key, k -> {
            ResourceLocation rl = Hearthbound.id("textures/entity/settler/" + k + ".png");
            if (exists(rl)) return rl;
            ResourceLocation alt = Hearthbound.id("textures/entity/settler/" + e.skin() + "/villager_" + g + ".png");
            if (exists(alt)) return alt;
            return Hearthbound.id("textures/entity/settler/valdoran/" + role + "_" + g + ".png");
        });
    }

    private static boolean exists(ResourceLocation rl) {
        return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
