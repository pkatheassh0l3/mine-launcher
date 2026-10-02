package com.hearthbound.compat;

import com.hearthbound.config.HBConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import java.util.Map;

/**
 * Optional integrations. Every call into another mod goes through here, guarded by
 * {@link #ascension()} so the classes that reference Ascension are never loaded without it.
 */
public final class Compat {
    private static boolean ascension;

    private Compat() {}

    public static void init(IEventBus modBus) {
        ascension = ModList.get().isLoaded("ascension");
        if (ascension) com.hearthbound.compat.ascension.AscensionCompat.init(modBus);
    }

    /** Ascension installed (regardless of the config switch). */
    public static boolean ascension() {
        return ascension;
    }

    /** Ascension installed and the integration enabled in the server config. */
    public static boolean ages() {
        return ascension && HBConfig.ASC_ENABLED.get();
    }

    /** Whether the player reached the given age. Empty/blank ages are always reached. */
    public static boolean ageReached(Player player, String age) {
        if (age == null || age.isBlank() || !ages() || player == null) return true;
        ResourceLocation id = ResourceLocation.tryParse(age);
        if (id == null) return true;
        return com.hearthbound.compat.ascension.AscensionCompat.isAgeUnlocked(player, id);
    }

    /** Display name of an age ("Iron Age"), or the raw id when unknown. */
    public static Component ageName(Player player, String age) {
        if (!ages()) return Component.literal(age);
        ResourceLocation id = ResourceLocation.tryParse(age);
        if (id == null) return Component.literal(age);
        return com.hearthbound.compat.ascension.AscensionCompat.ageName(player, id);
    }

    public static int ageColor(Player player, String age) {
        if (!ages()) return 0xFFE0B25A;
        ResourceLocation id = ResourceLocation.tryParse(age);
        if (id == null) return 0xFFE0B25A;
        return com.hearthbound.compat.ascension.AscensionCompat.ageColor(player, id);
    }

    /** Feeds an Ascension counter objective ({@code hearthbound:<path>}). No-op without Ascension. */
    public static void trigger(net.minecraft.server.level.ServerPlayer player, String path, Object subject, int amount) {
        if (ages()) com.hearthbound.compat.ascension.AscensionCompat.trigger(player, com.hearthbound.Hearthbound.id(path), subject, amount);
    }

    /** Highest character level allowed for this player (by age when enabled). */
    public static int levelCap(Player player) {
        int max = HBConfig.MAX_LEVEL.get();
        if (!ages() || !HBConfig.ASC_LEVEL_CAPS.get()) return max;
        return Math.min(max, bestByAge(player, HBConfig.parseMap(HBConfig.ASC_LEVEL_CAP_LIST.get()), max));
    }

    /** Highest village tier this player can make villages grow to. */
    public static int tierCap(Player player) {
        int max = HBConfig.MAX_TIER.get();
        if (!ages() || !HBConfig.ASC_TIER_CAPS.get()) return max;
        return Math.min(max, bestByAge(player, HBConfig.parseMap(HBConfig.ASC_TIER_CAP_LIST.get()), max));
    }

    public static void recordVillageAge(net.minecraft.server.level.ServerPlayer player) {
        if (ages() && HBConfig.ASC_TIER_CAPS.get())
            com.hearthbound.village.VillageData.get(player.server).recordTierCap(tierCap(player));
    }

    public static int sharedVillageTier(net.minecraft.server.MinecraftServer server) {
        int max = HBConfig.MAX_TIER.get();
        if (!ages() || !HBConfig.ASC_TIER_CAPS.get()) return max;
        int baseline = HBConfig.parseMap(HBConfig.ASC_TIER_CAP_LIST.get()).values().stream()
                .mapToInt(Integer::intValue).min().orElse(max);
        return com.hearthbound.village.VillageData.get(server).sharedTierCap(baseline, max);
    }

    /**
     * The largest value among the ages this player reached. Ages not listed do not count;
     * if the player reached none of the listed ages, the smallest listed value applies.
     */
    private static int bestByAge(Player player, Map<String, Integer> map, int fallback) {
        if (map.isEmpty()) return fallback;
        int best = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            min = Math.min(min, e.getValue());
            if (ageReached(player, e.getKey())) best = Math.max(best, e.getValue());
        }
        return best == Integer.MIN_VALUE ? min : best;
    }

    /** Next age that raises the level cap, for "reach X to level further" hints. */
    public static String nextLevelAge(Player player) {
        if (!ages() || !HBConfig.ASC_LEVEL_CAPS.get()) return null;
        int cap = levelCap(player);
        String best = null;
        int bestVal = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : HBConfig.parseMap(HBConfig.ASC_LEVEL_CAP_LIST.get()).entrySet()) {
            if (e.getValue() > cap && e.getValue() < bestVal && !ageReached(player, e.getKey())) {
                best = e.getKey();
                bestVal = e.getValue();
            }
        }
        return best;
    }
}
