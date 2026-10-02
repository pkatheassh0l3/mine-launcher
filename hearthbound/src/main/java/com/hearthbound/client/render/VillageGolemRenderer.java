package com.hearthbound.client.render;

import com.hearthbound.Hearthbound;
import com.hearthbound.entity.VillageGolemEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

public final class VillageGolemRenderer extends IronGolemRenderer {
    private static final ResourceLocation[] TEXTURES = {
            texture("stone"), texture("mechanical"), texture("warden"), texture("snow"), texture("furnace")
    };

    public VillageGolemRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation texture(String name) {
        return Hearthbound.id("textures/entity/golem/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return TEXTURES[entity instanceof VillageGolemEntity golem ? Math.floorMod(golem.variant(), TEXTURES.length) : 0];
    }
}
