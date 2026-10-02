package com.hearthbound.world;

import com.hearthbound.Hearthbound;
import com.hearthbound.config.HBConfig;
import com.hearthbound.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Knows which blocks belong to players, so villages never build over or flatten them:
 * blocks players place are recorded per chunk, and anything that looks man-made
 * ({@code #hearthbound:artificial}, persistent leaves) is treated the same way.
 */
public final class PlayerBuilds {
    public static final TagKey<Block> ARTIFICIAL = TagKey.create(Registries.BLOCK, Hearthbound.id("artificial"));
    public static final TagKey<Block> NO_BUILD_CREDIT = TagKey.create(Registries.BLOCK, Hearthbound.id("no_build_credit"));

    private PlayerBuilds() {}

    private static PlayerBlocks data(ServerLevel level, BlockPos pos, boolean create) {
        if (!level.hasChunkAt(pos)) return null;
        LevelChunk chunk = level.getChunkAt(pos);
        if (!create && !chunk.hasData(ModRegistry.PLAYER_BLOCKS.get())) return null;
        return chunk.getData(ModRegistry.PLAYER_BLOCKS.get());
    }

    public static void mark(ServerLevel level, BlockPos pos) {
        if (!HBConfig.TRACK_PLAYER_BLOCKS.get()) return;
        PlayerBlocks d = data(level, pos, true);
        if (d != null && d.positions.add(pos.asLong())) level.getChunkAt(pos).setUnsaved(true);
    }

    public static boolean unmark(ServerLevel level, BlockPos pos) {
        PlayerBlocks d = data(level, pos, false);
        if (d != null && d.positions.remove(pos.asLong())) {
            level.getChunkAt(pos).setUnsaved(true);
            return true;
        }
        return false;
    }

    public static boolean isPlayerBlock(ServerLevel level, BlockPos pos) {
        PlayerBlocks d = data(level, pos, false);
        if (d == null || !d.positions.contains(pos.asLong())) return false;
        if (level.getBlockState(pos).isAir()) { // destroyed by something else (explosion, piston…)
            d.positions.remove(pos.asLong());
            level.getChunkAt(pos).setUnsaved(true);
            return false;
        }
        return true;
    }

    /** Any recorded player block inside the box (checks only loaded chunks). */
    public static boolean anyInBox(ServerLevel level, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!level.getChunkSource().hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                if (!chunk.hasData(ModRegistry.PLAYER_BLOCKS.get())) continue;
                var it = chunk.getData(ModRegistry.PLAYER_BLOCKS.get()).positions.iterator();
                while (it.hasNext()) {
                    long l = it.nextLong();
                    if (!box.isInside(BlockPos.getX(l), BlockPos.getY(l), BlockPos.getZ(l))) continue;
                    if (chunk.getBlockState(BlockPos.of(l)).isAir()) {
                        it.remove();
                        chunk.setUnsaved(true);
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean looksArtificial(BlockState s) {
        if (s.isAir()) return false;
        if (s.is(ARTIFICIAL)) return true;
        return s.getBlock() instanceof LeavesBlock && s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT);
    }

    /** Whether a column holds something a player made: recorded blocks or man-made surface blocks. */
    public static boolean columnProtected(ServerLevel level, int x, int z, int yMin, int yMax) {
        if (!HBConfig.RESPECT_PLAYER_BUILDS.get()) return false;
        PlayerBlocks d = data(level, new BlockPos(x, 0, z), false);
        if (d != null && !d.positions.isEmpty()) {
            for (int y = yMin; y <= yMax; y++) if (d.positions.contains(BlockPos.asLong(x, y, z))) return true;
        }
        if (!HBConfig.AVOID_ARTIFICIAL.get()) return false;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockState s = level.getBlockState(new BlockPos(x, top, z));
        if (looksArtificial(s)) return true;
        int solid = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        return solid != top && looksArtificial(level.getBlockState(new BlockPos(x, solid, z)));
    }

    public static boolean protectedBlock(ServerLevel level, BlockPos pos, BlockState s) {
        if (!HBConfig.RESPECT_PLAYER_BUILDS.get()) return false;
        return isPlayerBlock(level, pos);
    }

    static boolean isLeaves(BlockState s) {
        return s.is(BlockTags.LEAVES);
    }
}
