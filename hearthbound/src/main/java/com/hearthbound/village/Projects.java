package com.hearthbound.village;

import com.hearthbound.block.ProjectStoneBlock;
import com.hearthbound.block.ProjectStoneBlockEntity;
import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.data.ContractTemplate;
import com.hearthbound.data.HBData;
import com.hearthbound.data.ProjectDef;
import com.hearthbound.item.ProjectStoneItem;
import com.hearthbound.network.Net;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.rpg.Contract;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rank;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.rpg.Wallet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Player building projects. A village offers the next level of each project type; a player
 * takes it and receives a foundation stone, places it on a free plot (which can be enlarged from
 * the stone), builds freely and hands the building in from the stone once the required blocks
 * are there. Finished projects earn trust and unlock the matching wares in the village hall.
 */
public final class Projects {
    private Projects() {}

    public static final int HALL_INDEX = 10000;

    // ================================================================== offers

    public record Offer(ProjectDef def, int level, ProjectDef.Level lvl) {}

    /** The next level of every project type this village could offer. */
    public static List<Offer> offers(Village v) {
        List<Offer> out = new ArrayList<>();
        for (ProjectDef d : HBData.projects()) {
            int next = v.projectLevel(d.id.toString()) + 1;
            ProjectDef.Level l = d.level(next);
            if (l != null) out.add(new Offer(d, next, l));
        }
        return out;
    }

    static int activeProjects(ServerPlayer p) {
        int n = 0;
        for (Contract c : Rpg.data(p).contracts) if (c.type == ContractTemplate.Type.BUILD) n++;
        return n;
    }

    /** Why the player cannot take this project now (null if they can). */
    public static Component lock(ServerPlayer p, Village v, Offer o) {
        if (!HBConfig.PROJECTS.get()) return Component.translatable("hearthbound.project.disabled");
        Project cur = v.projects.get(o.def.id.toString());
        if (cur != null) {
            return p.getUUID().equals(cur.owner) ? null : Component.translatable("hearthbound.project.taken_by", cur.ownerName);
        }
        if (v.tier() < o.lvl.minTier) return Component.translatable("hearthbound.gate.tier", Component.translatable("hearthbound.tier." + o.lvl.minTier));
        Rank rank = Rpg.data(p).rank(v.id);
        if (!rank.atLeast(o.lvl.rank)) return Component.translatable("hearthbound.gate.rank", o.lvl.rank.title());
        if (!Compat.ageReached(p, o.lvl.age)) return Component.translatable("hearthbound.gate.age", Compat.ageName(p, o.lvl.age));
        if (activeProjects(p) >= HBConfig.MAX_PROJECTS.get()) return Component.translatable("hearthbound.project.too_many", HBConfig.MAX_PROJECTS.get());
        return null;
    }

    // ================================================================== take / abandon

    public static void take(ServerPlayer p, Village v, String type) {
        ProjectDef def = HBData.project(type);
        if (def == null) return;
        Project mine = v.projects.get(type);
        if (mine != null) validateCore(p.server, v, mine);
        if (mine != null && p.getUUID().equals(mine.owner)) {
            // lost the stone? hand out a new one (only while it is not placed)
            boolean has = false;
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (isStoneFor(inv.getItem(i), v, type)) has = true;
            if (mine.core == null && !has) giveStone(p, v, mine);
            else VillageService.deny(p);
            return;
        }
        Offer o = null;
        for (Offer x : offers(v)) if (x.def == def) o = x;
        if (o == null || v.projects.containsKey(type) || lock(p, v, o) != null) {
            VillageService.deny(p);
            return;
        }
        Project pr = new Project();
        pr.type = type;
        pr.level = o.level;
        pr.owner = p.getUUID();
        pr.ownerName = p.getGameProfile().getName();
        pr.half = (o.lvl.size - 1) / 2;
        pr.taken = p.level().getGameTime();
        Contract c = new Contract();
        c.type = ContractTemplate.Type.BUILD;
        c.target = type;
        c.icon = def.icon;
        c.count = o.lvl.total();
        c.title = def.name;
        c.extra = Integer.toString(o.level);
        c.village = v.id;
        c.villageName = v.name;
        c.coins = o.lvl.coins;
        c.reputation = o.lvl.reputation;
        c.xp = o.lvl.xp;
        pr.contract = c.id;
        v.projects.put(type, pr);
        PlayerData d = Rpg.data(p);
        d.contracts.add(c);
        d.tracked = c.id;
        giveStone(p, v, pr);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
        Net.banner(p, Component.translatable("hearthbound.project.taken", Component.translatable(def.name), o.level),
                Component.translatable("hearthbound.project.taken_sub"), 0xFFE0B25A);
        VillageData.get(p.server).setDirty();
        VillageService.refresh(p, v);
    }

    static void giveStone(ServerPlayer p, Village v, Project pr) {
        ItemStack s = new ItemStack(ModRegistry.PROJECT_STONE_ITEM.get());
        CompoundTag t = new CompoundTag();
        t.putUUID("village", v.id);
        t.putString("villageName", v.name);
        t.putString("type", pr.type);
        t.putInt("level", pr.level);
        t.putUUID("owner", pr.owner);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        ProjectDef def = HBData.project(pr.type);
        if (def != null) s.set(DataComponents.CUSTOM_NAME, Component.translatable("hearthbound.project.stone_name", Component.translatable(def.name))
                .withStyle(Style.EMPTY.withItalic(false).withColor(0xE0B25A)));
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private static boolean isStoneFor(ItemStack s, Village v, String type) {
        if (!s.is(ModRegistry.PROJECT_STONE_ITEM.get())) return false;
        CompoundTag t = ProjectStoneItem.data(s);
        return t.hasUUID("village") && v.id.equals(t.getUUID("village")) && type.equals(t.getString("type"));
    }

    public static void abandon(ServerPlayer p, Village v, String type) {
        Project pr = v.projects.get(type);
        if (pr == null || !p.getUUID().equals(pr.owner)) return;
        cancel(p.server, v, pr);
        Rpg.data(p).contracts.removeIf(c -> c.id.equals(pr.contract));
        if (pr.contract != null && pr.contract.equals(Rpg.data(p).tracked)) Rpg.data(p).tracked = null;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isStoneFor(inv.getItem(i), v, type)) inv.setItem(i, ItemStack.EMPTY);
        Net.notify(p, Component.translatable("hearthbound.project.abandoned", projectName(type)), 0xFFE06A5A);
        VillageService.refresh(p, v);
    }

    /** Called when the matching contract is abandoned from anywhere. */
    public static void contractAbandoned(ServerPlayer p, Contract c) {
        if (c.type != ContractTemplate.Type.BUILD) return;
        Village v = VillageData.get(p.server).get(c.village);
        if (v == null) return;
        Project pr = v.projects.get(c.target);
        if (pr != null && c.id.equals(pr.contract)) {
            cancel(p.server, v, pr);
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (isStoneFor(inv.getItem(i), v, c.target)) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    private static void cancel(net.minecraft.server.MinecraftServer server, Village v, Project pr) {
        v.projects.remove(pr.type);
        if (pr.core != null) {
            ServerLevel level = server.getLevel(v.dimension);
            if (level != null && level.isLoaded(pr.core) && level.getBlockState(pr.core).is(ModRegistry.PROJECT_STONE.get())) {
                level.removeBlock(pr.core, false);
            }
        }
        VillageData.get(server).setDirty();
    }

    // ================================================================== placing

    static Component projectName(String type) {
        ProjectDef d = HBData.project(type);
        return d == null ? Component.literal(type) : Component.translatable(d.name);
    }

    /** Null if the stone can go down here, otherwise why not. */
    public static Component canPlace(ServerPlayer p, ItemStack stack, BlockPos pos) {
        CompoundTag t = ProjectStoneItem.data(stack);
        if (!t.hasUUID("village")) return Component.translatable("hearthbound.project.stone_blank");
        Village v = VillageData.get(p.server).get(t.getUUID("village"));
        if (v == null || !v.dimension.equals(p.level().dimension())) return Component.translatable("hearthbound.project.wrong_village", t.getString("villageName"));
        Project pr = v.projects.get(t.getString("type"));
        if (pr == null || !p.getUUID().equals(pr.owner)) return Component.translatable("hearthbound.project.not_yours");
        validateCore(p.server, v, pr);
        if (pr.core != null) return Component.translatable("hearthbound.project.already_placed");
        double dx = pos.getX() - v.center.getX(), dz = pos.getZ() - v.center.getZ();
        double max = v.radius() + 8;
        if (dx * dx + dz * dz > max * max) return Component.translatable("hearthbound.project.outside", v.name);
        return overlap(p.serverLevel(), v, pr, pos, pr.half);
    }

    /** Forgets a foundation stone that is no longer there (removed by commands, other mods...). */
    static void validateCore(net.minecraft.server.MinecraftServer server, Village v, Project pr) {
        if (pr.core == null) return;
        ServerLevel level = server.getLevel(v.dimension);
        if (level == null || !level.isLoaded(pr.core)) return;
        if (!(level.getBlockEntity(pr.core) instanceof ProjectStoneBlockEntity be) || !pr.type.equals(be.type) || !v.id.equals(be.village)) {
            pr.core = null;
            VillageData.get(server).setDirty();
        }
    }

    /** Checks a plot against the plaza, the village buildings and other projects. */
    static Component overlap(ServerLevel level, Village v, Project pr, BlockPos core, int half) {
        BoundingBox box = new BoundingBox(core.getX() - half, core.getY() - 2, core.getZ() - half, core.getX() + half, core.getY() + 30, core.getZ() + half);
        int plaza = 7;
        if (box.intersects(v.center.getX() - plaza, v.center.getZ() - plaza, v.center.getX() + plaza, v.center.getZ() + plaza)) {
            return Component.translatable("hearthbound.project.on_plaza");
        }
        if (v.flagPos != null && box.intersects(v.flagPos.getX() - 1, v.flagPos.getZ() - 1, v.flagPos.getX() + 1, v.flagPos.getZ() + 1)) {
            return Component.translatable("hearthbound.project.on_plaza");
        }
        for (PlacedBuilding b : v.buildings) {
            if (b.bounds.intersects(box.minX(), box.minZ(), box.maxX(), box.maxZ())) return Component.translatable("hearthbound.project.on_building");
        }
        for (Project o : v.projects.values()) {
            if (o == pr || o.core == null) continue;
            if (o.area(30).intersects(box.minX(), box.minZ(), box.maxX(), box.maxZ())) return Component.translatable("hearthbound.project.on_project", o.ownerName);
        }
        for (Project.Work w : v.works) {
            if (w.type().equals(pr.type)) continue; // extending your own earlier building is fine
            if (w.area().intersects(box.minX(), box.minZ(), box.maxX(), box.maxZ())) return Component.translatable("hearthbound.project.on_work", projectName(w.type()));
        }
        return null;
    }

    public static void placed(ServerPlayer p, BlockPos pos, CompoundTag data) {
        Village v = VillageData.get(p.server).get(data.getUUID("village"));
        if (v == null) return;
        Project pr = v.projects.get(data.getString("type"));
        if (pr == null) return;
        pr.core = pos.immutable();
        if (p.level().getBlockEntity(pos) instanceof ProjectStoneBlockEntity be) {
            be.village = v.id;
            be.type = pr.type;
            be.level = pr.level;
            be.owner = pr.owner;
            be.setChanged();
            p.level().sendBlockUpdated(pos, p.level().getBlockState(pos), p.level().getBlockState(pos), 3);
        }
        int side = pr.half * 2 + 1;
        Net.banner(p, Component.translatable("hearthbound.project.placed", projectName(pr.type)),
                Component.translatable("hearthbound.project.placed_sub", side, side), 0xFFE0B25A);
        p.level().playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6f, 1.2f);
        VillageData.get(p.server).setDirty();
        updateProgress(p, v, pr);
    }

    /** The owner (or a creative player) broke an unfinished stone: the project waits for a new spot. */
    public static void coreBroken(ServerPlayer p, ProjectStoneBlockEntity be) {
        Village v = VillageData.get(p.server).get(be.village);
        if (v == null) return;
        Project pr = v.projects.get(be.type);
        if (pr == null || !be.getBlockPos().equals(pr.core)) return;
        pr.core = null;
        ServerPlayer owner = p.server.getPlayerList().getPlayer(pr.owner);
        if (owner != null) {
            giveStone(owner, v, pr);
            Net.notify(owner, Component.translatable("hearthbound.project.stone_back"), 0xFFE0B25A);
        }
        VillageData.get(p.server).setDirty();
    }

    // ================================================================== progress

    record Found(Village village, Project project, ProjectDef def, ProjectDef.Level lvl) {}

    static Found find(net.minecraft.server.MinecraftServer server, ProjectStoneBlockEntity be) {
        Village v = VillageData.get(server).get(be.village);
        if (v == null) return null;
        Project pr = v.projects.get(be.type);
        if (pr == null || !be.getBlockPos().equals(pr.core)) return null;
        ProjectDef def = HBData.project(pr.type);
        ProjectDef.Level l = def == null ? null : def.level(pr.level);
        return l == null ? null : new Found(v, pr, def, l);
    }

    /** Counts the required blocks inside the plot. */
    static int[] count(ServerLevel level, Project pr, ProjectDef.Level l) {
        int[] have = new int[l.requirements.size()];
        BoundingBox box = pr.area(l.height);
        boolean track = HBConfig.TRACK_PLAYER_BLOCKS.get();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    m.set(x, y, z);
                    if (!level.isLoaded(m)) continue;
                    BlockState st = level.getBlockState(m);
                    if (st.isAir()) continue;
                    // water counts as source blocks; everything else must have been placed by a player
                    boolean fluid = !st.getFluidState().isEmpty() && st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
                    if (fluid ? !st.getFluidState().isSource() : track && !com.hearthbound.world.PlayerBuilds.isPlayerBlock(level, m)) continue;
                    for (int i = 0; i < have.length; i++) if (l.requirements.get(i).test(st.getBlock())) have[i]++;
                }
            }
        }
        return have;
    }

    static int progress(int[] have, ProjectDef.Level l) {
        int n = 0;
        for (int i = 0; i < have.length; i++) n += Math.min(have[i], l.requirements.get(i).count());
        return n;
    }

    static void updateProgress(ServerPlayer owner, Village v, Project pr) {
        ProjectDef def = HBData.project(pr.type);
        ProjectDef.Level l = def == null ? null : def.level(pr.level);
        if (l == null || pr.core == null || !(owner.level() instanceof ServerLevel sl) || !sl.dimension().equals(v.dimension)) return;
        int prog = progress(count(sl, pr, l), l);
        Contract c = Rpg.data(owner).contract(pr.contract);
        if (c != null && c.progress != prog) {
            boolean readyNow = prog >= c.count && c.progress < c.count;
            c.progress = prog;
            if (readyNow) Net.notify(owner, Component.translatable("hearthbound.project.ready", projectName(pr.type)), 0xFFFFD24A);
            Rpg.sync(owner);
        }
    }

    /** Every half second: draws the plot border for nearby players; every few seconds updates the owner's progress. */
    public static void tickStone(ServerLevel level, ProjectStoneBlockEntity be, int ticks) {
        Found f = find(level.getServer(), be);
        if (f == null) {
            if (ticks % 200 == 0 && !level.getBlockState(be.getBlockPos()).getValue(ProjectStoneBlock.DONE)) {
                level.removeBlock(be.getBlockPos(), false); // its project is gone
            }
            return;
        }
        BlockPos c = be.getBlockPos();
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (p.blockPosition().distSqr(c) < 48 * 48) near.add(p);
        if (near.isEmpty()) return;
        int h = f.project().half;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1f, 0.82f, 0.29f), 1.2f);
        int phase = (ticks / 10) % 2;
        for (int i = -h + phase; i <= h; i += 2) {
            border(level, near, dust, c, c.getX() + i, c.getZ() - h);
            border(level, near, dust, c, c.getX() + i, c.getZ() + h);
            border(level, near, dust, c, c.getX() - h, c.getZ() + i);
            border(level, near, dust, c, c.getX() + h, c.getZ() + i);
        }
        if (ticks % 100 == 0) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(f.project().owner);
            if (owner != null && owner.level() == level && owner.blockPosition().distSqr(c) < 96 * 96) updateProgress(owner, f.village(), f.project());
        }
    }

    private static void border(ServerLevel level, List<ServerPlayer> to, DustParticleOptions dust, BlockPos core, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int y = top >= core.getY() - 2 && top <= core.getY() + 3 ? top : core.getY();
        for (ServerPlayer p : to) level.sendParticles(p, dust, false, x + 0.5, y + 0.2, z + 0.5, 1, 0, 0, 0, 0);
    }

    // ================================================================== screen

    public static void refresh(ServerPlayer p, BlockPos pos) {
        if (!(p.level().getBlockEntity(pos) instanceof ProjectStoneBlockEntity be)) return;
        if (p.level().getBlockState(pos).getValue(ProjectStoneBlock.DONE)) return;
        Found f = find(p.server, be);
        if (f != null) Net.send(p, "project", view(p, f, pos));
    }

    public static void open(ServerPlayer p, BlockPos pos) {
        if (!(p.level().getBlockEntity(pos) instanceof ProjectStoneBlockEntity be)) return;
        if (p.level().getBlockState(pos).getValue(ProjectStoneBlock.DONE)) {
            Village v = VillageData.get(p.server).get(be.village);
            if (v != null && VillageService.inShop(p, v, be.type)) {
                VillageService.openShop(p, v, be.type);
            } else {
                p.displayClientMessage(Component.translatable("hearthbound.project.monument", projectName(be.type), be.level, v == null ? "?" : v.name).withColor(0xE0B25A), true);
            }
            return;
        }
        Found f = find(p.server, be);
        if (f == null) return;
        Net.send(p, "project", view(p, f, pos));
    }

    static CompoundTag view(ServerPlayer p, Found f, BlockPos pos) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos.asLong());
        t.putString("name", f.def().name);
        t.putString("desc", f.def().description);
        t.putString("icon", BuiltInRegistries.ITEM.getKey(f.def().icon).toString());
        t.putInt("level", f.project().level);
        t.putString("village", f.village().name);
        t.putString("owner", f.project().ownerName);
        t.putBoolean("mine", p.getUUID().equals(f.project().owner));
        t.putInt("side", f.project().half * 2 + 1);
        t.putInt("minSide", f.lvl().size);
        t.putInt("maxSide", f.lvl().maxSize);
        t.putInt("height", f.lvl().height);
        int[] have = count(p.serverLevel(), f.project(), f.lvl());
        ListTag reqs = new ListTag();
        boolean ok = true;
        for (int i = 0; i < have.length; i++) {
            ProjectDef.Requirement r = f.lvl().requirements.get(i);
            CompoundTag e = new CompoundTag();
            e.putString("block", r.block());
            e.putString("icon", BuiltInRegistries.ITEM.getKey(r.icon()).toString());
            e.putInt("have", have[i]);
            e.putInt("need", r.count());
            if (have[i] < r.count()) ok = false;
            reqs.add(e);
        }
        t.put("reqs", reqs);
        t.putBoolean("ready", ok);
        t.putInt("reputation", repReward(p, f.village(), f.lvl()));
        t.putInt("coins", f.lvl().coins);
        t.putInt("xp", f.lvl().xp);
        ListTag unlocks = new ListTag();
        for (ProjectDef.HallTrade tr : f.lvl().trades) {
            if (!tr.sell() || unlocks.size() >= 8) continue;
            unlocks.add(stackTag(p.registryAccess(), tr));
        }
        t.put("unlocks", unlocks);
        return t;
    }

    static int repReward(ServerPlayer p, Village v, ProjectDef.Level l) {
        double m = Citizenship.isCitizen(p, v) ? 1 + HBConfig.CITIZEN_PROJECT_BONUS.get() : 1;
        return (int) Math.round(l.reputation * m);
    }

    public static void resize(ServerPlayer p, BlockPos pos, int delta) {
        if (!(p.level().getBlockEntity(pos) instanceof ProjectStoneBlockEntity be)) return;
        Found f = find(p.server, be);
        if (f == null || !p.getUUID().equals(f.project().owner)) return;
        int half = f.project().half + Integer.signum(delta);
        if (half * 2 + 1 < f.lvl().size || half * 2 + 1 > f.lvl().maxSize) return;
        Component err = overlap(p.serverLevel(), f.village(), f.project(), pos, half);
        if (err != null) {
            p.displayClientMessage(err.copy().withColor(0xE06A5A), true);
            return;
        }
        f.project().half = half;
        VillageData.get(p.server).setDirty();
        p.level().playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6f, delta > 0 ? 1.3f : 0.8f);
        updateProgress(p, f.village(), f.project());
        Net.send(p, "project", view(p, f, pos));
    }

    /** The owner declares the building finished. */
    public static void finish(ServerPlayer p, BlockPos pos) {
        if (!(p.level().getBlockEntity(pos) instanceof ProjectStoneBlockEntity be)) return;
        Found f = find(p.server, be);
        if (f == null || !p.getUUID().equals(f.project().owner)) return;
        int[] have = count(p.serverLevel(), f.project(), f.lvl());
        for (int i = 0; i < have.length; i++) {
            if (have[i] < f.lvl().requirements.get(i).count()) {
                p.displayClientMessage(Component.translatable("hearthbound.project.missing").withColor(0xE06A5A), true);
                return;
            }
        }
        Village v = f.village();
        Project pr = f.project();
        v.projects.remove(pr.type);
        v.projectLevels.merge(pr.type, pr.level, Math::max);
        v.works.add(new Project.Work(pr.type, pr.level, pr.area(f.lvl().height), p.getGameProfile().getName()));
        v.prosperity = Math.min(100, v.prosperity + 5);
        PlayerData d = Rpg.data(p);
        d.contracts.removeIf(c -> c.id.equals(pr.contract));
        if (pr.contract != null && pr.contract.equals(d.tracked)) d.tracked = null;
        d.projectsDone++;
        int rep = repReward(p, v, f.lvl());
        Reputation.add(p, v, rep, true, true);
        Wallet.give(p, f.lvl().coins);
        d.coinsEarned += f.lvl().coins;
        Rpg.addXp(p, f.lvl().xp, false);
        p.level().setBlock(pos, p.level().getBlockState(pos).setValue(ProjectStoneBlock.DONE, true), 3);
        p.level().playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        p.level().playSound(null, pos, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        Net.banner(p, Component.translatable("hearthbound.project.done", Component.translatable(f.def().name), pr.level),
                Component.translatable(f.lvl().trades.isEmpty() ? "hearthbound.project.done_sub" : "hearthbound.project.done_unlocks", v.name), 0xFFFFD24A);
        Net.notify(p, Component.translatable("hearthbound.notify.reward", Wallet.format(f.lvl().coins), rep, f.lvl().xp), 0xFFE0B25A);
        Compat.trigger(p, "projects", f.def(), 1);
        // the village celebrates
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o != p && o.blockPosition().distSqr(v.center) < (double) (v.radius() + 64) * (v.radius() + 64)) {
                Net.notify(o, Component.translatable("hearthbound.project.done_other", p.getGameProfile().getName(), Component.translatable(f.def().name), v.name), 0xFFE0B25A);
            }
        }
        VillageData.get(p.server).setDirty();
        Rpg.sync(p);
        p.closeContainer();
        Net.send(p, "project_close", new CompoundTag());
    }

    /** Areas villagers must not build on (placed projects and finished player buildings). */
    public static List<BoundingBox> protectedAreas(Village v) {
        List<BoundingBox> out = new ArrayList<>();
        for (Project pr : v.projects.values()) if (pr.core != null) out.add(pr.area(30));
        for (Project.Work w : v.works) out.add(w.area());
        return out;
    }

    // ================================================================== hall trades

    public record HallEntry(int index, ProjectDef def, int level, ProjectDef.HallTrade trade) {}

    private static Collection<ProjectDef> cachedFor;
    private static List<HallEntry> cache = List.of();

    public static List<HallEntry> hallTrades() {
        Collection<ProjectDef> now = HBData.projects();
        if (now != cachedFor) {
            List<HallEntry> out = new ArrayList<>();
            java.util.Set<Integer> used = new java.util.HashSet<>();
            for (ProjectDef d : now) {
                for (int l = 1; l <= d.levels.size(); l++) {
                    List<ProjectDef.HallTrade> ts = d.level(l).trades;
                    for (int k = 0; k < ts.size(); k++) {
                        // stable across reloads: derived from the building, level and position
                        int idx = HALL_INDEX + Math.floorMod((d.id + "#" + l + "#" + k).hashCode(), 50_000_000);
                        while (!used.add(idx)) idx++;
                        out.add(new HallEntry(idx, d, l, ts.get(k)));
                    }
                }
            }
            cache = out;
            cachedFor = now;
        }
        return cache;
    }

    public static HallEntry hallTrade(int index) {
        for (HallEntry e : hallTrades()) if (e.index == index) return e;
        return null;
    }

    public static Component hallLock(ServerPlayer p, Village v, HallEntry e) {
        if (v.projectLevel(e.def.id.toString()) < e.level) {
            return Component.translatable("hearthbound.project.needs", Component.translatable(e.def.name), e.level);
        }
        Rank rank = Rpg.data(p).rank(v.id);
        if (!rank.atLeast(e.trade.rank())) return Component.translatable("hearthbound.gate.rank", e.trade.rank().title());
        if (!Compat.ageReached(p, e.trade.age())) return Component.translatable("hearthbound.gate.age", Compat.ageName(p, e.trade.age()));
        return null;
    }

    public static ItemStack stack(RegistryAccess access, ProjectDef.HallTrade t) {
        ItemStack s = new ItemStack(t.item(), t.count());
        if (!t.enchant().isEmpty()) {
            var reg = access.lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (String part : t.enchant().split(",")) {
                String[] kv = part.trim().split("=");
                ResourceLocation id = ResourceLocation.tryParse(kv[0]);
                if (id == null) continue;
                int lvl = 1;
                try {
                    if (kv.length > 1) lvl = Integer.parseInt(kv[1]);
                } catch (NumberFormatException ignored) {
                }
                final int level = lvl;
                reg.get(ResourceKey.create(Registries.ENCHANTMENT, id)).ifPresent(h -> m.set(h, level));
            }
            s.set(s.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, m.toImmutable());
        }
        return s;
    }

    static CompoundTag stackTag(RegistryAccess access, ProjectDef.HallTrade t) {
        ItemStack s = stack(access, t);
        return (CompoundTag) s.save(access);
    }
}
