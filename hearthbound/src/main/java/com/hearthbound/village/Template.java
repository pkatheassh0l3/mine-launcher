package com.hearthbound.village;

import com.hearthbound.Hearthbound;
import com.hearthbound.data.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A pre-built building loaded from a schematic file: vanilla/Create structure {@code .nbt}
 * or Sponge/WorldEdit {@code .schem} (versions 2 and 3).
 * <p>
 * On load the template is normalized to Hearthbound's local frame: its entrance faces local
 * north (−z) and the entrance sits at local y = 0 (ground level), so it can be placed exactly
 * like a procedural building. The entrance is found from the village jigsaw
 * ({@code minecraft:building_entrance}), from an explicit {@code front} hint or from the
 * doors on the outer walls. Structure voids, jigsaws and block entity contents are dropped.
 */
public final class Template {
    public record Block3(BlockPos pos, BlockState state) {}

    public final String id;
    public final List<Block3> blocks;
    public final int width;
    public final int depth;
    public final int minY;
    public final int maxY;
    public final int doorX;
    /** Exterior ground relative to the interior floor. Negative for raised entrances. */
    public final int terrainOffset;

    private Template(String id, List<Block3> blocks, int width, int depth, int minY, int maxY, int doorX, int terrainOffset) {
        this.id = id;
        this.blocks = blocks;
        this.width = width;
        this.depth = depth;
        this.minY = minY;
        this.maxY = maxY;
        this.doorX = doorX;
        this.terrainOffset = terrainOffset;
    }

    public static final int MAX_SIZE = 48;

    // ================================================================== reading

    /** Reads a gzip-compressed {@code .nbt} or {@code .schem}; {@code front} may be null (auto). */
    public static Template read(String id, InputStream in, String fileName, Direction front) throws Exception {
        CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.create(64L * 1024 * 1024));
        String lower = fileName.toLowerCase(Locale.ROOT);
        Raw raw = lower.endsWith(".schem") || lower.endsWith(".schematic") || root.contains("Schematic") || root.contains("BlockData")
                ? readSponge(root) : readStructure(root);
        if (raw == null) throw new IllegalArgumentException("Unsupported schematic format");
        if (front == null) front = hintFromName(fileName);
        return normalize(id, raw, front);
    }

    /** Parses an already loaded vanilla structure compound (e.g. from StructureTemplate.save). */
    public static Template fromStructureTag(String id, CompoundTag root, Direction front) {
        Raw raw = readStructure(root);
        return raw == null ? null : normalize(id, raw, front);
    }

    /** "house_front-east.schem" → EAST. */
    static Direction hintFromName(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (n.contains("front-" + d.getName()) || n.contains("front_" + d.getName())) return d;
        }
        return null;
    }

    /** Raw template: blocks in file coordinates plus optional entrance info. */
    private static final class Raw {
        int sx, sy, sz;
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos entrance;
        Direction entranceFacing;
    }

    private static BlockState stateFrom(CompoundTag p) {
        // vanilla uses Name/Properties; newer tooling writes id/properties
        if (!p.contains("Name") && p.contains("id")) {
            CompoundTag c = new CompoundTag();
            c.putString("Name", p.getString("id"));
            if (p.contains("properties")) c.put("Properties", p.getCompound("properties"));
            p = c;
        }
        ResourceLocation rl = ResourceLocation.tryParse(p.getString("Name"));
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return null;
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), p);
    }

    private static Raw readStructure(CompoundTag root) {
        if (!root.contains("size") || !root.contains("blocks")) return null;
        ListTag size = root.getList("size", Tag.TAG_INT);
        Raw raw = new Raw();
        raw.sx = size.getInt(0);
        raw.sy = size.getInt(1);
        raw.sz = size.getInt(2);
        ListTag palette = root.contains("palette") ? root.getList("palette", Tag.TAG_COMPOUND)
                : root.getList("palettes", Tag.TAG_LIST).isEmpty() ? new ListTag() : root.getList("palettes", Tag.TAG_LIST).getList(0);
        List<BlockState> pal = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) pal.add(stateFrom(palette.getCompound(i)));
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            int si = b.getInt("state");
            if (si < 0 || si >= pal.size()) continue;
            BlockState st = pal.get(si);
            if (st == null) continue;
            ListTag p = b.getList("pos", Tag.TAG_INT);
            BlockPos pos = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
            if (st.is(Blocks.JIGSAW)) {
                CompoundTag nbt = b.getCompound("nbt");
                String name = nbt.getString("name");
                Direction facing = jigsawFacing(st);
                if ((name.endsWith("building_entrance") || raw.entrance == null && onEdge(pos, raw, facing)) && facing != null && facing.getAxis().isHorizontal()) {
                    raw.entrance = pos;
                    raw.entranceFacing = facing;
                }
                BlockState fin = Json.state(nbt.getString("final_state"), Blocks.AIR.defaultBlockState());
                if (fin.is(Blocks.STRUCTURE_VOID)) continue;
                st = fin;
            }
            if (st.is(Blocks.STRUCTURE_VOID) || st.is(Blocks.STRUCTURE_BLOCK)) continue;
            raw.blocks.put(pos, st);
        }
        return raw;
    }

    private static boolean onEdge(BlockPos p, Raw raw, Direction f) {
        if (f == null) return false;
        return switch (f) {
            case NORTH -> p.getZ() == 0;
            case SOUTH -> p.getZ() == raw.sz - 1;
            case WEST -> p.getX() == 0;
            case EAST -> p.getX() == raw.sx - 1;
            default -> false;
        };
    }

    private static Direction jigsawFacing(BlockState st) {
        if (!st.hasProperty(BlockStateProperties.ORIENTATION)) return null;
        return st.getValue(BlockStateProperties.ORIENTATION).front();
    }

    /** Sponge schematic v2 (root) and v3 (root "Schematic" with "Blocks"). */
    private static Raw readSponge(CompoundTag root) {
        CompoundTag s = root.contains("Schematic") ? root.getCompound("Schematic") : root;
        Raw raw = new Raw();
        raw.sx = s.getShort("Width") & 0xFFFF;
        raw.sy = s.getShort("Height") & 0xFFFF;
        raw.sz = s.getShort("Length") & 0xFFFF;
        CompoundTag paletteTag;
        byte[] data;
        if (s.contains("Blocks")) { // v3
            CompoundTag blocks = s.getCompound("Blocks");
            paletteTag = blocks.getCompound("Palette");
            data = blocks.getByteArray("Data");
        } else { // v2
            paletteTag = s.getCompound("Palette");
            data = s.getByteArray("BlockData");
        }
        Map<Integer, BlockState> pal = new HashMap<>();
        for (String key : paletteTag.getAllKeys()) {
            pal.put(paletteTag.getInt(key), Json.state(key, null));
        }
        int index = 0;
        int i = 0;
        int total = raw.sx * raw.sy * raw.sz;
        while (i < data.length && index < total) {
            int value = 0, shift = 0;
            byte b;
            do {
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && i < data.length);
            int y = index / (raw.sx * raw.sz);
            int rem = index % (raw.sx * raw.sz);
            int z = rem / raw.sx;
            int x = rem % raw.sx;
            index++;
            BlockState st = pal.get(value);
            if (st == null || st.is(Blocks.STRUCTURE_VOID)) continue;
            raw.blocks.put(new BlockPos(x, y, z), st);
        }
        return raw;
    }

    // ================================================================== normalizing

    private static Template normalize(String id, Raw raw, Direction hint) {
        if (raw.sx > MAX_SIZE || raw.sz > MAX_SIZE || raw.sy > MAX_SIZE) {
            throw new IllegalArgumentException("Schematic too large (" + raw.sx + "×" + raw.sy + "×" + raw.sz + ", max " + MAX_SIZE + ")");
        }
        Direction front = hint;
        BlockPos entrance = raw.entrance;
        if (front == null && raw.entranceFacing != null) front = raw.entranceFacing;
        if (front == null) front = guessFront(raw);
        if (entrance == null) entrance = guessEntrance(raw, front);
        // rotate so that "front" becomes north
        Rotation rot = switch (front) {
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
        int w = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90 ? raw.sz : raw.sx;
        int d = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90 ? raw.sx : raw.sz;
        BlockPos doorFloor = guessEntrance(raw, front);
        // Door floors are explicit; a jigsaw marks walkable space above the floor.
        int groundY = doorFloor != null ? doorFloor.getY()
                : entrance == null ? lowestSolidLayer(raw) : entrance.getY() - 1;
        // Keep normalized block coordinates stable for saved buildings, but place new
        // buildings against the OUTSIDE entrance, not the elevated interior floor.
        BlockState entranceState = raw.entrance == null ? Blocks.AIR.defaultBlockState()
                : raw.blocks.getOrDefault(raw.entrance, Blocks.AIR.defaultBlockState());
        int exteriorY = raw.entrance != null ? raw.entrance.getY()
                - (entranceState.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, raw.entrance) ? 0 : 1)
                : stairApproachGround(raw, doorFloor, front, groundY);
        List<Block3> out = new ArrayList<>(raw.blocks.size());
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : raw.blocks.entrySet()) {
            BlockPos p = rotate(e.getKey(), rot, raw.sx, raw.sz);
            BlockPos local = new BlockPos(p.getX(), p.getY() - groundY, p.getZ());
            out.add(new Block3(local, e.getValue().rotate(rot)));
            if (!e.getValue().isAir()) {
                minY = Math.min(minY, local.getY());
                maxY = Math.max(maxY, local.getY());
            }
        }
        if (out.isEmpty() || minY == Integer.MAX_VALUE) throw new IllegalArgumentException("Empty schematic");
        int doorX = entrance == null ? w / 2 : rotate(entrance, rot, raw.sx, raw.sz).getX();
        return new Template(id, out, w, d, minY, maxY, Math.max(0, Math.min(w - 1, doorX)), Math.min(0, exteriorY - groundY));
    }

    /** Follow the outside approach of schematics without a village entrance marker.
     * Only connected, exposed, bottom-half stairs ascending towards the door count;
     * foundations, cellars and roof decorations do not determine the yard height. */
    private static int stairApproachGround(Raw raw, BlockPos doorFloor, Direction front, int floorY) {
        if (doorFloor == null) return floorY;
        int ground = floorY;
        int supportY = floorY;
        Direction across = front.getClockWise();
        for (int distance = 1; distance <= edgeDistance(doorFloor, raw, front); distance++) {
            BlockPos center = doorFloor.relative(front, distance);
            BlockPos next = null;
            boolean stair = false;
            for (int y = supportY; y >= supportY - 1 && next == null; y--) {
                for (int lateral : new int[]{0, -1, 1}) {
                    BlockPos pos = new BlockPos(center.getX(), y, center.getZ()).relative(across, lateral);
                    BlockState state = raw.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                    BlockState above = raw.blocks.getOrDefault(pos.above(), Blocks.AIR.defaultBlockState());
                    BlockState head = raw.blocks.getOrDefault(pos.above(2), Blocks.AIR.defaultBlockState());
                    if (!above.isAir() && !above.canBeReplaced() || !head.isAir() && !head.canBeReplaced()) continue;
                    boolean step = state.hasProperty(BlockStateProperties.STAIRS_SHAPE)
                            && state.getValue(BlockStateProperties.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM
                            && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == front.getOpposite();
                    boolean landing = y == supportY && state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, pos);
                    if (step || landing) {
                        next = pos;
                        stair = step;
                        break;
                    }
                }
            }
            if (next == null) break;
            supportY = next.getY();
            if (stair) ground = Math.min(ground, supportY - 1);
        }
        return ground;
    }

    private static BlockPos rotate(BlockPos p, Rotation rot, int sx, int sz) {
        int x = p.getX(), z = p.getZ();
        return switch (rot) {
            case CLOCKWISE_90 -> new BlockPos(sz - 1 - z, p.getY(), x);
            case CLOCKWISE_180 -> new BlockPos(sx - 1 - x, p.getY(), sz - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, p.getY(), sx - 1 - x);
            default -> p;
        };
    }

    /** Side with the most doors on (or next to) the outer wall. */
    private static Direction guessFront(Raw raw) {
        int[] score = new int[4];
        for (Map.Entry<BlockPos, BlockState> e : raw.blocks.entrySet()) {
            BlockState s = e.getValue();
            if (!(s.getBlock() instanceof DoorBlock) || s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) continue;
            BlockPos p = e.getKey();
            int[] dist = {p.getZ(), raw.sz - 1 - p.getZ(), p.getX(), raw.sx - 1 - p.getX()}; // N S W E
            int best = 0;
            for (int i = 1; i < 4; i++) if (dist[i] < dist[best]) best = i;
            if (dist[best] <= 2) score[best] += 3 - dist[best];
        }
        int best = 0;
        for (int i = 1; i < 4; i++) if (score[i] > score[best]) best = i;
        if (score[best] == 0) return Direction.NORTH;
        return new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}[best];
    }

    private static BlockPos guessEntrance(Raw raw, Direction front) {
        BlockPos best = null;
        for (Map.Entry<BlockPos, BlockState> e : raw.blocks.entrySet()) {
            if (!(e.getValue().getBlock() instanceof DoorBlock)) continue;
            if (e.getValue().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) continue;
            BlockPos p = e.getKey();
            if (best == null || edgeDistance(p, raw, front) < edgeDistance(best, raw, front)
                    || edgeDistance(p, raw, front) == edgeDistance(best, raw, front)
                    && (p.getY() < best.getY() || p.getY() == best.getY() && p.asLong() < best.asLong())) best = p;
        }
        // the ground is one block below the door
        return best == null ? null : best.below();
    }

    private static int edgeDistance(BlockPos p, Raw raw, Direction f) {
        return switch (f) {
            case SOUTH -> raw.sz - 1 - p.getZ();
            case WEST -> p.getX();
            case EAST -> raw.sx - 1 - p.getX();
            default -> p.getZ();
        };
    }

    /** Without an entrance, anchor to the lowest occupied structural layer.
     * A density threshold can mistake a pavilion's roof for its floor and bury the building. */
    private static int lowestSolidLayer(Raw raw) {
        int lowest = Integer.MAX_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : raw.blocks.entrySet()) {
            BlockState state = e.getValue();
            if (!state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty()
                    && !state.is(net.minecraft.tags.BlockTags.LEAVES)) lowest = Math.min(lowest, e.getKey().getY());
        }
        return lowest == Integer.MAX_VALUE ? 0 : lowest;
    }

    /** Blocks that need support are placed after the structure. */
    public static boolean isDetail(BlockState s) {
        Block b = s.getBlock();
        return b instanceof DoorBlock || b instanceof BedBlock
                || !s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !s.isAir()
                && !s.hasProperty(BlockStateProperties.STAIRS_SHAPE) && !s.hasProperty(BlockStateProperties.SLAB_TYPE)
                && !s.hasProperty(BlockStateProperties.NORTH) // fences, panes, walls
                && !s.is(Blocks.FARMLAND) && !s.is(Blocks.DIRT_PATH) && s.getFluidState().isEmpty();
    }

    @Override
    public String toString() {
        return id + " " + width + "x" + (maxY - minY + 1) + "x" + depth;
    }

    static void log(String msg, Object... args) {
        Hearthbound.LOGGER.info(msg, args);
    }
}
