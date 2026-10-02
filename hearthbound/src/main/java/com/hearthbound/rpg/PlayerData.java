package com.hearthbound.rpg;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player RPG state: level, class, skills, reputation per village, discovered villages,
 * active contracts and a few lifetime stats. Attached to the player and kept on death.
 * The same class is used on the client (filled from the sync packet).
 */
public final class PlayerData implements INBTSerializable<CompoundTag> {
    public int level = 1;
    public int xp;
    public int skillPoints;
    public PlayerClass clazz;
    public final Map<Skill, Integer> skills = new EnumMap<>(Skill.class);
    public final Map<UUID, Integer> reputation = new HashMap<>();
    public final Set<UUID> discovered = new LinkedHashSet<>();
    public final List<Contract> contracts = new ArrayList<>();
    public UUID tracked;
    public int contractsDone;
    public int coinsSpent;
    public int coinsEarned;
    public int donated;
    public int defended;
    public int hires;
    public int blocksBuilt;
    public boolean introSeen;
    /** Friendship with individual settlers, by resident id. */
    public final Map<UUID, Bond> bonds = new HashMap<>();
    public int storiesDone;
    public UUID citizen;
    public long citizenSince = -1;
    public int projectsDone;

    /** Friendship with one settler and how far their story has gone. */
    public static final class Bond {
        public int points;
        public int stage;          // personal quests completed
        public long lastTalk = -1; // day
        public long lastGift = -1; // day
        public String name = "";
        public String persona = "";
        public UUID village;
        /** Story dialogues already heard in each phase. */
        public int[] heard = new int[4];
        public long lineDay = -1;
        public int linesToday;
        /** Last dialogue heard (translation key), shown when reopening. */
        public String lastLine = "";

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("p", points);
            t.putInt("s", stage);
            t.putLong("talk", lastTalk);
            t.putLong("gift", lastGift);
            t.putString("n", name);
            t.putString("per", persona);
            if (village != null) t.putUUID("v", village);
            t.putIntArray("heard", heard);
            t.putLong("lineDay", lineDay);
            t.putInt("linesToday", linesToday);
            t.putString("last", lastLine);
            return t;
        }

        static Bond load(CompoundTag t) {
            Bond b = new Bond();
            b.points = t.getInt("p");
            b.stage = t.getInt("s");
            b.lastTalk = t.getLong("talk");
            b.lastGift = t.getLong("gift");
            b.name = t.getString("n");
            b.persona = t.getString("per");
            b.village = t.hasUUID("v") ? t.getUUID("v") : null;
            int[] h = t.getIntArray("heard");
            for (int i = 0; i < Math.min(4, h.length); i++) b.heard[i] = h[i];
            b.lineDay = t.getLong("lineDay");
            b.linesToday = t.getInt("linesToday");
            b.lastLine = t.getString("last");
            return b;
        }
    }

    public Bond bond(UUID person) {
        return bonds.computeIfAbsent(person, k -> new Bond());
    }

    public int bondPoints(UUID person) {
        Bond b = bonds.get(person);
        return b == null ? 0 : b.points;
    }

    /** Board contracts only: personal quests do not take a contract slot. */
    public int boardContracts() {
        int n = 0;
        for (Contract c : contracts) if (!c.personal() && c.type != com.hearthbound.data.ContractTemplate.Type.BUILD) n++;
        return n;
    }
    /** Client only: names/colors of known villages for the character screen. */
    public final Map<UUID, CompoundTag> villageInfo = new HashMap<>();

    public int skill(Skill s) {
        return skills.getOrDefault(s, 0);
    }

    public int rep(UUID village) {
        return reputation.getOrDefault(village, 0);
    }

    public Rank rank(UUID village) {
        return Rank.of(rep(village));
    }

    public Contract contract(UUID id) {
        for (Contract c : contracts) if (c.id.equals(id)) return c;
        return null;
    }

    public Contract trackedContract() {
        if (tracked != null) {
            Contract c = contract(tracked);
            if (c != null) return c;
        }
        return contracts.isEmpty() ? null : contracts.get(0);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return save();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        load(t);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("level", level);
        t.putInt("xp", xp);
        t.putInt("points", skillPoints);
        if (clazz != null) t.putString("class", clazz.id());
        CompoundTag sk = new CompoundTag();
        skills.forEach((k, v) -> sk.putInt(k.id(), v));
        t.put("skills", sk);
        ListTag rep = new ListTag();
        reputation.forEach((id, v) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("v", id);
            e.putInt("r", v);
            rep.add(e);
        });
        t.put("rep", rep);
        ListTag disc = new ListTag();
        for (UUID u : discovered) disc.add(NbtUtils.createUUID(u));
        t.put("discovered", disc);
        ListTag con = new ListTag();
        for (Contract c : contracts) con.add(c.save());
        t.put("contracts", con);
        if (tracked != null) t.putUUID("tracked", tracked);
        t.putInt("done", contractsDone);
        t.putInt("spent", coinsSpent);
        t.putInt("earned", coinsEarned);
        t.putInt("donated", donated);
        t.putInt("defended", defended);
        t.putInt("hires", hires);
        t.putInt("built", blocksBuilt);
        t.putBoolean("intro", introSeen);
        ListTag bl = new ListTag();
        bonds.forEach((id, b) -> {
            CompoundTag e = b.save();
            e.putUUID("id", id);
            bl.add(e);
        });
        t.put("bonds", bl);
        t.putInt("stories", storiesDone);
        if (citizen != null) t.putUUID("citizen", citizen);
        t.putLong("citizenSince", citizenSince);
        t.putInt("projects", projectsDone);
        return t;
    }

    public void load(CompoundTag t) {
        level = Math.max(1, t.getInt("level"));
        xp = t.getInt("xp");
        skillPoints = t.getInt("points");
        clazz = PlayerClass.byId(t.getString("class"));
        skills.clear();
        CompoundTag sk = t.getCompound("skills");
        for (Skill s : Skill.values()) if (sk.contains(s.id())) skills.put(s, sk.getInt(s.id()));
        reputation.clear();
        for (Tag x : t.getList("rep", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) x;
            reputation.put(e.getUUID("v"), e.getInt("r"));
        }
        discovered.clear();
        for (Tag x : t.getList("discovered", Tag.TAG_INT_ARRAY)) discovered.add(NbtUtils.loadUUID(x));
        contracts.clear();
        for (Tag x : t.getList("contracts", Tag.TAG_COMPOUND)) contracts.add(Contract.load((CompoundTag) x));
        tracked = t.hasUUID("tracked") ? t.getUUID("tracked") : null;
        contractsDone = t.getInt("done");
        coinsSpent = t.getInt("spent");
        coinsEarned = t.getInt("earned");
        donated = t.getInt("donated");
        defended = t.getInt("defended");
        hires = t.getInt("hires");
        blocksBuilt = t.getInt("built");
        introSeen = t.getBoolean("intro");
        bonds.clear();
        for (Tag x : t.getList("bonds", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) x;
            if (e.hasUUID("id")) bonds.put(e.getUUID("id"), Bond.load(e));
        }
        storiesDone = t.getInt("stories");
        citizen = t.hasUUID("citizen") ? t.getUUID("citizen") : null;
        citizenSince = t.contains("citizenSince") ? t.getLong("citizenSince") : -1;
        projectsDone = t.getInt("projects");
    }
}
