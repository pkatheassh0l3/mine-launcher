package com.hearthbound.village;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** A settler's job. Decides its workplace, held item, texture and what it offers in the UI. */
public enum Role {
    VILLAGER(Items.AIR, 0x9AA5B1),
    ELDER(Items.BOOK, 0xE8D8A8),
    BUILDER(Items.BRICKS, 0xC98B5A),
    FARMER(Items.IRON_HOE, 0x8DBF5A),
    SMITH(Items.IRON_PICKAXE, 0xB0B7C0),
    MERCHANT(Items.EMERALD, 0x55C47A),
    GUARD(Items.IRON_SWORD, 0x6C8FD6),
    INNKEEPER(Items.HONEY_BOTTLE, 0xE0A040),
    MAGE(Items.AMETHYST_SHARD, 0xB57CFF),
    PRIEST(Items.GOLDEN_APPLE, 0xF4E07A);

    public final Item tool;
    public final int color;

    Role(Item tool, int color) {
        this.tool = tool;
        this.color = color;
    }

    public String key() {
        return "hearthbound.role." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean fights() {
        return this == GUARD;
    }

    public static Role byId(String id) {
        for (Role r : values()) if (r.id().equalsIgnoreCase(id)) return r;
        return VILLAGER;
    }

    public static Role byOrdinal(int i) {
        Role[] v = values();
        return i >= 0 && i < v.length ? v[i] : VILLAGER;
    }
}
