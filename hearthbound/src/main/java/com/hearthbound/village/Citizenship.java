package com.hearthbound.village;

import com.hearthbound.config.HBConfig;
import com.hearthbound.network.Net;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rank;
import com.hearthbound.rpg.Rpg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * A player may be a citizen of one village: cheaper prices there, more trust from its building
 * projects, respawning at its hearth when they have no bed, and its banner next to their name.
 */
public final class Citizenship {
    private Citizenship() {}

    public static boolean isCitizen(Player p, Village v) {
        PlayerData d = Rpg.data(p);
        return v != null && d.citizen != null && d.citizen.equals(v.id);
    }

    public static Village home(ServerPlayer p) {
        java.util.UUID id = Rpg.data(p).citizen;
        return id == null ? null : VillageData.get(p.server).get(id);
    }

    /** Price multiplier for purchases in this village. */
    public static double buyFactor(Player p, Village v) {
        return isCitizen(p, v) ? 1 - HBConfig.CITIZEN_DISCOUNT.get() : 1;
    }

    public static Rank rankNeeded() {
        return Rank.byId(HBConfig.CITIZEN_RANK.get());
    }

    /** Why the player can't become a citizen now (null if they can). */
    public static Component lock(ServerPlayer p, Village v) {
        if (!HBConfig.CITIZENSHIP.get()) return Component.translatable("hearthbound.citizen.disabled");
        PlayerData d = Rpg.data(p);
        if (!d.rank(v.id).atLeast(rankNeeded())) return Component.translatable("hearthbound.gate.rank", rankNeeded().title());
        long day = p.serverLevel().getDayTime() / 24000L;
        int wait = HBConfig.CITIZEN_COOLDOWN_DAYS.get();
        if (d.citizenSince >= 0 && day - d.citizenSince < wait) {
            return Component.translatable("hearthbound.citizen.cooldown", wait - (day - d.citizenSince));
        }
        return null;
    }

    public static void join(ServerPlayer p, Village v) {
        if (isCitizen(p, v) || lock(p, v) != null) {
            VillageService.deny(p);
            return;
        }
        PlayerData d = Rpg.data(p);
        VillageData data = VillageData.get(p.server);
        Village old = d.citizen == null ? null : data.get(d.citizen);
        if (old != null) {
            old.citizens.remove(p.getUUID());
            Reputation.add(p, old, -HBConfig.CITIZEN_LEAVE_REP.get(), false, true);
        }
        d.citizen = v.id;
        d.citizenSince = p.serverLevel().getDayTime() / 24000L;
        v.citizens.put(p.getUUID(), p.getGameProfile().getName());
        data.setDirty();
        Flags.ensure(v);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.1f);
        Net.banner(p, Component.translatable("hearthbound.citizen.welcome", v.name), Component.translatable("hearthbound.citizen.welcome_sub"), 0xFF000000 | Flags.base(v).getTextColor());
        refreshNames(p);
        Rpg.sync(p);
        VillageService.refresh(p, v);
    }

    public static void renounce(ServerPlayer p, Village v) {
        if (!isCitizen(p, v)) return;
        PlayerData d = Rpg.data(p);
        d.citizen = null;
        d.citizenSince = p.serverLevel().getDayTime() / 24000L;
        v.citizens.remove(p.getUUID());
        Reputation.add(p, v, -HBConfig.CITIZEN_LEAVE_REP.get(), false, true);
        VillageData.get(p.server).setDirty();
        Net.notify(p, Component.translatable("hearthbound.citizen.left", v.name), 0xFFE06A5A);
        refreshNames(p);
        Rpg.sync(p);
        VillageService.refresh(p, v);
    }

    public static void refreshNames(ServerPlayer p) {
        p.refreshDisplayName();
        p.refreshTabListName();
    }

    // ================================================================== events

    /** Citizens without a (working) bed or anchor wake up at their village's hearth. */
    public static void respawn(net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent e) {
        if (e.isFromEndFight() || !(e.getEntity() instanceof ServerPlayer p) || !HBConfig.CITIZEN_RESPAWN.get()) return;
        if (p.getRespawnPosition() != null && !e.getDimensionTransition().missingRespawnBlock()) return;
        Village v = home(p);
        if (v == null) return;
        ServerLevel level = p.server.getLevel(v.dimension);
        if (level == null) return;
        int x = v.center.getX() + 2, z = v.center.getZ() + 2;
        level.getChunk(x >> 4, z >> 4); // make sure it is loaded
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        e.setDimensionTransition(new net.minecraft.world.level.portal.DimensionTransition(level, new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5),
                net.minecraft.world.phys.Vec3.ZERO, p.getYRot(), 0f, net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
        pendingMessage.add(p.getUUID());
    }

    private static final java.util.Set<java.util.UUID> pendingMessage = new java.util.HashSet<>();

    /** Tells the citizen where they woke up (after the respawn is done). */
    public static void respawned(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !pendingMessage.remove(p.getUUID())) return;
        Village v = home(p);
        if (v != null) p.displayClientMessage(Component.translatable("hearthbound.citizen.respawn", v.name).withColor(0xE0B25A), true);
    }

    private static Component prefix(Player p) {
        if (!(p instanceof ServerPlayer sp) || !HBConfig.CITIZEN_NAME_FLAG.get()) return null;
        Village v = home(sp);
        if (v == null) return null;
        return Component.literal("⚑ ").withColor(Flags.base(v).getTextColor());
    }

    public static void nameFormat(PlayerEvent.NameFormat e) {
        Component pre = prefix(e.getEntity());
        if (pre != null) e.setDisplayname(Component.empty().append(pre).append(e.getDisplayname()));
    }

    public static void tabName(PlayerEvent.TabListNameFormat e) {
        Component pre = prefix(e.getEntity());
        if (pre == null) return;
        Component base = e.getDisplayName() != null ? e.getDisplayName() : e.getEntity().getName();
        e.setDisplayName(Component.empty().append(pre).append(base));
    }
}
