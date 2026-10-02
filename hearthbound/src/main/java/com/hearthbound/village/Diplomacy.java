package com.hearthbound.village;

import com.hearthbound.Hearthbound;
import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.data.Culture;
import com.hearthbound.entity.SettlerEntity;
import com.hearthbound.network.Net;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.rpg.Rpg;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Life between villages. Every pair of villages in range has a relation from −100 to 100,
 * seeded by how their cultures get along. Friendly villages trade with caravans (resources
 * move, both prosper, relations improve); rival neighbours build up tension and may go to war.
 * Wars are fought with warbands: when a player is around the attacked village the warriors
 * really march in and fight, otherwise the battle is resolved by strength. Wars end in peace
 * and a truce, sometimes brokered by a player.
 */
public final class Diplomacy {
    public enum Stance { WAR, HOSTILE, RIVAL, NEUTRAL, FRIENDLY, ALLIED }

    public static final class Relation {
        public int value;
        public long warSince = -1;
        public long truceUntil = -1;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("v", value);
            t.putLong("war", warSince);
            t.putLong("truce", truceUntil);
            return t;
        }

        static Relation load(CompoundTag t) {
            Relation r = new Relation();
            r.value = t.getInt("v");
            r.warSince = t.getLong("war");
            r.truceUntil = t.getLong("truce");
            return r;
        }

        public boolean atWar() {
            return warSince >= 0;
        }
    }

    public static final class Caravan {
        public UUID from, to;
        public long arrive;
        public Resource resource;
        public int amount;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("from", from);
            t.putUUID("to", to);
            t.putLong("arrive", arrive);
            t.putString("res", resource.id());
            t.putInt("amount", amount);
            return t;
        }

        static Caravan load(CompoundTag t) {
            Caravan c = new Caravan();
            c.from = t.getUUID("from");
            c.to = t.getUUID("to");
            c.arrive = t.getLong("arrive");
            c.resource = Resource.byId(t.getString("res"));
            if (c.resource == null) c.resource = Resource.GOODS;
            c.amount = t.getInt("amount");
            return c;
        }
    }

    public static final class Warband {
        public UUID attacker, defender;
        public long ends;
        public boolean physical;
        /** Kills and loot of a physical warband; decides the outcome if they are still standing at the end. */
        public int score;
        public int spawned;
        public int looted;
        public final List<UUID> members = new ArrayList<>();
        transient long lastNotice;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("a", attacker);
            t.putUUID("d", defender);
            t.putLong("ends", ends);
            t.putBoolean("phys", physical);
            t.putInt("score", score);
            t.putInt("spawned", spawned);
            t.putInt("looted", looted);
            ListTag l = new ListTag();
            for (UUID u : members) l.add(NbtUtils.createUUID(u));
            t.put("m", l);
            return t;
        }

        static Warband load(CompoundTag t) {
            Warband w = new Warband();
            w.attacker = t.getUUID("a");
            w.defender = t.getUUID("d");
            w.ends = t.getLong("ends");
            w.physical = t.getBoolean("phys");
            w.score = t.getInt("score");
            w.spawned = t.getInt("spawned");
            w.looted = t.getInt("looted");
            for (Tag x : t.getList("m", Tag.TAG_INT_ARRAY)) w.members.add(NbtUtils.loadUUID(x));
            return w;
        }
    }

    /** Saved with {@link VillageData}. */
    public static final class State {
        public final Map<String, Relation> relations = new HashMap<>();
        public final List<Caravan> caravans = new ArrayList<>();
        public final List<Warband> warbands = new ArrayList<>();
        public long lastDay = -1;

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            CompoundTag rel = new CompoundTag();
            relations.forEach((k, r) -> rel.put(k, r.save()));
            t.put("rel", rel);
            ListTag cs = new ListTag();
            for (Caravan c : caravans) cs.add(c.save());
            t.put("caravans", cs);
            ListTag ws = new ListTag();
            for (Warband w : warbands) ws.add(w.save());
            t.put("warbands", ws);
            t.putLong("day", lastDay);
            return t;
        }

        public void load(CompoundTag t) {
            relations.clear();
            CompoundTag rel = t.getCompound("rel");
            for (String k : rel.getAllKeys()) relations.put(k, Relation.load(rel.getCompound(k)));
            caravans.clear();
            for (Tag x : t.getList("caravans", Tag.TAG_COMPOUND)) caravans.add(Caravan.load((CompoundTag) x));
            warbands.clear();
            for (Tag x : t.getList("warbands", Tag.TAG_COMPOUND)) warbands.add(Warband.load((CompoundTag) x));
            lastDay = t.getLong("day");
        }
    }

    private Diplomacy() {}

    // ================================================================== relations

    static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /** Base relation of two cultures (average of what each culture file says about the other). */
    public static int affinity(Village a, Village b) {
        Culture ca = a.culture(), cb = b.culture();
        if (ca == null || cb == null) return 0;
        if (ca.id.equals(cb.id)) return 40;
        int x = ca.relations.getOrDefault(cb.id.getPath(), 0);
        int y = cb.relations.getOrDefault(ca.id.getPath(), 0);
        return (x + y) / 2;
    }

    public static Relation relation(VillageData data, Village a, Village b) {
        return data.diplomacy.relations.computeIfAbsent(key(a.id, b.id), k -> {
            Relation r = new Relation();
            r.value = Mth.clamp(affinity(a, b) + (int) ((a.id.getLeastSignificantBits() ^ b.id.getLeastSignificantBits()) % 11), -100, 100);
            return r;
        });
    }

    public static Stance stance(Relation r) {
        if (r.atWar()) return Stance.WAR;
        int w = HBConfig.WAR_THRESHOLD.get();
        if (r.value <= w) return Stance.HOSTILE;
        if (r.value < -20) return Stance.RIVAL;
        if (r.value < 20) return Stance.NEUTRAL;
        if (r.value < 60) return Stance.FRIENDLY;
        return Stance.ALLIED;
    }

    public static int stanceColor(Stance s) {
        return switch (s) {
            case WAR -> 0xFFD04040;
            case HOSTILE -> 0xFFE06A5A;
            case RIVAL -> 0xFFE0A050;
            case NEUTRAL -> 0xFFB8B8B8;
            case FRIENDLY -> 0xFF6CD68A;
            case ALLIED -> 0xFF5AB0FF;
        };
    }

    public static void change(VillageData data, Village a, Village b, int delta) {
        Relation r = relation(data, a, b);
        r.value = Mth.clamp(r.value + delta, -100, 100);
        data.setDirty();
    }

    /** Villages in diplomatic range of {@code v}. */
    public static List<Village> neighbours(VillageData data, Village v) {
        List<Village> out = new ArrayList<>();
        double range = HBConfig.DIPLOMACY_RANGE.get();
        for (Village o : data.all()) {
            if (o == v || !o.dimension.equals(v.dimension)) continue;
            if (o.center.distSqr(v.center) <= range * range) out.add(o);
        }
        return out;
    }

    // ================================================================== ticking

    /** Called every 100 ticks. */
    public static void tick(MinecraftServer server, VillageData data) {
        if (!HBConfig.DIPLOMACY.get()) return;
        ServerLevel ow = server.overworld();
        long now = ow.getGameTime();
        long day = ow.getDayTime() / 24000L;
        if (data.diplomacy.lastDay != day) {
            data.diplomacy.lastDay = day;
            daily(server, data, day, now);
            data.setDirty();
        }
        caravans(server, data, now);
        warbands(server, data, now);
    }

    private static void daily(MinecraftServer server, VillageData data, long day, long now) {
        List<Village> all = new ArrayList<>(data.all());
        RandomSource r = server.overworld().random;
        double border = HBConfig.BORDER_RANGE.get();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Village a = all.get(i), b = all.get(j);
                if (!a.dimension.equals(b.dimension)) continue;
                double d2 = a.center.distSqr(b.center);
                if (d2 > (double) HBConfig.DIPLOMACY_RANGE.get() * HBConfig.DIPLOMACY_RANGE.get()) continue;
                Relation rel = relation(data, a, b);
                int base = affinity(a, b);
                if (!rel.atWar()) {
                    if (rel.value < base) rel.value++;
                    else if (rel.value > base + 20) rel.value--;
                }
                boolean sameCulture = a.culture != null && a.culture.equals(b.culture);
                if (!sameCulture && d2 < border * border && rel.value < 0 && !rel.atWar()) {
                    rel.value = Math.max(-100, rel.value - r.nextInt(3));
                }
                if (rel.atWar()) {
                    long days = day - rel.warSince;
                    if (days >= HBConfig.WAR_MIN_DAYS.get() && r.nextDouble() < HBConfig.PEACE_CHANCE.get()) {
                        makePeace(server, data, a, b, day, null);
                    } else {
                        // one side attacks, the stronger more often
                        int sa = strength(a), sb = strength(b);
                        Village att = r.nextInt(sa + sb + 1) < sa ? a : b;
                        launch(server, data, att, att == a ? b : a, now);
                    }
                } else if (HBConfig.WARS.get() && rel.value <= HBConfig.WAR_THRESHOLD.get() && day >= rel.truceUntil
                        && a.tier() >= 1 && b.tier() >= 1 && warsAllowed(server, a, b) && r.nextDouble() < HBConfig.WAR_CHANCE.get()) {
                    declareWar(server, data, a, b, day);
                }
            }
        }
        if (HBConfig.CARAVANS.get()) {
            for (Village v : all) {
                if (!v.has(Role.MERCHANT) || r.nextDouble() > HBConfig.CARAVAN_CHANCE.get()) continue;
                sendCaravan(server, data, v, now);
            }
        }
    }

    private static boolean warsAllowed(MinecraftServer server, Village a, Village b) {
        String age = HBConfig.ASC_AGE_WARS.get();
        if (age == null || age.isBlank() || !Compat.ages()) return true;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Compat.ageReached(p, age)) return true;
        }
        return false;
    }

    static int strength(Village v) {
        int guards = 0;
        for (Resident r : v.residents) if (r.alive() && r.role == Role.GUARD) guards++;
        return 3 + guards * 3 + v.tier() * 2 + v.prosperity / 25;
    }

    // ================================================================== caravans

    public static void sendCaravan(MinecraftServer server, VillageData data, Village from, long now) {
        Village best = null;
        int bestVal = 19;
        for (Village o : neighbours(data, from)) {
            Relation rel = relation(data, from, o);
            if (rel.atWar() || rel.value <= bestVal) continue;
            best = o;
            bestVal = rel.value;
        }
        if (best == null) return;
        Resource res = null;
        int most = 39;
        for (Resource r : Resource.values()) {
            if (from.get(r) > most) {
                most = from.get(r);
                res = r;
            }
        }
        if (res == null) return;
        int amount = Math.min(24, from.get(res) / 3);
        from.add(res, -amount);
        Caravan c = new Caravan();
        c.from = from.id;
        c.to = best.id;
        c.resource = res;
        c.amount = amount;
        double dist = Math.sqrt(from.center.distSqr(best.center));
        c.arrive = now + Mth.clamp((long) (dist * 20 / 1.2), 2400L, 24000L);
        data.diplomacy.caravans.add(c);
        ServerLevel level = server.getLevel(from.dimension);
        if (level != null) {
            spawnTraveler(level, from, from.center, best.center, true);
            notifyNear(level, from, Component.translatable("hearthbound.diplo.caravan_leaves", from.name, best.name), 0xFFE0B25A);
        }
    }

    private static void caravans(MinecraftServer server, VillageData data, long now) {
        for (Iterator<Caravan> it = data.diplomacy.caravans.iterator(); it.hasNext(); ) {
            Caravan c = it.next();
            Village from = data.get(c.from), to = data.get(c.to);
            if (from == null || to == null) {
                it.remove();
                continue;
            }
            if (relation(data, from, to).atWar()) {
                it.remove(); // plundered on the road
                continue;
            }
            if (now < c.arrive) continue;
            it.remove();
            to.add(c.resource, c.amount);
            to.add(Resource.GOODS, 3);
            from.add(Resource.GOODS, 5);
            from.prosperity = Math.min(100, from.prosperity + 2);
            to.prosperity = Math.min(100, to.prosperity + 2);
            change(data, from, to, 3);
            ServerLevel level = server.getLevel(to.dimension);
            if (level != null) {
                BlockPos edge = edgeToward(to, from.center);
                spawnTraveler(level, from, edge, to.center, false);
                notifyNear(level, to, Component.translatable("hearthbound.diplo.caravan_arrives", from.name, to.name), 0xFF6CD68A);
            }
            data.setDirty();
        }
    }

    static BlockPos edgeToward(Village v, BlockPos toward) {
        double dx = toward.getX() - v.center.getX(), dz = toward.getZ() - v.center.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        int r = v.radius() + 8;
        return v.center.offset((int) (dx / len * r), 0, (int) (dz / len * r));
    }

    /** A merchant with a llama walking between villages (only when someone can see it). */
    private static void spawnTraveler(ServerLevel level, Village home, BlockPos start, BlockPos target, boolean leaving) {
        if (!VillageManager.anyPlayerNear(level, start, 128)) return;
        if (!level.getChunkSource().hasChunk(start.getX() >> 4, start.getZ() >> 4)) return;
        int y = Terraform.groundTop(level, start.getX(), start.getZ()) + 1;
        BlockPos at = new BlockPos(start.getX(), y, start.getZ());
        SettlerEntity s = ModRegistry.SETTLER.get().create(level);
        if (s == null) return;
        Culture c = home.culture();
        boolean female = level.random.nextBoolean();
        s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        s.setup(c, Role.MERCHANT, c == null ? "Trader" : c.randomName(level.random, female), female, 0);
        Llama llama = EntityType.LLAMA.create(level);
        if (llama != null) {
            llama.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
            level.addFreshEntity(llama);
            llama.setLeashedTo(s, true);
        }
        s.travel(leaving ? target : target, level.getGameTime() + (leaving ? 2400 : 3600), llama == null ? null : llama.getUUID());
        level.addFreshEntity(s);
    }

    // ================================================================== war

    public static void declareWar(MinecraftServer server, VillageData data, Village a, Village b, long day) {
        Relation rel = relation(data, a, b);
        rel.warSince = day;
        data.setDirty();
        broadcast(server, a, b, Component.translatable("hearthbound.diplo.war_title"),
                Component.translatable("hearthbound.diplo.war_sub", a.name, b.name), 0xFFD04040);
        Hearthbound.LOGGER.info("War between {} and {}", a.name, b.name);
    }

    public static void makePeace(MinecraftServer server, VillageData data, Village a, Village b, long day, ServerPlayer broker) {
        Relation rel = relation(data, a, b);
        rel.warSince = -1;
        rel.truceUntil = day + HBConfig.TRUCE_DAYS.get();
        rel.value = Math.max(rel.value, HBConfig.WAR_THRESHOLD.get() + 30);
        data.diplomacy.warbands.removeIf(w -> (w.attacker.equals(a.id) && w.defender.equals(b.id)) || (w.attacker.equals(b.id) && w.defender.equals(a.id)));
        data.setDirty();
        Component sub = broker == null ? Component.translatable("hearthbound.diplo.peace_sub", a.name, b.name)
                : Component.translatable("hearthbound.diplo.peace_broker", a.name, b.name, broker.getGameProfile().getName());
        broadcast(server, a, b, Component.translatable("hearthbound.diplo.peace_title"), sub, 0xFF6CD68A);
    }

    public static void launch(MinecraftServer server, VillageData data, Village att, Village def, long now) {
        for (Warband w : data.diplomacy.warbands) if (w.defender.equals(def.id)) return; // one attack at a time
        Warband w = new Warband();
        w.attacker = att.id;
        w.defender = def.id;
        w.ends = now + HBConfig.RAID_DURATION.get() * 20L;
        ServerLevel level = server.getLevel(def.dimension);
        if (level != null && VillageManager.anyPlayerNear(level, def.center, 160) && level.isLoaded(def.center)) {
            int n = HBConfig.WARBAND_BASE.get() + HBConfig.WARBAND_PER_TIER.get() * att.tier();
            BlockPos edge = edgeToward(def, att.center);
            Culture c = att.culture();
            for (int i = 0; i < n; i++) {
                int x = edge.getX() + level.random.nextInt(7) - 3, z = edge.getZ() + level.random.nextInt(7) - 3;
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                SettlerEntity s = ModRegistry.SETTLER.get().create(level);
                if (s == null) continue;
                boolean female = level.random.nextBoolean();
                s.moveTo(x + 0.5, y, z + 0.5, 0, 0);
                s.setup(c, Role.GUARD, c == null ? "Raider" : c.randomName(level.random, female), female, 0);
                s.joinWarband(att.id, def.id, def.center, def.radius(), w.ends);
                level.addFreshEntity(s);
                w.members.add(s.getUUID());
            }
            w.physical = !w.members.isEmpty();
            w.spawned = w.members.size();
            if (w.physical) {
                for (ServerPlayer p : level.players()) {
                    if (p.blockPosition().distSqr(def.center) < (double) (def.radius() + 96) * (def.radius() + 96)) {
                        Net.banner(p, Component.translatable("hearthbound.diplo.attack_title", att.name), Component.translatable("hearthbound.diplo.attack_sub", def.name), 0xFFD04040);
                        level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2f, 1.2f);
                    }
                }
            }
        }
        data.diplomacy.warbands.add(w);
        data.setDirty();
    }

    private static void warbands(MinecraftServer server, VillageData data, long now) {
        for (Iterator<Warband> it = data.diplomacy.warbands.iterator(); it.hasNext(); ) {
            Warband w = it.next();
            Village att = data.get(w.attacker), def = data.get(w.defender);
            if (att == null || def == null) {
                it.remove();
                continue;
            }
            ServerLevel level = server.getLevel(def.dimension);
            if (w.physical && level != null) {
                if (w.members.isEmpty()) { // every raider fell
                    it.remove();
                    resolve(server, data, att, def, false, w.physical);
                    continue;
                }
                if (now >= w.ends) {
                    for (UUID u : w.members) {
                        var e = level.getEntity(u);
                        if (e != null) e.discard();
                    }
                    it.remove();
                    // the raiders must have achieved something: kills (3) and looted houses (1) against their number
                    boolean won = w.score >= Math.max(2, w.spawned);
                    resolve(server, data, att, def, won, true);
                }
            } else if (now >= w.ends) {
                it.remove();
                int sa = strength(att) + server.overworld().random.nextInt(6);
                int sd = strength(def) + 2 + server.overworld().random.nextInt(6);
                resolve(server, data, att, def, sa > sd, false);
            }
        }
    }

    /** Applies the outcome of a battle. */
    private static void resolve(MinecraftServer server, VillageData data, Village att, Village def, boolean attackerWon, boolean fought) {
        Village winner = attackerWon ? att : def, loser = attackerWon ? def : att;
        for (Resource r : Resource.values()) {
            int loss = loser.get(r) * 15 / 100;
            loser.add(r, -loss);
            winner.add(r, loss / 2);
        }
        loser.prosperity = Math.max(0, loser.prosperity - 8);
        if (attackerWon && !fought) {
            // a defender falls (in a real battle the dead are already dead)
            for (Resident r : def.residents) {
                if (r.alive() && r.role != Role.ELDER) {
                    r.deadSince = server.overworld().getGameTime();
                    ServerLevel level = server.getLevel(def.dimension);
                    if (level != null && r.entity != null && level.getEntity(r.entity) != null) level.getEntity(r.entity).discard();
                    break;
                }
            }
        }
        change(data, att, def, -5);
        broadcast(server, att, def, Component.translatable(attackerWon ? "hearthbound.diplo.raid_won" : "hearthbound.diplo.raid_lost", att.name),
                Component.translatable("hearthbound.diplo.raid_sub", def.name), attackerWon ? 0xFFD04040 : 0xFF6CD68A);
    }

    // ================================================================== raiders (called by the entities)

    private static Warband warbandOf(VillageData data, UUID member) {
        for (Warband w : data.diplomacy.warbands) if (w.members.contains(member)) return w;
        return null;
    }

    /** False once the attack is over (peace, the village is gone...): the raider then leaves. */
    public static boolean raidActive(MinecraftServer server, SettlerEntity raider) {
        VillageData data = VillageData.get(server);
        Warband w = warbandOf(data, raider.getUUID());
        return w != null && data.get(w.defender) != null;
    }

    /** A raider died (killed by anyone): the warband loses a member. */
    public static void raiderDied(MinecraftServer server, SettlerEntity raider) {
        VillageData data = VillageData.get(server);
        Warband w = warbandOf(data, raider.getUUID());
        if (w == null) return;
        w.members.remove(raider.getUUID());
        data.setDirty();
    }

    /** A raider killed a defender. */
    public static void raiderKill(MinecraftServer server, SettlerEntity raider) {
        VillageData data = VillageData.get(server);
        Warband w = warbandOf(data, raider.getUUID());
        if (w == null) return;
        w.score += 3;
        data.setDirty();
    }

    /** Somewhere in the attacked village worth raiding: a house door, or the plaza. */
    public static BlockPos raidWaypoint(MinecraftServer server, UUID defender, RandomSource random) {
        Village v = VillageData.get(server).get(defender);
        if (v == null) return null;
        List<BlockPos> spots = new ArrayList<>();
        for (PlacedBuilding b : v.buildings) if (b.complete) spots.add(b.doorstep());
        if (spots.isEmpty() || random.nextInt(5) == 0) return v.center;
        return spots.get(random.nextInt(spots.size()));
    }

    /** A raider reached a house: it steals some resources for its village. Returns true if it took anything. */
    public static boolean pillage(ServerLevel level, SettlerEntity raider) {
        if (!HBConfig.RAIDERS_PILLAGE.get()) return false;
        VillageData data = VillageData.get(level.getServer());
        Warband w = warbandOf(data, raider.getUUID());
        if (w == null) return false;
        Village att = data.get(w.attacker), def = data.get(w.defender);
        if (att == null || def == null) return false;
        List<Resource> have = new ArrayList<>();
        for (Resource r : Resource.values()) if (def.get(r) > 0) have.add(r);
        w.score += 1;
        BlockPos at = raider.blockPosition();
        level.playSound(null, at, SoundEvents.CHEST_OPEN, SoundSource.HOSTILE, 0.8f, 0.8f);
        level.playSound(null, at, SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 0.8f, 1.1f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.01);
        if (!have.isEmpty()) {
            Resource r = have.get(level.random.nextInt(have.size()));
            int n = Math.min(def.get(r), 2 + level.random.nextInt(4));
            def.add(r, -n);
            att.add(r, n);
            w.looted += n;
            long now = level.getGameTime();
            if (now - w.lastNotice > 200) {
                w.lastNotice = now;
                notifyNear(level, def, Component.translatable("hearthbound.diplo.pillage", att.name, n,
                        Component.translatable("hearthbound.resource." + r.id()), def.name), 0xFFE06A5A);
            }
        }
        def.prosperity = Math.max(0, def.prosperity - 1);
        data.setDirty();
        return true;
    }

    /** Players the raiders leave alone: friends of the attacking village. */
    public static boolean spareFromRaid(ServerPlayer p, SettlerEntity raider) {
        Village att = VillageData.get(p.server).get(raider.villageId());
        if (att == null) return false;
        return Rpg.data(p).rank(att.id).ordinal() >= com.hearthbound.rpg.Rank.FRIEND.ordinal();
    }

    // ================================================================== players

    /** A player pays for a gift that improves the relation between two villages. */
    public static boolean gift(ServerPlayer p, VillageData data, Village from, Village to) {
        change(data, from, to, HBConfig.GIFT_RELATION.get());
        Reputation.add(p, from, 3, true, true);
        return true;
    }

    /** Notifies players who know either village. */
    static void broadcast(MinecraftServer server, Village a, Village b, Component title, Component sub, int color) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var d = Rpg.data(p);
            if (d.discovered.contains(a.id) || d.discovered.contains(b.id)) Net.banner(p, title, sub, color);
        }
    }

    static void notifyNear(ServerLevel level, Village v, Component text, int color) {
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(v.center) < (double) (v.radius() + 96) * (v.radius() + 96)) Net.notify(p, text, color);
        }
    }

    public static Component stanceName(Stance s) {
        return Component.translatable("hearthbound.stance." + s.name().toLowerCase(java.util.Locale.ROOT));
    }
}
