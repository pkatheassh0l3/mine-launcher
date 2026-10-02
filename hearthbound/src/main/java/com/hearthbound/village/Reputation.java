package com.hearthbound.village;

import com.hearthbound.config.HBConfig;
import com.hearthbound.data.Culture;
import com.hearthbound.network.Net;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rank;
import com.hearthbound.rpg.Rpg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Reputation changes, with the Diplomacy bonus, culture sharing and rank-up banners. */
public final class Reputation {
    private Reputation() {}

    /**
     * @param bonus   apply Diplomacy (only for gains)
     * @param notify  show a popup
     */
    public static void add(ServerPlayer p, Village v, int amount, boolean bonus, boolean notify) {
        if (amount == 0 || v == null) return;
        PlayerData d = Rpg.data(p);
        int delta = amount;
        if (bonus && amount > 0) delta = (int) Math.round(amount * Rpg.reputationMultiplier(p));
        Rank before = d.rank(v.id);
        d.reputation.put(v.id, d.rep(v.id) + delta);
        d.discovered.add(v.id);
        Rank after = d.rank(v.id);
        if (notify) {
            Net.notify(p, Component.translatable(delta > 0 ? "hearthbound.notify.rep_up" : "hearthbound.notify.rep_down",
                    (delta > 0 ? "+" : "") + delta, v.name), delta > 0 ? 0xFF6CD68A : 0xFFE06A5A);
        }
        if (after != before) {
            Culture c = v.culture();
            Net.banner(p, Component.translatable(after.ordinal() > before.ordinal() ? "hearthbound.banner.rankup" : "hearthbound.banner.rankdown", after.title()),
                    Component.literal(v.name), 0xFF000000 | after.color);
            if (after.ordinal() > before.ordinal()) {
                p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.5f, 1.2f);
            }
            if (after == Rank.HERO && v.lord == null && VillageService.canBeLord(p)) {
                p.displayClientMessage(Component.translatable("hearthbound.lordship.offer", v.name), false);
            }
        }
        if (delta > 0 && bonus && HBConfig.DIPLOMACY.get()) {
            VillageData vd = VillageData.get(p.server);
            for (Village o : Diplomacy.neighbours(vd, v)) {
                if (!d.discovered.contains(o.id)) continue;
                Diplomacy.Relation rel = Diplomacy.relation(vd, v, o);
                if (rel.atWar()) {
                    int loss = (int) Math.floor(delta * HBConfig.ENEMY_SHARE.get());
                    if (loss > 0) d.reputation.put(o.id, d.rep(o.id) - loss);
                } else if (Diplomacy.stance(rel) == Diplomacy.Stance.ALLIED) {
                    int gain = (int) Math.floor(delta * HBConfig.ALLY_SHARE.get());
                    if (gain > 0) d.reputation.put(o.id, d.rep(o.id) + gain);
                }
            }
        }
        if (delta > 0 && bonus && HBConfig.SHARE_REP_WITH_CULTURE.get()) {
            int share = (int) Math.floor(delta * HBConfig.CULTURE_SHARE_RATIO.get());
            if (share > 0) {
                for (Village o : VillageData.get(p.server).all()) {
                    if (o == v || o.culture == null || !o.culture.equals(v.culture) || !d.discovered.contains(o.id)) continue;
                    d.reputation.put(o.id, d.rep(o.id) + share);
                }
            }
        }
    }
}
