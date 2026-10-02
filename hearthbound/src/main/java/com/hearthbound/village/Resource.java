package com.hearthbound.village;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Abstract village resources. Keeps the economy simple: no item logistics, just four pools. */
public enum Resource {
    FOOD(Items.BREAD, 0xE0B050),
    WOOD(Items.OAK_LOG, 0xA87B4F),
    STONE(Items.COBBLESTONE, 0x9EA3A8),
    GOODS(Items.IRON_INGOT, 0x7FB8E6);

    public final Item icon;
    public final int color;

    Resource(Item icon, int color) {
        this.icon = icon;
        this.color = color;
    }

    public String key() {
        return "hearthbound.resource." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Resource byId(String s) {
        for (Resource r : values()) if (r.id().equalsIgnoreCase(s)) return r;
        return null;
    }
}
