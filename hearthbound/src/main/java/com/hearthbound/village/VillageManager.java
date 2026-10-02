package com.hearthbound.village;

import com.hearthbound.Hearthbound;
import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.data.BuildingDef;
import com.hearthbound.data.Culture;
import com.hearthbound.data.HBData;
import com.hearthbound.entity.SettlerEntity;
import com.hearthbound.network.Net;
import com.hearthbound.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives every village: construction steps, the economy, residents, the daily board,
 * tribute and raids, plus natural founding near exploring players.
 */
public final class VillageManager {
    /** Resolved placements of buildings under construction (rebuilt after restarts). */
    private static final Map<String, List<Blueprint.Placement>> PLANS = new HashMap<>();
    private static final Map<String, Integer> CURSORS = new HashMap<>();
    private static final Map<UUID, Integer> PLOT_FAILURES = new HashMap<>();
    public static final String RAIDER_TAG = "hearthbound_raider";

    private VillageManager() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long t = overworld.getGameTime();
        VillageData data = VillageData.get(server);

        if (t % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) Compat.recordVillageAge(player);
            for (Village village : data.all()) refreshTierCap(overworld, village);
        }

        for (Village v : new ArrayList<>(data.all())) {
            ServerLevel level = server.getLevel(v.dimension);
            if (level == null || !level.isLoaded(v.center)) continue;
            boolean playersNear = anyPlayerNear(level, v.center, 160);
            if (!playersNear && !HBConfig.GROW_WITHOUT_PLAYERS.get()) continue;
            try {
                if (t % HBConfig.CONSTRUCTION_INTERVAL.get() == 0) constructStep(level, v, data);
                if (t % HBConfig.ECONOMY_INTERVAL.get() == 0) economy(level, v, data);
                if (t % 100 == 7) {
                    daily(level, v, data);
                    if (playersNear) NightGolems.tick(level, v, data);
                    if (playersNear) raids(level, v, data);
                    if (playersNear && v.flagPos == null && HBConfig.FLAGS.get()) Flags.placeInWorld(level, v);
                    if (playersNear && assignBeds(level, v)) {
                        rebindSettlers(level, v);
                        data.setDirty();
                    }
                    if (playersNear) {
                        // buildings raised by older versions: connect their glass panes, bars and fences
                        for (PlacedBuilding b : v.buildings) {
                            if (b.complete && !b.connected && level.isAreaLoaded(b.origin, 16)) {
                                reconnect(level, b);
                                data.setDirty();
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Hearthbound.LOGGER.error("Error ticking village {}: {}", v.name, e.toString());
            }
        }

        if (t % 100 == 37) {
            try {
                Diplomacy.tick(server, data);
            } catch (Exception e) {
                Hearthbound.LOGGER.error("Error in village diplomacy: {}", e.toString());
            }
        }

        if (t % HBConfig.SPAWN_CHECK_INTERVAL.get() == 0 && HBConfig.NATURAL_VILLAGES.get() && !HBData.cultures().isEmpty()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isSpectator()) continue;
                try {
                    trySpawnNear(p.serverLevel(), p, data);
                } catch (Exception e) {
                    Hearthbound.LOGGER.error("Error founding a village: {}", e.toString());
                }
            }
        }
    }

    static boolean anyPlayerNear(ServerLevel level, BlockPos pos, double dist) {
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.blockPosition().distSqr(pos) < dist * dist) return true;
        return false;
    }

    // ================================================================== founding

    private static void trySpawnNear(ServerLevel level, ServerPlayer p, VillageData data) {
        if (!HBConfig.DIMENSIONS.get().contains(level.dimension().location().toString())) return;
        int max = HBConfig.MAX_VILLAGES.get();
        if (max > 0 && data.count(level, true) >= max) return;
        RandomSource r = level.random;
        if (r.nextDouble() > HBConfig.SPAWN_CHANCE.get()) return;
        int minD = HBConfig.MIN_SPAWN_DISTANCE.get();
        int maxD = Math.max(minD + 1, HBConfig.MAX_SPAWN_DISTANCE.get());
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = minD + r.nextDouble() * (maxD - minD);
        int x = p.getBlockX() + (int) (Math.cos(ang) * dist);
        int z = p.getBlockZ() + (int) (Math.sin(ang) * dist);
        BlockPos probe = new BlockPos(x, 0, z);
        if (!areaLoaded(level, probe, 48)) return;
        if (data.nearest(level, probe, HBConfig.VILLAGE_SPACING.get()) != null) return;
        int excl = HBConfig.WORLD_SPAWN_EXCLUSION.get();
        if (excl > 0 && level.dimension() == Level.OVERWORLD && level.getSharedSpawnPos().distSqr(probe) < (double) excl * excl) return;
        int y = Terraform.groundTop(level, x, z) + 1;
        BlockPos center = new BlockPos(x, y, z);
        if (!flatEnough(level, center, 5, HBConfig.MAX_SLOPE.get())) return;
        if (nearPlayerBuilds(level, center)) return;
        Culture c = pickCulture(level, center, r);
        if (c == null) return;
        Village v = create(level, c, center, true, null);
        if (v != null) Hearthbound.LOGGER.info("Founded {} ({}) at {}", v.name, c.id, center);
    }

    /** A village never settles on top of a player's base. */
    static boolean nearPlayerBuilds(ServerLevel level, BlockPos center) {
        if (!HBConfig.RESPECT_PLAYER_BUILDS.get()) return false;
        int r = 40;
        if (com.hearthbound.world.PlayerBuilds.anyInBox(level, new BoundingBox(center.getX() - r, center.getY() - 16, center.getZ() - r,
                center.getX() + r, center.getY() + 48, center.getZ() + r))) return true;
        for (int dx = -r; dx <= r; dx += 3) {
            for (int dz = -r; dz <= r; dz += 3) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                if (com.hearthbound.world.PlayerBuilds.columnProtected(level, x, z, center.getY() - 8, center.getY() + 32)) return true;
            }
        }
        return false;
    }

    static Culture pickCulture(ServerLevel level, BlockPos pos, RandomSource r) {
        Holder<Biome> biome = level.getBiome(pos);
        List<Culture> ok = new ArrayList<>();
        int total = 0;
        for (Culture c : HBData.cultures()) {
            if (!c.natural || c.weight <= 0 || !c.likes(biome)) continue;
            ok.add(c);
            total += c.weight;
        }
        if (ok.isEmpty()) return null;
        int pick = r.nextInt(total);
        for (Culture c : ok) {
            pick -= c.weight;
            if (pick < 0) return c;
        }
        return ok.get(0);
    }

    /** Creates a village: plaza, hearth, starter buildings (natural villages) and first residents. */
    public static Village create(ServerLevel level, Culture c, BlockPos center, boolean natural, ServerPlayer lord) {
        VillageData data = VillageData.get(level.getServer());
        Village v = new Village();
        v.culture = c.id;
        v.dimension = level.dimension();
        v.center = center;
        v.natural = natural;
        v.founded = level.getGameTime();
        v.name = uniqueName(c, level.random, data);
        v.stock.put(Resource.FOOD, 40);
        v.stock.put(Resource.WOOD, 40);
        v.stock.put(Resource.STONE, 30);
        v.stock.put(Resource.GOODS, 10);
        if (lord != null) {
            v.lord = lord.getUUID();
            v.lordName = lord.getGameProfile().getName();
        }
        data.add(v);

        List<Blueprint.Placement> square = new ArrayList<>(Terraform.plan(level, null,
                new BoundingBox(center.getX() - 4, center.getY() - 1, center.getZ() - 4, center.getX() + 4, center.getY() - 1, center.getZ() + 4),
                center.getY() - 1, center.getY() + 4, null));
        square.sort(java.util.Comparator.<Blueprint.Placement>comparingInt(pl -> pl.phase().ordinal())
                .thenComparingInt(pl -> pl.phase().topDown() ? -pl.pos().getY() : pl.pos().getY()));
        for (Blueprint.Placement pl : square) place(level, pl);
        for (Blueprint.Placement pl : Blueprint.plaza(c, center)) place(level, pl);
        level.setBlock(center, ModRegistry.VILLAGE_HEARTH.get().defaultBlockState(), 3);

        int starters = natural ? HBConfig.STARTER_BUILDINGS.get() : 1;
        for (int i = 0; i < starters && v.planIndex < c.plan.size(); i++) {
            BuildingDef def = HBData.building(c.plan.get(v.planIndex));
            v.planIndex++;
            if (def == null) continue;
            PlacedBuilding b = findPlot(level, v, def);
            if (b == null) continue;
            v.buildings.add(b);
            if (natural) {
                instantBuild(level, v, b);
            }
        }
        refreshTierCap(level, v);
        ensureResidents(level, v);
        Contracts.refreshBoard(level, v, true);
        data.setDirty();
        return v;
    }

    /** Registers an existing (vanilla) village as a Hearthbound village without building a square. */
    public static Village adopt(ServerLevel level, Culture c, BlockPos anchor) {
        VillageData data = VillageData.get(level.getServer());
        Village v = new Village();
        v.culture = c.id;
        v.dimension = level.dimension();
        v.center = anchor;
        v.natural = true;
        v.founded = level.getGameTime();
        v.name = uniqueName(c, level.random, data);
        v.stock.put(Resource.FOOD, 60);
        v.stock.put(Resource.WOOD, 50);
        v.stock.put(Resource.STONE, 40);
        v.stock.put(Resource.GOODS, 15);
        v.prosperity = 60;
        data.add(v);
        VanillaReplacement.lightHearth(level, v);
        if (HBConfig.FLAGS.get()) Flags.placeInWorld(level, v);
        Contracts.refreshBoard(level, v, true);
        Hearthbound.LOGGER.info("Adopted a vanilla village as {} ({}) at {}", v.name, c.id, anchor);
        return v;
    }

    private static String uniqueName(Culture c, RandomSource r, VillageData data) {
        for (int i = 0; i < 20; i++) {
            String n = c.randomVillageName(r);
            boolean used = false;
            for (Village o : data.all()) if (o.name.equalsIgnoreCase(n)) used = true;
            if (!used) return n;
        }
        return c.randomVillageName(r) + " " + (data.all().size() + 1);
    }

    // ================================================================== plots

    static boolean areaLoaded(ServerLevel level, BlockPos pos, int radius) {
        for (int dx = -radius; dx <= radius; dx += 16)
            for (int dz = -radius; dz <= radius; dz += 16)
                if (!level.getChunkSource().hasChunk((pos.getX() + dx) >> 4, (pos.getZ() + dz) >> 4)) return false;
        return true;
    }

    static boolean flatEnough(ServerLevel level, BlockPos c, int r, int slope) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                int x = c.getX() + dx, z = c.getZ() + dz;
                int h = Terraform.groundTop(level, x, z) + 1;
                BlockPos top = new BlockPos(x, h - 1, z);
                if (!level.getFluidState(top).isEmpty()) return false;
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        return max - min <= slope;
    }

    /** Finds a free, reasonably flat spot for a building, facing the square. */
    /** Picks a schematic for this building (config folder first, then the culture's list), or null. */
    public static Template chooseTemplate(ServerLevel level, Village v, BuildingDef def) {
        if (!HBConfig.USE_SCHEMATICS.get()) return null;
        Culture c = v.culture();
        RandomSource r = level.random;
        String bid = def.id.getPath();
        java.util.List<String> pool = new ArrayList<>();
        if (HBConfig.FOLDER_SCHEMATICS.get() && c != null) pool.addAll(Templates.fromFolder(c.id.getPath(), bid));
        boolean fromFolder = !pool.isEmpty();
        if (pool.isEmpty() && HBConfig.BUNDLED_SCHEMATICS.get() && c != null) pool.addAll(c.structures.getOrDefault(bid, java.util.List.of()));
        if (pool.isEmpty()) return null;
        double chance = fromFolder ? 1.0 : HBConfig.SCHEMATIC_CHANCE.get() * (c == null ? 1.0 : c.structureChance);
        if (r.nextDouble() > chance) return null;
        for (int tries = 0; tries < 4; tries++) {
            Template t = Templates.get(level, pool.get(r.nextInt(pool.size())));
            if (t != null) return t;
        }
        return null;
    }

    public static PlacedBuilding findPlot(ServerLevel level, Village v, BuildingDef def) {
        Template t = chooseTemplate(level, v, def);
        PlacedBuilding b = t == null ? null : findPlot(level, v, def, t);
        return b != null ? b : findPlot(level, v, def, null);
    }

    public static PlacedBuilding findPlot(ServerLevel level, Village v, BuildingDef def, Template tpl) {
        RandomSource r = level.random;
        int bw = tpl != null ? tpl.width : def.width;
        int bd = tpl != null ? tpl.depth : def.depth;
        int size = Math.max(bw, bd);
        int spacing = HBConfig.BUILDING_SPACING.get();
        int pad = HBConfig.TERRAFORM.get() ? HBConfig.TERRAFORM_PAD.get() : 0;
        int minR = 9 + size / 2 + spacing / 2;
        int maxR = Math.max(minR + 6, v.radius() + 10);
        int slope = HBConfig.MAX_SLOPE.get();
        for (int attempt = 0; attempt < 90; attempt++) {
            int ring = minR + (int) ((maxR - minR) * Math.min(1.0, attempt / 70.0)) + r.nextInt(5);
            double ang = r.nextDouble() * Math.PI * 2;
            int cx = v.center.getX() + (int) Math.round(Math.cos(ang) * ring);
            int cz = v.center.getZ() + (int) Math.round(Math.sin(ang) * ring);
            Direction facing = Direction.getNearest(v.center.getX() - cx, 0, v.center.getZ() - cz);
            if (facing.getAxis() == Direction.Axis.Y) facing = Direction.NORTH;
            // origin so that the local center (w/2, d/2) lands on (cx, cz)
            BlockPos off = Blueprint.toWorld(BlockPos.ZERO, facing, new BlockPos(bw / 2, 0, bd / 2));
            int ox = cx - off.getX(), oz = cz - off.getZ();
            BoundingBox foot = Blueprint.bounds(new BlockPos(ox, 0, oz), facing, bw, bd, 0, 0);
            // terrain
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            int n = 0;
            boolean bad = false;
            BoundingBox terrain = foot.inflatedBy(Math.max(1, pad));
            for (int x = terrain.minX(); x <= terrain.maxX() && !bad; x++) {
                for (int z = terrain.minZ(); z <= terrain.maxZ(); z++) {
                    if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                        bad = true;
                        break;
                    }
                    int h = Terraform.groundTop(level, x, z) + 1;
                    BlockPos top = new BlockPos(x, h - 1, z);
                    if (!level.getFluidState(top).isEmpty() || level.getBlockState(top).is(ModRegistry.VILLAGE_HEARTH.get())) {
                        bad = true;
                        break;
                    }
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                    n++;
                }
            }
            if (bad || n == 0 || max - min > Math.min(slope, HBConfig.TERRAFORM.get() ? slope : 12)) continue;
            // Keep the whole floor above the hillside, even without terraforming.
            int terrainOffset = tpl == null ? 0 : tpl.terrainOffset;
            int baseY = max - 1 - terrainOffset;
            int buildingHeight = tpl != null ? Math.max(1, tpl.maxY) : Blueprint.generate(v.culture(), def, 0).height();
            if (baseY + buildingHeight + 2 >= level.getMaxBuildHeight()
                    || baseY + (tpl == null ? 0 : tpl.minY) - 12 < level.getMinBuildHeight()) continue;
            // never build over or level a player's construction
            if (HBConfig.RESPECT_PLAYER_BUILDS.get()) {
                BoundingBox guard = foot.inflatedBy(pad + 2);
                int protectMinY = baseY + Math.min(terrainOffset, tpl == null ? 0 : tpl.minY) - 12;
                int protectMaxY = baseY + buildingHeight + 2;
                boolean player = false;
                for (int x = guard.minX(); x <= guard.maxX() && !player; x++) {
                    for (int z = guard.minZ(); z <= guard.maxZ(); z++) {
                        if (com.hearthbound.world.PlayerBuilds.columnProtected(level, x, z, protectMinY, protectMaxY)) {
                            player = true;
                            break;
                        }
                    }
                }
                if (player) continue;
                if (com.hearthbound.world.PlayerBuilds.anyInBox(level, new BoundingBox(guard.minX(), protectMinY, guard.minZ(), guard.maxX(), protectMaxY, guard.maxZ()))) continue;
            }
            BlockPos origin = new BlockPos(ox, baseY, oz);
            BoundingBox withMargin = Blueprint.bounds(origin, facing, bw, bd, 24, 2);
            // plaza
            int plaza = 7 + spacing / 2;
            if (withMargin.intersects(v.center.getX() - plaza, v.center.getZ() - plaza, v.center.getX() + plaza, v.center.getZ() + plaza)) continue;
            boolean overlap = false;
            for (PlacedBuilding o : v.buildings) {
                if (o.bounds.inflatedBy(spacing).intersects(withMargin)) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;
            // plots of player building projects
            for (BoundingBox a : Projects.protectedAreas(v)) {
                if (a.inflatedBy(spacing).intersects(withMargin.minX(), withMargin.minZ(), withMargin.maxX(), withMargin.maxZ())) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;
            // other villages
            for (Village o : VillageData.get(level.getServer()).all()) {
                if (o == v || !o.dimension.equals(v.dimension)) continue;
                for (PlacedBuilding ob : o.buildings) if (ob.bounds.intersects(withMargin)) overlap = true;
            }
            if (overlap) continue;
            int h = buildingHeight;
            BoundingBox bounds = Blueprint.bounds(origin, facing, bw, bd, h + 1, 0);
            bounds = new BoundingBox(bounds.minX(), baseY + (tpl == null ? 0 : Math.min(tpl.minY, terrainOffset)),
                    bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
            PlacedBuilding pb = new PlacedBuilding(def.id, origin, facing, bw, bd, h, r.nextLong(), bounds);
            if (tpl != null) {
                pb.template = tpl.id;
                pb.doorX = tpl.doorX;
                pb.terrainOffset = terrainOffset;
            }
            return pb;
        }
        return null;
    }

    // ================================================================== construction

    private static String key(Village v, PlacedBuilding b) {
        return v.id + "/" + v.buildings.indexOf(b);
    }

    private static List<Blueprint.Placement> plan(ServerLevel level, Village v, PlacedBuilding b) {
        String k = key(v, b);
        List<Blueprint.Placement> list = PLANS.get(k);
        if (list == null) {
            BuildingDef def = b.definition();
            if (def == null) return List.of();
            list = blueprintFor(level, v, b, def).resolve(level, b.origin, b.facing, groundWork(level, v, b), b.terrainOffset);
            PLANS.put(k, list);
            CURSORS.put(k, 0);
        }
        return list;
    }

    private static void constructStep(ServerLevel level, Village v, VillageData data) {
        PlacedBuilding b = v.construction();
        if (b == null) return;
        if (b.definition() == null) {
            b.complete = true;
            return;
        }
        if (HBConfig.REQUIRE_BUILDER.get() && !v.has(Role.BUILDER)) return;
        List<Blueprint.Placement> list = plan(level, v, b);
        String k = key(v, b);
        int cursor = CURSORS.getOrDefault(k, 0);
        int budget = HBConfig.BLOCKS_PER_STEP.get();
        BlockPos last = null;
        while (budget > 0 && cursor < list.size()) {
            Blueprint.Placement pl = list.get(cursor);
            if (!level.isLoaded(pl.pos())) break;
            cursor++;
            if (place(level, pl)) {
                budget--;
                last = pl.pos();
            }
        }
        CURSORS.put(k, cursor);
        b.total = list.size();
        b.placed = cursor;
        if (last != null) {
            BlockState s = level.getBlockState(last);
            if (!s.isAir()) {
                level.playSound(null, last, s.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.5f, 0.9f);
                level.sendParticles(ParticleTypes.POOF, last.getX() + 0.5, last.getY() + 0.5, last.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
            }
        }
        if (cursor >= list.size()) {
            finish(level, v, b);
        }
        data.setDirty();
    }

    /** Places one block. Returns false when nothing had to change (free). */
    static boolean place(ServerLevel level, Blueprint.Placement pl) {
        BlockPos pos = pl.pos();
        BlockState cur = level.getBlockState(pos);
        BlockState want = pl.state();
        if (cur == want) return false;
        if (cur.getDestroySpeed(level, pos) < 0 || Terraform.untouchable(level, pos, cur)) return false;
        if (pl.phase() == Blueprint.Phase.FILL) {
            level.setBlock(pos, want, Block.UPDATE_CLIENTS);
            return true;
        }
        if (pl.phase() == Blueprint.Phase.CLEAR || pl.phase() == Blueprint.Phase.CUT || pl.phase() == Blueprint.Phase.TREES) {
            if (cur.isAir()) return false;
            level.setBlock(pos, want, Block.UPDATE_CLIENTS);
            return true;
        }
        if (want.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            if (want.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                return false; // placed together with the lower half
            }
            level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(pos.above(), want.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            return true;
        }
        if (want.getBlock() instanceof BedBlock && want.hasProperty(BlockStateProperties.BED_PART)) {
            if (want.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return false;
            Direction f = want.getValue(BedBlock.FACING);
            level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(pos.relative(f), want.setValue(BlockStateProperties.BED_PART, BedPart.HEAD), Block.UPDATE_ALL);
            return true;
        }
        if (connects(want)) want = Block.updateFromNeighbourShapes(want, level, pos);
        level.setBlock(pos, want, Block.UPDATE_ALL);
        return true;
    }

    /** Blocks whose shape depends on their neighbours (glass panes, iron bars, fences, walls, stairs). */
    static boolean connects(BlockState s) {
        Block b = s.getBlock();
        return b instanceof net.minecraft.world.level.block.CrossCollisionBlock || b instanceof net.minecraft.world.level.block.WallBlock
                || b instanceof net.minecraft.world.level.block.StairBlock;
    }

    /** Recomputes the connections of panes, bars, fences and walls of a finished building. */
    static void reconnect(ServerLevel level, PlacedBuilding b) {
        BoundingBox box = b.bounds.inflatedBy(1);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    m.set(x, y, z);
                    BlockState st = level.getBlockState(m);
                    if (!connects(st)) continue;
                    BlockState fixed = Block.updateFromNeighbourShapes(st, level, m);
                    if (fixed != st) level.setBlock(m, fixed, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        b.connected = true;
    }

    /** The schematic of a building when it has one (and it still exists), otherwise its procedural design. */
    static Blueprint blueprintFor(ServerLevel level, Village v, PlacedBuilding b, BuildingDef def) {
        if (b.template != null) {
            Template t = Templates.get(level, b.template);
            if (t != null && t.width == b.width && t.depth == b.depth) return Blueprint.fromTemplate(v.culture(), t);
        }
        return Blueprint.generate(v.culture(), def, b.seed);
    }

    /** Tree felling, levelling and blending around a building. */
    static List<Blueprint.Placement> groundWork(ServerLevel level, Village v, PlacedBuilding b) {
        BoundingBox foot = new BoundingBox(b.bounds.minX(), b.origin.getY(), b.bounds.minZ(), b.bounds.maxX(), b.origin.getY(), b.bounds.maxZ());
        return Terraform.plan(level, v, foot, b.origin.getY() + b.terrainOffset, b.origin.getY() + b.height, b);
    }

    static void instantBuild(ServerLevel level, Village v, PlacedBuilding b) {
        BuildingDef def = b.definition();
        if (def == null) return;
        List<Blueprint.Placement> list = blueprintFor(level, v, b, def).resolve(level, b.origin, b.facing, groundWork(level, v, b), b.terrainOffset);
        for (Blueprint.Placement pl : list) place(level, pl);
        b.total = list.size();
        b.placed = list.size();
        b.complete = true;
        reconnect(level, b);
        if (HBConfig.BUILD_PATHS.get()) path(level, v, b.doorstep(), v.center);
    }

    private static void finish(ServerLevel level, Village v, PlacedBuilding b) {
        reconnect(level, b);
        String k = key(v, b);
        PLANS.remove(k);
        CURSORS.remove(k);
        int tierBefore = v.tier();
        b.complete = true;
        if (HBConfig.BUILD_PATHS.get()) path(level, v, b.doorstep(), v.center);
        ensureResidents(level, v);
        BuildingDef def = b.definition();
        Component name = def == null ? Component.literal(b.def.toString()) : def.title();
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(v.center) < (double) (v.radius() + 48) * (v.radius() + 48)) {
                Net.notify(p, Component.translatable("hearthbound.notify.built", v.name, name), 0xFFE0B25A);
                if (v.tier() > tierBefore) {
                    Net.banner(p, Component.translatable("hearthbound.banner.tierup", Component.translatable("hearthbound.tier." + v.tier())),
                            Component.literal(v.name), v.culture() == null ? 0xFFE0B25A : v.culture().color);
                }
            }
        }
        level.playSound(null, b.center(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        VillageData.get(level.getServer()).setDirty();
    }

    /** Lays a path from {@code from} to {@code to}, only over natural ground. */
    static void path(ServerLevel level, Village v, BlockPos from, BlockPos to) {
        Culture c = v.culture();
        BlockState path = c == null ? Blocks.DIRT_PATH.defaultBlockState() : c.block("path", Blocks.DIRT_PATH.defaultBlockState());
        int x0 = from.getX(), z0 = from.getZ(), x1 = to.getX(), z1 = to.getZ();
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int guard = 0;
        while (guard++ < 400) {
            BlockPos col = new BlockPos(x0, 0, z0);
            if (col.distSqr(new BlockPos(to.getX(), 0, to.getZ())) <= 25) break;
            paveAt(level, v, x0, z0, path);
            if (x0 == x1 && z0 == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                z0 += sz;
            }
            // widen diagonal steps so the path stays walkable
            paveAt(level, v, x0, z0 - sz, path);
        }
    }

    private static void paveAt(ServerLevel level, Village v, int x, int z, BlockState path) {
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return;
        int h = Terraform.groundTop(level, x, z) + 1;
        BlockPos top = new BlockPos(x, h - 1, z);
        for (PlacedBuilding b : v.buildings) if (b.bounds.isInside(top) || b.bounds.isInside(top.above())) return;
        BlockState s = level.getBlockState(top);
        if (Terraform.untouchable(level, top, s) || Terraform.untouchable(level, top.above(), level.getBlockState(top.above()))) return;
        boolean natural = s.is(BlockTags.DIRT) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(BlockTags.BASE_STONE_OVERWORLD);
        if (!natural) return;
        level.setBlock(top, path, Block.UPDATE_ALL);
        BlockState above = level.getBlockState(top.above());
        if (!above.isAir() && above.canBeReplaced()) level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    // ================================================================== economy

    private static void economy(ServerLevel level, Village v, VillageData data) {
        double mult = HBConfig.PRODUCTION_MULTIPLIER.get();
        int pop = v.population();
        int[] gain = new int[Resource.values().length];
        gain[Resource.WOOD.ordinal()] += 1 + pop / 4;
        gain[Resource.STONE.ordinal()] += 1 + pop / 5;
        for (Resident r : v.residents) {
            if (!r.alive()) continue;
            if (r.role == Role.FARMER) gain[Resource.FOOD.ordinal()] += 2;
            if (r.role == Role.SMITH) gain[Resource.GOODS.ordinal()] += 1;
            if (r.role == Role.BUILDER) gain[Resource.WOOD.ordinal()] += 1;
        }
        for (PlacedBuilding b : v.buildings) {
            if (!b.complete) continue;
            BuildingDef d = b.definition();
            if (d == null) continue;
            d.produces.forEach((res, amt) -> gain[res.ordinal()] += amt);
        }
        for (Resource r : Resource.values()) v.add(r, (int) Math.round(gain[r.ordinal()] * mult));
        int upkeep = (pop + 2) / 3;
        if (v.get(Resource.FOOD) < upkeep) {
            v.stock.put(Resource.FOOD, 0);
            v.prosperity = Math.max(0, v.prosperity - 2);
        } else {
            v.add(Resource.FOOD, -upkeep);
            v.prosperity = Math.min(100, v.prosperity + 1);
        }
        refreshTierCap(level, v);
        if (v.construction() == null) startNext(level, v);
        ensureResidents(level, v);
        rebindSettlers(level, v);
        data.setDirty();
    }

    /** One player's recorded age unlocks growth server-wide, including while offline. */
    static void refreshTierCap(ServerLevel level, Village v) {
        int cap = Math.min(HBConfig.MAX_TIER.get(), Math.max(v.tier(), Compat.sharedVillageTier(level.getServer())));
        if (v.tierCap != cap) {
            v.tierCap = cap;
            VillageData.get(level.getServer()).setDirty();
        }
    }

    /** The building the village wants next (lord's choice first), or null when the plan is done. */
    public static BuildingDef nextBuilding(Village v) {
        if (v.lordChoice != null) {
            BuildingDef d = HBData.building(v.lordChoice);
            if (d != null) return d;
        }
        Culture c = v.culture();
        if (c == null || v.planIndex >= c.plan.size()) return null;
        return HBData.building(c.plan.get(v.planIndex));
    }

    public static Map<Resource, Integer> cost(BuildingDef def) {
        Map<Resource, Integer> out = new java.util.EnumMap<>(Resource.class);
        def.cost.forEach((r, a) -> out.put(r, (int) Math.ceil(a * HBConfig.COST_MULTIPLIER.get())));
        return out;
    }

    /** Why the next building is waiting (null when it can start). */
    public static Component blocker(ServerLevel level, Village v) {
        BuildingDef def = nextBuilding(v);
        if (def == null) return Component.translatable("hearthbound.ui.plan_done");
        if (projectedTier(v) > Math.min(v.tierCap, HBConfig.MAX_TIER.get())) return Component.translatable("hearthbound.ui.wait_tier");
        if (!def.age.isEmpty()) {
            boolean any = false;
            for (ServerPlayer p : level.players()) if (p.blockPosition().distSqr(v.center) < 200 * 200 && Compat.ageReached(p, def.age)) any = true;
            if (!any) return Component.translatable("hearthbound.ui.wait_age", Compat.ageName(null, def.age));
        }
        for (Map.Entry<Resource, Integer> e : cost(def).entrySet()) {
            if (v.get(e.getKey()) < e.getValue()) return Component.translatable("hearthbound.ui.wait_resources");
        }
        return null;
    }

    private static int projectedTier(Village v) {
        List<? extends Integer> t = HBConfig.TIER_THRESHOLDS.get();
        int done = v.completedBuildings() + 1;
        int tier = 0;
        for (int i = 0; i < t.size(); i++) if (done >= t.get(i)) tier = i;
        return tier;
    }

    private static void startNext(ServerLevel level, Village v) {
        if (blocker(level, v) != null) return;
        BuildingDef def = nextBuilding(v);
        PlacedBuilding b = findPlot(level, v, def);
        if (b == null) {
            int f = PLOT_FAILURES.merge(v.id, 1, Integer::sum);
            if (f >= 6) {
                // skip a building that does not fit anywhere
                PLOT_FAILURES.remove(v.id);
                if (v.lordChoice != null) v.lordChoice = null;
                else v.planIndex++;
            }
            return;
        }
        PLOT_FAILURES.remove(v.id);
        cost(def).forEach((r, a) -> v.add(r, -a));
        if (v.lordChoice != null && v.lordChoice.equals(def.id)) v.lordChoice = null;
        else v.planIndex++;
        v.buildings.add(b);
    }

    // ================================================================== residents

    /** Spawns missing residents (new jobs, free beds, respawns) at their homes. */
    public static void ensureResidents(ServerLevel level, Village v) {
        Culture c = v.culture();
        if (c == null) return;
        long now = level.getGameTime();
        int respawnDays = HBConfig.RESPAWN_DAYS.get();
        // respawns
        for (Resident r : v.residents) {
            if (!r.alive() && respawnDays > 0 && now - r.deadSince > respawnDays * 24000L) {
                r.deadSince = -1;
                spawnResident(level, v, r, c);
            } else if (r.alive() && r.entity == null) {
                spawnResident(level, v, r, c); // moved here from another village
            }
        }
        // new residents
        int housing = v.housing();
        int guard = 0;
        while (v.residents.size() < housing && guard++ < 16) {
            Resident r = new Resident();
            r.role = nextRole(v);
            r.female = level.random.nextBoolean();
            r.variant = level.random.nextInt(2);
            r.name = c.randomName(level.random, r.female);
            r.work = workplaceFor(v, r.role);
            r.home = homeFor(v);
            Persons.assignPersona(v, r, level.random);
            v.residents.add(r);
            spawnResident(level, v, r, c);
        }
        promoteGuard(level, v, c);
        if (assignBeds(level, v)) rebindSettlers(level, v);
    }

    // ================================================================== beds

    /** Head blocks of the beds inside a building, or null if it is not loaded. */
    private static List<BlockPos> bedsIn(ServerLevel level, PlacedBuilding b) {
        BoundingBox bb = b.bounds;
        if (!level.hasChunkAt(new BlockPos(bb.minX(), bb.minY(), bb.minZ())) || !level.hasChunkAt(new BlockPos(bb.maxX(), bb.minY(), bb.maxZ()))
                || !level.hasChunkAt(new BlockPos(bb.minX(), bb.minY(), bb.maxZ())) || !level.hasChunkAt(new BlockPos(bb.maxX(), bb.minY(), bb.minZ()))) return null;
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())) {
            if (isBedHead(level.getBlockState(pos))) out.add(pos.immutable());
        }
        return out;
    }

    public static boolean isBedHead(BlockState s) {
        return s.getBlock() instanceof BedBlock && s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    /**
     * Gives every resident a bed of the village: they keep the one they have while it stands, the rest get a free
     * bed in their own house or, failing that, the nearest free bed in the village (which becomes their home).
     * Returns true if anything changed.
     */
    static boolean assignBeds(ServerLevel level, Village v) {
        boolean changed = false;
        java.util.Set<BlockPos> taken = new java.util.HashSet<>();
        // keep the beds that still exist (or cannot be checked now)
        for (Resident r : v.residents) {
            if (r.bed == null) continue;
            boolean valid = !level.hasChunkAt(r.bed) || isBedHead(level.getBlockState(r.bed));
            if (valid && taken.add(r.bed)) continue;
            r.bed = null;
            changed = true;
        }
        // free beds of finished buildings, by building
        Map<BlockPos, Integer> free = new java.util.LinkedHashMap<>();
        for (int i = 0; i < v.buildings.size(); i++) {
            PlacedBuilding b = v.buildings.get(i);
            if (!b.complete) continue;
            List<BlockPos> beds = bedsIn(level, b);
            if (beds == null) continue;
            for (BlockPos pos : beds) if (!taken.contains(pos)) free.putIfAbsent(pos, i);
        }
        for (Resident r : v.residents) {
            if (r.bed != null || !r.alive()) continue;
            if (free.isEmpty()) break;
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            BlockPos from = r.home >= 0 && r.home < v.buildings.size() ? v.buildings.get(r.home).center() : v.center;
            for (Map.Entry<BlockPos, Integer> e : free.entrySet()) {
                double d = e.getKey().distSqr(from) + (e.getValue() == r.home ? 0 : 10000); // own house first
                if (d < bestD) {
                    best = e.getKey();
                    bestD = d;
                }
            }
            r.bed = best;
            r.home = free.remove(best);
            changed = true;
        }
        // the home follows the bed (beds claimed by the settlers themselves included)
        for (Resident r : v.residents) {
            if (r.bed == null) continue;
            PlacedBuilding b = v.buildingAt(r.bed);
            int i = b == null ? -1 : v.buildings.indexOf(b);
            if (i >= 0 && i != r.home) {
                r.home = i;
                changed = true;
            }
        }
        return changed;
    }

    /** A settler without a bed claims a free one it found, like vanilla villagers do. */
    public static boolean claimBed(ServerLevel level, SettlerEntity e, BlockPos pos) {
        Village v = VillageData.get(level.getServer()).get(e.villageId());
        if (v == null) return false;
        Resident self = null;
        for (Resident r : v.residents) {
            if (e.getUUID().equals(r.entity)) self = r;
            else if (pos.equals(r.bed)) return false;
        }
        if (self == null) return false;
        self.bed = pos.immutable();
        PlacedBuilding b = v.buildingAt(pos);
        if (b != null) self.home = v.buildings.indexOf(b);
        bind(v, self, e);
        VillageData.get(level.getServer()).setDirty();
        return true;
    }

    /** How many guards the village wants: one per {@code residentsPerGuard} people, never fewer than {@code minGuards} or its watchtower posts. */
    static int guardsWanted(Village v, int population) {
        int per = HBConfig.RESIDENTS_PER_GUARD.get();
        int want = Math.max(HBConfig.MIN_GUARDS.get(), per > 0 ? (population + per - 1) / per : 0);
        return Math.max(want, guardPosts(v));
    }

    private static int guardPosts(Village v) {
        int n = 0;
        for (PlacedBuilding b : v.buildings) {
            if (!b.complete || b.definition() == null) continue;
            for (Role r : b.definition().roles) if (r == Role.GUARD) n++;
        }
        return n;
    }

    private static int guards(Village v) {
        int n = 0;
        for (Resident r : v.residents) if (r.role == Role.GUARD) n++;
        return n;
    }

    /** A village short of guards arms one of its unemployed villagers (fixes villages founded before the guard quota). */
    private static void promoteGuard(ServerLevel level, Village v, Culture c) {
        if (guards(v) >= guardsWanted(v, v.residents.size())) return;
        for (Resident r : v.residents) {
            if (r.role != Role.VILLAGER || !r.alive() || r.entity == null) continue;
            if (!(level.getEntity(r.entity) instanceof SettlerEntity e)) continue;
            r.role = Role.GUARD;
            r.work = workplaceFor(v, Role.GUARD);
            e.promote(c, Role.GUARD);
            bind(v, r, e);
            VillageData.get(level.getServer()).setDirty();
            return; // one per tick is enough
        }
    }

    private static Role nextRole(Village v) {
        List<Role> jobs = new ArrayList<>();
        if (v.buildings.stream().noneMatch(b -> b.complete && b.definition() != null && b.definition().roles.contains(Role.ELDER))) jobs.add(Role.ELDER);
        for (PlacedBuilding b : v.buildings) {
            if (!b.complete || b.definition() == null) continue;
            jobs.addAll(b.definition().roles);
        }
        // guards are handled by the quota below (watchtower posts count towards it)
        jobs.removeIf(role -> role == Role.GUARD);
        for (Resident r : v.residents) jobs.remove(r.role);
        int guards = guards(v);
        int want = guardsWanted(v, v.residents.size() + 1);
        // the elder and the builder come first: without them the village does not grow
        for (Role key : List.of(Role.ELDER, Role.BUILDER)) if (jobs.contains(key)) return key;
        if (guards < Math.min(HBConfig.MIN_GUARDS.get(), want)) return Role.GUARD;
        if (!jobs.isEmpty()) return jobs.get(0);
        if (guards < want) return Role.GUARD;
        return v.residents.size() % 3 == 0 ? Role.FARMER : Role.VILLAGER;
    }

    static int workplaceFor(Village v, Role role) {
        for (int i = 0; i < v.buildings.size(); i++) {
            PlacedBuilding b = v.buildings.get(i);
            if (b.complete && b.definition() != null && b.definition().roles.contains(role)) return i;
        }
        return -1;
    }

    static int homeFor(Village v) {
        int[] used = new int[v.buildings.size()];
        for (Resident r : v.residents) if (r.home >= 0 && r.home < used.length) used[r.home]++;
        for (int i = 0; i < v.buildings.size(); i++) {
            PlacedBuilding b = v.buildings.get(i);
            BuildingDef d = b.definition();
            if (!b.complete || d == null) continue;
            int cap = d.residents < 0 ? HBConfig.RESIDENTS_PER_HOUSE.get() : d.residents;
            if (used[i] < cap) return i;
        }
        return -1;
    }

    static boolean spawnResident(ServerLevel level, Village v, Resident r, Culture c) {
        SettlerEntity e = ModRegistry.SETTLER.get().create(level);
        if (e == null) return false;
        BlockPos at = v.center.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2);
        if (r.home >= 0 && r.home < v.buildings.size()) at = v.buildings.get(r.home).doorstep();
        if (!level.hasChunkAt(at)) at = v.center;
        if (!level.hasChunkAt(at)) {
            e.discard();
            return false; // r.entity stays null: tried again on the next village tick
        }
        at = safeSpot(level, at);
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        e.setup(c, r.role, r.name, r.female, r.variant);
        bind(v, r, e);
        if (!level.addFreshEntity(e)) return false;
        r.entity = e.getUUID();
        return true;
    }

    /**
     * A place to stand near {@code at}: searched from its own height (so a settler spawning at a
     * door under the eaves stays on the ground instead of landing on the roof).
     */
    static BlockPos safeSpot(ServerLevel level, BlockPos at) {
        for (int dy = 0; dy <= 4; dy++) {
            BlockPos p = at.above(dy);
            if (standable(level, p)) return p;
        }
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos p = at.below(dy);
            if (standable(level, p)) return p;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos p = at.relative(d).above(dy);
                if (standable(level, p)) return p;
            }
        }
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        return new BlockPos(at.getX(), h, at.getZ());
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p.below()).isSolid()
                && level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
    }

    /** Settlers stranded on a roof (older versions spawned some there) are brought back to the door. */
    static void rescueFromRoofs(ServerLevel level, Village v, List<SettlerEntity> settlers) {
        for (SettlerEntity e : settlers) {
            if (e.isCompanion() || e.isTraveler() || e.isWarband() || !e.onGround()) continue;
            BlockPos at = e.blockPosition();
            for (PlacedBuilding b : v.buildings) {
                if (!b.complete) continue;
                BoundingBox bb = b.bounds;
                if (at.getX() < bb.minX() - 1 || at.getX() > bb.maxX() + 1 || at.getZ() < bb.minZ() - 1 || at.getZ() > bb.maxZ() + 1) continue;
                BlockPos door = b.doorstep();
                if (at.getY() >= door.getY() + 2 && level.canSeeSky(at)) {
                    BlockPos to = safeSpot(level, door);
                    e.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
                    e.getNavigation().stop();
                }
                break;
            }
        }
    }

    private static void bind(Village v, Resident r, SettlerEntity e) {
        BlockPos home = r.home >= 0 && r.home < v.buildings.size() ? v.buildings.get(r.home).center() : null;
        BlockPos work;
        PlacedBuilding site = v.construction();
        if (r.role == Role.BUILDER && site != null) work = site.center();
        else if (r.work >= 0 && r.work < v.buildings.size()) work = v.buildings.get(r.work).center();
        else if (r.role == Role.GUARD) work = v.center;
        else work = null;
        e.bindVillage(v.id, v.center, v.radius(), home, work);
        e.setBed(r.bed);
    }

    /** Refreshes home/work/radius of loaded settlers and fills in missing workplaces. */
    private static void rebindSettlers(ServerLevel level, Village v) {
        int r = v.radius() + 32;
        AABB box = new AABB(v.center).inflate(r, 64, r);
        List<SettlerEntity> list = level.getEntitiesOfClass(SettlerEntity.class, box, s -> v.id.equals(s.villageId()));
        rescueFromRoofs(level, v, list);
        for (SettlerEntity e : list) {
            for (Resident res : v.residents) {
                if (e.getUUID().equals(res.entity)) {
                    if (res.work < 0) res.work = workplaceFor(v, res.role);
                    if (res.home < 0) res.home = homeFor(v);
                    bind(v, res, e);
                }
            }
        }
    }

    public static void onSettlerDeath(ServerLevel level, SettlerEntity e) {
        Village v = VillageData.get(level.getServer()).get(e.villageId());
        if (v == null) return;
        for (Resident r : v.residents) {
            if (e.getUUID().equals(r.entity)) r.deadSince = level.getGameTime();
        }
        VillageData.get(level.getServer()).setDirty();
    }

    // ================================================================== daily

    private static void daily(ServerLevel level, Village v, VillageData data) {
        long day = level.getDayTime() / 24000L;
        if (v.tradeDay != day) {
            v.tradeDay = day;
            v.soldToday.clear();
            data.setDirty();
        }
        if (v.boardDay != day) {
            Contracts.refreshBoard(level, v, false);
            data.setDirty();
        }
        if (v.lord != null && v.tributeDay != day) {
            v.tributeDay = day;
            v.treasury += v.population() * HBConfig.LORD_TRIBUTE_PER_SETTLER.get();
            data.setDirty();
        }
    }

    // ================================================================== raids

    public static final String RAID_VILLAGE_KEY = "hbRaidVillage";
    public static final String RAID_ID_KEY = "hbRaidId";
    private static final Map<UUID, Long> LOOT_NOTICE = new HashMap<>();

    private static void raids(ServerLevel level, Village v, VillageData data) {
        long now = level.getGameTime();
        if (v.raidRemaining > 0) {
            if (v.raidEnds <= 0) v.raidEnds = now + HBConfig.RAID_TIMEOUT.get() * 20L;
            if (now > v.raidEnds) endRaid(level, v, data);
            return; // one attack at a time
        }
        if (!HBConfig.RAIDS_ENABLED.get()) return;
        long time = level.getDayTime() % 24000L;
        long day = level.getDayTime() / 24000L;
        // night raids of monsters
        if (v.tier() >= HBConfig.RAID_MIN_TIER.get() && time >= 13000 && time <= 22000 && v.lastRaidNight != day) {
            v.lastRaidNight = day;
            data.setDirty();
            if (level.random.nextDouble() <= HBConfig.RAID_CHANCE.get()) {
                startRaid(level, v);
                return;
            }
        }
        // pillagers come at a random moment of the day, different for every village
        if (HBConfig.PILLAGER_RAIDS.get() && v.tier() >= HBConfig.PILLAGER_MIN_TIER.get() && v.lastPillagerDay != day) {
            long slot = 1000 + Math.floorMod(v.id.getMostSignificantBits() + day * 7919L, 21000L);
            if (time >= slot) {
                v.lastPillagerDay = day;
                data.setDirty();
                if (level.random.nextDouble() <= HBConfig.PILLAGER_CHANCE.get() && pillagerAgeReached(level, v)) startPillagerRaid(level, v);
            }
        }
    }

    private static boolean pillagerAgeReached(ServerLevel level, Village v) {
        String age = HBConfig.ASC_AGE_PILLAGERS.get();
        if (age == null || age.isBlank() || !Compat.ages()) return true;
        double r = v.radius() + 160;
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(v.center) < r * r && Compat.ageReached(p, age)) return true;
        }
        return false;
    }

    /** The attackers were not beaten in time: they leave, with what they took. */
    private static void endRaid(ServerLevel level, Village v, VillageData data) {
        int r = v.radius() + 64;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(v.center).inflate(r, 48, r), m -> isRaiderOf(m, v))) {
            level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            m.discard();
        }
        v.raidRemaining = 0;
        v.raidEnds = 0;
        v.raidId = 0;
        v.prosperity = Math.max(0, v.prosperity - 4);
        data.setDirty();
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(v.center) < (double) (v.radius() + 96) * (v.radius() + 96)) {
                Net.notify(p, Component.translatable("hearthbound.raid.retreat", v.name), 0xFFE0A050);
            }
        }
    }

    public static boolean isRaiderOf(Mob m, Village v) {
        if (!m.getTags().contains(RAIDER_TAG)) return false;
        var tag = m.getPersistentData();
        return !tag.hasUUID(RAID_VILLAGE_KEY) || v.id.equals(tag.getUUID(RAID_VILLAGE_KEY));
    }

    /** True if the mob belongs to the raid the village is suffering right now. */
    public static boolean inCurrentRaid(Mob m, Village v) {
        if (v == null || v.raidRemaining <= 0) return false;
        var tag = m.getPersistentData();
        if (!tag.hasUUID(RAID_VILLAGE_KEY) || !v.id.equals(tag.getUUID(RAID_VILLAGE_KEY))) return false;
        return !tag.contains(RAID_ID_KEY) || tag.getLong(RAID_ID_KEY) == v.raidId;
    }

    public static void startRaid(ServerLevel level, Village v) {
        int n = HBConfig.RAID_BASE_SIZE.get() + HBConfig.RAID_SIZE_PER_TIER.get() * v.tier();
        spawnRaid(level, v, HBConfig.RAID_MOBS.get(), n, false);
    }

    public static void startPillagerRaid(ServerLevel level, Village v) {
        int n = HBConfig.PILLAGER_BASE.get() + HBConfig.PILLAGER_PER_TIER.get() * v.tier();
        spawnRaid(level, v, HBConfig.PILLAGER_MOBS.get(), n, true);
    }

    private static void spawnRaid(ServerLevel level, Village v, List<? extends String> mobs, int n, boolean pillagers) {
        if (mobs.isEmpty()) return;
        double ang = level.random.nextDouble() * Math.PI * 2;
        int dist = v.radius() + 10;
        int bx = v.center.getX() + (int) (Math.cos(ang) * dist);
        int bz = v.center.getZ() + (int) (Math.sin(ang) * dist);
        List<String> band = new ArrayList<>();
        for (int i = 0; i < n; i++) band.add(mobs.get(level.random.nextInt(mobs.size())));
        if (pillagers) {
            band.add(0, "minecraft:pillager"); // the captain
            if (v.tier() >= HBConfig.PILLAGER_RAVAGER_TIER.get()) band.add("minecraft:ravager");
        }
        int spawned = 0;
        long raidId = level.getGameTime();
        v.raidId = raidId;
        v.raidRemaining = band.size(); // provisional, so the mobs are accepted when they join the level
        for (int i = 0; i < band.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(band.get(i));
            if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) continue;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            int x = bx + level.random.nextInt(9) - 4, z = bz + level.random.nextInt(9) - 4;
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!(type.create(level) instanceof Mob mob)) continue;
            mob.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
            mob.addTag(RAIDER_TAG);
            mob.getPersistentData().putUUID(RAID_VILLAGE_KEY, v.id);
            mob.getPersistentData().putLong(RAID_ID_KEY, raidId);
            mob.setPersistenceRequired();
            if (mob instanceof net.minecraft.world.entity.raid.Raider raider) raider.setCanJoinRaid(false);
            if (i == 0) {
                boolean captain = pillagers && mob instanceof net.minecraft.world.entity.raid.Raider;
                mob.setCustomName(Component.translatable(captain ? "hearthbound.raid.captain" : "hearthbound.raid.warlord").withColor(0xB0503A));
                mob.setCustomNameVisible(true);
                var hp = mob.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(hp.getBaseValue() * (captain ? 2.0 : 2.5));
                mob.setHealth(mob.getMaxHealth());
                if (captain) {
                    ((net.minecraft.world.entity.raid.Raider) mob).setPatrolLeader(true);
                    mob.setItemSlot(EquipmentSlot.HEAD, net.minecraft.world.entity.raid.Raid.getLeaderBannerInstance(
                            level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN)));
                    mob.setDropChance(EquipmentSlot.HEAD, 2.0f);
                } else {
                    mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                }
                mob.setGlowingTag(true);
            }
            level.addFreshEntity(mob);
            spawned++;
        }
        v.raidRemaining = spawned;
        if (spawned == 0) {
            v.raidId = 0;
            return;
        }
        v.raidEnds = level.getGameTime() + HBConfig.RAID_TIMEOUT.get() * 20L;
        v.raidPillagers = pillagers;
        VillageData.get(level.getServer()).setDirty();
        String k = pillagers ? "hearthbound.raid.pillagers" : "hearthbound.raid";
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(v.center) < (double) (v.radius() + 96) * (v.radius() + 96)) {
                Net.banner(p, Component.translatable(k + ".title"), Component.translatable(k + ".sub", v.name), 0xFFB0503A);
                level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, pillagers ? 0.9f : 1f);
            }
        }
    }

    /** Somewhere in the village worth raiding: a house door, or the plaza. */
    public static BlockPos raidWaypoint(Village v, RandomSource random) {
        List<BlockPos> spots = new ArrayList<>();
        for (PlacedBuilding b : v.buildings) if (b.complete) spots.add(b.doorstep());
        if (spots.isEmpty() || random.nextInt(5) == 0) return v.center;
        return spots.get(random.nextInt(spots.size()));
    }

    /** A pillager ransacks a house: the village loses some resources and a bit of prosperity. */
    public static void loot(ServerLevel level, Village v, Mob mob) {
        if (!HBConfig.PILLAGER_LOOT.get()) return;
        List<Resource> have = new ArrayList<>();
        for (Resource r : Resource.values()) if (v.get(r) > 0) have.add(r);
        BlockPos at = mob.blockPosition();
        level.playSound(null, at, SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 1f, 1f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.01);
        v.prosperity = Math.max(0, v.prosperity - 1);
        if (!have.isEmpty()) {
            Resource r = have.get(level.random.nextInt(have.size()));
            int n = Math.min(v.get(r), 2 + level.random.nextInt(4));
            v.add(r, -n);
            long now = level.getGameTime();
            Long last = LOOT_NOTICE.get(v.id);
            if (last == null || now - last > 200) {
                LOOT_NOTICE.put(v.id, now);
                for (ServerPlayer p : level.players()) {
                    if (p.blockPosition().distSqr(v.center) < (double) (v.radius() + 96) * (v.radius() + 96)) {
                        Net.notify(p, Component.translatable("hearthbound.raid.loot", n, Component.translatable("hearthbound.resource." + r.id()), v.name), 0xFFE06A5A);
                    }
                }
            }
        }
        VillageData.get(level.getServer()).setDirty();
    }

    public static void clearCaches() {
        PLANS.clear();
        CURSORS.clear();
        PLOT_FAILURES.clear();
    }

    /** Used by the /hearthbound grow command: finish the current building immediately. */
    public static void finishNow(ServerLevel level, Village v) {
        PlacedBuilding b = v.construction();
        if (b == null) {
            startNextForced(level, v);
            b = v.construction();
        }
        if (b == null) return;
        String k = key(v, b);
        PLANS.remove(k);
        CURSORS.remove(k);
        instantBuild(level, v, b);
        finish(level, v, b);
    }

    private static void startNextForced(ServerLevel level, Village v) {
        BuildingDef def = nextBuilding(v);
        if (def == null) return;
        PlacedBuilding b = findPlot(level, v, def);
        if (b == null) return;
        if (v.lordChoice != null && v.lordChoice.equals(def.id)) v.lordChoice = null;
        else v.planIndex++;
        v.buildings.add(b);
    }
}

