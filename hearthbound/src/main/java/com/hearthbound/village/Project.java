package com.hearthbound.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.UUID;

/** A building project a player is raising for a village (taken, maybe already placed). */
public final class Project {
    public String type = "";
    public int level = 1;
    public UUID owner;
    public String ownerName = "";
    public UUID contract;
    /** Foundation stone position, null until placed. */
    public BlockPos core;
    /** Half the side of the plot (plot side = 2 * half + 1). */
    public int half;
    public long taken;

    public BoundingBox area(int height) {
        return new BoundingBox(core.getX() - half, core.getY() - 2, core.getZ() - half, core.getX() + half, core.getY() + height, core.getZ() + half);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type);
        t.putInt("level", level);
        if (owner != null) t.putUUID("owner", owner);
        t.putString("ownerName", ownerName);
        if (contract != null) t.putUUID("contract", contract);
        if (core != null) t.put("core", NbtUtils.writeBlockPos(core));
        t.putInt("half", half);
        t.putLong("taken", taken);
        return t;
    }

    public static Project load(CompoundTag t) {
        Project p = new Project();
        p.type = t.getString("type");
        p.level = Math.max(1, t.getInt("level"));
        p.owner = t.hasUUID("owner") ? t.getUUID("owner") : null;
        p.ownerName = t.getString("ownerName");
        p.contract = t.hasUUID("contract") ? t.getUUID("contract") : null;
        p.core = NbtUtils.readBlockPos(t, "core").orElse(null);
        p.half = t.getInt("half");
        p.taken = t.getLong("taken");
        return p;
    }

    /** A finished player building: kept so villagers never build over it. */
    public record Work(String type, int level, BoundingBox area, String builder) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("type", type);
            t.putInt("level", level);
            t.putIntArray("box", new int[]{area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ()});
            t.putString("builder", builder);
            return t;
        }

        public static Work load(CompoundTag t) {
            int[] b = t.getIntArray("box");
            BoundingBox box = b.length == 6 ? new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]) : new BoundingBox(0, 0, 0, 0, 0, 0);
            return new Work(t.getString("type"), t.getInt("level"), box, t.getString("builder"));
        }
    }
}
