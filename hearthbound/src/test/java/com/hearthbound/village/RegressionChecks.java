package com.hearthbound.village;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;

/** Runs in a real NeoForge test server with Minecraft registries initialized. */
@GameTestHolder("hearthbound")
@PrefixGameTestTemplate(false)
public final class RegressionChecks {
    @GameTest(template = "empty")
    public static void regressions(GameTestHelper helper) {
        for (int population : new int[]{0, 9, 10, 19, 20, 29, 30, 100}) {
            int expected = population < 10 ? 0 : population < 20 ? 1 : population < 30 ? 2 : 3;
            check(NightGolems.quota(population, 10, 3) == expected, "population " + population);
        }
        check(NightGolems.quota(100, 10, 0) == 0, "disabled cap");
        for (Direction front : Direction.Plane.HORIZONTAL) {
            Template t = Template.fromStructureTag("test:doors", fixture(false), front);
            check(t.minY == -2, "basement floor preserved: " + front);
            check(t.blocks.stream().anyMatch(b -> b.state().is(Blocks.OAK_DOOR) && b.pos().getY() == 1), "lower entrance at y=1");
            check(t.blocks.stream().anyMatch(b -> b.state().is(Blocks.OAK_DOOR) && b.pos().getY() == 5), "upstairs door preserved");
        }
        Template jigsaw = Template.fromStructureTag("test:jigsaw", fixture(true), Direction.NORTH);
        check(jigsaw.minY == 0, "jigsaw floor at y=0, not buried at y=-1");
        check(jigsaw.blocks.stream().anyMatch(b -> b.state().isAir() && b.pos().getY() == 1), "jigsaw entrance walkable above floor");
        CompoundTag pavilionTag = fixture(false);
        ListTag pavilionBlocks = new ListTag();
        pavilionBlocks.add(block(0, 0, 0, 0));
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) pavilionBlocks.add(block(x, 6, z, 0));
        pavilionTag.put("blocks", pavilionBlocks);
        Template pavilion = Template.fromStructureTag("test:pavilion", pavilionTag, Direction.NORTH);
        check(pavilion.minY == 0 && pavilion.maxY == 6, "roof must not become floor");
        var level = helper.getLevel();
        var origin = helper.absolutePos(new net.minecraft.core.BlockPos(0, 4, 0));
        Template basement = Template.fromStructureTag("test:basement", fixture(false), Direction.NORTH);
        var plan = Blueprint.fromTemplate(null, basement).resolve(level, origin, Direction.NORTH);
        check(plan.stream().anyMatch(p -> p.pos().equals(origin.offset(2, -1, 2)) && p.state().isAir()), "clear omitted basement air");
        Village v = new Village();
        v.center = origin;
        v.golemNight = 5;
        v.golemsSpawned = 2;
        Village saved = Village.load(v.save());
        check(saved.golemNight == 5 && saved.golemsSpawned == 2, "persist nightly budget");
        var golem = com.hearthbound.registry.ModRegistry.VILLAGE_GOLEM.get().create(level);
        check(golem != null, "registered golem creates successfully");
        golem.bind(v, 5);
        CompoundTag entityTag = new CompoundTag();
        golem.addAdditionalSaveData(entityTag);
        var restored = com.hearthbound.registry.ModRegistry.VILLAGE_GOLEM.get().create(level);
        restored.readAdditionalSaveData(entityTag);
        check(restored.variant() == golem.variant(), "texture survives save/load");
        check(!golem.canAttackType(net.minecraft.world.entity.EntityType.PLAYER), "never attacks players");
        check(golem.getExperienceReward(level, null) == 10, "10 XP reward");
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, golem)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, golem.position())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
        var loot = level.getServer().reloadableRegistries().getLootTable(golem.getLootTable());
        for (int i = 0; i < 20; i++) {
            var drops = loot.getRandomItems(params);
            check(drops.size() == 1 && drops.getFirst().is(net.minecraft.world.item.Items.POPPY)
                    && drops.getFirst().getCount() >= 1 && drops.getFirst().getCount() <= 2, "flowers only, no iron");
        }
        golem.aiStep();
        check(golem.isRemoved(), "retire invalid/old night without death");
        helper.succeed();
        System.out.println("PASS: population thresholds, caps, four rotations, basement/door heights, jigsaw floor, roof fallback, cellar clearing, entity persistence, XP and flower-only loot.");
    }

    @GameTest(template = "empty")
    public static void nightLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        level.setDayTime(18000);
        level.updateSkyBrightness();
        check(level.isNight(), "night setup");
        Village v = new Village();
        v.center = helper.absolutePos(new net.minecraft.core.BlockPos(2, 0, 2));
        VillageData data = VillageData.get(level.getServer());
        data.add(v);
        for (int i = 0; i < 9; i++) v.residents.add(new Resident());
        NightGolems.tick(level, v, data);
        check(v.golemsSpawned == 0, "nine residents: no golem");
        Resident dead = new Resident(); dead.deadSince = 0; v.residents.add(dead);
        NightGolems.tick(level, v, data);
        check(v.golemsSpawned == 0, "dead residents do not unlock golems");
        v.residents.add(new Resident());
        for (int i = 0; i < 10 && v.golemsSpawned == 0; i++) NightGolems.tick(level, v, data);
        check(v.golemsSpawned == 1, "ten living residents spawn one golem");
        var box = new net.minecraft.world.phys.AABB(v.center).inflate(v.radius() + 10);
        var spawned = level.getEntitiesOfClass(com.hearthbound.entity.VillageGolemEntity.class, box);
        check(spawned.size() == 1, "one actual entity");
        var golem = spawned.getFirst();
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        zombie.moveTo(v.center.getX(), v.center.getY(), v.center.getZ());
        golem.setTarget(zombie);
        check(golem.getTarget() == zombie, "defends against nearby monsters");
        zombie.moveTo(v.center.getX() + 200, v.center.getY(), v.center.getZ());
        golem.setTarget(zombie);
        check(golem.getTarget() == null, "does not pursue monsters outside village");
        golem.discard();
        NightGolems.tick(level, v, data);
        check(v.golemsSpawned == 1 && level.getEntitiesOfClass(com.hearthbound.entity.VillageGolemEntity.class, box).isEmpty(), "no same-night replacement");
        level.setDayTime(42000);
        level.updateSkyBrightness();
        for (int i = 0; i < 10; i++) NightGolems.tick(level, v, data);
        spawned = level.getEntitiesOfClass(com.hearthbound.entity.VillageGolemEntity.class, box);
        check(v.golemNight == 1 && v.golemsSpawned == 1 && spawned.size() == 1, "next night resets budget once");
        level.setDayTime(48000);
        level.updateSkyBrightness();
        golem = spawned.getFirst();
        golem.aiStep();
        check(golem.isRemoved(), "dawn retires golem");
        data.remove(v.id);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void raisedEntrances(GameTestHelper helper) throws Exception {
        for (Direction front : Direction.Plane.HORIZONTAL) {
            CompoundTag tag = stairFixture(front);
            Template stairs = Template.fromStructureTag("test:stairs", tag, front);
            check(stairs.terrainOffset == -3, "three-block exterior offset: " + front);
            check(stairs.blocks.stream().anyMatch(b -> b.state().is(Blocks.OAK_DOOR) && b.pos().getY() == 1), "interior stays at original height");
        }
        for (String biome : new String[]{"giant_taiga", "swamp"}) {
            String resource = "/data/hearthbound/hearthbound/structures/rs/" + biome + "/masons_house_1.nbt";
            Template t;
            try (var input = RegressionChecks.class.getResourceAsStream(resource)) {
                check(input != null, "bundled masonry house exists");
                t = Template.read("test:" + biome, input, resource, null);
            }
            check(t.terrainOffset == -2, "bundled stairs determine exterior height: " + biome);
            var level = helper.getLevel();
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                int index = facing.get2DDataValue() + (biome.equals("swamp") ? 4 : 0);
                var ground = helper.absolutePos(new net.minecraft.core.BlockPos(30 + index * 25, 4, 30));
                var origin = ground.above(-t.terrainOffset);
                var bounds = Blueprint.bounds(origin, facing, t.width, t.depth, t.maxY + 1, 0);
                PlacedBuilding building = new PlacedBuilding(net.minecraft.resources.ResourceLocation.parse("hearthbound:house"), origin,
                        facing, t.width, t.depth, t.maxY, 0, bounds);
                building.terrainOffset = t.terrainOffset;
                building.doorX = t.doorX;
                Village village = new Village(); village.center = origin.offset(0, 0, 100);
                village.buildings.add(building);
                // Flat ground at the exterior access height, then use the actual construction pipeline.
                for (int x = bounds.minX() - 2; x <= bounds.maxX() + 2; x++)
                    for (int z = bounds.minZ() - 2; z <= bounds.maxZ() + 2; z++)
                        level.setBlock(new net.minecraft.core.BlockPos(x, ground.getY(), z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                var plan = Blueprint.fromTemplate(null, t).resolve(level, origin, facing,
                        VillageManager.groundWork(level, village, building), building.terrainOffset);
                for (var placement : plan) VillageManager.place(level, placement);
                for (var block : t.blocks) {
                    if (!block.state().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.STAIRS_SHAPE)
                            || block.pos().getY() != t.terrainOffset + 1) continue;
                    var pos = Blueprint.toWorld(origin, facing, block.pos());
                    check(pos.getY() == ground.getY() + 1, "bottom stair above exterior ground");
                    check(level.getBlockState(pos).getBlock() == block.state().getBlock(), "bottom stair survives terraforming");
                }
                var outside = building.doorstep();
                check(outside.getY() == ground.getY() + 1, "path endpoint uses exterior height");
                check(level.getBlockState(outside).isAir(), "approach not filled up to door floor");
                check(PlacedBuilding.load(building.save()).terrainOffset == -2, "save exterior height");
                CompoundTag oldSave = building.save(); oldSave.remove("terrainOffset");
                check(PlacedBuilding.load(oldSave).terrainOffset == 0, "old buildings preserve placement");
            }
        }
        helper.succeed();
    }

    private static CompoundTag stairFixture(Direction front) {
        CompoundTag root = fixture(false);
        root.put("size", ints(7, 9, 7));
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        CompoundTag stair = new CompoundTag(); stair.putString("Name", "minecraft:stone_brick_stairs");
        CompoundTag props = new CompoundTag(); props.putString("half", "bottom");
        props.putString("facing", front.getOpposite().getName()); stair.put("Properties", props); palette.add(stair);
        ListTag blocks = new ListTag();
        // Door floor y=3, outward steps y=3,2,1. Ground must be y=0.
        int[][] coordinates = {{3, 4, 3, 1}, {3, 3, 3, 0}, {3, 3, 2, 2}, {3, 2, 1, 2}, {3, 1, 0, 2}, {3, 0, 4, 0}};
        for (int[] c : coordinates) {
            int x = c[0], z = c[2];
            if (front == Direction.SOUTH) { x = 6 - c[0]; z = 6 - c[2]; }
            else if (front == Direction.EAST) { x = 6 - c[2]; z = c[0]; }
            else if (front == Direction.WEST) { x = c[2]; z = 6 - c[0]; }
            blocks.add(block(x, c[1], z, c[3]));
        }
        root.put("blocks", blocks);
        return root;
    }

    private static CompoundTag fixture(boolean jigsaw) {
        CompoundTag root = new CompoundTag();
        root.put("size", ints(5, 9, 5));
        ListTag palette = new ListTag();
        CompoundTag stone = new CompoundTag(); stone.putString("Name", "minecraft:stone"); palette.add(stone);
        CompoundTag door = new CompoundTag(); door.putString("Name", jigsaw ? "minecraft:jigsaw" : "minecraft:oak_door");
        CompoundTag properties = new CompoundTag();
        properties.putString(jigsaw ? "orientation" : "half", jigsaw ? "north_up" : "lower");
        door.put("Properties", properties); palette.add(door); root.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) blocks.add(block(x, 0, z, 0));
        CompoundTag entrance = block(2, jigsaw ? 1 : 3, 0, 1);
        if (jigsaw) {
            CompoundTag nbt = new CompoundTag(); nbt.putString("name", "minecraft:building_entrance");
            nbt.putString("final_state", "minecraft:air"); entrance.put("nbt", nbt);
        } else blocks.add(block(2, 7, 0, 1));
        blocks.add(entrance); root.put("blocks", blocks); return root;
    }

    private static CompoundTag block(int x, int y, int z, int state) {
        CompoundTag b = new CompoundTag(); b.put("pos", ints(x, y, z)); b.putInt("state", state); return b;
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag(); for (int value : values) list.add(IntTag.valueOf(value)); return list;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
