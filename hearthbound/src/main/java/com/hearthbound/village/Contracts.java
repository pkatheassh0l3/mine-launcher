package com.hearthbound.village;

import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.data.ContractTemplate;
import com.hearthbound.data.HBData;
import com.hearthbound.data.Json;
import com.hearthbound.data.Matcher;
import com.hearthbound.network.Net;
import com.hearthbound.rpg.Contract;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rank;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.rpg.Wallet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Contract boards, accepting, progress and turning in. */
public final class Contracts {
    private Contracts() {}

    public static int boardSize(Village v) {
        return HBConfig.BOARD_SIZE.get() + HBConfig.BOARD_SIZE_PER_TIER.get() * v.tier();
    }

    /** Refills the board once per in-game day. */
    public static void refreshBoard(ServerLevel level, Village v, boolean force) {
        long day = level.getDayTime() / 24000L;
        if (!force && v.boardDay == day && !v.board.isEmpty()) return;
        v.boardDay = day;
        v.board.clear();
        RandomSource r = RandomSource.create(v.id.getLeastSignificantBits() ^ day * 31L);
        List<ContractTemplate> pool = new ArrayList<>();
        int total = 0;
        for (ContractTemplate t : HBData.contracts()) {
            if (t.tier > v.tier()) continue;
            if (!t.cultures.isEmpty() && !t.cultures.contains(v.culture)) continue;
            if (t.type == ContractTemplate.Type.EXPLORE && VillageData.get(level.getServer()).all().size() < 2 && r.nextBoolean()) continue;
            pool.add(t);
            total += t.weight;
        }
        int n = boardSize(v);
        for (int i = 0; i < n && total > 0; i++) {
            int pick = r.nextInt(total);
            ContractTemplate chosen = null;
            for (ContractTemplate t : pool) {
                pick -= t.weight;
                if (pick < 0) {
                    chosen = t;
                    break;
                }
            }
            if (chosen == null) break;
            Contract rolled = roll(chosen, v, r);
            if (chosen.type == ContractTemplate.Type.ENVOY) {
                java.util.List<Village> near = new java.util.ArrayList<>();
                VillageData vd = VillageData.get(level.getServer());
                for (Village o : Diplomacy.neighbours(vd, v)) if (!Diplomacy.relation(vd, v, o).atWar()) near.add(o);
                if (near.isEmpty()) {
                    pool.remove(chosen);
                    total -= chosen.weight;
                    continue;
                }
                Village dest = near.get(r.nextInt(near.size()));
                rolled.target = dest.id.toString();
                rolled.extra = dest.name;
                rolled.count = 1;
            }
            v.board.add(rolled);
            pool.remove(chosen);
            total -= chosen.weight;
        }
    }

    static Contract roll(ContractTemplate t, Village v, RandomSource r) {
        Contract c = Contract.fromTemplate(t);
        double scale = (1 + v.tier() * HBConfig.CONTRACT_TIER_SCALING.get());
        int count = t.count[0] + (t.count[1] > t.count[0] ? r.nextInt(t.count[1] - t.count[0] + 1) : 0);
        c.count = Math.max(1, (int) Math.round(count * (t.type == ContractTemplate.Type.DELIVER || t.type == ContractTemplate.Type.DONATE ? scale : 1)));
        double rew = HBConfig.CONTRACT_REWARD_MULTIPLIER.get() * scale;
        int coins = t.coins[0] + (t.coins[1] > t.coins[0] ? r.nextInt(t.coins[1] - t.coins[0] + 1) : 0);
        c.coins = (int) Math.round(coins * rew);
        c.reputation = (int) Math.round(t.reputation * scale);
        c.xp = (int) Math.round(t.xp * rew);
        c.village = v.id;
        c.villageName = v.name;
        return c;
    }

    // ================================================================== accept / abandon

    public static boolean accept(ServerPlayer p, Village v, UUID offerId) {
        PlayerData d = Rpg.data(p);
        Contract offer = null;
        for (Contract c : v.board) if (c.id.equals(offerId)) offer = c;
        if (offer == null) return false;
        ContractTemplate t = offer.template == null ? null : HBData.contract(offer.template);
        if (!Compat.ageReached(p, HBConfig.ASC_AGE_CONTRACTS.get())) {
            fail(p, Component.translatable("hearthbound.gate.age", Compat.ageName(p, HBConfig.ASC_AGE_CONTRACTS.get())));
            return false;
        }
        if (t != null && !Compat.ageReached(p, t.age)) {
            fail(p, Component.translatable("hearthbound.gate.age", Compat.ageName(p, t.age)));
            return false;
        }
        Rank need = t == null ? Rank.STRANGER : t.rank;
        if (!d.rank(v.id).atLeast(need)) {
            fail(p, Component.translatable("hearthbound.gate.rank", need.title()));
            return false;
        }
        if (d.boardContracts() >= Rpg.maxContracts(p)) {
            fail(p, Component.translatable("hearthbound.contract.full", Rpg.maxContracts(p)));
            return false;
        }
        Contract c = offer.copy();
        c.progress = 0;
        int days = HBConfig.CONTRACT_DAYS.get();
        c.expires = days <= 0 ? 0 : p.level().getGameTime() + days * 24000L;
        d.contracts.add(c);
        d.tracked = c.id;
        v.board.remove(offer);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        Net.notify(p, Component.translatable("hearthbound.contract.accepted", c.title()), 0xFFE0B25A);
        return true;
    }

    public static void abandon(ServerPlayer p, UUID id) {
        PlayerData d = Rpg.data(p);
        Contract c0 = d.contract(id);
        if (c0 != null) Projects.contractAbandoned(p, c0);
        d.contracts.removeIf(c -> c.id.equals(id));
        if (id.equals(d.tracked)) d.tracked = null;
    }

    // ================================================================== turn in

    /** Hands in a contract at its village. Deliveries take the items now. */
    public static boolean turnIn(ServerPlayer p, Village v, UUID id) {
        PlayerData d = Rpg.data(p);
        Contract c = d.contract(id);
        if (c != null && c.personal()) return Persons.turnIn(p, c);
        if (c != null && c.type == ContractTemplate.Type.BUILD) {
            p.displayClientMessage(Component.translatable("hearthbound.project.finish_at_stone").withColor(0xE0B25A), true);
            return false;
        }
        if (c == null || !v.id.equals(c.village)) return false;
        if (c.type == ContractTemplate.Type.DELIVER) {
            int have = countItems(p, c.target);
            if (have < c.count) {
                fail(p, Component.translatable("hearthbound.contract.missing", c.count - have, c.targetName()));
                return false;
            }
            takeItems(p, c.target, c.count);
        } else if (c.progress < c.count) {
            fail(p, Component.translatable("hearthbound.contract.unfinished"));
            return false;
        }
        complete(p, v, c);
        return true;
    }

    private static void complete(ServerPlayer p, Village v, Contract c) {
        PlayerData d = Rpg.data(p);
        d.contracts.remove(c);
        if (c.id.equals(d.tracked)) d.tracked = null;
        d.contractsDone++;
        Wallet.give(p, c.coins);
        d.coinsEarned += c.coins;
        for (String s : c.items) {
            ItemStack st = parseStack(s);
            if (!st.isEmpty() && !p.getInventory().add(st)) p.drop(st, false);
        }
        Reputation.add(p, v, c.reputation, true, false);
        v.prosperity = Math.min(100, v.prosperity + 2);
        if (c.type == ContractTemplate.Type.DELIVER) v.add(Resource.GOODS, Math.max(1, c.count / 8));
        Rpg.addXp(p, c.xp, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
        Net.banner(p, Component.translatable("hearthbound.contract.completed"), c.title(), 0xFFFFD24A);
        Net.notify(p, Component.translatable("hearthbound.notify.reward", Wallet.format(c.coins), c.reputation, c.xp), 0xFFE0B25A);
        Compat.trigger(p, "contracts", c, 1);
        if (c.type == ContractTemplate.Type.ENVOY) {
            try {
                VillageData vd = VillageData.get(p.server);
                Village dest = vd.get(UUID.fromString(c.target));
                if (dest != null) {
                    Diplomacy.change(vd, v, dest, 8);
                    Net.notify(p, Component.translatable("hearthbound.diplo.envoy_done", v.name, dest.name), 0xFF6CD68A);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        VillageData.get(p.server).setDirty();
    }

    /** "minecraft:bread*4" → 4 bread. */
    public static ItemStack parseStack(String s) {
        int n = 1;
        String id = s;
        int star = s.indexOf('*');
        if (star > 0) {
            id = s.substring(0, star);
            try {
                n = Integer.parseInt(s.substring(star + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        Item it = Json.item(id, null);
        return it == null ? ItemStack.EMPTY : new ItemStack(it, Math.max(1, n));
    }

    // ================================================================== inventory helpers

    static void give(ServerPlayer p, ItemStack st) {
        if (!st.isEmpty() && !p.getInventory().add(st)) p.drop(st, false);
    }

    public static int countItems(ServerPlayer p, String matcher) {
        Matcher<Item> m = Matcher.of(matcher, Registries.ITEM);
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && m.test(BuiltInRegistries.ITEM.wrapAsHolder(s.getItem()))) n += s.getCount();
        }
        return n;
    }

    public static void takeItems(ServerPlayer p, String matcher, int count) {
        Matcher<Item> m = Matcher.of(matcher, Registries.ITEM);
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !m.test(BuiltInRegistries.ITEM.wrapAsHolder(s.getItem()))) continue;
            int take = Math.min(count, s.getCount());
            s.shrink(take);
            count -= take;
        }
    }

    // ================================================================== progress events

    /** A player killed something. {@code inVillage} is the village the kill happened in, if any. */
    public static void onKill(ServerPlayer p, LivingEntity victim, Village inVillage) {
        PlayerData d = Rpg.data(p);
        boolean changed = false;
        EntityType<?> type = victim.getType();
        boolean monster = type.getCategory() == MobCategory.MONSTER
                || victim instanceof com.hearthbound.entity.SettlerEntity s && s.isWarband();
        for (Contract c : d.contracts) {
            if (c.progress >= c.count) continue;
            if (c.type == ContractTemplate.Type.HUNT) {
                boolean match = c.target.equals("monster") ? monster
                        : Matcher.of(c.target, Registries.ENTITY_TYPE).test(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type));
                if (match) {
                    c.progress++;
                    changed = true;
                }
            } else if (c.type == ContractTemplate.Type.DEFEND && monster && inVillage != null && inVillage.id.equals(c.village)) {
                c.progress++;
                changed = true;
            }
            if (changed && c.progress >= c.count) {
                Net.notify(p, Component.translatable("hearthbound.contract.ready", c.title(), c.personal() ? c.personName : c.villageName), 0xFFFFD24A);
            }
        }
        if (changed) Rpg.sync(p);
    }

    public static void onDiscover(ServerPlayer p, Village v) {
        PlayerData d = Rpg.data(p);
        boolean changed = false;
        for (Contract c : d.contracts) {
            if (c.type == ContractTemplate.Type.EXPLORE && !v.id.equals(c.village) && c.progress < c.count) {
                c.progress++;
                changed = true;
                if (c.progress >= c.count) Net.notify(p, Component.translatable("hearthbound.contract.ready", c.title(), c.villageName), 0xFFFFD24A);
            }
        }
        if (changed) Rpg.sync(p);
    }

    /** Envoys deliver their letter by walking into the destination village. */
    public static void onEnterVillage(ServerPlayer p, Village v) {
        PlayerData d = Rpg.data(p);
        boolean changed = false;
        for (Contract c : d.contracts) {
            if (c.type == ContractTemplate.Type.ENVOY && c.progress < c.count && v.id.toString().equals(c.target)) {
                c.progress = c.count;
                changed = true;
                Net.notify(p, Component.translatable("hearthbound.contract.letter_delivered", v.name, c.villageName), 0xFFFFD24A);
            }
        }
        if (changed) Rpg.sync(p);
    }

    public static void onDonate(ServerPlayer p, Village v, Resource r, int points) {
        PlayerData d = Rpg.data(p);
        for (Contract c : d.contracts) {
            if (c.type == ContractTemplate.Type.DONATE && v.id.equals(c.village) && c.progress < c.count
                    && (c.target.isEmpty() || c.target.equalsIgnoreCase(r.id()))) {
                c.progress = Math.min(c.count, c.progress + points);
                if (c.progress >= c.count) Net.notify(p, Component.translatable("hearthbound.contract.ready", c.title(), c.villageName), 0xFFFFD24A);
            }
        }
    }

    /** Removes expired contracts (checked periodically). */
    public static void expire(ServerPlayer p) {
        PlayerData d = Rpg.data(p);
        long now = p.level().getGameTime();
        boolean changed = false;
        for (Iterator<Contract> it = d.contracts.iterator(); it.hasNext(); ) {
            Contract c = it.next();
            if (c.expires > 0 && now > c.expires) {
                it.remove();
                changed = true;
                Net.notify(p, Component.translatable("hearthbound.contract.expired", c.title()), 0xFFE06A5A);
            }
        }
        if (changed) Rpg.sync(p);
    }

    private static void fail(ServerPlayer p, Component msg) {
        p.displayClientMessage(msg.copy().withColor(0xE06A5A), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1f);
    }
}
