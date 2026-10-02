package com.hearthbound.rpg;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * The class a player picks once. It grants a free rank in its skill and a passive talent
 * (tuned in the {@code rpg.classPerks} section of the server config).
 */
public enum PlayerClass {
    WARRIOR(Skill.VALOR, Items.IRON_SWORD, 0xE06A5A),
    ROGUE(Skill.COMMERCE, Items.ENDER_PEARL, 0x8A8FC0),
    CLERIC(Skill.DIPLOMACY, Items.GOLDEN_APPLE, 0xF4E07A),
    RANGER(Skill.SCOUTING, Items.BOW, 0x8DBF5A),
    ARTISAN(Skill.CRAFT, Items.ANVIL, 0xC98B5A),
    NOBLE(Skill.LEADERSHIP, Items.GOLDEN_HELMET, 0x6C8FD6);

    public final Skill bonusSkill;
    public final Item icon;
    public final int color;

    PlayerClass(Skill bonusSkill, Item icon, int color) {
        this.bonusSkill = bonusSkill;
        this.icon = icon;
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("hearthbound.class." + id());
    }

    public Component description() {
        return Component.translatable("hearthbound.class." + id() + ".desc");
    }

    public Component perk() {
        return Component.translatable("hearthbound.class." + id() + ".perk");
    }

    public static PlayerClass byId(String s) {
        if (s == null) return null;
        for (PlayerClass c : values()) if (c.id().equalsIgnoreCase(s)) return c;
        return null;
    }
}
