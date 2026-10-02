package com.hearthbound.village;

import com.hearthbound.config.HBConfig;
import com.hearthbound.entity.VillageGolemEntity;
import com.hearthbound.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Persisted nightly budget prevents duplicates on chunk unloads, restarts and deaths. */
public final class NightGolems {
    private NightGolems() {}

    public static int quota(int population, int residentsPerGolem, int maximum) {
        return Math.min(maximum, population / residentsPerGolem);
    }

    public static void tick(ServerLevel level, Village village, VillageData data) {
        if (!HBConfig.NIGHT_GOLEMS.get() || !level.isNight()) return;
        long night = level.getDayTime() / 24000L;
        if (village.golemNight != night) {
            village.golemNight = night;
            village.golemsSpawned = 0;
            data.setDirty();
        }
        int alive = (int) village.residents.stream().filter(Resident::alive).count();
        int wanted = quota(alive, HBConfig.RESIDENTS_PER_GOLEM.get(), HBConfig.MAX_GOLEMS.get());
        if (village.golemsSpawned >= wanted) return;
        for (int attempt = 0; attempt < 24; attempt++) {
            int range = Math.max(6, village.radius() / 2);
            int x = village.center.getX() + level.random.nextInt(range * 2 + 1) - range;
            int z = village.center.getZ() + level.random.nextInt(range * 2 + 1) - range;
            if (!level.isAreaLoaded(new BlockPos(x, village.center.getY(), z), 3)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getWorldBorder().isWithinBounds(pos) || village.buildingAt(pos.below()) != null) continue;
            if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
            VillageGolemEntity golem = ModRegistry.VILLAGE_GOLEM.get().create(level);
            if (golem == null) return;
            golem.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360, 0);
            if (!level.getWorldBorder().isWithinBounds(golem.getBoundingBox())
                    || !level.noCollision(golem) || level.containsAnyLiquid(golem.getBoundingBox())) continue;
            golem.bind(village, night);
            if (level.addFreshEntity(golem)) {
                village.golemsSpawned++;
                data.setDirty();
            }
            return;
        }
    }
}
