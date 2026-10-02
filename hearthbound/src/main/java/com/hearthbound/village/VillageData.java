package com.hearthbound.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** All villages of the server, saved with the overworld. Each village records its own dimension. */
public final class VillageData extends SavedData {
    private static final String NAME = "hearthbound_villages";
    private final Map<UUID, Village> villages = new LinkedHashMap<>();
    public final Diplomacy.State diplomacy = new Diplomacy.State();
    private int sharedTierCap = -1;

    /** A single player's achievement is enough, and leaving never revokes it. */
    public void recordTierCap(int cap) {
        if (cap > sharedTierCap) {
            sharedTierCap = cap;
            setDirty();
        }
    }

    public int sharedTierCap(int baseline, int maximum) {
        return Math.min(maximum, Math.max(baseline, sharedTierCap));
    }

    public static VillageData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(VillageData::new, VillageData::load), NAME);
    }

    public Collection<Village> all() {
        return villages.values();
    }

    public Village get(UUID id) {
        return id == null ? null : villages.get(id);
    }

    public void add(Village v) {
        villages.put(v.id, v);
        setDirty();
    }

    public void remove(UUID id) {
        villages.remove(id);
        setDirty();
    }

    /** The village whose radius contains the position, if any. */
    public Village at(ServerLevel level, BlockPos pos) {
        Village best = null;
        double bestD = Double.MAX_VALUE;
        for (Village v : villages.values()) {
            if (!v.dimension.equals(level.dimension())) continue;
            double d = v.center.distSqr(pos);
            if (d <= (double) v.radius() * v.radius() && d < bestD) {
                best = v;
                bestD = d;
            }
        }
        return best;
    }

    public Village nearest(ServerLevel level, BlockPos pos, double maxDist) {
        Village best = null;
        double bestD = maxDist * maxDist;
        for (Village v : villages.values()) {
            if (!v.dimension.equals(level.dimension())) continue;
            double d = v.center.distSqr(pos);
            if (d < bestD) {
                best = v;
                bestD = d;
            }
        }
        return best;
    }

    public int count(ServerLevel level, boolean naturalOnly) {
        int n = 0;
        for (Village v : villages.values()) if (v.dimension.equals(level.dimension()) && (!naturalOnly || v.natural)) n++;
        return n;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (Village v : villages.values()) l.add(v.save());
        tag.put("villages", l);
        tag.put("diplomacy", diplomacy.save());
        tag.putInt("sharedTierCap", sharedTierCap);
        return tag;
    }

    public static VillageData load(CompoundTag tag, HolderLookup.Provider registries) {
        VillageData d = new VillageData();
        d.sharedTierCap = tag.contains("sharedTierCap") ? tag.getInt("sharedTierCap") : -1;
        for (Tag x : tag.getList("villages", Tag.TAG_COMPOUND)) {
            try {
                Village v = Village.load((CompoundTag) x);
                d.villages.put(v.id, v);
            } catch (Exception e) {
                com.hearthbound.Hearthbound.LOGGER.error("Skipping a corrupt village entry: {}", e.toString());
            }
        }
        if (tag.contains("diplomacy")) d.diplomacy.load(tag.getCompound("diplomacy"));
        return d;
    }
}
