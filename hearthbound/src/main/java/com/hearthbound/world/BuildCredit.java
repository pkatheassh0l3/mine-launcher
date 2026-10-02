package com.hearthbound.world;

import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.network.Net;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.village.Reputation;
import com.hearthbound.village.Village;
import com.hearthbound.village.VillageData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Villagers appreciate players who build in their village: every few blocks placed there
 * earn reputation and a little XP, up to a daily cap. Removing your own blocks takes the
 * progress back, so placing and breaking the same block does not pay twice.
 */
public final class BuildCredit {
    private static final class Tally {
        long day;
        int count;
        int granted; // reputation already given today
        int best;    // highest count reached today
    }

    private static final Map<UUID, Map<UUID, Tally>> TALLIES = new HashMap<>();

    private BuildCredit() {}

    private static Tally tally(ServerPlayer p, Village v) {
        long day = p.level().getDayTime() / 24000L;
        Tally t = TALLIES.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).computeIfAbsent(v.id, k -> new Tally());
        if (t.day != day) {
            t.day = day;
            t.count = 0;
            t.granted = 0;
            t.best = 0;
        }
        return t;
    }

    public static void placed(ServerPlayer p, Village v) {
        if (!HBConfig.BUILD_REWARDS.get() || p.isCreative()) return;
        Tally t = tally(p, v);
        t.count++;
        Rpg.data(p).blocksBuilt++;
        if (t.count <= t.best) return; // re-placing what was removed today
        t.best = t.count;
        Compat.trigger(p, "build", v, 1);
        int per = HBConfig.BLOCKS_PER_REP.get();
        if (t.best % per == 0 && t.granted < HBConfig.BUILD_REP_DAILY_CAP.get()) {
            t.granted++;
            Reputation.add(p, v, 1, true, false);
            Rpg.addXp(p, (int) Math.max(1, Math.round(per * HBConfig.BUILD_XP_PER_BLOCK.get())), false);
            if (t.granted == 1 || t.granted % 5 == 0) {
                Net.notify(p, Component.translatable("hearthbound.notify.build_praise", v.name), 0xFF6CD68A);
            }
            if (t.granted >= HBConfig.BUILD_REP_DAILY_CAP.get()) {
                Net.notify(p, Component.translatable("hearthbound.notify.build_cap", v.name), 0xFF9AA0AC);
            }
        }
        if (t.best % HBConfig.BUILD_PROSPERITY_EVERY.get() == 0) {
            v.prosperity = Math.min(100, v.prosperity + 1);
            VillageData.get(p.server).setDirty();
        }
    }

    public static void removed(ServerPlayer p, Village v) {
        Tally t = tally(p, v);
        if (t.count > 0) t.count--;
        if (Rpg.data(p).blocksBuilt > 0) Rpg.data(p).blocksBuilt--;
    }

    public static void clear() {
        TALLIES.clear();
    }
}
