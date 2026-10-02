package com.hearthbound.compat.ascension;

import com.ascension.api.AscensionAPI;
import com.ascension.api.event.AgeUnlockedEvent;
import com.ascension.data.Age;
import com.ascension.data.ProgressionData;
import com.hearthbound.Hearthbound;
import com.hearthbound.rpg.Rpg;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Only loaded when Ascension is installed. */
public final class AscensionCompat {
    private AscensionCompat() {}

    public static void init(IEventBus modBus) {
        AscensionObjectives.register();
        AscensionRewards.register();
        NeoForge.EVENT_BUS.addListener(AscensionCompat::onAge);
        Hearthbound.LOGGER.info("Ascension detected: registered Hearthbound objectives and rewards");
    }

    private static void onAge(AgeUnlockedEvent event) {
        ServerPlayer p = event.getPlayer();
        Rpg.onCapRaised(p);
        com.hearthbound.compat.Compat.recordVillageAge(p);
    }

    public static boolean isAgeUnlocked(Player player, ResourceLocation age) {
        try {
            return AscensionAPI.isAgeUnlocked(player, age);
        } catch (Throwable t) {
            return true;
        }
    }

    private static Age age(Player player, ResourceLocation id) {
        try {
            ProgressionData d = player != null ? ProgressionData.of(player) : ProgressionData.SERVER;
            return d == null ? null : d.age(id);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Component ageName(Player player, ResourceLocation id) {
        Age a = age(player, id);
        return a == null ? Component.literal(id.getPath()) : a.title();
    }

    public static int ageColor(Player player, ResourceLocation id) {
        Age a = age(player, id);
        return a == null ? 0xFFE0B25A : (0xFF000000 | a.color);
    }

    public static void trigger(ServerPlayer player, ResourceLocation type, Object subject, int amount) {
        try {
            AscensionAPI.trigger(player, type, subject, amount);
        } catch (Throwable ignored) {
        }
    }
}
