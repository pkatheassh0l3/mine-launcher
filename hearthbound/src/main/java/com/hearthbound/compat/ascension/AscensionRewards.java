package com.hearthbound.compat.ascension;

import com.ascension.api.AscensionAPI;
import com.ascension.api.Reward;
import com.google.gson.JsonObject;
import com.hearthbound.Hearthbound;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.rpg.Wallet;
import com.hearthbound.village.Village;
import com.hearthbound.village.VillageData;
import com.hearthbound.village.Reputation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Reward types for Ascension quests:
 * {@code hearthbound:coins} ({@code amount} copper), {@code hearthbound:rpg_xp} ({@code amount}),
 * {@code hearthbound:skill_point} ({@code amount}) and {@code hearthbound:reputation}
 * ({@code amount} with every village the player knows).
 */
public final class AscensionRewards {
    private AscensionRewards() {}

    static int amount(JsonObject j, int def) {
        return j.has("amount") ? j.get("amount").getAsInt() : def;
    }

    public static void register() {
        AscensionAPI.registerReward(Hearthbound.id("coins"), (t, j, text) -> new Simple(text, amount(j, 10), "coins", new ItemStack(ModRegistry.SILVER_COIN.get())) {
            @Override
            public void grant(ServerPlayer p) {
                Wallet.give(p, value);
            }
        });
        AscensionAPI.registerReward(Hearthbound.id("rpg_xp"), (t, j, text) -> new Simple(text, amount(j, 50), "rpg_xp", new ItemStack(Items.EXPERIENCE_BOTTLE)) {
            @Override
            public void grant(ServerPlayer p) {
                Rpg.addXp(p, value, false);
            }
        });
        AscensionAPI.registerReward(Hearthbound.id("skill_point"), (t, j, text) -> new Simple(text, amount(j, 1), "skill_point", new ItemStack(Items.NETHER_STAR)) {
            @Override
            public void grant(ServerPlayer p) {
                Rpg.data(p).skillPoints += value;
                Rpg.sync(p);
            }
        });
        AscensionAPI.registerReward(Hearthbound.id("reputation"), (t, j, text) -> new Simple(text, amount(j, 20), "reputation", new ItemStack(Items.EMERALD)) {
            @Override
            public void grant(ServerPlayer p) {
                VillageData vd = VillageData.get(p.server);
                for (UUID id : new ArrayList<>(Rpg.data(p).discovered)) {
                    Village v = vd.get(id);
                    if (v != null) Reputation.add(p, v, value, false, false);
                }
                Rpg.sync(p);
            }
        });
    }

    abstract static class Simple extends Reward {
        final int value;
        final String kind;
        final ItemStack icon;

        Simple(String text, int value, String kind, ItemStack icon) {
            super(text);
            this.value = value;
            this.kind = kind;
            this.icon = icon;
        }

        @Override
        protected Component describe() {
            return Component.translatable("hearthbound.reward." + kind, value);
        }

        @Override
        public ItemStack icon() {
            return icon;
        }
    }
}
