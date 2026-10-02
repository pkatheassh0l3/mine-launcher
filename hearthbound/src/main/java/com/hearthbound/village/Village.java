package com.hearthbound.village;

import com.hearthbound.config.HBConfig;
import com.hearthbound.data.BuildingDef;
import com.hearthbound.data.Culture;
import com.hearthbound.data.HBData;
import com.hearthbound.rpg.Contract;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A settlement: its buildings, people, stock and board. Stored in {@link VillageData}. */
public final class Village {
    public UUID id = UUID.randomUUID();
    public String name = "";
    public ResourceLocation culture;
    public ResourceKey<Level> dimension = Level.OVERWORLD;
    public BlockPos center = BlockPos.ZERO;
    public long founded;
    public boolean natural = true;
    public UUID lord;
    public String lordName = "";

    public final Map<Resource, Integer> stock = new EnumMap<>(Resource.class);
    public final List<PlacedBuilding> buildings = new ArrayList<>();
    public final List<Resident> residents = new ArrayList<>();
    public final Map<BlockPos, VillageRepairs.Repair> repairs = new java.util.LinkedHashMap<>();
    public int planIndex;
    public ResourceLocation lordChoice;
    public int prosperity = 50;
    public int treasury;
    public int tierCap = 4;

    public final List<Contract> board = new ArrayList<>();
    public long boardDay = -1;
    public final Map<Integer, Integer> soldToday = new HashMap<>();
    public long tradeDay = -1;
    public long tributeDay = -1;
    public long golemNight = -1;
    public int golemsSpawned;
    public long lastRaidNight = -1;
    public int raidRemaining;
    public long raidEnds;
    /** Identifies the current raid (its start time), so stragglers of old raids are recognised. */
    public long raidId;
    public boolean raidPillagers;
    public long lastPillagerDay = -1;
    /** Building projects in progress, by project type. */
    public final Map<String, Project> projects = new HashMap<>();
    /** Highest finished level of each project type. */
    public final Map<String, Integer> projectLevels = new HashMap<>();
    /** Finished player buildings (areas villagers must respect). */
    public final List<Project.Work> works = new ArrayList<>();
    /** Citizens: player id -> name. */
    public final Map<UUID, String> citizens = new java.util.LinkedHashMap<>();
    /** The village banner (see {@link Flags}); empty until generated. */
    public CompoundTag flag = new CompoundTag();
    public BlockPos flagPos;

    public int projectLevel(String type) {
        return projectLevels.getOrDefault(type, 0);
    }

    public Culture culture() {
        return HBData.culture(culture);
    }

    public int completedBuildings() {
        int n = 0;
        for (PlacedBuilding b : buildings) if (b.complete) n++;
        return n;
    }

    public PlacedBuilding construction() {
        for (PlacedBuilding b : buildings) if (!b.complete) return b;
        return null;
    }

    public int tier() {
        List<? extends Integer> t = HBConfig.TIER_THRESHOLDS.get();
        int done = completedBuildings();
        int tier = 0;
        for (int i = 0; i < t.size(); i++) if (done >= t.get(i)) tier = i;
        return Math.min(tier, HBConfig.MAX_TIER.get());
    }

    public int radius() {
        return HBConfig.BASE_RADIUS.get() + HBConfig.RADIUS_PER_TIER.get() * tier();
    }

    public boolean contains(BlockPos pos) {
        return center.distSqr(pos) <= (double) radius() * radius();
    }

    public int population() {
        int n = 0;
        for (Resident r : residents) if (r.alive()) n++;
        return n;
    }

    /** Beds available: sum of residents of every finished building. */
    public int housing() {
        int n = 0;
        for (PlacedBuilding b : buildings) {
            if (!b.complete) continue;
            BuildingDef d = b.definition();
            if (d == null) continue;
            n += d.residents < 0 ? HBConfig.RESIDENTS_PER_HOUSE.get() : d.residents;
        }
        return Math.min(n, HBConfig.MAX_POPULATION.get());
    }

    public int get(Resource r) {
        return stock.getOrDefault(r, 0);
    }

    public void add(Resource r, int amount) {
        stock.put(r, Math.max(0, get(r) + amount));
    }

    public boolean has(Role role) {
        for (Resident r : residents) if (r.alive() && r.role == role) return true;
        return false;
    }

    public PlacedBuilding buildingAt(BlockPos pos) {
        for (PlacedBuilding b : buildings) if (b.bounds.isInside(pos)) return b;
        return null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("culture", culture == null ? "" : culture.toString());
        t.putString("dim", dimension.location().toString());
        t.put("center", NbtUtils.writeBlockPos(center));
        t.putLong("founded", founded);
        t.putBoolean("natural", natural);
        if (lord != null) t.putUUID("lord", lord);
        t.putString("lordName", lordName);
        CompoundTag st = new CompoundTag();
        stock.forEach((r, v) -> st.putInt(r.id(), v));
        t.put("stock", st);
        ListTag bl = new ListTag();
        for (PlacedBuilding b : buildings) bl.add(b.save());
        t.put("buildings", bl);
        t.put("repairs", VillageRepairs.save(this));
        ListTag rl = new ListTag();
        for (Resident r : residents) rl.add(r.save());
        t.put("residents", rl);
        t.putInt("plan", planIndex);
        if (lordChoice != null) t.putString("lordChoice", lordChoice.toString());
        t.putInt("prosperity", prosperity);
        t.putInt("treasury", treasury);
        t.putInt("tierCap", tierCap);
        ListTag bd = new ListTag();
        for (Contract c : board) bd.add(c.save());
        t.put("board", bd);
        t.putLong("boardDay", boardDay);
        CompoundTag sold = new CompoundTag();
        soldToday.forEach((k, v) -> sold.putInt(Integer.toString(k), v));
        t.put("sold", sold);
        t.putLong("tradeDay", tradeDay);
        t.putLong("tributeDay", tributeDay);
        t.putLong("golemNight", golemNight);
        t.putInt("golemsSpawned", golemsSpawned);
        t.putLong("raidNight", lastRaidNight);
        t.putInt("raidLeft", raidRemaining);
        t.putLong("raidEnds", raidEnds);
        t.putLong("raidId", raidId);
        t.putBoolean("raidPill", raidPillagers);
        t.putLong("pillDay", lastPillagerDay);
        ListTag pl = new ListTag();
        for (Project p : projects.values()) pl.add(p.save());
        t.put("projects", pl);
        CompoundTag lv = new CompoundTag();
        projectLevels.forEach(lv::putInt);
        t.put("projectLevels", lv);
        ListTag wl = new ListTag();
        for (Project.Work w : works) wl.add(w.save());
        t.put("works", wl);
        ListTag cl = new ListTag();
        citizens.forEach((u, n) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", u);
            e.putString("name", n);
            cl.add(e);
        });
        t.put("citizens", cl);
        t.put("flag", flag);
        if (flagPos != null) t.put("flagPos", NbtUtils.writeBlockPos(flagPos));
        return t;
    }

    public static Village load(CompoundTag t) {
        Village v = new Village();
        v.id = t.getUUID("id");
        v.name = t.getString("name");
        v.culture = ResourceLocation.tryParse(t.getString("culture"));
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
        if (dim != null) v.dimension = ResourceKey.create(Registries.DIMENSION, dim);
        v.center = NbtUtils.readBlockPos(t, "center").orElse(BlockPos.ZERO);
        v.founded = t.getLong("founded");
        v.natural = t.getBoolean("natural");
        if (t.hasUUID("lord")) v.lord = t.getUUID("lord");
        v.lordName = t.getString("lordName");
        CompoundTag st = t.getCompound("stock");
        for (Resource r : Resource.values()) v.stock.put(r, st.getInt(r.id()));
        for (Tag x : t.getList("buildings", Tag.TAG_COMPOUND)) v.buildings.add(PlacedBuilding.load((CompoundTag) x));
        for (Tag x : t.getList("residents", Tag.TAG_COMPOUND)) v.residents.add(Resident.load((CompoundTag) x));
        VillageRepairs.load(v, t.getList("repairs", Tag.TAG_COMPOUND));
        v.planIndex = t.getInt("plan");
        if (t.contains("lordChoice")) v.lordChoice = ResourceLocation.tryParse(t.getString("lordChoice"));
        v.prosperity = t.getInt("prosperity");
        v.treasury = t.getInt("treasury");
        v.tierCap = t.contains("tierCap") ? t.getInt("tierCap") : 4;
        for (Tag x : t.getList("board", Tag.TAG_COMPOUND)) v.board.add(Contract.load((CompoundTag) x));
        v.boardDay = t.getLong("boardDay");
        CompoundTag sold = t.getCompound("sold");
        for (String k : sold.getAllKeys()) {
            try {
                v.soldToday.put(Integer.parseInt(k), sold.getInt(k));
            } catch (NumberFormatException ignored) {
            }
        }
        v.tradeDay = t.getLong("tradeDay");
        v.tributeDay = t.getLong("tributeDay");
        v.golemNight = t.contains("golemNight") ? t.getLong("golemNight") : -1;
        v.golemsSpawned = t.getInt("golemsSpawned");
        v.lastRaidNight = t.getLong("raidNight");
        v.raidRemaining = t.getInt("raidLeft");
        v.raidEnds = t.getLong("raidEnds");
        v.raidId = t.getLong("raidId");
        v.raidPillagers = t.getBoolean("raidPill");
        v.lastPillagerDay = t.contains("pillDay") ? t.getLong("pillDay") : -1;
        for (Tag x : t.getList("projects", Tag.TAG_COMPOUND)) {
            Project p = Project.load((CompoundTag) x);
            v.projects.put(p.type, p);
        }
        CompoundTag lv = t.getCompound("projectLevels");
        for (String k : lv.getAllKeys()) v.projectLevels.put(k, lv.getInt(k));
        for (Tag x : t.getList("works", Tag.TAG_COMPOUND)) v.works.add(Project.Work.load((CompoundTag) x));
        for (Tag x : t.getList("citizens", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) x;
            if (e.hasUUID("id")) v.citizens.put(e.getUUID("id"), e.getString("name"));
        }
        v.flag = t.getCompound("flag");
        v.flagPos = NbtUtils.readBlockPos(t, "flagPos").orElse(null);
        return v;
    }
}
