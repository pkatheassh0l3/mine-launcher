package com.hearthbound.rpg;

import com.hearthbound.Hearthbound;
import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.network.Net;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.village.Village;
import com.hearthbound.village.VillageData;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Levels, XP, skills, classes and derived numbers. */
public final class Rpg {
    private static final ResourceLocation VALOR_HEALTH = Hearthbound.id("valor_health");
    private static final ResourceLocation VALOR_DAMAGE = Hearthbound.id("valor_damage");

    private Rpg() {}

    public static PlayerData data(Player p) {
        return p.getData(ModRegistry.PLAYER_DATA.get());
    }

    public static int xpToNext(int level) {
        return (int) Math.max(1, Math.round(HBConfig.XP_BASE.get() * Math.pow(level, HBConfig.XP_EXPONENT.get())));
    }

    // ================================================================== xp

    /** Adds XP (with multipliers) and levels up as far as the level cap allows. */
    public static void addXp(ServerPlayer p, int base, boolean notify) {
        if (base <= 0) return;
        PlayerData d = data(p);
        double mult = HBConfig.XP_MULTIPLIER.get() * (1 + d.skill(Skill.SCOUTING) * HBConfig.SKILL_SCOUTING_XP.get());
        int amount = (int) Math.max(1, Math.round(base * mult));
        d.xp += amount;
        if (notify) Net.notify(p, Component.translatable("hearthbound.notify.xp", amount), 0xFF9FE06A);
        levelUp(p, d);
        sync(p);
    }

    private static void levelUp(ServerPlayer p, PlayerData d) {
        int cap = Compat.levelCap(p);
        boolean up = false;
        while (d.level < cap && d.xp >= xpToNext(d.level)) {
            d.xp -= xpToNext(d.level);
            d.level++;
            d.skillPoints += HBConfig.SKILL_POINTS_PER_LEVEL.get();
            up = true;
        }
        if (d.level >= cap) d.xp = Math.min(d.xp, xpToNext(d.level));
        if (up) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            Net.banner(p, Component.translatable("hearthbound.banner.levelup", d.level),
                    Component.translatable("hearthbound.banner.levelup.sub", d.skillPoints), 0xFFFFD24A);
        }
    }

    /** Called when an Ascension age is reached: the level cap may have gone up. */
    public static void onCapRaised(ServerPlayer p) {
        PlayerData d = data(p);
        levelUp(p, d);
        sync(p);
    }

    // ================================================================== class and skills

    public static boolean chooseClass(ServerPlayer p, PlayerClass c) {
        PlayerData d = data(p);
        if (c == null) return false;
        if (d.clazz != null) {
            if (!HBConfig.ALLOW_CLASS_CHANGE.get() || d.clazz == c) return false;
            // move the free rank to the new class skill
            int old = d.skill(d.clazz.bonusSkill);
            if (old > 0) d.skills.put(d.clazz.bonusSkill, old - 1);
        } else {
            Wallet.give(p, HBConfig.CLASS_START_COINS.get());
        }
        d.clazz = c;
        d.skills.put(c.bonusSkill, Math.min(maxRank(), d.skill(c.bonusSkill) + 1));
        d.introSeen = true;
        applyAttributes(p);
        Net.banner(p, c.title(), c.perk(), 0xFF000000 | c.color);
        sync(p);
        return true;
    }

    public static int maxRank() {
        return HBConfig.MAX_SKILL_RANK.get();
    }

    public static boolean raise(ServerPlayer p, Skill s) {
        PlayerData d = data(p);
        if (s == null || d.skillPoints <= 0 || d.skill(s) >= maxRank()) return false;
        if (!Compat.ageReached(p, HBConfig.ASC_AGE_SKILLS.get())) {
            p.displayClientMessage(Component.translatable("hearthbound.gate.age", Compat.ageName(p, HBConfig.ASC_AGE_SKILLS.get())), true);
            return false;
        }
        d.skillPoints--;
        d.skills.put(s, d.skill(s) + 1);
        applyAttributes(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
        sync(p);
        return true;
    }

    /** Valor bonuses are transient modifiers: re-applied on login, respawn and skill change. */
    public static void applyAttributes(ServerPlayer p) {
        PlayerData d = data(p);
        int valor = d.skill(Skill.VALOR);
        modifier(p, Attributes.MAX_HEALTH, VALOR_HEALTH, valor * HBConfig.SKILL_VALOR_HEALTH.get());
        modifier(p, Attributes.ATTACK_DAMAGE, VALOR_DAMAGE, valor * HBConfig.SKILL_VALOR_DAMAGE.get());
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static void modifier(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id, double amount) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    // ================================================================== derived numbers

    public static int maxContracts(Player p) {
        PlayerData d = data(p);
        return HBConfig.MAX_ACTIVE_CONTRACTS.get() + d.skill(Skill.LEADERSHIP) / HBConfig.SKILL_LEADERSHIP_CONTRACTS_EVERY.get();
    }

    public static int maxCompanions(Player p) {
        PlayerData d = data(p);
        int n = HBConfig.MAX_COMPANIONS.get() + d.skill(Skill.LEADERSHIP) / HBConfig.SKILL_LEADERSHIP_COMPANIONS_EVERY.get();
        if (d.clazz == PlayerClass.NOBLE) n++;
        return n;
    }

    public static double reputationMultiplier(Player p) {
        return 1 + data(p).skill(Skill.DIPLOMACY) * HBConfig.SKILL_DIPLOMACY_BONUS.get();
    }

    public static double donationMultiplier(Player p) {
        PlayerData d = data(p);
        double m = 1 + d.skill(Skill.CRAFT) * HBConfig.SKILL_CRAFT_BONUS.get();
        if (d.clazz == PlayerClass.ARTISAN) m += HBConfig.PERK_ARTISAN_DONATION.get();
        return m;
    }

    public static int radarRange(Player p) {
        PlayerData d = data(p);
        int ranks = d.skill(Skill.SCOUTING) + (d.clazz == PlayerClass.RANGER ? 1 : 0);
        return 200 + ranks * HBConfig.SKILL_SCOUTING_RADAR.get();
    }

    /** Price the player pays for something that costs {@code base}. */
    public static int buyPrice(Player p, int base, Rank rank) {
        PlayerData d = data(p);
        double m = HBConfig.BUY_PRICE_MULTIPLIER.get();
        m *= 1 - Math.max(0, rank.ordinal() - Rank.STRANGER.ordinal()) * HBConfig.RANK_DISCOUNT.get();
        m *= 1 - d.skill(Skill.COMMERCE) * HBConfig.SKILL_TRADE_DISCOUNT.get();
        if (rank == Rank.WARY) m *= 1.15;
        if (rank == Rank.HOSTILE) m *= 1.5;
        return Math.max(1, (int) Math.ceil(base * Math.max(0.1, m)));
    }

    /** Coins the player receives for something worth {@code base}. */
    public static int sellPrice(Player p, int base, Rank rank) {
        PlayerData d = data(p);
        double m = HBConfig.SELL_PRICE_MULTIPLIER.get();
        m *= 1 + Math.max(0, rank.ordinal() - Rank.STRANGER.ordinal()) * HBConfig.RANK_DISCOUNT.get();
        m *= 1 + d.skill(Skill.COMMERCE) * HBConfig.SKILL_TRADE_DISCOUNT.get();
        if (d.clazz == PlayerClass.ROGUE) m *= 1 + HBConfig.PERK_ROGUE_SELL.get();
        return Math.max(base > 0 ? 1 : 0, (int) Math.floor(base * m));
    }

    // ================================================================== sync

    public static void sync(ServerPlayer p) {
        PlayerData d = data(p);
        CompoundTag t = d.save();
        t.putInt("cap", Compat.levelCap(p));
        t.putInt("next", xpToNext(d.level));
        t.putInt("maxContracts", maxContracts(p));
        t.putInt("maxCompanions", maxCompanions(p));
        t.putInt("maxRank", maxRank());
        t.putInt("radar", radarRange(p));
        String nextAge = Compat.nextLevelAge(p);
        if (nextAge != null) {
            t.putString("nextAge", nextAge);
            t.putString("nextAgeName", Component.Serializer.toJson(Compat.ageName(p, nextAge), p.registryAccess()));
        }
        t.putBoolean("skillsLocked", !Compat.ageReached(p, HBConfig.ASC_AGE_SKILLS.get()));
        if (t.getBoolean("skillsLocked")) t.putString("skillsAge", Component.Serializer.toJson(Compat.ageName(p, HBConfig.ASC_AGE_SKILLS.get()), p.registryAccess()));
        t.putBoolean("classChange", HBConfig.ALLOW_CLASS_CHANGE.get());
        // known villages (for the character screen and the compass)
        ListTag vs = new ListTag();
        VillageData vd = VillageData.get(p.server);
        for (UUID id : d.discovered) {
            Village v = vd.get(id);
            if (v == null) continue;
            CompoundTag e = new CompoundTag();
            e.putUUID("id", v.id);
            e.putString("name", v.name);
            e.putString("dim", v.dimension.location().toString());
            e.putInt("x", v.center.getX());
            e.putInt("y", v.center.getY());
            e.putInt("z", v.center.getZ());
            e.putInt("tier", v.tier());
            var c = v.culture();
            e.putInt("color", c == null ? 0xFFE0B25A : c.color);
            e.putString("culture", c == null ? "" : c.name);
            e.putBoolean("lord", p.getUUID().equals(v.lord));
            vs.add(e);
        }
        t.put("villages", vs);
        Net.send(p, "sync", t);
    }
}
