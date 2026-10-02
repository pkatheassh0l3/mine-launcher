package com.hearthbound.village;

import com.hearthbound.config.HBConfig;
import com.hearthbound.world.PlayerBuilds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import java.util.*;

/** Only damage witnessed during a creeper blast is eligible, never ordinary mining. */
public final class VillageRepairs {
    public record Repair(BlockState state, long capturedAt, boolean confirmed) {}
    private VillageRepairs() {}

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getExplosion().getDirectSourceEntity() instanceof Creeper)) return;
        var data = VillageData.get(level.getServer());
        // Include neighbours: doors, beds and decorations can break indirectly.
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos p : event.getAffectedBlocks()) {
            positions.add(p.immutable());
            for (Direction d : Direction.values()) positions.add(p.relative(d));
        }
        for (BlockPos p : new ArrayList<>(positions)) {
            if (!level.isLoaded(p)) continue;
            var state = level.getBlockState(p);
            if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                positions.add(p.above());
                positions.add(p.below());
            } else if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                var facing = state.getValue(net.minecraft.world.level.block.BedBlock.FACING);
                positions.add(p.relative(facing));
                positions.add(p.relative(facing.getOpposite()));
            }
        }
        for (Village v : data.all()) {
            if (!v.dimension.equals(level.dimension())) continue;
            for (BlockPos p : positions) {
                if (!level.isLoaded(p) || v.buildingAt(p) == null || PlayerBuilds.isPlayerBlock(level, p)) continue;
                BlockState state = level.getBlockState(p);
                if (state.isAir() || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || state.getDestroySpeed(level, p) < 0) continue;
                v.repairs.putIfAbsent(p, new Repair(state, level.getGameTime(), false));
            }
        }
        data.setDirty();
    }

    /** Confirm on the following tick, even if no builder/player is present. */
    public static void confirm(ServerLevel level, Village v, VillageData data) {
        var it = v.repairs.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Repair r = e.getValue();
            if (r.confirmed() || level.getGameTime() <= r.capturedAt() || !level.isLoaded(e.getKey())) continue;
            if (level.getBlockState(e.getKey()).isAir()) e.setValue(new Repair(r.state(), r.capturedAt(), true));
            else it.remove(); // Survived, protected, or already replaced by a player.
            data.setDirty();
        }
    }

    public static void step(ServerLevel level, Village v, VillageData data) {
        if (!v.has(Role.BUILDER)) return;
        int budget = HBConfig.BLOCKS_PER_STEP.get();
        var positions = new ArrayList<>(v.repairs.keySet());
        positions.sort(Comparator.comparingInt(BlockPos::getY));
        for (BlockPos p : positions) {
            if (budget <= 0) break;
            Repair r = v.repairs.get(p);
            if (!r.confirmed() || !level.isLoaded(p)) continue;
            if (!level.getBlockState(p).isAir()) {
                v.repairs.remove(p); // Respect player repairs and subsequent changes.
                data.setDirty();
                continue;
            }
            if (!r.state().canSurvive(level, p)) continue; // Wait for its support.
            // No block-entity NBT: containers are rebuilt empty, never duplicate loot.
            // Defer neighbour physics while rebuilding paired blocks/supports across batches.
            if (level.setBlock(p, r.state(), Block.UPDATE_CLIENTS)) {
                v.repairs.remove(p);
                budget--;
                data.setDirty();
            }
        }
    }

    public static ListTag save(Village v) {
        ListTag list = new ListTag();
        v.repairs.forEach((p, r) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", p.asLong());
            t.put("state", NbtUtils.writeBlockState(r.state()));
            t.putLong("time", r.capturedAt());
            t.putBoolean("confirmed", r.confirmed());
            list.add(t);
        });
        return list;
    }

    public static void load(Village v, ListTag list) {
        for (Tag entry : list) {
            CompoundTag t = (CompoundTag) entry;
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), t.getCompound("state"));
            if (!state.isAir()) v.repairs.put(BlockPos.of(t.getLong("pos")),
                    new Repair(state, t.getLong("time"), t.getBoolean("confirmed")));
        }
    }
}
