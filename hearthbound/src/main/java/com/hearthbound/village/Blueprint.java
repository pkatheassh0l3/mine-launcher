package com.hearthbound.village;

import com.hearthbound.data.BuildingDef;
import com.hearthbound.data.Culture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Procedural building generator. Buildings are described in a local frame
 * (x = width, left to right; z = depth, front (door) to back; y = 0 is the floor) and
 * turned to face the village square when placed. The same {@link BuildingDef.Kind}
 * looks different in every culture because all blocks come from the culture palette.
 */
public final class Blueprint {
    public enum Phase {
        TREES, CUT, FILL, FOUNDATION, CLEAR, STRUCTURE, DETAIL;

        /** Phases removing blocks go top-down, the rest bottom-up. */
        public boolean topDown() {
            return this == TREES || this == CUT || this == CLEAR;
        }
    }

    public record Entry(BlockState state, Phase phase) {}

    public record Placement(BlockPos pos, BlockState state, Phase phase) {}

    private static final int FLOOR_H = 4;

    private final Map<BlockPos, Entry> map = new LinkedHashMap<>();
    private final Culture c;
    private final RandomSource rand;
    private final int w;
    private final int d;
    private int maxY = 1;
    private int bottomY = 0;

    private Blueprint(Culture c, int w, int d, long seed) {
        this.c = c;
        this.w = w;
        this.d = d;
        this.rand = RandomSource.create(seed);
    }

    public int height() {
        return maxY;
    }

    public Map<BlockPos, Entry> blocks() {
        return map;
    }

    // ================================================================== palette

    private BlockState p(String key, Block def) {
        return c == null ? def.defaultBlockState() : c.block(key, def.defaultBlockState());
    }

    private BlockState foundation() { return p("foundation", Blocks.COBBLESTONE); }
    private BlockState floor() { return p("floor", Blocks.SPRUCE_PLANKS); }
    private BlockState wall() { return p("wall", Blocks.OAK_PLANKS); }
    private BlockState pillar() { return p("pillar", Blocks.STRIPPED_OAK_LOG); }
    private BlockState trim() { return p("trim", Blocks.STONE_BRICKS); }
    private BlockState roofStairs() { return p("roof_stairs", Blocks.SPRUCE_STAIRS); }
    private BlockState roofSlab() { return p("roof_slab", Blocks.SPRUCE_SLAB); }
    private BlockState roofBlock() { return p("roof_block", Blocks.SPRUCE_PLANKS); }
    private BlockState window() { return p("window", Blocks.GLASS_PANE); }
    private BlockState door() { return p("door", Blocks.OAK_DOOR); }
    private BlockState fence() { return p("fence", Blocks.OAK_FENCE); }
    private BlockState gate() { return p("gate", Blocks.OAK_FENCE_GATE); }
    private BlockState light() { return p("light", Blocks.LANTERN); }
    private BlockState accent() { return p("accent", Blocks.RED_WOOL); }
    private BlockState carpet() { return p("carpet", Blocks.RED_CARPET); }
    private BlockState bed() { return p("bed", Blocks.RED_BED); }
    private BlockState crop() { return p("crop", Blocks.WHEAT); }
    private BlockState table() { return p("table", Blocks.OAK_PRESSURE_PLATE); }
    private BlockState seat() { return p("seat", Blocks.OAK_STAIRS); }
    private BlockState decor() { return p("decor", Blocks.POTTED_POPPY); }
    private BlockState banner() { return p("banner_block", Blocks.RED_WOOL); }
    private BlockState specialWindow() { return p("special_window", Blocks.PURPLE_STAINED_GLASS_PANE); }

    // ================================================================== state helpers

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> prop, T value) {
        return s.hasProperty(prop) ? s.setValue(prop, value) : s;
    }

    private static BlockState axis(BlockState s, Direction.Axis a) {
        return with(s, BlockStateProperties.AXIS, a);
    }

    private static BlockState facing(BlockState s, Direction f) {
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.setValue(BlockStateProperties.HORIZONTAL_FACING, f);
        if (s.hasProperty(BlockStateProperties.FACING)) return s.setValue(BlockStateProperties.FACING, f);
        return s;
    }

    private static BlockState stairs(BlockState s, Direction f, boolean top) {
        s = facing(s, f);
        return with(s, BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    private static BlockState slab(BlockState s, SlabType type) {
        return with(s, BlockStateProperties.SLAB_TYPE, type);
    }

    // ================================================================== writers

    private void put(int x, int y, int z, BlockState s, Phase ph) {
        map.put(new BlockPos(x, y, z), new Entry(s, ph));
        if (y > maxY) maxY = y;
    }

    private void set(int x, int y, int z, BlockState s) {
        put(x, y, z, s, Phase.STRUCTURE);
    }

    private void detail(int x, int y, int z, BlockState s) {
        put(x, y, z, s, Phase.DETAIL);
    }

    private void air(int x, int y, int z) {
        put(x, y, z, Blocks.AIR.defaultBlockState(), Phase.STRUCTURE);
    }

    private boolean isSet(int x, int y, int z) {
        Entry e = map.get(new BlockPos(x, y, z));
        return e != null && !e.state.isAir();
    }

    private void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
    }

    private void door(int x, int z, int y, Direction face) {
        BlockState dr = facing(door(), face);
        dr = with(dr, BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT);
        detail(x, y, z, with(dr, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        detail(x, y + 1, z, with(dr, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** Bed with its foot at (x, z) and head one block in {@code dir}. */
    private void bed(int x, int y, int z, Direction dir) {
        BlockState b = facing(bed(), dir);
        detail(x, y, z, with(b, BlockStateProperties.BED_PART, BedPart.FOOT));
        detail(x + dir.getStepX(), y, z + dir.getStepZ(), with(b, BlockStateProperties.BED_PART, BedPart.HEAD));
    }

    private void tableAt(int x, int y, int z) {
        set(x, y, z, fence());
        detail(x, y + 1, z, table());
    }

    private void ladder(int x, int z, int y0, int y1, Direction face) {
        for (int y = y0; y <= y1; y++) {
            air(x, y, z);
            detail(x, y, z, facing(Blocks.LADDER.defaultBlockState(), face));
        }
    }

    // ================================================================== shells

    /** Walls, pillars, beams, windows and inner floors. Returns the y of the wall top (roof base). */
    private int shell(int floors, boolean windows, BlockState wallBlock, boolean timber) {
        int top = floors * FLOOR_H;
        box(0, 0, 0, w - 1, 0, d - 1, floor());
        for (int y = 1; y < top; y++) {
            boolean beamRow = timber && y % FLOOR_H == 0;
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edgeX = x == 0 || x == w - 1;
                    boolean edgeZ = z == 0 || z == d - 1;
                    if (!edgeX && !edgeZ) {
                        if (y % FLOOR_H == 0) set(x, y, z, floor()); // upper floors
                        else air(x, y, z);
                        continue;
                    }
                    boolean corner = edgeX && edgeZ;
                    boolean midPillar = timber && ((edgeZ && (x % 4 == 0)) || (edgeX && (z % 4 == 0)));
                    if (corner || midPillar) set(x, y, z, axis(pillar(), Direction.Axis.Y));
                    else if (beamRow) set(x, y, z, axis(pillar(), edgeZ ? Direction.Axis.X : Direction.Axis.Z));
                    else set(x, y, z, wallBlock);
                }
            }
        }
        // top beam ring
        for (int x = 0; x < w; x++) {
            set(x, top, 0, timber ? axis(pillar(), Direction.Axis.X) : wallBlock);
            set(x, top, d - 1, timber ? axis(pillar(), Direction.Axis.X) : wallBlock);
        }
        for (int z = 0; z < d; z++) {
            set(0, top, z, timber ? axis(pillar(), Direction.Axis.Z) : wallBlock);
            set(w - 1, top, z, timber ? axis(pillar(), Direction.Axis.Z) : wallBlock);
        }
        if (windows) {
            for (int f = 0; f < floors; f++) {
                int y = f * FLOOR_H + 2;
                for (int x = 2; x < w - 2; x += 2) {
                    if (Math.abs(x - w / 2) > 1 || f > 0) setWindow(x, y, 0);
                    setWindow(x, y, d - 1);
                }
                for (int z = 2; z < d - 2; z += 2) {
                    setWindow(0, y, z);
                    setWindow(w - 1, y, z);
                }
            }
        }
        door(w / 2, 0, 1, Direction.NORTH);
        return top;
    }

    private void setWindow(int x, int y, int z) {
        Entry e = map.get(new BlockPos(x, y, z));
        if (e != null && e.state.is(BlockTags.LOGS)) return;
        set(x, y, z, window());
    }

    /** Open pavilion: floor, pillars on the corners and every few blocks, beams on top. */
    private int pavilion(int wallBackRows) {
        int top = FLOOR_H;
        box(0, 0, 0, w - 1, 0, d - 1, trim());
        for (int y = 1; y < top; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edgeX = x == 0 || x == w - 1;
                    boolean edgeZ = z == 0 || z == d - 1;
                    if (!edgeX && !edgeZ) {
                        air(x, y, z);
                        continue;
                    }
                    boolean post = (edgeX && edgeZ) || (edgeZ && x % 4 == 0) || (edgeX && z % 4 == 0);
                    boolean backWall = z >= d - wallBackRows && (z == d - 1 || edgeX);
                    if (post) set(x, y, z, axis(pillar(), Direction.Axis.Y));
                    else if (backWall) set(x, y, z, wall());
                    else air(x, y, z);
                }
            }
        }
        for (int x = 0; x < w; x++) {
            set(x, top, 0, axis(pillar(), Direction.Axis.X));
            set(x, top, d - 1, axis(pillar(), Direction.Axis.X));
        }
        for (int z = 0; z < d; z++) {
            set(0, top, z, axis(pillar(), Direction.Axis.Z));
            set(w - 1, top, z, axis(pillar(), Direction.Axis.Z));
        }
        return top;
    }

    // ================================================================== roofs

    private void roof(Culture.RoofStyle style, int top) {
        switch (style) {
            case FLAT -> flatRoof(top);
            case TIERED -> hipRoof(top);
            case STEEP -> gableRoof(top, true);
            default -> gableRoof(top, false);
        }
    }

    /** Gable roof with a ridge along the longest side; {@code steep} doubles the pitch. */
    private void gableRoof(int top, boolean steep) {
        boolean alongX = w >= d;
        int span = alongX ? d : w;      // across the slope
        int len = alongX ? w : d;       // along the ridge
        Direction up1 = alongX ? Direction.SOUTH : Direction.EAST;  // low side at v = -1
        Direction up2 = alongX ? Direction.NORTH : Direction.WEST;  // low side at v = span
        int rise = steep ? 2 : 1;
        for (int i = 0; ; i++) {
            int vf = -1 + i;
            int vb = span - i;
            int y = top + i * rise;
            if (vf > vb) break;
            for (int u = -1; u <= len; u++) {
                if (vf == vb) {
                    put2(alongX, u, y, vf, slab(roofSlab(), SlabType.BOTTOM));
                    if (steep) put2(alongX, u, y - 1, vf, roofBlock());
                } else {
                    put2(alongX, u, y, vf, stairs(roofStairs(), up1, false));
                    put2(alongX, u, y, vb, stairs(roofStairs(), up2, false));
                    if (steep) {
                        put2(alongX, u, y - 1, vf, u == -1 || u == len ? stairs(roofStairs(), up2.getOpposite(), true) : roofBlock());
                        put2(alongX, u, y - 1, vb, u == -1 || u == len ? stairs(roofStairs(), up1.getOpposite(), true) : roofBlock());
                    }
                }
            }
            // gable walls
            for (int r = 0; r < rise; r++) {
                int yy = y - r;
                if (yy <= top) continue;
                for (int v = Math.max(0, vf + 1); v <= Math.min(span - 1, vb - 1); v++) {
                    put2(alongX, 0, yy, v, wall());
                    put2(alongX, len - 1, yy, v, wall());
                }
            }
            if (vf == vb || vf + 1 == vb) {
                if (vf + 1 == vb) {
                    // ridge cap
                    for (int u = -1; u <= len; u++) {
                        if (steep) continue;
                    }
                }
                break;
            }
        }
        // gable accent: a light on the front gable
    }

    private void put2(boolean alongX, int u, int y, int v, BlockState s) {
        if (alongX) set(u, y, v, s);
        else set(v, y, u, s);
    }

    private void flatRoof(int top) {
        box(0, top, 0, w - 1, top, d - 1, roofBlock());
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (!edge) continue;
                if ((x + z) % 2 == 0) set(x, top + 1, z, trim());
                else set(x, top + 1, z, slab(roofSlab(), SlabType.BOTTOM));
            }
        }
    }

    private void hipRoof(int top) {
        for (int i = 0; ; i++) {
            int x0 = -1 + i, x1 = w - i, z0 = -1 + i, z1 = d - i;
            int y = top + i;
            if (x0 > x1 || z0 > z1) break;
            if (x1 - x0 <= 1 || z1 - z0 <= 1) {
                for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) set(x, y, z, roofBlock());
                set((x0 + x1) / 2, y + 1, (z0 + z1) / 2, slab(roofSlab(), SlabType.BOTTOM));
                break;
            }
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, stairs(roofStairs(), Direction.SOUTH, false));
                set(x, y, z1, stairs(roofStairs(), Direction.NORTH, false));
            }
            for (int z = z0 + 1; z < z1; z++) {
                set(x0, y, z, stairs(roofStairs(), Direction.EAST, false));
                set(x1, y, z, stairs(roofStairs(), Direction.WEST, false));
            }
            // fill the ring under the next layer so the roof is closed
            if (i >= 1) {
                for (int x = x0 + 1; x < x1; x++) {
                    if (!isSet(x, y, z0 + 1)) set(x, y, z0 + 1, roofBlock());
                    if (!isSet(x, y, z1 - 1)) set(x, y, z1 - 1, roofBlock());
                }
                for (int z = z0 + 1; z < z1; z++) {
                    if (!isSet(x0 + 1, y, z)) set(x0 + 1, y, z, roofBlock());
                    if (!isSet(x1 - 1, y, z)) set(x1 - 1, y, z, roofBlock());
                }
            }
        }
    }

    // ================================================================== furnishing helpers

    private void lanternsInCorners(int y) {
        detail(1, y, 1, light());
        detail(w - 2, y, d - 2, light());
    }

    private void carpetRunner(int y) {
        for (int z = 1; z < d - 2; z++) detail(w / 2, y, z, carpet());
    }

    // ================================================================== kinds

    public static Blueprint generate(Culture culture, BuildingDef def, long seed) {
        Blueprint b = new Blueprint(culture, def.width, def.depth, seed);
        Culture.RoofStyle style = culture == null ? Culture.RoofStyle.GABLE : culture.roof;
        switch (def.kind) {
            case HALL -> b.hall(def.floors, style);
            case FARM -> b.farm();
            case FORGE -> b.forge(style);
            case MARKET -> b.market(style);
            case TAVERN -> b.tavern(Math.max(2, def.floors), style);
            case TOWER -> b.tower(Math.max(2, def.floors));
            case SHRINE -> b.shrine(style);
            case MAGE_TOWER -> b.mageTower(Math.max(2, def.floors));
            case STOREHOUSE -> b.storehouse(style);
            default -> b.house(def.floors, style);
        }
        return b;
    }

    private void house(int floors, Culture.RoofStyle style) {
        int top = shell(floors, true, wall(), true);
        // beds along the back wall
        bed(1, 1, d - 3, Direction.SOUTH);
        if (w >= 7) bed(3, 1, d - 3, Direction.SOUTH);
        detail(w - 2, 1, d - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
        detail(w - 2, 1, d - 3, facing(Blocks.BARREL.defaultBlockState(), Direction.UP));
        detail(w - 2, 1, 1, light());
        if (w >= 7) detail(1, 1, 1, decor());
        if (floors > 1) {
            ladder(w - 2, 1, 1, top - 1, Direction.SOUTH);
            // move the lantern out of the ladder column
            detail(1, 1, 1, light());
            for (int f = 1; f < floors; f++) {
                int y = f * FLOOR_H + 1;
                bed(1, y, d - 3, Direction.SOUTH);
                detail(w - 2, y, d - 2, facing(Blocks.CHEST.defaultBlockState(), Direction.NORTH));
                detail(2, y, 1, light());
            }
        }
        roof(style, top);
        if (style == Culture.RoofStyle.FLAT) detail(w / 2, top + 1, d / 2, light());
    }

    private void hall(int floors, Culture.RoofStyle style) {
        int top = shell(Math.max(1, floors), true, wall(), true);
        carpetRunner(1);
        detail(w / 2, 1, d - 2, facing(Blocks.LECTERN.defaultBlockState(), Direction.NORTH));
        for (int x = 1; x < w - 1; x++) {
            if (Math.abs(x - w / 2) <= 1) continue;
            set(x, 1, d - 2, Blocks.BOOKSHELF.defaultBlockState());
            if (top > 3) set(x, 2, d - 2, Blocks.BOOKSHELF.defaultBlockState());
        }
        bed(1, 1, 2, Direction.NORTH);
        bed(w - 2, 1, 2, Direction.NORTH);
        tableAt(2, 1, d / 2);
        tableAt(w - 3, 1, d / 2);
        detail(w / 2 - 1, 1, 1, light());
        detail(w / 2 + 1, 1, 1, light());
        detail(1, 1, d - 3, facing(Blocks.CHEST.defaultBlockState(), Direction.EAST));
        detail(w / 2, 1, 2, with(facing(Blocks.BELL.defaultBlockState(), Direction.NORTH), BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR));
        roof(style, top);
        // banners of the culture beside the door
        set(w / 2 - 1, 3, 0, banner());
        set(w / 2 + 1, 3, 0, banner());
    }

    private void farm() {
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
        int wx = w / 2;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (edge) {
                    set(x, 0, z, foundation());
                    set(x, 1, z, fence());
                    for (int y = 2; y <= 3; y++) air(x, y, z);
                    continue;
                }
                if (x == wx) {
                    set(x, 0, z, Blocks.WATER.defaultBlockState());
                    air(x, 1, z);
                } else {
                    set(x, 0, z, farmland);
                    BlockState cr = crop();
                    if (cr.getBlock() instanceof CropBlock crop) {
                        cr = crop.getStateForAge(Math.min(crop.getMaxAge(), 1 + rand.nextInt(crop.getMaxAge())));
                    }
                    detail(x, 1, z, cr);
                }
                for (int y = 2; y <= 3; y++) air(x, y, z);
            }
        }
        set(wx, 1, 0, facing(gate(), Direction.NORTH));
        set(wx, 0, 0, Blocks.WATER.defaultBlockState()); // keeps the front of the channel flowing
        set(wx, 0, 0, foundation());
        // scarecrow
        set(1, 0, d - 2, foundation());
        set(1, 1, d - 2, fence());
        set(1, 2, d - 2, Blocks.HAY_BLOCK.defaultBlockState());
        set(1, 3, d - 2, facing(Blocks.CARVED_PUMPKIN.defaultBlockState(), Direction.NORTH));
        set(w - 2, 0, 1, foundation());
        detail(w - 2, 1, 1, Blocks.COMPOSTER.defaultBlockState());
        detail(0, 2, 0, light());
        detail(w - 1, 2, 0, light());
        maxY = Math.max(maxY, 3);
    }

    private void forge(Culture.RoofStyle style) {
        int top = pavilion(d / 2);
        detail(2, 1, 2, facing(Blocks.ANVIL.defaultBlockState(), Direction.EAST));
        detail(1, 1, d - 2, facing(Blocks.BLAST_FURNACE.defaultBlockState(), Direction.NORTH));
        detail(2, 1, d - 2, facing(Blocks.FURNACE.defaultBlockState(), Direction.NORTH));
        detail(3, 1, d - 2, Blocks.SMITHING_TABLE.defaultBlockState());
        detail(w - 3, 1, d - 2, with(facing(Blocks.GRINDSTONE.defaultBlockState(), Direction.NORTH), BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
        detail(w - 2, 1, d - 2, Blocks.LAVA_CAULDRON.defaultBlockState());
        detail(w - 2, 1, 2, facing(Blocks.CHEST.defaultBlockState(), Direction.WEST));
        detail(w - 2, 1, 1, light());
        detail(1, 1, 1, light());
        roof(style == Culture.RoofStyle.FLAT ? Culture.RoofStyle.FLAT : Culture.RoofStyle.GABLE, top);
    }

    private void market(Culture.RoofStyle style) {
        int top = pavilion(0);
        for (int x = 2; x < w - 2; x++) {
            if (Math.abs(x - w / 2) <= 0) continue;
            set(x, 1, 2, accent());
            if (x % 2 == 0) detail(x, 2, 2, light());
            set(x, 1, d - 3, accent());
        }
        detail(1, 1, d - 2, facing(Blocks.BARREL.defaultBlockState(), Direction.UP));
        detail(w - 2, 1, d - 2, facing(Blocks.BARREL.defaultBlockState(), Direction.UP));
        detail(1, 1, 1, Blocks.COMPOSTER.defaultBlockState());
        detail(w - 2, 1, 1, decor());
        roof(style == Culture.RoofStyle.STEEP ? Culture.RoofStyle.GABLE : style, top);
    }

    private void tavern(int floors, Culture.RoofStyle style) {
        int top = shell(floors, true, wall(), true);
        // bar counter
        for (int x = 1; x < w - 1; x++) {
            if (x == w - 2) continue;
            set(x, 1, d - 3, accent());
        }
        detail(2, 2, d - 3, Blocks.BREWING_STAND.defaultBlockState());
        detail(4, 2, d - 3, light());
        for (int x = 1; x < w - 1; x++) {
            detail(x, 1, d - 2, facing(Blocks.BARREL.defaultBlockState(), Direction.NORTH));
        }
        detail(w - 2, 1, d - 2, facing(Blocks.SMOKER.defaultBlockState(), Direction.NORTH));
        // tables with stools
        for (int tx = 2; tx < w - 2; tx += 4) {
            tableAt(tx, 1, 2);
            detail(tx - 1, 1, 2, stairs(seat(), Direction.WEST, false));
            detail(tx + 1, 1, 2, stairs(seat(), Direction.EAST, false));
        }
        ladder(w - 2, 1, 1, top - 1, Direction.SOUTH);
        for (int f = 1; f < floors; f++) {
            int y = f * FLOOR_H + 1;
            bed(1, y, d - 3, Direction.SOUTH);
            bed(3, y, d - 3, Direction.SOUTH);
            detail(1, y, 1, light());
            detail(w - 3, y, d - 2, facing(Blocks.CHEST.defaultBlockState(), Direction.NORTH));
        }
        roof(style, top);
        set(w / 2 - 1, 3, 0, banner());
    }

    private void tower(int floors) {
        int top = shell(floors, false, trim(), false);
        // arrow slits
        for (int f = 0; f < floors; f++) {
            int y = f * FLOOR_H + 2;
            set(w / 2, y, d - 1, window());
            set(0, y, d / 2, window());
            set(w - 1, y, d / 2, window());
        }
        ladder(1, d - 2, 1, top, Direction.NORTH);
        air(1, top, d - 2);
        detail(1, top, d - 2, facing(Blocks.LADDER.defaultBlockState(), Direction.NORTH));
        box(1, top, 1, w - 2, top, d - 3, floor());
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (edge && (x + z) % 2 == 0) set(x, top + 1, z, trim());
            }
        }
        // flag
        set(w - 1, top + 1, 0, fence());
        set(w - 1, top + 2, 0, fence());
        set(w - 1, top + 3, 0, banner());
        detail(w / 2, top + 1, d / 2, light());
        detail(1, 1, 1, light());
        detail(w - 2, 1, 1, facing(Blocks.CHEST.defaultBlockState(), Direction.SOUTH));
    }

    private void shrine(Culture.RoofStyle style) {
        int top = shell(1, false, wall(), false);
        // columns instead of side walls
        for (int z = 1; z < d - 1; z++) {
            for (int y = 1; y < top; y++) {
                if (z % 2 == 1) set(0, y, z, trim());
                else set(0, y, z, air0());
                if (z % 2 == 1) set(w - 1, y, z, trim());
                else set(w - 1, y, z, air0());
            }
        }
        box(1, 0, 1, w - 2, 0, d - 2, trim());
        carpetRunner(1);
        set(w / 2, 1, d - 2, accent());
        detail(w / 2, 2, d - 2, light());
        detail(w / 2 - 1, 1, d - 2, decor());
        detail(w / 2 + 1, 1, d - 2, decor());
        detail(w / 2, 1, 1, with(facing(Blocks.BELL.defaultBlockState(), Direction.NORTH), BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR));
        bed(1, 1, d - 3, Direction.SOUTH);
        roof(style == Culture.RoofStyle.FLAT ? Culture.RoofStyle.TIERED : style, top);
    }

    private BlockState air0() {
        return Blocks.AIR.defaultBlockState();
    }

    private void mageTower(int floors) {
        int top = shell(floors, false, wall(), true);
        for (int f = 0; f < floors; f++) {
            int y = f * FLOOR_H + 2;
            for (int x = 2; x < w - 2; x += 2) {
                set(x, y, 0, specialWindow());
                set(x, y, d - 1, specialWindow());
            }
            set(0, y, d / 2, specialWindow());
            set(w - 1, y, d / 2, specialWindow());
        }
        detail(w / 2, 1, d / 2, Blocks.ENCHANTING_TABLE.defaultBlockState());
        for (int x = 1; x < w - 1; x++) set(x, 1, d - 2, Blocks.BOOKSHELF.defaultBlockState());
        set(1, 2, d - 2, Blocks.BOOKSHELF.defaultBlockState());
        set(w - 2, 2, d - 2, Blocks.BOOKSHELF.defaultBlockState());
        ladder(1, 1, 1, top - 1, Direction.SOUTH);
        detail(w - 2, 1, 1, Blocks.CAULDRON.defaultBlockState());
        detail(w - 2, 1, 2, Blocks.BREWING_STAND.defaultBlockState());
        detail(2, 1, d - 3, light());
        for (int f = 1; f < floors; f++) {
            int y = f * FLOOR_H + 1;
            bed(w - 2, y, d - 3, Direction.SOUTH);
            detail(w / 2, y, d - 2, facing(Blocks.LECTERN.defaultBlockState(), Direction.NORTH));
            detail(2, y, d - 2, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            detail(w - 2, y, 1, light());
        }
        gableRoof(top, true);
        set(w / 2, maxY + 1, d / 2, Blocks.AMETHYST_BLOCK.defaultBlockState());
    }

    private void storehouse(Culture.RoofStyle style) {
        int top = shell(1, true, wall(), true);
        for (int x = 1; x < w - 1; x++) {
            if (x == w / 2) continue;
            detail(x, 1, d - 2, facing(Blocks.BARREL.defaultBlockState(), Direction.NORTH));
            detail(x, 2, d - 2, facing(Blocks.BARREL.defaultBlockState(), Direction.NORTH));
        }
        for (int z = 1; z < d - 2; z++) {
            set(1, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
            detail(w - 2, 1, z, facing(Blocks.CHEST.defaultBlockState(), Direction.WEST));
        }
        detail(w / 2, 1, d - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
        detail(w / 2 - 1, 1, 1, light());
        roof(style, top);
    }

    // ================================================================== schematics

    /** Blueprint from a normalized schematic (entrance at local y = 0, facing local north). */
    public static Blueprint fromTemplate(Culture culture, Template t) {
        Blueprint b = new Blueprint(culture, t.width, t.depth, 0);
        for (Template.Block3 blk : t.blocks) {
            BlockState s = blk.state();
            Phase ph = s.isAir() ? Phase.STRUCTURE : Template.isDetail(s) ? Phase.DETAIL : Phase.STRUCTURE;
            b.map.put(blk.pos(), new Entry(s, ph));
        }
        b.maxY = Math.max(1, t.maxY);
        b.bottomY = t.minY;
        return b;
    }

    // ================================================================== plaza

    /** The village square: a paved circle with the hearth in the middle and lamp posts. */
    public static List<Placement> plaza(Culture c, BlockPos center) {
        List<Placement> out = new ArrayList<>();
        BlockState paving = c == null ? Blocks.STONE_BRICKS.defaultBlockState() : c.block("plaza", Blocks.STONE_BRICKS.defaultBlockState());
        BlockState post = c == null ? Blocks.OAK_FENCE.defaultBlockState() : c.block("fence", Blocks.OAK_FENCE.defaultBlockState());
        BlockState lamp = c == null ? Blocks.LANTERN.defaultBlockState() : c.block("light", Blocks.LANTERN.defaultBlockState());
        int r = 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                out.add(new Placement(center.offset(dx, -1, dz), paving, Phase.STRUCTURE));
                for (int y = 0; y <= 3; y++) {
                    out.add(new Placement(center.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), Phase.CLEAR));
                }
            }
        }
        int[][] corners = {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
        for (int[] k : corners) {
            out.add(new Placement(center.offset(k[0], 0, k[1]), post, Phase.STRUCTURE));
            out.add(new Placement(center.offset(k[0], 1, k[1]), post, Phase.STRUCTURE));
            out.add(new Placement(center.offset(k[0], 2, k[1]), lamp, Phase.DETAIL));
        }
        return out;
    }

    // ================================================================== world transform

    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Local (x, y, z) → world, where local north (−z) points to {@code facing}. */
    public static BlockPos toWorld(BlockPos origin, Direction facing, BlockPos local) {
        int x = local.getX(), z = local.getZ();
        int wx, wz;
        switch (facing) {
            case EAST -> { wx = -z; wz = x; }
            case SOUTH -> { wx = -x; wz = -z; }
            case WEST -> { wx = z; wz = -x; }
            default -> { wx = x; wz = z; }
        }
        return origin.offset(wx, local.getY(), wz);
    }

    /** World bounds of a {@code w × d} footprint, {@code h} high, with a margin. */
    public static BoundingBox bounds(BlockPos origin, Direction facing, int w, int d, int h, int margin) {
        BlockPos a = toWorld(origin, facing, new BlockPos(-margin, 0, -margin));
        BlockPos b = toWorld(origin, facing, new BlockPos(w - 1 + margin, h, d - 1 + margin));
        return BoundingBox.fromCorners(a, b);
    }

    /**
     * Resolves the blueprint against the world: adds a foundation under the floor down to
     * the ground and clears the air space, then sorts everything in build order.
     */
    public List<Placement> resolve(ServerLevel level, BlockPos origin, Direction facing) {
        return resolve(level, origin, facing, List.of());
    }

    /**
     * @param ground terraforming placements (tree felling, cuts and fills) computed around the plot;
     *               the building's own blocks override them where they overlap
     */
    public List<Placement> resolve(ServerLevel level, BlockPos origin, Direction facing, List<Placement> ground) {
        return resolve(level, origin, facing, ground, 0);
    }

    public List<Placement> resolve(ServerLevel level, BlockPos origin, Direction facing, List<Placement> ground, int terrainOffset) {
        Rotation rot = rotation(facing);
        Map<BlockPos, Placement> world = new LinkedHashMap<>();
        for (Placement pl : ground) world.put(pl.pos(), pl);
        BlockState found = foundation();
        // foundation columns (under the footprint)
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                BlockPos col = toWorld(origin, facing, new BlockPos(x, bottomY, z));
                for (int dy = 1; dy <= 12; dy++) {
                    BlockPos p = col.below(dy);
                    BlockState s = level.getBlockState(p);
                    if (!s.canBeReplaced() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) break;
                    world.put(p, new Placement(p, found, Phase.FOUNDATION));
                }
            }
        }
        // clear the volume (1 block margin around the footprint, up to the roof + 2)
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                // Sparse schematics omit air, including cellars below their entrance.
                int clearFrom = x >= 0 && x < w && z >= 0 && z < d ? Math.min(bottomY + 1, terrainOffset + 1) : terrainOffset + 1;
                for (int y = clearFrom; y <= maxY + 2; y++) {
                    BlockPos p = toWorld(origin, facing, new BlockPos(x, y, z));
                    world.put(p, new Placement(p, Blocks.AIR.defaultBlockState(), Phase.CLEAR));
                }
            }
        }
        for (Map.Entry<BlockPos, Entry> e : map.entrySet()) {
            BlockPos p = toWorld(origin, facing, e.getKey());
            BlockState s = e.getValue().state.rotate(rot);
            Phase ph = e.getValue().state.isAir() ? Phase.CLEAR : e.getValue().phase;
            world.put(p, new Placement(p, s, ph));
        }
        List<Placement> out = new ArrayList<>(world.values());
        out.sort(Comparator.<Placement>comparingInt(pl -> pl.phase().ordinal())
                .thenComparingInt(pl -> pl.phase().topDown() ? -pl.pos().getY() : pl.pos().getY()));
        return out;
    }
}
