package com.hearthbound.rpg;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Character skills, raised with skill points earned on level up. Effects are configured in the server config. */
public enum Skill {
    VALOR(Items.IRON_SWORD, 0xE06A5A),
    COMMERCE(Items.GOLD_INGOT, 0xF0C850),
    DIPLOMACY(Items.WRITABLE_BOOK, 0x6CD6C8),
    LEADERSHIP(Items.WHITE_BANNER, 0x6C8FD6),
    CRAFT(Items.ANVIL, 0xC98B5A),
    SCOUTING(Items.SPYGLASS, 0x8DBF5A);

    public final Item icon;
    public final int color;

    Skill(Item icon, int color) {
        this.icon = icon;
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("hearthbound.skill." + id());
    }

    public Component description() {
        return Component.translatable("hearthbound.skill." + id() + ".desc");
    }

    public static Skill byId(String s) {
        for (Skill k : values()) if (k.id().equalsIgnoreCase(s)) return k;
        return null;
    }
}
