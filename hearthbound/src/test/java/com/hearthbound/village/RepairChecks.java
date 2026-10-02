package com.hearthbound.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("hearthbound")
@PrefixGameTestTemplate(false)
public final class RepairChecks {
    @GameTest(template = "empty")
    public static void pairedBlocksAndEmptyContainers(GameTestHelper h) {
        var level = h.getLevel();
        var p = h.absolutePos(new BlockPos(1, 2, 1));
        var v = new Village();
        var builder = new Resident(); builder.role = Role.BUILDER; builder.name = "Builder";
        v.residents.add(builder);
        level.setBlock(p.below(), Blocks.STONE.defaultBlockState(), 2);
        var lower = Blocks.OAK_DOOR.defaultBlockState();
        var upper = lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        v.repairs.put(p, new VillageRepairs.Repair(lower, -1, true));
        v.repairs.put(p.above(), new VillageRepairs.Repair(upper, -1, true));
        var chest = p.offset(2,0,0);
        v.repairs.put(chest, new VillageRepairs.Repair(Blocks.CHEST.defaultBlockState(), -1, true));
        for (int i=0; i<4; i++) VillageRepairs.step(level, v, new VillageData());
        h.assertTrue(level.getBlockState(p).equals(lower) && level.getBlockState(p.above()).equals(upper), "Both door halves restored");
        h.assertTrue(level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c && c.isEmpty(), "Container restored empty");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void creeperDamageSurvivesRestartAndWaitsForBuilder(GameTestHelper h) {
        var level = h.getLevel();
        var p = h.absolutePos(new BlockPos(2, 2, 2));
        var village = new Village();
        village.center = p;
        village.dimension = level.dimension();
        village.buildings.add(new PlacedBuilding(ResourceLocation.parse("hearthbound:test"), p,
                Direction.NORTH, 5, 5, 5, 1, BoundingBox.fromCorners(p.offset(-2,-2,-2), p.offset(2,2,2))));
        var data = VillageData.get(level.getServer());
        data.add(village);
        level.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        var creeper = EntityType.CREEPER.create(level);
        level.explode(creeper, p.getX()+0.5, p.getY()+0.5, p.getZ()+0.5, 3, Level.ExplosionInteraction.TNT);
        h.assertTrue(level.getBlockState(p).isAir(), "Real explosion destroys wall");
        h.assertTrue(village.repairs.containsKey(p), "Event records wall");
        h.runAfterDelay(2, () -> {
            try {
                VillageRepairs.confirm(level, village, data);
                var restored = Village.load(village.save());
                VillageRepairs.step(level, restored, data);
                h.assertTrue(level.getBlockState(p).isAir(), "No builder means no repair");
                var builder = new Resident(); builder.role = Role.BUILDER; builder.name = "Builder";
                restored.residents.add(builder);
                VillageRepairs.step(level, restored, data);
                h.assertTrue(level.getBlockState(p).is(Blocks.OAK_PLANKS), "Saved damage repaired by builder");
                h.assertTrue(restored.repairs.isEmpty(), "Finished repairs removed");
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                VillageRepairs.step(level, restored, data);
                h.assertTrue(level.getBlockState(p).isAir(), "Manual removal stays removed");
                h.succeed();
            } finally { data.remove(village.id); }
        });
    }

    @GameTest(template = "empty")
    public static void repairsRespectReplacementAndDiscardSurvivors(GameTestHelper h) {
        var level = h.getLevel();
        var p = h.absolutePos(new BlockPos(1, 2, 1));
        var v = new Village();
        var builder = new Resident(); builder.role = Role.BUILDER; builder.name = "Builder";
        v.residents.add(builder);
        v.repairs.put(p, new VillageRepairs.Repair(Blocks.OAK_PLANKS.defaultBlockState(), -1, true));
        level.setBlock(p, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        VillageRepairs.step(level, v, new VillageData());
        h.assertTrue(level.getBlockState(p).is(Blocks.GOLD_BLOCK) && v.repairs.isEmpty(), "Player replacement respected");
        v.repairs.put(p, new VillageRepairs.Repair(Blocks.OAK_PLANKS.defaultBlockState(), -1, false));
        VillageRepairs.confirm(level, v, new VillageData());
        h.assertTrue(v.repairs.isEmpty(), "Protected/surviving block never queued for later repair");
        h.succeed();
    }
}
