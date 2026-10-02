package com.hearthbound.rpg;

import com.hearthbound.config.HBConfig;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Reputation ranks with a village. Thresholds come from the server config. */
public enum Rank {
    HOSTILE(0xD04040),
    WARY(0xD08A40),
    STRANGER(0xB8B8B8),
    ACQUAINTANCE(0x9FD0FF),
    FRIEND(0x6CD68A),
    ALLY(0x5AB0FF),
    HERO(0xFFD24A);

    public final int color;

    Rank(int color) {
        this.color = color;
    }

    public Component title() {
        return Component.translatable("hearthbound.rank." + name().toLowerCase(Locale.ROOT));
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean atLeast(Rank other) {
        return ordinal() >= other.ordinal();
    }

    /** Minimum reputation of this rank (HOSTILE = Integer.MIN_VALUE). */
    public int threshold() {
        if (this == HOSTILE) return Integer.MIN_VALUE;
        List<? extends Integer> t = HBConfig.RANK_THRESHOLDS.get();
        int i = ordinal() - 1;
        if (i < t.size()) return t.get(i);
        return defaults()[i];
    }

    private static int[] defaults() {
        return new int[]{-100, 0, 50, 150, 400, 1000};
    }

    public static Rank of(int rep) {
        Rank out = HOSTILE;
        for (Rank r : values()) if (rep >= r.threshold()) out = r;
        return out;
    }

    public Rank next() {
        return this == HERO ? HERO : values()[ordinal() + 1];
    }

    public static Rank byId(String s) {
        for (Rank r : values()) if (r.id().equalsIgnoreCase(s)) return r;
        return STRANGER;
    }
}
