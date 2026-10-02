package com.hearthbound.village;

import com.hearthbound.data.BuildingDef;
import com.hearthbound.data.HBData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** A building placed (or being built) in a village. */
public final class PlacedBuilding {
    public ResourceLocation def;
    /** World position of the local (0, 0, 0) corner: front-left of the floor. */
    public BlockPos origin;
    public Direction facing;
    public int width;
    public int depth;
    public int height;
    public long seed;
    public boolean complete;
    /** Panes, bars, fences and walls already reconnected to their neighbours. */
    public boolean connected;
    public int placed;
    public int total;
    public BoundingBox bounds;
    /** Schematic id, or null for a procedural building. */
    public String template;
    public int doorX = -1;
    /** Saved separately so old buildings retain their original placement. */
    public int terrainOffset;

    public PlacedBuilding() {}

    public PlacedBuilding(ResourceLocation def, BlockPos origin, Direction facing, int width, int depth, int height, long seed, BoundingBox bounds) {
        this.def = def;
        this.origin = origin;
        this.facing = facing;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.seed = seed;
        this.bounds = bounds;
    }

    public BuildingDef definition() {
        return HBData.building(def);
    }

    /** Center of the footprint at floor level. */
    public BlockPos center() {
        return new BlockPos((bounds.minX() + bounds.maxX()) / 2, origin.getY() + 1, (bounds.minZ() + bounds.maxZ()) / 2);
    }

    /** The block right outside the front door. */
    public BlockPos doorstep() {
        BlockPos local = new BlockPos(doorX >= 0 ? doorX : width / 2, terrainOffset + 1, -1);
        return Blueprint.toWorld(origin, facing, local);
    }

    public float progress() {
        if (complete) return 1f;
        return total <= 0 ? 0f : Math.min(1f, placed / (float) total);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("def", def.toString());
        t.put("origin", NbtUtils.writeBlockPos(origin));
        t.putString("facing", facing.getName());
        t.putInt("w", width);
        t.putInt("d", depth);
        t.putInt("h", height);
        t.putLong("seed", seed);
        t.putBoolean("complete", complete);
        t.putBoolean("connected", connected);
        t.putInt("placed", placed);
        t.putInt("total", total);
        if (template != null) t.putString("template", template);
        t.putInt("doorX", doorX);
        t.putInt("terrainOffset", terrainOffset);
        t.putIntArray("bb", new int[]{bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()});
        return t;
    }

    public static PlacedBuilding load(CompoundTag t) {
        PlacedBuilding b = new PlacedBuilding();
        b.def = ResourceLocation.parse(t.getString("def"));
        b.origin = NbtUtils.readBlockPos(t, "origin").orElse(BlockPos.ZERO);
        Direction f = Direction.byName(t.getString("facing"));
        b.facing = f == null ? Direction.NORTH : f;
        b.width = t.getInt("w");
        b.depth = t.getInt("d");
        b.height = t.getInt("h");
        b.seed = t.getLong("seed");
        b.complete = t.getBoolean("complete");
        b.connected = t.getBoolean("connected");
        b.placed = t.getInt("placed");
        b.total = t.getInt("total");
        b.template = t.contains("template") ? t.getString("template") : null;
        b.doorX = t.contains("doorX") ? t.getInt("doorX") : -1;
        b.terrainOffset = t.getInt("terrainOffset");
        int[] bb = t.getIntArray("bb");
        b.bounds = bb.length == 6 ? new BoundingBox(bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]) : new BoundingBox(b.origin);
        return b;
    }
}
