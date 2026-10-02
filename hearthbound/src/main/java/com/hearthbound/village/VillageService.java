package com.hearthbound.village;

import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.data.BuildingDef;
import com.hearthbound.data.ContractTemplate;
import com.hearthbound.data.Culture;
import com.hearthbound.data.HBData;
import com.hearthbound.data.ProjectDef;
import com.hearthbound.entity.SettlerEntity;
import com.hearthbound.network.Net;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.rpg.Contract;
import com.hearthbound.rpg.PlayerClass;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rank;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.rpg.Wallet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything a player does with a village: opening the ledger, trading, contracts, donations, hiring and lordship. */
public final class VillageService {
    private VillageService() {}

    // ================================================================== opening

    public static void openAt(ServerPlayer p, BlockPos pos) {
        Village v = VillageData.get(p.server).at(p.serverLevel(), pos);
        if (v == null) v = VillageData.get(p.server).nearest(p.serverLevel(), pos, 16);
        if (v == null) {
            p.displayClientMessage(Component.translatable("hearthbound.ui.no_village"), true);
            return;
        }
        open(p, v, "overview");
    }

    public static void openFromSettler(ServerPlayer p, SettlerEntity e) {
        Village v = VillageData.get(p.server).get(e.villageId());
        if (v == null) v = VillageData.get(p.server).at(p.serverLevel(), e.blockPosition());
        if (v == null) {
            p.displayClientMessage(Component.translatable("hearthbound.ui.wanderer", e.getCustomName()), true);
            return;
        }
        String tab = switch (e.role()) {
            case MERCHANT, SMITH, FARMER, MAGE -> HBConfig.CENTRAL_MARKET.get() ? "trade" : "projects"; // wares are sold in their buildings
            case ELDER -> "contracts";
            case PRIEST -> "shrine";
            case INNKEEPER -> "people";
            case BUILDER -> "build";
            default -> "overview";
        };
        CompoundTag extra = new CompoundTag();
        extra.putString("speaker", e.getCustomName() == null ? "" : e.getCustomName().getString());
        extra.putString("speakerRole", e.role().id());
        extra.putInt("speakerVariant", e.variant());
        open(p, v, tab, extra);
    }

    public static void open(ServerPlayer p, Village v, String tab) {
        open(p, v, tab, new CompoundTag());
    }

    /** Shop (project type) each player is browsing, so refreshes keep showing that shop. */
    private static final Map<java.util.UUID, String> SHOPS = new java.util.HashMap<>();

    /** True if the player stands inside (or at the door of) a finished building of that type. */
    public static boolean inShop(ServerPlayer p, Village v, String type) {
        if (!p.level().dimension().equals(v.dimension)) return false;
        for (Project.Work w : v.works) {
            if (w.type().equals(type) && w.area().inflatedBy(3).isInside(p.blockPosition())) return true;
        }
        return false;
    }

    /** Opens the shop of one player building: only its wares, and only here can they be bought. */
    public static void openShop(ServerPlayer p, Village v, String type) {
        discover(p, v);
        SHOPS.put(p.getUUID(), type);
        CompoundTag view = view(p, v);
        view.putString("tab", "trade");
        Rpg.sync(p);
        Net.send(p, "village", view);
    }

    public static void open(ServerPlayer p, Village v, String tab, CompoundTag extra) {
        SHOPS.remove(p.getUUID());
        discover(p, v);
        CompoundTag view = view(p, v);
        view.putString("tab", tab);
        view.merge(extra);
        Rpg.sync(p);
        Net.send(p, "village", view);
    }

    public static void refresh(ServerPlayer p, Village v) {
        CompoundTag view = view(p, v);
        view.putString("tab", "");
        view.putBoolean("refresh", true);
        Rpg.sync(p);
        Net.send(p, "village", view);
    }

    // ================================================================== discovery

    public static void discover(ServerPlayer p, Village v) {
        PlayerData d = Rpg.data(p);
        if (d.discovered.contains(v.id)) return;
        d.discovered.add(v.id);
        boolean ranger = d.clazz == PlayerClass.RANGER;
        double perk = ranger ? 1 + HBConfig.PERK_RANGER_DISCOVERY.get() : 1;
        Reputation.add(p, v, (int) Math.round(HBConfig.DISCOVER_REP.get() * perk), true, false);
        Rpg.addXp(p, (int) Math.round(HBConfig.XP_DISCOVER.get() * perk), false);
        Culture c = v.culture();
        Net.banner(p, Component.translatable("hearthbound.banner.discovered", v.name),
                c == null ? Component.empty() : c.title(), c == null ? 0xFFE0B25A : c.color);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.4f, 1.5f);
        Contracts.onDiscover(p, v);
        Compat.trigger(p, "discover", v, 1);
        Rpg.sync(p);
    }

    // ================================================================== view

    static String json(ServerPlayer p, Component c) {
        return Component.Serializer.toJson(c, p.registryAccess());
    }

    private static String itemId(Item i) {
        return BuiltInRegistries.ITEM.getKey(i).toString();
    }

    public static CompoundTag view(ServerPlayer p, Village v) {
        ServerLevel level = p.server.getLevel(v.dimension);
        PlayerData d = Rpg.data(p);
        Culture c = v.culture();
        Rank rank = d.rank(v.id);
        CompoundTag t = new CompoundTag();
        String shop = SHOPS.get(p.getUUID());
        if (shop != null && !inShop(p, v, shop)) {
            SHOPS.remove(p.getUUID());
            shop = null;
        }
        boolean central = HBConfig.CENTRAL_MARKET.get();
        t.putBoolean("centralMarket", central); // without it the ledger has no trade tab: each shop sells its own wares
        if (shop != null) {
            ProjectDef sd = HBData.project(shop);
            t.putString("shop", shop);
            t.putString("shopName", sd == null ? shop : sd.name);
            t.putString("shopIcon", sd == null ? "minecraft:chest" : itemId(sd.icon));
        }
        t.putUUID("id", v.id);
        t.putString("name", v.name);
        if (c != null) {
            t.putString("culture", c.name);
            t.putString("cultureDesc", c.description);
            t.putString("people", c.people);
            t.putInt("color", c.color);
            t.putString("icon", itemId(c.icon));
            t.putString("skin", c.skin);
        }
        t.putInt("tier", v.tier());
        t.putInt("tierCap", Math.min(v.tierCap, HBConfig.MAX_TIER.get()));
        t.putInt("radius", v.radius());
        t.putInt("population", v.population());
        t.putInt("housing", v.housing());
        t.putInt("prosperity", v.prosperity);
        t.putInt("buildingsDone", v.completedBuildings());
        t.putInt("x", v.center.getX());
        t.putInt("y", v.center.getY());
        t.putInt("z", v.center.getZ());
        t.putString("lordName", v.lordName == null ? "" : v.lordName);
        boolean lord = p.getUUID().equals(v.lord);
        t.putBoolean("isLord", lord);
        if (lord) t.putInt("treasury", v.treasury);
        t.putBoolean("canClaimLord", v.lord == null && rank == Rank.HERO && canBeLord(p));
        t.putBoolean("lordAgeLocked", v.lord == null && rank == Rank.HERO && !canBeLord(p));
        if (!canBeLord(p)) t.putString("lordAge", json(p, Compat.ageName(p, HBConfig.ASC_AGE_LORDSHIP.get())));

        // player standing
        t.putInt("rep", d.rep(v.id));
        t.putString("rank", rank.id());
        Rank next = rank.next();
        t.putInt("nextRankAt", next == rank ? d.rep(v.id) : next.threshold());
        t.putInt("rankAt", rank == Rank.HOSTILE ? d.rep(v.id) : rank.threshold());
        t.putInt("balance", Wallet.balance(p));

        // stock
        CompoundTag stock = new CompoundTag();
        for (Resource r : Resource.values()) stock.putInt(r.id(), v.get(r));
        t.put("stock", stock);

        // buildings
        ListTag bl = new ListTag();
        for (PlacedBuilding b : v.buildings) {
            BuildingDef def = b.definition();
            CompoundTag e = new CompoundTag();
            e.putString("name", def == null ? b.def.toString() : def.name);
            e.putString("icon", def == null ? "minecraft:barrier" : itemId(def.icon));
            e.putBoolean("complete", b.complete);
            e.putFloat("progress", b.progress());
            e.putString("kind", def == null ? "" : def.kind.name());
            bl.add(e);
        }
        t.put("buildings", bl);
        BuildingDef nextB = VillageManager.nextBuilding(v);
        if (nextB != null && v.construction() == null) {
            CompoundTag nb = new CompoundTag();
            nb.putString("name", nextB.name);
            nb.putString("desc", nextB.description);
            nb.putString("icon", itemId(nextB.icon));
            CompoundTag cost = new CompoundTag();
            VillageManager.cost(nextB).forEach((r, a) -> cost.putInt(r.id(), a));
            nb.put("cost", cost);
            Component why = level == null ? null : VillageManager.blocker(level, v);
            if (why != null) nb.putString("blocker", json(p, why));
            t.put("next", nb);
        } else if (nextB == null && v.construction() == null) {
            t.putBoolean("planDone", true);
        }
        if (lord && HBConfig.LORD_CHOOSES_BUILDINGS.get() && c != null) {
            ListTag choices = new ListTag();
            Set<ResourceLocation> seen = new LinkedHashSet<>(c.plan);
            for (ResourceLocation id : seen) {
                BuildingDef def = HBData.building(id);
                if (def == null) continue;
                CompoundTag e = new CompoundTag();
                e.putString("id", id.toString());
                e.putString("name", def.name);
                e.putString("icon", itemId(def.icon));
                e.putBoolean("chosen", id.equals(v.lordChoice));
                choices.add(e);
            }
            t.put("planChoices", choices);
        }

        t.putInt("revivalCopper", HBConfig.REVIVAL_COPPER.get());
        t.putInt("revivalSilver", HBConfig.REVIVAL_SILVER.get());
        t.putInt("revivalCost", ResidentRevival.cost());
        t.putBoolean("canRevive", ResidentRevival.allowed(p, v));
        // residents
        ListTag rl = new ListTag();
        for (Resident r : v.residents) {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", r.id);
            e.putString("name", r.name);
            e.putString("role", r.role.id());
            e.putBoolean("alive", r.alive());
            e.putBoolean("female", r.female);
            e.putInt("variant", r.variant);
            if (r.persona.isEmpty()) {
                Persons.assignPersona(v, r, p.getRandom());
                VillageData.get(p.server).setDirty();
            }
            com.hearthbound.data.Persona per = Persons.persona(r);
            if (per != null) {
                e.putString("persona", per.key());
                List<String> bios = Persons.bioKeys(per, r);
                if (!bios.isEmpty()) e.putString("bioFirst", bios.get(0));
                if (per.source) e.putString("source", per.key() + ".source");
            }
            int bond = Rpg.data(p).bondPoints(r.id);
            e.putInt("bond", bond);
            PlayerData.Bond bd = Rpg.data(p).bonds.get(r.id);
            e.putInt("stage", bd == null ? 0 : bd.stage);
            e.putInt("stages", per == null ? 0 : per.stages.size());
            rl.add(e);
        }
        t.put("residents", rl);

        // trades
        ListTag trades = new ListTag();
        if (c != null) {
            boolean tradeAge = Compat.ageReached(p, HBConfig.ASC_AGE_TRADE.get());
            for (int i = 0; i < c.trades.size(); i++) {
                Culture.Trade tr = c.trades.get(i);
                if (tr.tier() > v.tier() + 1) continue; // hide far-away tiers
                boolean needsBuilding = !tr.building().isEmpty() && HBData.project(tr.building()) != null;
                if (needsBuilding && tr.buildingLevel() > v.projectLevel(tr.building()) + 1) continue;
                if (shop != null && !shop.equals(tr.building())) continue;
                CompoundTag e = new CompoundTag();
                e.putInt("index", i);
                e.putString("item", itemId(tr.item()));
                e.putInt("count", tr.count());
                e.putBoolean("sell", tr.sell());
                e.putInt("price", tr.sell() ? citizenPrice(p, v, Rpg.buyPrice(p, tr.price(), rank)) : Rpg.sellPrice(p, tr.price(), rank));
                e.putString("role", tr.role().id());
                if (needsBuilding) {
                    e.putString("source", json(p, Component.translatable("hearthbound.project.level_name",
                            Component.translatable(HBData.project(tr.building()).name), tr.buildingLevel())));
                    if (shop == null && !central) e.putString("where", json(p, Component.translatable("hearthbound.shop.buy_at", Component.translatable(HBData.project(tr.building()).name))));
                }
                int limit = HBConfig.DAILY_STOCK.get();
                e.putInt("left", limit <= 0 ? -1 : Math.max(0, limit - v.soldToday.getOrDefault(i, 0)));
                Component lock = null;
                if (!tradeAge) lock = Component.translatable("hearthbound.gate.age", Compat.ageName(p, HBConfig.ASC_AGE_TRADE.get()));
                else if (tr.tier() > v.tier()) lock = Component.translatable("hearthbound.gate.tier", Component.translatable("hearthbound.tier." + tr.tier()));
                else if (!rank.atLeast(tr.rank())) lock = Component.translatable("hearthbound.gate.rank", tr.rank().title());
                else if (!Compat.ageReached(p, tr.age())) lock = Component.translatable("hearthbound.gate.age", Compat.ageName(p, tr.age()));
                else if (needsBuilding && v.projectLevel(tr.building()) < tr.buildingLevel())
                    lock = Component.translatable("hearthbound.project.needs", Component.translatable(HBData.project(tr.building()).name), tr.buildingLevel());
                else if (!needsBuilding && !v.has(tr.role()) && tr.role() != Role.VILLAGER) lock = Component.translatable("hearthbound.gate.role", Component.translatable(tr.role().key()));
                if (lock != null) e.putString("lock", json(p, lock));
                if (!tr.sell()) e.putInt("have", Contracts.countItems(p, itemId(tr.item())));
                trades.add(e);
            }
        }
        // hall wares unlocked by player buildings
        boolean tradeAge2 = Compat.ageReached(p, HBConfig.ASC_AGE_TRADE.get());
        for (Projects.HallEntry he : Projects.hallTrades()) {
            // show what is unlocked plus the next level of each building, so progress stays visible
            if (he.level() > v.projectLevel(he.def().id.toString()) + 1) continue;
            if (shop != null && !shop.equals(he.def().id.toString())) continue;
            ProjectDef.HallTrade tr = he.trade();
            CompoundTag e = new CompoundTag();
            e.putInt("index", he.index());
            e.putString("item", itemId(tr.item()));
            e.put("stack", Projects.stackTag(p.registryAccess(), tr));
            e.putInt("count", tr.count());
            e.putBoolean("sell", tr.sell());
            e.putInt("price", tr.sell() ? citizenPrice(p, v, Rpg.buyPrice(p, tr.price(), rank)) : Rpg.sellPrice(p, tr.price(), rank));
            e.putString("role", "villager");
            e.putString("source", json(p, Component.translatable("hearthbound.project.level_name", Component.translatable(he.def().name), he.level())));
            if (shop == null && !central) e.putString("where", json(p, Component.translatable("hearthbound.shop.buy_at", Component.translatable(he.def().name))));
            int limit = HBConfig.DAILY_STOCK.get();
            e.putInt("left", limit <= 0 ? -1 : Math.max(0, limit - v.soldToday.getOrDefault(he.index(), 0)));
            Component lock = !tradeAge2 ? Component.translatable("hearthbound.gate.age", Compat.ageName(p, HBConfig.ASC_AGE_TRADE.get())) : Projects.hallLock(p, v, he);
            if (lock != null) e.putString("lock", json(p, lock));
            if (!tr.sell()) e.putInt("have", Contracts.countItems(p, itemId(tr.item())));
            trades.add(e);
        }
        t.put("trades", trades);

        // building projects
        ListTag projects = new ListTag();
        for (Projects.Offer o : Projects.offers(v)) {
            CompoundTag e = new CompoundTag();
            e.putString("id", o.def().id.toString());
            e.putString("name", o.def().name);
            e.putString("desc", o.def().description);
            e.putString("icon", itemId(o.def().icon));
            e.putInt("level", o.level());
            e.putInt("levels", o.def().levels.size());
            e.putInt("size", o.lvl().size);
            e.putInt("maxSize", o.lvl().maxSize);
            ListTag reqs = new ListTag();
            for (ProjectDef.Requirement r : o.lvl().requirements) {
                CompoundTag q = new CompoundTag();
                q.putString("icon", itemId(r.icon()));
                q.putString("block", r.block());
                q.putInt("count", r.count());
                reqs.add(q);
            }
            e.put("reqs", reqs);
            e.putInt("reputation", Projects.repReward(p, v, o.lvl()));
            e.putInt("coins", o.lvl().coins);
            e.putInt("xp", o.lvl().xp);
            int unlocks = 0;
            for (ProjectDef.HallTrade tr : o.lvl().trades) if (tr.sell()) unlocks++;
            e.putInt("unlocks", unlocks);
            Project cur = v.projects.get(o.def().id.toString());
            if (cur != null) {
                e.putString("owner", cur.ownerName);
                e.putBoolean("mine", p.getUUID().equals(cur.owner));
                e.putBoolean("placed", cur.core != null);
            }
            Component lock = Projects.lock(p, v, o);
            if (lock != null) e.putString("lock", json(p, lock));
            projects.add(e);
        }
        t.put("projects", projects);
        ListTag works = new ListTag();
        for (Project.Work w : v.works) {
            ProjectDef def = HBData.project(w.type());
            CompoundTag e = new CompoundTag();
            e.putString("name", def == null ? w.type() : def.name);
            e.putString("icon", def == null ? "minecraft:bricks" : itemId(def.icon));
            e.putInt("level", w.level());
            e.putString("builder", w.builder());
            works.add(e);
        }
        t.put("works", works);

        // citizenship and banner
        Flags.ensure(v);
        t.put("flag", Flags.item(v, p.registryAccess()).save(p.registryAccess()));
        t.putInt("flagColor", Flags.base(v).getTextColor());
        t.putBoolean("citizen", Citizenship.isCitizen(p, v));
        t.putInt("citizens", v.citizens.size());
        t.putBoolean("citizenship", HBConfig.CITIZENSHIP.get());
        Component clock = Citizenship.lock(p, v);
        if (clock != null) t.putString("citizenLock", json(p, clock));
        Village home = Citizenship.home(p);
        if (home != null && home != v) t.putString("citizenOf", home.name);
        t.putInt("citizenDiscount", (int) Math.round(HBConfig.CITIZEN_DISCOUNT.get() * 100));
        t.putInt("flagCost", HBConfig.FLAG_COPY_COST.get());

        // blessings
        ListTag bless = new ListTag();
        if (c != null && HBConfig.BLESSINGS_ENABLED.get()) {
            for (int i = 0; i < c.blessings.size(); i++) {
                Culture.Blessing b = c.blessings.get(i);
                CompoundTag e = new CompoundTag();
                e.putInt("index", i);
                e.putString("name", b.name());
                e.putString("effect", b.effect().unwrapKey().map(k -> k.location().toString()).orElse(""));
                e.putInt("amp", b.amplifier());
                e.putInt("color", b.effect().value().getColor());
                e.putInt("price", blessingPrice(p, rank));
                e.putInt("minutes", blessingMinutes(p));
                Component lock = null;
                if (!v.has(Role.PRIEST)) lock = Component.translatable("hearthbound.gate.role", Component.translatable(Role.PRIEST.key()));
                else if (!rank.atLeast(b.rank())) lock = Component.translatable("hearthbound.gate.rank", b.rank().title());
                else if (!Compat.ageReached(p, b.age())) lock = Component.translatable("hearthbound.gate.age", Compat.ageName(p, b.age()));
                if (lock != null) e.putString("lock", json(p, lock));
                bless.add(e);
            }
        }
        t.put("blessings", bless);

        // contract board
        if (level != null) Contracts.refreshBoard(level, v, false);
        ListTag board = new ListTag();
        boolean contractAge = Compat.ageReached(p, HBConfig.ASC_AGE_CONTRACTS.get());
        for (Contract k : v.board) {
            CompoundTag e = k.save();
            ContractTemplate tpl = k.template == null ? null : HBData.contract(k.template);
            Component lock = null;
            if (!contractAge) lock = Component.translatable("hearthbound.gate.age", Compat.ageName(p, HBConfig.ASC_AGE_CONTRACTS.get()));
            else if (tpl != null && !rank.atLeast(tpl.rank)) lock = Component.translatable("hearthbound.gate.rank", tpl.rank.title());
            else if (tpl != null && !Compat.ageReached(p, tpl.age)) lock = Component.translatable("hearthbound.gate.age", Compat.ageName(p, tpl.age));
            if (lock != null) e.putString("lock", json(p, lock));
            e.putString("objectiveText", json(p, k.objective()));
            e.putString("titleText", json(p, k.title()));
            board.add(e);
        }
        t.put("board", board);
        ListTag mine = new ListTag();
        for (Contract k : d.contracts) {
            if (!v.id.equals(k.village)) continue;
            CompoundTag e = k.save();
            boolean ready = k.type == ContractTemplate.Type.DELIVER ? Contracts.countItems(p, k.target) >= k.count : k.progress >= k.count;
            if (k.type == ContractTemplate.Type.DELIVER) e.putInt("progress", Math.min(k.count, Contracts.countItems(p, k.target)));
            e.putBoolean("ready", ready);
            e.putString("objectiveText", json(p, k.objective()));
            e.putString("titleText", json(p, k.title()));
            mine.add(e);
        }
        t.put("mine", mine);
        t.putInt("activeContracts", d.boardContracts());
        t.putInt("maxContracts", Rpg.maxContracts(p));

        // donations preview
        CompoundTag don = new CompoundTag();
        Map<Resource, Integer> preview = donationValue(p, null, false);
        for (Resource r : Resource.values()) don.putInt(r.id(), preview.getOrDefault(r, 0));
        t.put("donate", don);

        // diplomacy
        ListTag diplo = new ListTag();
        if (HBConfig.DIPLOMACY.get()) {
            VillageData vd = VillageData.get(p.server);
            for (Village o : Diplomacy.neighbours(vd, v)) {
                Diplomacy.Relation rel = Diplomacy.relation(vd, v, o);
                Diplomacy.Stance st = Diplomacy.stance(rel);
                CompoundTag e = new CompoundTag();
                e.putUUID("id", o.id);
                e.putString("name", o.name);
                Culture oc = o.culture();
                e.putString("culture", oc == null ? "" : oc.name);
                e.putInt("color", oc == null ? 0xFFE0B25A : oc.color);
                e.putInt("value", rel.value);
                e.putString("stance", st.name());
                e.putInt("stanceColor", Diplomacy.stanceColor(st));
                e.putInt("dist", (int) Math.sqrt(o.center.distSqr(v.center)));
                e.putInt("tier", o.tier());
                e.putBoolean("known", d.discovered.contains(o.id));
                long truce = rel.truceUntil - p.level().getDayTime() / 24000L;
                if (!rel.atWar() && truce > 0) e.putInt("truce", (int) truce);
                int caravans = 0;
                for (Diplomacy.Caravan cv : vd.diplomacy.caravans) {
                    if ((cv.from.equals(v.id) && cv.to.equals(o.id)) || (cv.from.equals(o.id) && cv.to.equals(v.id))) caravans++;
                }
                e.putInt("caravans", caravans);
                Rank other = d.rank(o.id);
                boolean canMediate = rel.atWar() && rank.atLeast(Rank.ALLY) && other.atLeast(Rank.FRIEND);
                e.putBoolean("canMediate", canMediate);
                if (rel.atWar() && !canMediate) e.putString("mediateLock", json(p, Component.translatable("hearthbound.diplo.mediate_need")));
                diplo.add(e);
            }
        }
        t.put("diplomacy", diplo);
        t.putInt("giftCost", HBConfig.GIFT_COST.get());
        t.putInt("mediationCost", HBConfig.MEDIATION_COST.get());

        // hiring
        CompoundTag hire = new CompoundTag();
        hire.putInt("cost", hireCost(p, v));
        hire.putInt("days", HBConfig.HIRE_DAYS.get());
        hire.putInt("have", companions(p).size());
        hire.putInt("max", Rpg.maxCompanions(p));
        Component hl = hireLock(p, v, rank);
        if (hl != null) hire.putString("lock", json(p, hl));
        t.put("hire", hire);
        return t;
    }

    // ================================================================== trading

    public static void trade(ServerPlayer p, Village v, int index, int times) {
        if (index >= Projects.HALL_INDEX) {
            hallTrade(p, v, index, times);
            return;
        }
        Culture c = v.culture();
        if (c == null || index < 0 || index >= c.trades.size()) return;
        times = Math.max(1, Math.min(64, times));
        Culture.Trade tr = c.trades.get(index);
        Rank rank = Rpg.data(p).rank(v.id);
        boolean needsBuilding = !tr.building().isEmpty() && HBData.project(tr.building()) != null;
        boolean unlocked = needsBuilding ? v.projectLevel(tr.building()) >= tr.buildingLevel() : (v.has(tr.role()) || tr.role() == Role.VILLAGER);
        if (needsBuilding && !HBConfig.CENTRAL_MARKET.get() && !inShop(p, v, tr.building())) {
            p.displayClientMessage(Component.translatable("hearthbound.shop.buy_at", Component.translatable(HBData.project(tr.building()).name)).withColor(0xE06A5A), true);
            deny(p);
            return;
        }
        if (!Compat.ageReached(p, HBConfig.ASC_AGE_TRADE.get()) || !Compat.ageReached(p, tr.age())
                || !rank.atLeast(tr.rank()) || tr.tier() > v.tier() || !unlocked) {
            deny(p);
            return;
        }
        int limit = HBConfig.DAILY_STOCK.get();
        int done = 0;
        int coins = 0;
        for (int i = 0; i < times; i++) {
            if (limit > 0 && v.soldToday.getOrDefault(index, 0) >= limit) break;
            if (tr.sell()) {
                int price = citizenPrice(p, v, Rpg.buyPrice(p, tr.price(), rank));
                if (!Wallet.pay(p, price)) break;
                ItemStack st = new ItemStack(tr.item(), tr.count());
                if (!p.getInventory().add(st)) p.drop(st, false);
                coins += price;
            } else {
                String id = itemId(tr.item());
                if (Contracts.countItems(p, id) < tr.count()) break;
                Contracts.takeItems(p, id, tr.count());
                int price = Rpg.sellPrice(p, tr.price(), rank);
                Wallet.give(p, price);
                Rpg.data(p).coinsEarned += price;
                coins += price;
                v.add(Resource.GOODS, 1);
            }
            v.soldToday.merge(index, 1, Integer::sum);
            done++;
        }
        if (done == 0) {
            deny(p);
            return;
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8f, 1f);
        int rep = (int) Math.floor(coins * HBConfig.TRADE_REP_PER_COIN.get());
        if (rep > 0) Reputation.add(p, v, rep, true, true);
        int xp = (int) Math.floor(coins * HBConfig.XP_PER_COIN_TRADED.get());
        if (xp > 0) Rpg.addXp(p, xp, false);
        Compat.trigger(p, "trade", tr, coins);
        VillageData.get(p.server).setDirty();
        refresh(p, v);
    }

    static int citizenPrice(ServerPlayer p, Village v, int price) {
        return Math.max(1, (int) Math.round(price * Citizenship.buyFactor(p, v)));
    }

    static void hallTrade(ServerPlayer p, Village v, int index, int times) {
        Projects.HallEntry he = Projects.hallTrade(index);
        if (he == null || !Compat.ageReached(p, HBConfig.ASC_AGE_TRADE.get()) || Projects.hallLock(p, v, he) != null) {
            deny(p);
            return;
        }
        if (!HBConfig.CENTRAL_MARKET.get() && !inShop(p, v, he.def().id.toString())) {
            p.displayClientMessage(Component.translatable("hearthbound.shop.buy_at", Component.translatable(he.def().name)).withColor(0xE06A5A), true);
            deny(p);
            return;
        }
        ProjectDef.HallTrade tr = he.trade();
        Rank rank = Rpg.data(p).rank(v.id);
        times = Math.max(1, Math.min(64, times));
        int limit = HBConfig.DAILY_STOCK.get();
        int done = 0, coins = 0;
        for (int i = 0; i < times; i++) {
            if (limit > 0 && v.soldToday.getOrDefault(index, 0) >= limit) break;
            if (tr.sell()) {
                int price = citizenPrice(p, v, Rpg.buyPrice(p, tr.price(), rank));
                if (!Wallet.pay(p, price)) break;
                ItemStack st = Projects.stack(p.registryAccess(), tr);
                if (!p.getInventory().add(st)) p.drop(st, false);
                coins += price;
            } else {
                String id = itemId(tr.item());
                if (Contracts.countItems(p, id) < tr.count()) break;
                Contracts.takeItems(p, id, tr.count());
                int price = Rpg.sellPrice(p, tr.price(), rank);
                Wallet.give(p, price);
                Rpg.data(p).coinsEarned += price;
                coins += price;
                v.add(Resource.GOODS, 1);
            }
            v.soldToday.merge(index, 1, Integer::sum);
            done++;
        }
        if (done == 0) {
            deny(p);
            return;
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8f, 1f);
        int rep = (int) Math.floor(coins * HBConfig.TRADE_REP_PER_COIN.get());
        if (rep > 0) Reputation.add(p, v, rep, true, true);
        int xp = (int) Math.floor(coins * HBConfig.XP_PER_COIN_TRADED.get());
        if (xp > 0) Rpg.addXp(p, xp, false);
        Compat.trigger(p, "trade", tr, coins);
        VillageData.get(p.server).setDirty();
        refresh(p, v);
    }

    // ================================================================== banner

    public static void flagSet(ServerPlayer p, Village v) {
        if (!p.getUUID().equals(v.lord) || !Flags.setFromItem(v, p.getMainHandItem())) {
            p.displayClientMessage(Component.translatable("hearthbound.flag.hold_banner").withColor(0xE06A5A), true);
            return;
        }
        applyFlag(p, v);
        Net.notify(p, Component.translatable("hearthbound.flag.changed", v.name), 0xFFE0B25A);
    }

    public static void flagReset(ServerPlayer p, Village v) {
        if (!p.getUUID().equals(v.lord)) return;
        Flags.generate(v);
        applyFlag(p, v);
    }

    private static void applyFlag(ServerPlayer p, Village v) {
        ServerLevel level = p.server.getLevel(v.dimension);
        if (level != null && HBConfig.FLAGS.get()) Flags.placeInWorld(level, v);
        VillageData.get(p.server).setDirty();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) if (v.citizens.containsKey(o.getUUID())) Citizenship.refreshNames(o);
        refresh(p, v);
    }

    public static void flagCopy(ServerPlayer p, Village v) {
        if (!Citizenship.isCitizen(p, v) && !p.getUUID().equals(v.lord)) {
            deny(p);
            return;
        }
        if (!Wallet.pay(p, HBConfig.FLAG_COPY_COST.get())) {
            deny(p);
            return;
        }
        ItemStack s = Flags.item(v, p.registryAccess());
        if (!p.getInventory().add(s)) p.drop(s, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
        refresh(p, v);
    }

    // ================================================================== blessings

    static int blessingPrice(ServerPlayer p, Rank rank) {
        double base = HBConfig.BLESSING_COST.get();
        if (Rpg.data(p).clazz == PlayerClass.CLERIC) base *= 1 - HBConfig.PERK_CLERIC_BLESSING.get();
        return Rpg.buyPrice(p, (int) Math.max(1, Math.round(base)), rank);
    }

    static int blessingMinutes(ServerPlayer p) {
        double m = HBConfig.BLESSING_MINUTES.get();
        if (Rpg.data(p).clazz == PlayerClass.CLERIC) m *= 1 + HBConfig.PERK_CLERIC_BLESSING.get();
        return (int) Math.round(m);
    }

    public static void bless(ServerPlayer p, Village v, int index) {
        Culture c = v.culture();
        if (c == null || !HBConfig.BLESSINGS_ENABLED.get() || index < 0 || index >= c.blessings.size()) return;
        Culture.Blessing b = c.blessings.get(index);
        Rank rank = Rpg.data(p).rank(v.id);
        if (!v.has(Role.PRIEST) || !rank.atLeast(b.rank()) || !Compat.ageReached(p, b.age())) {
            deny(p);
            return;
        }
        int price = blessingPrice(p, rank);
        if (!Wallet.pay(p, price)) {
            deny(p);
            return;
        }
        p.addEffect(new MobEffectInstance(b.effect(), blessingMinutes(p) * 60 * 20, b.amplifier()));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.3f);
        Reputation.add(p, v, 2, true, false);
        refresh(p, v);
    }

    // ================================================================== donations

    /** Points each item is worth when donated, by resource. */
    static int pointsOf(ItemStack s, Resource r) {
        if (s.isEmpty() || s.getItem() instanceof com.hearthbound.item.CoinItem) return 0;
        Item i = s.getItem();
        switch (r) {
            case WOOD -> {
                if (s.is(ItemTags.LOGS)) return 4;
                if (s.is(ItemTags.PLANKS)) return 1;
                if (i == Items.STICK || i == Items.BAMBOO) return 0;
                return 0;
            }
            case STONE -> {
                if (s.is(ItemTags.STONE_BRICKS) || i == Items.BRICKS || i == Items.MUD_BRICKS) return 2;
                if (s.is(ItemTags.STONE_CRAFTING_MATERIALS) || s.is(ItemTags.STONE_TOOL_MATERIALS) || i == Items.STONE || i == Items.SANDSTONE
                        || i == Items.CLAY_BALL || i == Items.BRICK || i == Items.DEEPSLATE || i == Items.TUFF || i == Items.GRANITE
                        || i == Items.DIORITE || i == Items.ANDESITE) return 1;
                return 0;
            }
            case FOOD -> {
                FoodProperties f = s.getFoodProperties(null);
                if (f != null) return Math.max(1, f.nutrition() / 2);
                if (i == Items.WHEAT || i == Items.SUGAR_CANE || i == Items.PUMPKIN || i == Items.MELON_SLICE) return 1;
                if (i == Items.HAY_BLOCK) return 9;
                return 0;
            }
            case GOODS -> {
                if (i == Items.IRON_INGOT || i == Items.GOLD_INGOT) return 4;
                if (i == Items.COPPER_INGOT || i == Items.LEATHER || i == Items.BOOK || i == Items.GLASS) return 2;
                if (i == Items.EMERALD || i == Items.DIAMOND) return 10;
                if (s.is(ItemTags.WOOL) || s.is(ItemTags.COALS) || i == Items.STRING || i == Items.PAPER || i == Items.IRON_NUGGET || i == Items.GOLD_NUGGET) return 1;
                return 0;
            }
            default -> {
                return 0;
            }
        }
    }

    /** Total donation value in the inventory; with {@code take} it also removes the items. */
    static Map<Resource, Integer> donationValue(ServerPlayer p, Resource only, boolean take) {
        Map<Resource, Integer> out = new EnumMap<>(Resource.class);
        Inventory inv = p.getInventory();
        double mult = Rpg.donationMultiplier(p);
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack s = inv.getItem(i);
            for (Resource r : Resource.values()) {
                if (only != null && r != only) continue;
                int pts = pointsOf(s, r);
                if (pts <= 0) continue;
                out.merge(r, (int) Math.round(pts * s.getCount() * mult), Integer::sum);
                if (take) inv.setItem(i, ItemStack.EMPTY);
                break;
            }
        }
        return out;
    }

    public static void donate(ServerPlayer p, Village v, Resource r) {
        if (r == null) return;
        Map<Resource, Integer> got = donationValue(p, r, true);
        int pts = got.getOrDefault(r, 0);
        if (pts <= 0) {
            deny(p);
            return;
        }
        v.add(r, pts);
        PlayerData d = Rpg.data(p);
        d.donated += pts;
        Reputation.add(p, v, (int) Math.max(1, Math.round(pts * HBConfig.DONATE_REP_PER_POINT.get())), true, true);
        Rpg.addXp(p, (int) Math.round(pts * HBConfig.XP_PER_DONATION_POINT.get()), true);
        Contracts.onDonate(p, v, r, pts);
        Compat.trigger(p, "donate", r, pts);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8f, 1f);
        VillageData.get(p.server).setDirty();
        refresh(p, v);
    }

    // ================================================================== hiring

    public static List<SettlerEntity> companions(ServerPlayer p) {
        List<SettlerEntity> out = new ArrayList<>();
        for (ServerLevel l : p.server.getAllLevels()) {
            for (var e : l.getAllEntities()) {
                if (e instanceof SettlerEntity s && s.isCompanion() && p.getUUID().equals(s.owner()) && s.isAlive()) out.add(s);
            }
        }
        return out;
    }

    static int hireCost(ServerPlayer p, Village v) {
        double c = HBConfig.HIRE_BASE_COST.get() + HBConfig.HIRE_COST_PER_TIER.get() * v.tier();
        if (Rpg.data(p).clazz == PlayerClass.NOBLE) c *= 1 - HBConfig.PERK_NOBLE_HIRE.get();
        return Rpg.buyPrice(p, (int) Math.max(1, Math.round(c)), Rpg.data(p).rank(v.id));
    }

    static Component hireLock(ServerPlayer p, Village v, Rank rank) {
        if (!HBConfig.COMPANIONS_ENABLED.get()) return Component.translatable("hearthbound.gate.disabled");
        if (!v.has(Role.INNKEEPER)) return Component.translatable("hearthbound.gate.role", Component.translatable(Role.INNKEEPER.key()));
        if (!Compat.ageReached(p, HBConfig.ASC_AGE_COMPANIONS.get())) return Component.translatable("hearthbound.gate.age", Compat.ageName(p, HBConfig.ASC_AGE_COMPANIONS.get()));
        Rank need = Rank.byId(HBConfig.HIRE_MIN_RANK.get());
        if (!rank.atLeast(need)) return Component.translatable("hearthbound.gate.rank", need.title());
        if (companions(p).size() >= Rpg.maxCompanions(p)) return Component.translatable("hearthbound.gate.companions", Rpg.maxCompanions(p));
        return null;
    }

    public static void hire(ServerPlayer p, Village v) {
        Rank rank = Rpg.data(p).rank(v.id);
        if (hireLock(p, v, rank) != null) {
            deny(p);
            return;
        }
        int cost = hireCost(p, v);
        if (!Wallet.pay(p, cost)) {
            deny(p);
            return;
        }
        Culture c = v.culture();
        SettlerEntity e = ModRegistry.SETTLER.get().create(p.serverLevel());
        if (e == null) return;
        boolean female = p.getRandom().nextBoolean();
        e.setup(c, Role.GUARD, c == null ? "Sellsword" : c.randomName(p.getRandom(), female), female, p.getRandom().nextInt(2));
        e.makeCompanion(p, p.level().getGameTime() + HBConfig.HIRE_DAYS.get() * 24000L);
        e.moveTo(p.getX() + 1, p.getY(), p.getZ() + 1, p.getYRot(), 0);
        p.serverLevel().addFreshEntity(e);
        Rpg.data(p).hires++;
        Compat.trigger(p, "hire", e, 1);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1f, 1f);
        Net.banner(p, Component.translatable("hearthbound.hire.done", e.getCustomName()), Component.translatable("hearthbound.hire.days", HBConfig.HIRE_DAYS.get()), 0xFF6C8FD6);
        refresh(p, v);
    }

    // ================================================================== diplomacy

    public static void gift(ServerPlayer p, Village v, Village other) {
        if (other == null || other == v || !HBConfig.DIPLOMACY.get()) return;
        if (!Wallet.pay(p, HBConfig.GIFT_COST.get())) {
            deny(p);
            return;
        }
        VillageData vd = VillageData.get(p.server);
        Diplomacy.gift(p, vd, v, other);
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1.1f);
        Net.notify(p, Component.translatable("hearthbound.diplo.gift_done", v.name, other.name), 0xFF6CD68A);
        refresh(p, v);
    }

    public static void mediate(ServerPlayer p, Village v, Village other) {
        if (other == null || !HBConfig.DIPLOMACY.get()) return;
        VillageData vd = VillageData.get(p.server);
        Diplomacy.Relation rel = Diplomacy.relation(vd, v, other);
        PlayerData d = Rpg.data(p);
        if (!rel.atWar() || !d.rank(v.id).atLeast(Rank.ALLY) || !d.rank(other.id).atLeast(Rank.FRIEND)) {
            deny(p);
            return;
        }
        if (!Wallet.pay(p, HBConfig.MEDIATION_COST.get())) {
            deny(p);
            return;
        }
        Diplomacy.makePeace(p.server, vd, v, other, p.level().getDayTime() / 24000L, p);
        Reputation.add(p, v, 20, true, true);
        Reputation.add(p, other, 20, true, true);
        Rpg.addXp(p, 120, true);
        refresh(p, v);
    }

    // ================================================================== lordship

    public static boolean canBeLord(ServerPlayer p) {
        return Compat.ageReached(p, HBConfig.ASC_AGE_LORDSHIP.get());
    }

    public static void claimLordship(ServerPlayer p, Village v) {
        if (v.lord != null || Rpg.data(p).rank(v.id) != Rank.HERO || !canBeLord(p)) {
            deny(p);
            return;
        }
        v.lord = p.getUUID();
        v.lordName = p.getGameProfile().getName();
        VillageData.get(p.server).setDirty();
        Net.banner(p, Component.translatable("hearthbound.lordship.claimed"), Component.literal(v.name), 0xFFFFD24A);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        refresh(p, v);
    }

    public static void choosePlan(ServerPlayer p, Village v, ResourceLocation id) {
        if (!p.getUUID().equals(v.lord) || !HBConfig.LORD_CHOOSES_BUILDINGS.get()) return;
        v.lordChoice = id != null && HBData.building(id) != null ? id : null;
        VillageData.get(p.server).setDirty();
        refresh(p, v);
    }

    public static void collectTribute(ServerPlayer p, Village v) {
        if (!p.getUUID().equals(v.lord) || v.treasury <= 0) return;
        Wallet.give(p, v.treasury);
        Rpg.data(p).coinsEarned += v.treasury;
        v.treasury = 0;
        p.level().playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 1.4f);
        VillageData.get(p.server).setDirty();
        refresh(p, v);
    }

    // ================================================================== charter

    public static boolean foundByCharter(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        if (!HBConfig.CHARTER_ENABLED.get()) {
            p.displayClientMessage(Component.translatable("hearthbound.gate.disabled"), true);
            return false;
        }
        if (!Compat.ageReached(p, HBConfig.ASC_AGE_CHARTER.get())) {
            p.displayClientMessage(Component.translatable("hearthbound.gate.age", Compat.ageName(p, HBConfig.ASC_AGE_CHARTER.get())), true);
            return false;
        }
        VillageData data = VillageData.get(p.server);
        if (data.nearest(level, pos, HBConfig.CHARTER_SPACING.get()) != null) {
            p.displayClientMessage(Component.translatable("hearthbound.charter.too_close", HBConfig.CHARTER_SPACING.get()), true);
            return false;
        }
        int y = Terraform.groundTop(level, pos.getX(), pos.getZ()) + 1;
        BlockPos center = new BlockPos(pos.getX(), y, pos.getZ());
        if (!VillageManager.flatEnough(level, center, 5, HBConfig.MAX_SLOPE.get() + 1)) {
            p.displayClientMessage(Component.translatable("hearthbound.charter.not_flat"), true);
            return false;
        }
        Culture c = VillageManager.pickCulture(level, center, level.random);
        if (c == null) {
            List<Culture> all = new ArrayList<>(HBData.cultures());
            if (all.isEmpty()) return false;
            c = all.get(level.random.nextInt(all.size()));
        }
        Village v = VillageManager.create(level, c, center, false, p);
        PlayerData d = Rpg.data(p);
        d.reputation.put(v.id, Math.max(d.rep(v.id), Rank.HERO.threshold()));
        d.discovered.add(v.id);
        Net.banner(p, Component.translatable("hearthbound.charter.founded", v.name), c.title(), c.color);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.8f);
        Rpg.sync(p);
        return true;
    }

    // ================================================================== misc

    public static Village byId(ServerPlayer p, UUID id) {
        return VillageData.get(p.server).get(id);
    }

    /** The player must stand close to the village to act on it (anti-cheat for remote packets). */
    public static boolean near(ServerPlayer p, Village v) {
        if (!p.level().dimension().equals(v.dimension)) return false;
        double r = v.radius() + 24;
        return p.blockPosition().distSqr(v.center) < r * r;
    }

    public static void deny(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.6f, 1f);
    }
}

