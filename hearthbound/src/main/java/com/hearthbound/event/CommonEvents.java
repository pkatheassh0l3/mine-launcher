package com.hearthbound.event;

import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.data.Culture;
import com.hearthbound.entity.SettlerEntity;
import com.hearthbound.network.Net;
import com.hearthbound.rpg.PlayerClass;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.village.Contracts;
import com.hearthbound.village.PlacedBuilding;
import com.hearthbound.village.Reputation;
import com.hearthbound.village.Village;
import com.hearthbound.village.VillageData;
import com.hearthbound.village.VillageManager;
import com.hearthbound.village.VillageService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CommonEvents {
    /** Village each player is currently standing in (for the entry banner). */
    private static final Map<UUID, UUID> CURRENT = new HashMap<>();
    private static final Map<UUID, Long> HURT_COOLDOWN = new HashMap<>();

    private CommonEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(VillageManager::onServerTick);
        bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.hearthbound.village.VillageRepairs::onExplosion);
        bus.addListener(CommonEvents::playerTick);
        bus.addListener(CommonEvents::login);
        bus.addListener(CommonEvents::respawn);
        bus.addListener(com.hearthbound.village.Citizenship::respawn);
        bus.addListener(com.hearthbound.village.Citizenship::respawned);
        bus.addListener(com.hearthbound.village.Citizenship::nameFormat);
        bus.addListener(com.hearthbound.village.Citizenship::tabName);
        bus.addListener(CommonEvents::dimension);
        bus.addListener(CommonEvents::death);
        bus.addListener(CommonEvents::incomingDamage);
        bus.addListener(CommonEvents::blockBreak);
        bus.addListener(CommonEvents::blockPlace);
        bus.addListener(CommonEvents::joinLevel);
        bus.addListener(CommonEvents::converted);
        bus.addListener(com.hearthbound.village.VanillaReplacement::onJoin);
        bus.addListener(CommonEvents::stopped);
    }

    private static void stopped(ServerStoppedEvent e) {
        VillageManager.clearCaches();
        CURRENT.clear();
        HURT_COOLDOWN.clear();
        com.hearthbound.world.BuildCredit.clear();
    }

    // ------------------------------------------------------------------ players

    private static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Rpg.applyAttributes(p);
            Rpg.sync(p);
            if (Rpg.data(p).clazz == null) {
                Net.banner(p, Component.translatable("hearthbound.welcome.title"), Component.translatable("hearthbound.welcome.sub"), 0xFFE0B25A);
            }
        }
    }

    private static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Rpg.applyAttributes(p);
            Rpg.sync(p);
        }
    }

    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Rpg.applyAttributes(p);
            Rpg.sync(p);
        }
    }

    private static void playerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        ServerLevel level = p.serverLevel();
        Village v = VillageData.get(p.server).at(level, p.blockPosition());
        UUID now = v == null ? null : v.id;
        UUID before = CURRENT.get(p.getUUID());
        if (now != null && !now.equals(before)) {
            VillageService.discover(p, v);
            Contracts.onEnterVillage(p, v);
            PlayerData d = Rpg.data(p);
            Culture c = v.culture();
            CompoundTag t = new CompoundTag();
            t.putString("name", v.name);
            t.putString("culture", c == null ? "" : c.name);
            t.putInt("color", c == null ? 0xFFE0B25A : c.color);
            t.putString("rank", d.rank(v.id).id());
            t.putInt("tier", v.tier());
            t.putString("lord", v.lordName == null ? "" : v.lordName);
            Net.send(p, "enter", t);
        }
        if (now == null && before != null) Net.send(p, "leave", new CompoundTag());
        if (now == null) CURRENT.remove(p.getUUID());
        else CURRENT.put(p.getUUID(), now);
        if (p.tickCount % 200 == 0) Contracts.expire(p);
    }

    // ------------------------------------------------------------------ combat

    private static void incomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof SettlerEntity s) || s.isCompanion() || s.isWarband() || s.isTraveler()) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || p.isCreative()) return;
        long t = p.level().getGameTime();
        Long last = HURT_COOLDOWN.get(s.getUUID());
        if (last != null && t - last < 40) return;
        HURT_COOLDOWN.put(s.getUUID(), t);
        Village v = VillageData.get(p.server).get(s.villageId());
        if (v != null) Reputation.add(p, v, -HBConfig.HURT_SETTLER_REP.get(), false, true);
    }

    private static void death(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        Entity killerEntity = e.getSource().getEntity();
        ServerPlayer killer = killerEntity instanceof ServerPlayer sp ? sp : null;
        if (killerEntity instanceof SettlerEntity comp && comp.isCompanion() && comp.owner() != null
                && level.getPlayerByUUID(comp.owner()) instanceof ServerPlayer owner) {
            killer = owner; // companions' kills count for their owner
        }

        if (victim instanceof SettlerEntity s && s.isWarband()) {
            com.hearthbound.village.Diplomacy.raiderDied(level.getServer(), s);
            if (killer != null) {
                VillageData vd = VillageData.get(level.getServer());
                Village attacker = vd.get(s.villageId());
                Village defended = vd.get(s.warTarget());
                if (attacker != null) Reputation.add(killer, attacker, -HBConfig.WAR_KILL_PENALTY.get(), false, true);
                if (defended != null) {
                    Reputation.add(killer, defended, HBConfig.DEFEND_REP.get() * 3, true, true);
                    Rpg.addXp(killer, HBConfig.XP_DEFEND.get() * 3, false);
                    Rpg.data(killer).defended++;
                    Compat.trigger(killer, "defend", victim, 1);
                    Contracts.onKill(killer, victim, defended);
                }
            }
            return;
        }
        if (victim instanceof SettlerEntity s) {
            if ((!s.isCompanion() || s.isFollower()) && !s.isTraveler()) {
                VillageManager.onSettlerDeath(level, s);
                if (killer != null) {
                    Village v = VillageData.get(level.getServer()).get(s.villageId());
                    if (v != null) Reputation.add(killer, v, -HBConfig.KILL_SETTLER_REP.get(), false, true);
                }
            }
            return;
        }

        Village inVillage = VillageData.get(level.getServer()).at(level, victim.blockPosition());
        boolean raider = victim.getTags().contains(VillageManager.RAIDER_TAG);
        boolean countsForRaid = true;
        if (raider && victim.getPersistentData().hasUUID(VillageManager.RAID_VILLAGE_KEY)) {
            Village home = VillageData.get(level.getServer()).get(victim.getPersistentData().getUUID(VillageManager.RAID_VILLAGE_KEY));
            if (home != null) inVillage = home;
            countsForRaid = victim instanceof net.minecraft.world.entity.Mob vm && VillageManager.inCurrentRaid(vm, home);
        }
        if (raider && inVillage == null) {
            inVillage = VillageData.get(level.getServer()).nearest(level, victim.blockPosition(), 128);
        }
        if (raider && countsForRaid && inVillage != null && inVillage.raidRemaining > 0) {
            inVillage.raidRemaining--;
            if (inVillage.raidRemaining == 0) raidRepelled(level, inVillage);
        }
        if (killer == null) return;
        Contracts.onKill(killer, victim, inVillage);
        boolean hostile = victim.getType().getCategory() == MobCategory.MONSTER;
        if (inVillage != null && hostile) {
            PlayerData d = Rpg.data(killer);
            double perk = d.clazz == PlayerClass.WARRIOR ? 1 + HBConfig.PERK_WARRIOR_DEFEND.get() : 1;
            int rep = (int) Math.round(HBConfig.DEFEND_REP.get() * perk * (raider ? 2 : 1));
            Reputation.add(killer, inVillage, rep, true, false);
            Rpg.addXp(killer, (int) Math.round(HBConfig.XP_DEFEND.get() * perk * (raider ? 2 : 1)), false);
            d.defended++;
            Compat.trigger(killer, "defend", victim, 1);
        }
    }

    private static void raidRepelled(ServerLevel level, Village v) {
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(v.center) < (double) (v.radius() + 64) * (v.radius() + 64)) {
                Net.banner(p, Component.translatable("hearthbound.raid.repelled"), Component.literal(v.name), 0xFF6CD68A);
                Reputation.add(p, v, v.raidPillagers ? 25 : 15, true, true);
                Rpg.addXp(p, v.raidPillagers ? 70 : 40, true);
                if (v.raidPillagers) com.hearthbound.rpg.Wallet.give(p, 30);
            }
        }
        v.prosperity = Math.min(100, v.prosperity + 5);
        v.raidEnds = 0;
        VillageData.get(level.getServer()).setDirty();
    }

    // ------------------------------------------------------------------ world

    /** Remembers player blocks (villages never touch them) and rewards building inside villages. */
    private static void blockPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        java.util.List<net.minecraft.core.BlockPos> positions = new java.util.ArrayList<>();
        if (e instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (var snap : multi.getReplacedBlockSnapshots()) positions.add(snap.getPos());
        } else {
            positions.add(e.getPos());
        }
        for (var pos : positions) com.hearthbound.world.PlayerBuilds.mark(level, pos);
        if (e.getPlacedBlock().is(com.hearthbound.world.PlayerBuilds.NO_BUILD_CREDIT)) return;
        Village v = VillageData.get(p.server).at(level, e.getPos());
        if (v != null) com.hearthbound.world.BuildCredit.placed(p, v);
    }

    private static void blockBreak(BlockEvent.BreakEvent e) {
        Player player = e.getPlayer();
        if (!(player instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        boolean own = com.hearthbound.world.PlayerBuilds.unmark(level, e.getPos());
        Village v = VillageData.get(p.server).at(level, e.getPos());
        if (own) {
            if (v != null) com.hearthbound.world.BuildCredit.removed(p, v);
            return;
        }
        if (!HBConfig.PROTECT_BUILDINGS.get() || p.isCreative()) return;
        if (v == null || p.getUUID().equals(v.lord)) return;
        PlacedBuilding b = v.buildingAt(e.getPos());
        if (b == null) return;
        Reputation.add(p, v, -HBConfig.BREAK_BLOCK_REP.get(), false, false);
        p.displayClientMessage(Component.translatable("hearthbound.warn.break", v.name).withColor(0xE06A5A), true);
    }

    /** Raid mobs that turn into something else (zombie into drowned...) stay part of the raid. */
    private static void converted(net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post e) {
        LivingEntity from = e.getEntity(), to = e.getOutcome();
        if (!from.getTags().contains(VillageManager.RAIDER_TAG)) return;
        to.addTag(VillageManager.RAIDER_TAG);
        var src = from.getPersistentData();
        if (src.hasUUID(VillageManager.RAID_VILLAGE_KEY)) to.getPersistentData().putUUID(VillageManager.RAID_VILLAGE_KEY, src.getUUID(VillageManager.RAID_VILLAGE_KEY));
        if (src.contains(VillageManager.RAID_ID_KEY)) to.getPersistentData().putLong(VillageManager.RAID_ID_KEY, src.getLong(VillageManager.RAID_ID_KEY));
        if (to instanceof net.minecraft.world.entity.Mob m) m.setPersistenceRequired();
    }

    /**
     * Mobs that attack vanilla villagers (zombies, illagers, ravagers), raid mobs and the ones listed
     * in the config also hunt settlers. Other monsters leave them alone unless they are provoked.
     */
    private static void joinLevel(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (e.getEntity() instanceof Monster m && SettlerEntity.huntsSettlers(m)) {
            m.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(m, SettlerEntity.class, true, s -> !((SettlerEntity) s).isCompanion() || m.getTags().contains(VillageManager.RAIDER_TAG)));
        }
        // raid mobs: hunt settlers without needing to see them and march from house to house
        if (e.getEntity() instanceof net.minecraft.world.entity.PathfinderMob m && m.getTags().contains(VillageManager.RAIDER_TAG)
                && m.getPersistentData().hasUUID(VillageManager.RAID_VILLAGE_KEY) && e.getLevel() instanceof ServerLevel sl) {
            java.util.UUID vid = m.getPersistentData().getUUID(VillageManager.RAID_VILLAGE_KEY);
            if (!VillageManager.inCurrentRaid(m, VillageData.get(sl.getServer()).get(vid))) {
                e.setCanceled(true); // straggler of a raid that is over
                return;
            }
            // illagers out of a vanilla raid would otherwise "hold ground" and never close in
            m.goalSelector.removeAllGoals(g -> g.getClass().getName().endsWith("HoldGroundAttackGoal"));
            m.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(m, SettlerEntity.class, 5, false, false,
                    s -> !((SettlerEntity) s).isTraveler() && vid.equals(((SettlerEntity) s).villageId())));
            m.goalSelector.addGoal(4, new com.hearthbound.village.RaidVillageGoal(m, vid, m instanceof net.minecraft.world.entity.raid.Raider));
        }
    }
}
