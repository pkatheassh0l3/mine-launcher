package com.hearthbound.village;

import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.data.ContractTemplate;
import com.hearthbound.data.HBData;
import com.hearthbound.data.Matcher;
import com.hearthbound.data.Persona;
import com.hearthbound.entity.SettlerEntity;
import com.hearthbound.network.Net;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.rpg.Contract;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.rpg.Wallet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Personal relationships with settlers. Every resident has a life story (a {@link Persona})
 * told through a chain of personal quests. Friendship (0-100) grows by talking, gifts and
 * helping them; good friends travel with you, and loyal ones move to a village you rule.
 */
public final class Persons {
    private Persons() {}

    /** Friendship needed for each level: stranger, acquaintance, friend, confidant, loyal. */
    private static final int[] LEVELS = {0, 15, 35, 60, 80};

    public static int levelAt(int level) {
        return LEVELS[Math.max(0, Math.min(LEVELS.length - 1, level))];
    }

    public static int level(int points) {
        int l = 0;
        for (int i = 0; i < LEVELS.length; i++) if (points >= LEVELS[i]) l = i;
        return l;
    }

    /** A resident with the village they live in. */
    public record Ref(Village village, Resident resident) {}

    // ================================================================== lookup

    public static void assignPersona(Village v, Resident r, RandomSource random) {
        if (!r.persona.isEmpty() && HBData.persona(ResourceLocation.tryParse(r.persona)) != null) return;
        // neighbours never share a story
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (Resident o : v.residents) if (o != r && !o.persona.isEmpty()) taken.add(o.persona);
        List<Persona> fit = new ArrayList<>();
        int total = 0;
        for (Persona p : HBData.personas()) {
            if (p.weight > 0 && p.fits(r.role, r.female, v.culture) && !taken.contains(p.id.toString())) {
                fit.add(p);
                total += p.weight;
            }
        }
        if (fit.isEmpty()) {
            for (Persona p : HBData.personas()) {
                if (p.weight > 0 && p.fits(r.role, r.female, v.culture)) {
                    fit.add(p);
                    total += p.weight;
                }
            }
        }
        if (fit.isEmpty()) {
            for (Persona p : HBData.personas()) {
                if (p.weight > 0 && p.fitsIgnoringRole(r.female, v.culture)) {
                    fit.add(p);
                    total += Math.max(1, p.weight);
                }
            }
        }
        if (fit.isEmpty() || total <= 0) return;
        int pick = random.nextInt(total);
        for (Persona p : fit) {
            pick -= Math.max(1, p.weight);
            if (pick < 0) {
                r.persona = p.id.toString();
                r.origin = -1;
                int n = HBData.origins(r.role.id());
                if (p.origin && n > 0) {
                    java.util.Set<Integer> used = new java.util.HashSet<>();
                    for (Resident o : v.residents) if (o != r && o.role == r.role) used.add(o.origin);
                    int start = random.nextInt(n);
                    r.origin = start;
                    for (int i = 0; i < n; i++) {
                        int c = (start + i) % n;
                        if (!used.contains(c)) {
                            r.origin = c;
                            break;
                        }
                    }
                }
                return;
            }
        }
    }

    /** The paragraphs of a settler's biography, in the order they are revealed. */
    public static List<String> bioKeys(Persona per, Resident r) {
        List<String> out = new ArrayList<>();
        if (per == null) return out;
        if (per.origin && r.origin >= 0) out.add("hearthbound.origin." + r.role.id() + "." + r.origin);
        for (int i = 0; i < per.bios; i++) out.add(per.key() + ".bio." + i);
        return out;
    }

    public static Persona persona(Resident r) {
        return r.persona.isEmpty() ? null : HBData.persona(ResourceLocation.tryParse(r.persona));
    }

    public static Ref byEntity(MinecraftServer server, SettlerEntity e) {
        VillageData data = VillageData.get(server);
        Village v = data.get(e.villageId());
        if (v != null) {
            for (Resident r : v.residents) if (e.getUUID().equals(r.entity)) return new Ref(v, r);
        }
        for (Village o : data.all()) {
            for (Resident r : o.residents) if (e.getUUID().equals(r.entity)) return new Ref(o, r);
        }
        return null;
    }

    public static Ref byId(MinecraftServer server, UUID residentId) {
        if (residentId == null) return null;
        for (Village v : VillageData.get(server).all()) {
            for (Resident r : v.residents) if (residentId.equals(r.id)) return new Ref(v, r);
        }
        return null;
    }

    private static SettlerEntity entity(ServerPlayer p, UUID id) {
        if (id == null) return null;
        if (p.serverLevel().getEntity(id) instanceof SettlerEntity s && s.distanceToSqr(p) < 16 * 16) return s;
        return null;
    }

    private static int followers(ServerPlayer p) {
        int n = 0;
        for (ServerLevel l : p.server.getAllLevels()) {
            n += l.getEntities(ModRegistry.SETTLER.get(), s -> s.isFollower() && p.getUUID().equals(s.owner())).size();
        }
        return n;
    }

    // ================================================================== screen

    public static void open(ServerPlayer p, SettlerEntity e) {
        Ref ref = byEntity(p.server, e);
        if (ref == null) {
            VillageService.openFromSettler(p, e);
            return;
        }
        VillageService.discover(p, ref.village);
        if (ref.resident.persona.isEmpty()) {
            assignPersona(ref.village, ref.resident, p.getRandom());
            VillageData.get(p.server).setDirty();
        }
        PlayerData.Bond b = bond(p, ref);
        CompoundTag view = view(p, ref, e, b);
        Rpg.sync(p);
        Net.send(p, "person", view);
    }

    private static PlayerData.Bond bond(ServerPlayer p, Ref ref) {
        PlayerData.Bond b = Rpg.data(p).bond(ref.resident.id);
        b.name = ref.resident.name;
        b.persona = ref.resident.persona;
        b.village = ref.village.id;
        return b;
    }

    private static long day(ServerPlayer p) {
        return p.serverLevel().getDayTime() / 24000L;
    }

    private static CompoundTag view(ServerPlayer p, Ref ref, SettlerEntity e, PlayerData.Bond b) {
        Village v = ref.village;
        Resident r = ref.resident;
        Persona per = persona(r);
        CompoundTag t = new CompoundTag();
        t.putUUID("entity", e.getUUID());
        t.putUUID("person", r.id);
        t.putString("name", r.name);
        t.putString("role", r.role.id());
        t.putBoolean("female", r.female);
        t.putUUID("village", v.id);
        t.putString("villageName", v.name);
        t.putInt("color", v.culture() == null ? 0xE0B25A : v.culture().color);
        String key = per == null ? "hearthbound.persona.none" : per.key();
        t.putString("key", key);
        t.putString("icon", BuiltInRegistries.ITEM.getKey(per == null ? r.role.tool : per.icon).toString());
        int lvl = level(b.points);
        t.putInt("bond", b.points);
        t.putInt("level", lvl);
        t.putInt("levelAt", LEVELS[lvl]);
        t.putInt("nextAt", lvl + 1 < LEVELS.length ? LEVELS[lvl + 1] : 100);
        int stages = per == null ? 0 : per.stages.size();
        t.putInt("stages", stages);
        t.putInt("stage", b.stage);
        // the story opens up as friendship grows
        int bio = 1 + (b.points >= LEVELS[2] ? 1 : 0) + (b.points >= LEVELS[3] || (stages > 0 && b.stage >= stages) ? 1 : 0);
        List<String> parts = bioKeys(per, r);
        ListTag bioKeys = new ListTag();
        for (int i = 0; i < Math.min(bio, parts.size()); i++) bioKeys.add(StringTag.valueOf(parts.get(i)));
        t.put("bioKeys", bioKeys);
        t.putBoolean("moreBio", bio < parts.size());
        if (per != null && per.source) t.putString("source", per.key() + ".source");
        long day = day(p);
        t.putBoolean("talked", b.lastTalk == day);
        t.putBoolean("gifted", b.lastGift == day);
        int talks = per == null ? 3 : per.talkLines;
        int line = (int) Math.floorMod(day + r.id.getLeastSignificantBits(), 2L) + (lvl >= 2 ? Math.min(2, talks - 2) : 0);
        t.putString("line", b.lastLine.isEmpty() ? key + ".talk." + Math.max(0, Math.min(talks - 1, line)) : b.lastLine);
        int phase = phase(per, b);
        int total = per == null ? 0 : per.dialogues[phase];
        t.putInt("dlgHeard", Math.min(total, b.heard[phase]));
        t.putInt("dlgTotal", total);
        int limit = HBConfig.DIALOGUES_PER_DAY.get();
        int today = b.lineDay == day ? b.linesToday : 0;
        t.putBoolean("dlgNew", total > b.heard[phase] && (limit <= 0 || today < limit));
        ListTag likes = new ListTag();
        if (per != null && b.points >= LEVELS[1]) {
            for (String l : per.likes) {
                if (likes.size() >= 5) break;
                likes.add(StringTag.valueOf(l));
            }
        }
        t.put("likes", likes);
        t.putBoolean("likesHidden", per != null && !per.likes.isEmpty() && b.points < LEVELS[1]);
        // personal quest
        CompoundTag q = new CompoundTag();
        if (per == null || stages == 0) {
            q.putString("state", "none");
        } else if (b.stage >= stages) {
            q.putString("state", "done");
            q.putString("text", key + ".epilogue");
        } else {
            Persona.Stage st = per.stages.get(b.stage);
            String sk = key + ".stage." + b.stage;
            q.putString("title", sk + ".title");
            q.putString("text", sk + ".ask");
            q.putString("icon", BuiltInRegistries.ITEM.getKey(st.icon).toString());
            q.putInt("bondReward", st.bond);
            q.putInt("coins", coins(st));
            q.putInt("xp", xp(st));
            q.putBoolean("keepsake", !st.keepsakeItem.isEmpty());
            Contract active = active(p, r.id);
            if (active != null) {
                int progress = active.type == ContractTemplate.Type.DELIVER ? Math.min(active.count, Contracts.countItems(p, active.target)) : active.progress;
                q.putString("state", progress >= active.count ? "ready" : "active");
                q.putUUID("contract", active.id);
                q.putString("objective", Component.Serializer.toJson(active.objective(), p.registryAccess()));
                q.putInt("progress", progress);
                q.putInt("count", active.count);
            } else if (b.points < st.minBond) {
                q.putString("state", "locked");
                q.putInt("need", st.minBond);
            } else {
                q.putString("state", "available");
            }
        }
        t.put("quest", q);
        // follow / move
        boolean mine = e.isFollower() && p.getUUID().equals(e.owner());
        t.putBoolean("following", mine);
        t.putBoolean("waiting", mine && e.waiting());
        t.putBoolean("otherFollow", e.isCompanion() && !mine);
        t.putInt("followAt", HBConfig.BOND_FOLLOW.get());
        t.putInt("moveAt", HBConfig.BOND_MOVE.get());
        t.putInt("followers", followers(p));
        t.putInt("maxFollowers", HBConfig.MAX_FOLLOWERS.get());
        ListTag homes = new ListTag();
        for (Village o : VillageData.get(p.server).all()) {
            if (o == v || !p.getUUID().equals(o.lord)) continue;
            CompoundTag h = new CompoundTag();
            h.putUUID("id", o.id);
            h.putString("name", o.name);
            homes.add(h);
        }
        t.put("homes", homes);
        t.putString("tab", switch (r.role) {
            case MERCHANT, SMITH, FARMER, MAGE -> HBConfig.CENTRAL_MARKET.get() ? "trade" : "projects"; // wares are sold in their buildings
            case ELDER -> "contracts";
            case PRIEST -> "shrine";
            case INNKEEPER -> "people";
            case BUILDER -> "build";
            default -> "overview";
        });
        return t;
    }

    private static int coins(Persona.Stage st) {
        return (int) Math.round(st.coins * HBConfig.STORY_REWARD_MULTIPLIER.get());
    }

    private static int xp(Persona.Stage st) {
        return (int) Math.round(st.xp * HBConfig.STORY_REWARD_MULTIPLIER.get());
    }

    private static Contract active(ServerPlayer p, UUID person) {
        for (Contract c : Rpg.data(p).contracts) if (person.equals(c.person)) return c;
        return null;
    }

    private static void refresh(ServerPlayer p, SettlerEntity e) {
        Ref ref = byEntity(p.server, e);
        if (ref == null) return;
        CompoundTag view = view(p, ref, e, bond(p, ref));
        view.putBoolean("refresh", true);
        Rpg.sync(p);
        Net.send(p, "person", view);
    }

    /** "Name: «text»" in chat, in the settler's colors. */
    private static void say(ServerPlayer p, Resident r, Component text) {
        p.sendSystemMessage(Component.empty()
                .append(Component.literal(r.name).withColor(r.role.color))
                .append(Component.literal(": ").withColor(0x9AA0AC))
                .append(Component.literal("«").append(text).append("»").withStyle(Style.EMPTY.withItalic(true)).withColor(0xE8E6E1)));
    }

    // ================================================================== friendship

    public static void addBond(ServerPlayer p, Ref ref, int amount) {
        if (amount == 0) return;
        PlayerData.Bond b = bond(p, ref);
        int before = level(b.points);
        b.points = Math.max(0, Math.min(100, b.points + amount));
        int after = level(b.points);
        Net.notify(p, Component.translatable(amount > 0 ? "hearthbound.bond.up" : "hearthbound.bond.down", (amount > 0 ? "+" : "") + amount, ref.resident.name),
                amount > 0 ? 0xFFF08CB0 : 0xFFE06A5A);
        if (after > before) {
            Net.banner(p, Component.translatable("hearthbound.bond.level_up", Component.translatable("hearthbound.bond.level." + after)),
                    Component.literal(ref.resident.name), 0xFFF08CB0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
            if (b.points >= HBConfig.BOND_FOLLOW.get() && LEVELS[before] < HBConfig.BOND_FOLLOW.get()) {
                p.sendSystemMessage(Component.translatable("hearthbound.bond.can_follow", ref.resident.name).withColor(0xF08CB0));
            }
            if (b.points >= HBConfig.BOND_MOVE.get() && LEVELS[before] < HBConfig.BOND_MOVE.get()) {
                p.sendSystemMessage(Component.translatable("hearthbound.bond.can_move", ref.resident.name).withColor(0xF08CB0));
            }
        }
    }

    public static void talk(ServerPlayer p, SettlerEntity e) {
        Ref ref = byEntity(p.server, e);
        if (ref == null) return;
        PlayerData.Bond b = bond(p, ref);
        long day = day(p);
        Persona per = persona(ref.resident);
        String key = per == null ? "hearthbound.persona.none" : per.key();
        int talks = per == null ? 3 : per.talkLines;
        int lvl = level(b.points);
        int line = (int) Math.floorMod(day + ref.resident.id.getLeastSignificantBits(), 2L) + (lvl >= 2 ? Math.min(2, talks - 2) : 0);
        String said = key + ".talk." + Math.max(0, Math.min(talks - 1, line));
        boolean fresh = false;
        int phase = phase(per, b);
        if (per != null && per.dialogues[phase] > b.heard[phase]) {
            if (b.lineDay != day) {
                b.lineDay = day;
                b.linesToday = 0;
            }
            int limit = HBConfig.DIALOGUES_PER_DAY.get();
            if (limit <= 0 || b.linesToday < limit) {
                said = key + ".dialogue." + phase + "." + b.heard[phase];
                b.heard[phase]++;
                b.linesToday++;
                fresh = true;
            } else {
                said = "hearthbound.person.tomorrow";
            }
        }
        if (!said.startsWith("hearthbound.person.")) b.lastLine = said;
        say(p, ref.resident, Component.translatable(said));
        if (fresh && HBConfig.BOND_DIALOGUE.get() > 0) addBond(p, ref, HBConfig.BOND_DIALOGUE.get());
        e.getLookControl().setLookAt(p);
        p.level().playSound(null, e.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.8f, e.getVoicePitch());
        if (b.lastTalk != day) {
            b.lastTalk = day;
            addBond(p, ref, HBConfig.BOND_TALK.get());
        }
        refresh(p, e);
    }

    /** Story phase: 0 before chapter 1, 1 and 2 between chapters, 3 once the story is over. */
    static int phase(Persona per, PlayerData.Bond b) {
        if (per == null) return 0;
        int stages = per.stages.size();
        return stages > 0 && b.stage >= stages ? 3 : Math.min(2, b.stage);
    }

    public static void gift(ServerPlayer p, SettlerEntity e) {
        Ref ref = byEntity(p.server, e);
        if (ref == null) return;
        PlayerData.Bond b = bond(p, ref);
        long day = day(p);
        ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.isEmpty()) {
            p.displayClientMessage(Component.translatable("hearthbound.bond.gift_empty").withColor(0xE06A5A), true);
            return;
        }
        if (b.lastGift == day) {
            p.displayClientMessage(Component.translatable("hearthbound.bond.gift_today", ref.resident.name).withColor(0xE06A5A), true);
            return;
        }
        Persona per = persona(ref.resident);
        boolean liked = false;
        if (per != null) {
            for (String l : per.likes) {
                if (Matcher.of(l, Registries.ITEM).test(BuiltInRegistries.ITEM.wrapAsHolder(held.getItem()))) {
                    liked = true;
                    break;
                }
            }
        }
        Component name = held.getHoverName();
        held.shrink(1);
        b.lastGift = day;
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(liked ? ParticleTypes.HEART : ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 2.1, e.getZ(), liked ? 5 : 3, 0.3, 0.2, 0.3, 0.02);
        }
        p.level().playSound(null, e.blockPosition(), liked ? SoundEvents.VILLAGER_CELEBRATE : SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.9f, e.getVoicePitch());
        say(p, ref.resident, Component.translatable(liked ? "hearthbound.bond.gift_liked" : "hearthbound.bond.gift_ok", name));
        addBond(p, ref, liked ? HBConfig.BOND_GIFT_LIKED.get() : HBConfig.BOND_GIFT.get());
        refresh(p, e);
    }

    // ================================================================== personal quests

    public static void accept(ServerPlayer p, SettlerEntity e) {
        Ref ref = byEntity(p.server, e);
        if (ref == null) return;
        Persona per = persona(ref.resident);
        PlayerData.Bond b = bond(p, ref);
        if (per == null || b.stage >= per.stages.size() || active(p, ref.resident.id) != null) return;
        Persona.Stage st = per.stages.get(b.stage);
        if (b.points < st.minBond) return;
        Contract c = new Contract();
        c.type = switch (st.type) {
            case DELIVER -> ContractTemplate.Type.DELIVER;
            case HUNT -> ContractTemplate.Type.HUNT;
            case DEFEND -> ContractTemplate.Type.DEFEND;
            case EXPLORE -> ContractTemplate.Type.EXPLORE;
            case ENVOY -> ContractTemplate.Type.ENVOY;
        };
        c.target = st.target;
        c.icon = st.icon;
        c.count = st.count;
        if (st.type == Persona.Type.ENVOY) {
            Village dest = envoyDestination(p, ref.village);
            if (dest == null) {
                c.type = ContractTemplate.Type.EXPLORE; // nobody to write to yet: find a village instead
                c.count = 1;
            } else {
                c.target = dest.id.toString();
                c.extra = dest.name;
                c.count = 1;
            }
        }
        c.title = per.key() + ".stage." + b.stage + ".title";
        c.village = ref.village.id;
        c.villageName = ref.village.name;
        c.person = ref.resident.id;
        c.personName = ref.resident.name;
        c.stage = b.stage;
        c.coins = coins(st);
        c.xp = xp(st);
        c.reputation = 4;
        c.items = new ArrayList<>(st.items);
        Rpg.data(p).contracts.add(c);
        Rpg.data(p).tracked = c.id;
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        Net.notify(p, Component.translatable("hearthbound.contract.accepted", c.title()), 0xFFF08CB0);
        refresh(p, e);
    }

    private static Village envoyDestination(ServerPlayer p, Village from) {
        VillageData data = VillageData.get(p.server);
        Village best = null;
        double bestD = Double.MAX_VALUE;
        for (Village o : data.all()) {
            if (o == from || !o.dimension.equals(from.dimension)) continue;
            if (HBConfig.DIPLOMACY.get() && Diplomacy.relation(data, from, o).atWar()) continue;
            double d = o.center.distSqr(from.center);
            if (d < bestD) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /** Hands in a personal quest (from the settler's screen or the village ledger). */
    public static boolean turnIn(ServerPlayer p, Contract c) {
        Ref ref = byId(p.server, c.person);
        if (ref == null) {
            Rpg.data(p).contracts.remove(c); // the person is gone
            return false;
        }
        if (c.type == ContractTemplate.Type.DELIVER) {
            int have = Contracts.countItems(p, c.target);
            if (have < c.count) {
                p.displayClientMessage(Component.translatable("hearthbound.contract.missing", c.count - have, c.targetName()).withColor(0xE06A5A), true);
                return false;
            }
            Contracts.takeItems(p, c.target, c.count);
        } else if (c.progress < c.count) {
            p.displayClientMessage(Component.translatable("hearthbound.contract.unfinished").withColor(0xE06A5A), true);
            return false;
        }
        complete(p, ref, c);
        return true;
    }

    private static void complete(ServerPlayer p, Ref ref, Contract c) {
        PlayerData d = Rpg.data(p);
        d.contracts.remove(c);
        if (c.id.equals(d.tracked)) d.tracked = null;
        Persona per = persona(ref.resident);
        PlayerData.Bond b = bond(p, ref);
        Persona.Stage st = per != null && c.stage < per.stages.size() ? per.stages.get(c.stage) : null;
        Wallet.give(p, c.coins);
        d.coinsEarned += c.coins;
        for (String s : c.items) Contracts.give(p, Contracts.parseStack(s));
        if (st != null && !st.keepsakeItem.isEmpty()) Contracts.give(p, keepsake(p, st, ref.resident));
        Rpg.addXp(p, c.xp, false);
        Reputation.add(p, ref.village, c.reputation, true, false);
        String key = per == null ? "hearthbound.persona.none" : per.key();
        say(p, ref.resident, Component.translatable(key + ".stage." + c.stage + ".done"));
        b.stage = Math.max(b.stage, c.stage + 1);
        addBond(p, ref, st == null ? 10 : st.bond);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        boolean finished = per != null && b.stage >= per.stages.size();
        if (finished) {
            d.storiesDone++;
            Net.banner(p, Component.translatable("hearthbound.story.finished"), Component.translatable("hearthbound.story.finished_sub", ref.resident.name), 0xFFF08CB0);
            Compat.trigger(p, "stories", ref.resident, 1);
        } else {
            Net.banner(p, Component.translatable("hearthbound.story.chapter", c.stage + 1), c.title(), 0xFFF08CB0);
        }
        Net.notify(p, Component.translatable("hearthbound.notify.reward", Wallet.format(c.coins), c.reputation, c.xp), 0xFFE0B25A);
        VillageData.get(p.server).setDirty();
        Rpg.sync(p);
    }

    private static ItemStack keepsake(ServerPlayer p, Persona.Stage st, Resident r) {
        ItemStack s = Contracts.parseStack(st.keepsakeItem);
        if (s.isEmpty()) return s;
        if (!st.keepsakeName.isEmpty()) {
            s.set(DataComponents.CUSTOM_NAME, Component.translatable(st.keepsakeName).withStyle(Style.EMPTY.withItalic(false).withColor(0xFFD24A)));
        }
        s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.translatable("hearthbound.keepsake.lore", r.name).withStyle(Style.EMPTY.withItalic(true).withColor(0xB8A0C8)))));
        if (!st.keepsakeEnchant.isEmpty()) {
            for (String part : st.keepsakeEnchant.split(",")) {
                String[] kv = part.trim().split("=");
                ResourceLocation id = ResourceLocation.tryParse(kv[0]);
                if (id == null) continue;
                int lvl = 1;
                try {
                    if (kv.length > 1) lvl = Integer.parseInt(kv[1]);
                } catch (NumberFormatException ignored) {
                }
                final int level = lvl;
                p.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ResourceKey.create(Registries.ENCHANTMENT, id))
                        .ifPresent(h -> s.enchant(h, level));
            }
        }
        return s;
    }

    // ================================================================== follow / move

    public static void follow(ServerPlayer p, SettlerEntity e) {
        Ref ref = byEntity(p.server, e);
        if (ref == null || e.isCompanion()) return;
        PlayerData.Bond b = bond(p, ref);
        if (b.points < HBConfig.BOND_FOLLOW.get()) return;
        if (followers(p) >= HBConfig.MAX_FOLLOWERS.get()) {
            p.displayClientMessage(Component.translatable("hearthbound.bond.too_many", HBConfig.MAX_FOLLOWERS.get()).withColor(0xE06A5A), true);
            return;
        }
        e.makeFollower(p);
        say(p, ref.resident, Component.translatable("hearthbound.bond.follow_yes"));
        p.level().playSound(null, e.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, e.getVoicePitch());
        refresh(p, e);
    }

    public static void toggleWait(ServerPlayer p, SettlerEntity e) {
        if (!e.isFollower() || !p.getUUID().equals(e.owner())) return;
        e.setWaiting(!e.waiting());
        e.getNavigation().stop();
        p.displayClientMessage(Component.translatable(e.waiting() ? "hearthbound.companion.wait" : "hearthbound.companion.follow", e.getCustomName()), true);
        refresh(p, e);
    }

    public static void goHome(ServerPlayer p, SettlerEntity e) {
        if (!e.isFollower() || !p.getUUID().equals(e.owner())) return;
        Ref ref = byEntity(p.server, e);
        e.stopFollowing();
        if (ref != null) say(p, ref.resident, Component.translatable("hearthbound.bond.go_home", ref.village.name));
        refresh(p, e);
    }

    /** A loyal friend moves to a village the player rules. */
    public static void move(ServerPlayer p, SettlerEntity e, UUID to) {
        Ref ref = byEntity(p.server, e);
        if (ref == null) return;
        VillageData data = VillageData.get(p.server);
        Village dest = data.get(to);
        Village from = ref.village;
        Resident r = ref.resident;
        if (dest == null || dest == from || !p.getUUID().equals(dest.lord)) return;
        if (e.isCompanion() && !p.getUUID().equals(e.owner())) return;
        if (bond(p, ref).points < HBConfig.BOND_MOVE.get()) return;
        if (e.isSleeping()) e.stopSleeping();
        from.residents.remove(r);
        r.bed = null; // gets a bed in the new village
        e.setBed(null);
        r.home = VillageManager.homeFor(dest);
        r.work = VillageManager.workplaceFor(dest, r.role);
        dest.residents.add(r);
        BlockPos home = r.home >= 0 && r.home < dest.buildings.size() ? dest.buildings.get(r.home).center() : null;
        BlockPos work = r.work >= 0 && r.work < dest.buildings.size() ? dest.buildings.get(r.work).center() : r.role == Role.GUARD ? dest.center : null;
        e.bindVillage(dest.id, dest.center, dest.radius(), home, work);
        if (!e.isFollower()) {
            ServerLevel level = p.server.getLevel(dest.dimension);
            if (level == e.level() && level.isLoaded(dest.center)) {
                BlockPos at = r.home >= 0 && r.home < dest.buildings.size() ? dest.buildings.get(r.home).doorstep() : dest.center;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                ((ServerLevel) e.level()).sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                e.moveTo(at.getX() + 0.5, y, at.getZ() + 0.5, e.getYRot(), 0);
                e.getNavigation().stop();
            } else {
                e.discard();
                r.entity = null; // appears in the new village when it is next loaded
            }
        }
        PlayerData.Bond b = bond(p, new Ref(dest, r));
        b.village = dest.id;
        dest.prosperity = Math.min(100, dest.prosperity + 3);
        Reputation.add(p, from, -HBConfig.MOVE_REP_COST.get(), false, true);
        say(p, r, Component.translatable("hearthbound.bond.move_yes", dest.name));
        Net.banner(p, Component.translatable("hearthbound.bond.moved", r.name), Component.translatable("hearthbound.bond.moved_sub", from.name, dest.name), 0xFFF08CB0);
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        data.setDirty();
        if (e.isAlive()) refresh(p, e);
    }

    // ================================================================== network

    public static void handle(ServerPlayer p, String action, CompoundTag d) {
        if (!HBConfig.BONDS.get()) return;
        SettlerEntity e = entity(p, d.hasUUID("entity") ? d.getUUID("entity") : null);
        if (e == null) return;
        switch (action) {
            case "person_talk" -> talk(p, e);
            case "person_gift" -> gift(p, e);
            case "person_accept" -> accept(p, e);
            case "person_turnin" -> {
                Ref ref = byEntity(p.server, e);
                Contract c = ref == null ? null : active(p, ref.resident.id);
                if (c != null) turnIn(p, c);
                refresh(p, e);
            }
            case "person_follow" -> follow(p, e);
            case "person_wait" -> toggleWait(p, e);
            case "person_home" -> goHome(p, e);
            case "person_move" -> {
                if (d.hasUUID("to")) move(p, e, d.getUUID("to"));
            }
            case "person_village" -> VillageService.openFromSettler(p, e);
            case "person_track" -> {
                Ref ref = byEntity(p.server, e);
                Contract c = ref == null ? null : active(p, ref.resident.id);
                if (c != null) Rpg.data(p).tracked = c.id;
                Rpg.sync(p);
            }
            default -> {
            }
        }
    }
}
