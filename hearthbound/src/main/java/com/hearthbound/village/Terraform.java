package com.hearthbound.village;

import com.hearthbound.config.HBConfig;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.world.PlayerBuilds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prepares the ground for a building the way a real village would:
 * <ol>
 *   <li>fells every tree that touches the area (whole trees, not just the blocks in the way),</li>
 *   <li>levels the footprint and a small yard to the floor height,</li>
 *   <li>blends a smooth slope from the yard back into the natural terrain, so no cliffs or
 *       holes are left, and re-covers the ground with the local surface (grass, sand, snow soil…).</li>
 * </ol>
 * Columns with player builds, other village buildings, the square or water are never touched.
 */
public final class Terraform {
    private Terraform() {}

    public static List<Blueprint.Placement> plan(ServerLevel level, Village v, BoundingBox foot, int baseY, int topY, PlacedBuilding self) {
        List<Blueprint.Placement> out = new ArrayList<>();
        if (!HBConfig.TERRAFORM.get()) return out;
        int pad = HBConfig.TERRAFORM_PAD.get();
        int maxSkirt = HBConfig.TERRAFORM_SKIRT.get();
        int minX = foot.minX() - pad, maxX = foot.maxX() + pad, minZ = foot.minZ() - pad, maxZ = foot.maxZ() + pad;
        Set<Long> changedColumns = new HashSet<>();
        int lowest = baseY;

        for (int x = minX - maxSkirt; x <= maxX + maxSkirt; x++) {
            for (int z = minZ - maxSkirt; z <= maxZ + maxSkirt; z++) {
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                int dx = Math.max(0, Math.max(minX - x, x - maxX));
                int dz = Math.max(0, Math.max(minZ - z, z - maxZ));
                boolean inYard = dx == 0 && dz == 0;
                int top = groundTop(level, x, z);
                BlockPos topPos = new BlockPos(x, top, z);
                BlockState topState = level.getBlockState(topPos);
                if (!level.getFluidState(topPos).isEmpty()) continue;
                if (blocked(level, v, self, x, z)) continue;
                if (PlayerBuilds.columnProtected(level, x, z, Math.min(top, baseY) - 2, Math.max(top, baseY) + 24)) continue;
                int delta = top - baseY;
                int target;
                if (inYard) {
                    target = baseY;
                } else {
                    if (delta == 0) continue;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    int skirt = Mth.clamp((int) Math.ceil(Math.abs(delta) * 1.5), 2, Math.max(2, maxSkirt));
                    if (dist > skirt) continue;
                    double t = dist / (skirt + 1.0);
                    double smooth = t * t * (3 - 2 * t);
                    target = (int) Math.round(baseY + delta * smooth);
                }
                if (target == top && !inYard) continue;
                BlockState surface = surfaceFor(topState, level, topPos);
                BlockState filler = fillerFor(surface);
                if (target < top) {
                    // cut down to the target, re-cover the new top with the surface
                    for (int y = top + 2; y > target; y--) {
                        BlockPos p = new BlockPos(x, y, z);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir()) continue;
                        if (y > top && (!s.canBeReplaced() || !s.getFluidState().isEmpty())) continue; // only vegetation above the ground
                        out.add(new Blueprint.Placement(p, Blocks.AIR.defaultBlockState(), Blueprint.Phase.CUT));
                    }
                    out.add(new Blueprint.Placement(new BlockPos(x, target, z), surface, Blueprint.Phase.FILL));
                } else if (target > top) {
                    for (int y = top; y < target; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        out.add(new Blueprint.Placement(p, filler, Blueprint.Phase.FILL));
                    }
                    out.add(new Blueprint.Placement(new BlockPos(x, target, z), surface, Blueprint.Phase.FILL));
                    // plants on the old surface get buried
                    BlockPos above = new BlockPos(x, target + 1, z);
                    BlockState a = level.getBlockState(above);
                    if (!a.isAir() && a.canBeReplaced() && a.getFluidState().isEmpty()) out.add(new Blueprint.Placement(above, Blocks.AIR.defaultBlockState(), Blueprint.Phase.CUT));
                } else {
                    // yard at the right height: tidy vegetation and make it look natural
                    BlockPos above = new BlockPos(x, top + 1, z);
                    BlockState a = level.getBlockState(above);
                    if (!a.isAir() && a.canBeReplaced() && a.getFluidState().isEmpty() && !a.is(BlockTags.SNOW)) out.add(new Blueprint.Placement(above, Blocks.AIR.defaultBlockState(), Blueprint.Phase.CUT));
                }
                changedColumns.add(BlockPos.asLong(x, 0, z));
                lowest = Math.min(lowest, Math.min(top, target));
            }
        }

        if (HBConfig.REMOVE_TREES.get()) {
            fellTrees(level, v, self, out, minX - 1, maxX + 1, minZ - 1, maxZ + 1, lowest, topY + 4, changedColumns);
        }
        return out;
    }

    /** Other buildings, the square and the hearth are off limits. */
    private static boolean blocked(ServerLevel level, Village v, PlacedBuilding self, int x, int z) {
        if (v != null) {
            int dx = x - v.center.getX(), dz = z - v.center.getZ();
            if (dx * dx + dz * dz <= 36) return true;
            for (PlacedBuilding b : v.buildings) {
                if (b == self) continue;
                BoundingBox bb = b.bounds;
                if (x >= bb.minX() - 1 && x <= bb.maxX() + 1 && z >= bb.minZ() - 1 && z <= bb.maxZ() + 1) return true;
            }
            // player project plots and finished player buildings
            for (Project pr : v.projects.values()) {
                if (pr.core != null && Math.abs(x - pr.core.getX()) <= pr.half + 1 && Math.abs(z - pr.core.getZ()) <= pr.half + 1) return true;
            }
            for (Project.Work w : v.works) {
                BoundingBox a = w.area();
                if (x >= a.minX() - 1 && x <= a.maxX() + 1 && z >= a.minZ() - 1 && z <= a.maxZ() + 1) return true;
            }
        }
        return false;
    }

    /** Height of the real ground (ignoring tree trunks, leaves and vegetation). */
    public static int groundTop(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        int floor = Math.max(level.getMinBuildHeight(), y - 40);
        while (y > floor) {
            BlockState s = level.getBlockState(p.setY(y));
            if (!s.getFluidState().isEmpty()) break; // water/lava is a surface: never dig under it
            if (treePart(s) || s.isAir() || s.canBeReplaced()) {
                y--;
                continue;
            }
            break;
        }
        return y;
    }

    // ================================================================== surface

    /** The natural top block of this column (kept when it is soil), grass otherwise. */
    static BlockState surfaceFor(BlockState top, ServerLevel level, BlockPos pos) {
        Block b = top.getBlock();
        if (b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL || b == Blocks.MYCELIUM || b == Blocks.SAND || b == Blocks.RED_SAND
                || b == Blocks.COARSE_DIRT || b == Blocks.MUD || b == Blocks.MOSS_BLOCK || b == Blocks.SNOW_BLOCK || b == Blocks.GRAVEL
                || b == Blocks.TERRACOTTA || b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.ROOTED_DIRT) {
            return b.defaultBlockState();
        }
        if (b == Blocks.DIRT || b == Blocks.DIRT_PATH || b == Blocks.FARMLAND) return Blocks.GRASS_BLOCK.defaultBlockState();
        if (top.is(BlockTags.SAND)) return b.defaultBlockState();
        if (top.is(BlockTags.BASE_STONE_OVERWORLD)) return b.defaultBlockState();
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    static BlockState fillerFor(BlockState surface) {
        Block b = surface.getBlock();
        if (b == Blocks.SAND) return Blocks.SANDSTONE.defaultBlockState();
        if (b == Blocks.RED_SAND) return Blocks.RED_SANDSTONE.defaultBlockState();
        if (b == Blocks.MUD) return Blocks.MUD.defaultBlockState();
        if (b == Blocks.GRAVEL) return Blocks.GRAVEL.defaultBlockState();
        if (b == Blocks.TERRACOTTA || b == Blocks.STONE || b == Blocks.DEEPSLATE) return surface;
        if (b == Blocks.SNOW_BLOCK) return Blocks.DIRT.defaultBlockState();
        return Blocks.DIRT.defaultBlockState();
    }

    // ================================================================== trees

    private static boolean treePart(BlockState s) {
        if (s.is(BlockTags.LOGS)) return true;
        if (s.getBlock() instanceof LeavesBlock) return !s.hasProperty(LeavesBlock.PERSISTENT) || !s.getValue(LeavesBlock.PERSISTENT);
        Block b = s.getBlock();
        return b == Blocks.VINE || b == Blocks.COCOA || b == Blocks.BEE_NEST || b == Blocks.MANGROVE_ROOTS
                || b == Blocks.MUSHROOM_STEM || b == Blocks.RED_MUSHROOM_BLOCK || b == Blocks.BROWN_MUSHROOM_BLOCK
                || b == Blocks.GLOW_LICHEN;
    }

    /** Removes every natural tree connected to the area (bounded by the configured limit). */
    private static void fellTrees(ServerLevel level, Village v, PlacedBuilding self, List<Blueprint.Placement> out,
                                  int minX, int maxX, int minZ, int maxZ, int yMin, int yMax, Set<Long> changed) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        int maxSkirt = HBConfig.TERRAFORM_SKIRT.get();
        for (int x = minX - maxSkirt; x <= maxX + maxSkirt; x++) {
            for (int z = minZ - maxSkirt; z <= maxZ + maxSkirt; z++) {
                boolean inArea = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                if (!inArea && !changed.contains(BlockPos.asLong(x, 0, z))) continue;
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                for (int y = yMin; y <= yMax; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (treePart(level.getBlockState(p)) && seen.add(p.asLong())) queue.add(p);
                }
            }
        }
        int limit = HBConfig.TREE_LIMIT.get();
        int removed = 0;
        int reach = 12 + maxSkirt;
        while (!queue.isEmpty() && removed < limit) {
            BlockPos p = queue.poll();
            if (PlayerBuilds.isPlayerBlock(level, p) || blocked(level, v, self, p.getX(), p.getZ())) continue;
            BlockState s = level.getBlockState(p);
            if (!treePart(s)) continue;
            out.add(new Blueprint.Placement(p, Blocks.AIR.defaultBlockState(), Blueprint.Phase.TREES));
            removed++;
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        if (ox == 0 && oy == 0 && oz == 0) continue;
                        BlockPos n = p.offset(ox, oy, oz);
                        if (n.getX() < minX - reach || n.getX() > maxX + reach || n.getZ() < minZ - reach || n.getZ() > maxZ + reach) continue;
                        if (n.getY() < yMin - 3 || !seen.add(n.asLong())) continue;
                        if (!level.isLoaded(n)) continue;
                        if (treePart(level.getBlockState(n))) queue.add(n);
                    }
                }
            }
        }
    }

    /** The block a village should leave alone. */
    public static boolean untouchable(ServerLevel level, BlockPos pos, BlockState cur) {
        if (cur.is(ModRegistry.VILLAGE_HEARTH.get())) return true;
        return PlayerBuilds.protectedBlock(level, pos, cur);
    }
}
