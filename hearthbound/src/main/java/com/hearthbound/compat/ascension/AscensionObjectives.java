package com.hearthbound.compat.ascension;

import com.ascension.api.AscensionAPI;
import com.ascension.api.Objective;
import com.ascension.api.ObjectiveContext;
import com.google.gson.JsonObject;
import com.hearthbound.Hearthbound;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.rpg.Contract;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rank;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.village.Village;
import com.hearthbound.village.VillageData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Objective types Hearthbound adds to Ascension quests:
 * <ul>
 *   <li>{@code hearthbound:level} – reach character level {@code count}</li>
 *   <li>{@code hearthbound:reputation} – be at least {@code rank} with {@code count} villages (optional {@code culture})</li>
 *   <li>{@code hearthbound:discover} – discover {@code count} villages</li>
 *   <li>{@code hearthbound:village_tier} – discover a village of tier {@code count} or more</li>
 *   <li>{@code hearthbound:lord} – rule a village</li>
 *   <li>{@code hearthbound:contracts} – complete contracts (optional {@code contract_type}, {@code culture})</li>
 *   <li>{@code hearthbound:donate} – donate resource points to villages</li>
 *   <li>{@code hearthbound:trade} – spend coins with settlers</li>
 *   <li>{@code hearthbound:defend} – slay raiders inside villages</li>
 *   <li>{@code hearthbound:hire} – hire companions</li>
 *   <li>{@code hearthbound:friends} – befriend {@code count} settlers (optional {@code bond_level} 1-4, default 2 = friend)</li>
 *   <li>{@code hearthbound:stories} – complete the personal story of {@code count} settlers</li>
 *   <li>{@code hearthbound:projects} – finish {@code count} building projects</li>
 *   <li>{@code hearthbound:citizen} – become a citizen of a village</li>
 * </ul>
 */
public final class AscensionObjectives {
    private AscensionObjectives() {}

    public static void register() {
        AscensionAPI.registerObjective(Hearthbound.id("level"), Level::new);
        AscensionAPI.registerObjective(Hearthbound.id("reputation"), Reputation::new);
        AscensionAPI.registerObjective(Hearthbound.id("discover"), Discover::new);
        AscensionAPI.registerObjective(Hearthbound.id("village_tier"), VillageTier::new);
        AscensionAPI.registerObjective(Hearthbound.id("lord"), Lord::new);
        AscensionAPI.registerObjective(Hearthbound.id("contracts"), Contracts::new);
        AscensionAPI.registerObjective(Hearthbound.id("donate"), (t, c) -> new Counter(t, c, Items.CHEST));
        AscensionAPI.registerObjective(Hearthbound.id("trade"), (t, c) -> new Counter(t, c, Items.EMERALD));
        AscensionAPI.registerObjective(Hearthbound.id("defend"), (t, c) -> new Counter(t, c, Items.SHIELD));
        AscensionAPI.registerObjective(Hearthbound.id("hire"), (t, c) -> new Counter(t, c, Items.IRON_SWORD));
        AscensionAPI.registerObjective(Hearthbound.id("build"), (t, c) -> new Counter(t, c, Items.BRICKS));
        AscensionAPI.registerObjective(Hearthbound.id("friends"), Friends::new);
        AscensionAPI.registerObjective(Hearthbound.id("stories"), Stories::new);
        AscensionAPI.registerObjective(Hearthbound.id("projects"), Projects::new);
        AscensionAPI.registerObjective(Hearthbound.id("citizen"), Citizen::new);
    }

    static String str(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    static String key(ResourceLocation type) {
        return "hearthbound.objective." + type.getPath();
    }

    static ItemStack coin() {
        return new ItemStack(ModRegistry.GOLD_COIN.get());
    }

    // ------------------------------------------------------------------ polled

    static final class Friends extends Objective {
        private final int level;

        Friends(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
            int l;
            try {
                l = Integer.parseInt(str(ctx.json(), "bond_level", "2"));
            } catch (NumberFormatException e) {
                l = 2;
            }
            this.level = Math.max(1, Math.min(4, l));
        }

        @Override
        public Mode mode() {
            return Mode.POLLED;
        }

        @Override
        public int poll(ServerPlayer p) {
            int need = com.hearthbound.village.Persons.levelAt(level);
            int n = 0;
            for (PlayerData.Bond b : Rpg.data(p).bonds.values()) if (b.points >= need) n++;
            return n;
        }

        @Override
        protected Component describe() {
            return Component.translatable(key(type), target, Component.translatable("hearthbound.bond.level." + level));
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.POPPY));
        }
    }

    static final class Projects extends Objective {
        Projects(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
        }

        @Override
        public Mode mode() {
            return Mode.POLLED;
        }

        @Override
        public int poll(ServerPlayer p) {
            return Rpg.data(p).projectsDone;
        }

        @Override
        protected Component describe() {
            return Component.translatable(key(type), target);
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.BRICKS));
        }
    }

    static final class Citizen extends Objective {
        Citizen(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
        }

        @Override
        public Mode mode() {
            return Mode.POLLED;
        }

        @Override
        public boolean showsCount() {
            return false;
        }

        @Override
        public int poll(ServerPlayer p) {
            return Rpg.data(p).citizen != null ? 1 : 0;
        }

        @Override
        protected Component describe() {
            return Component.translatable(key(type));
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.WHITE_BANNER));
        }
    }

    static final class Stories extends Objective {
        Stories(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
        }

        @Override
        public Mode mode() {
            return Mode.POLLED;
        }

        @Override
        public int poll(ServerPlayer p) {
            return Rpg.data(p).storiesDone;
        }

        @Override
        protected Component describe() {
            return Component.translatable(key(type), target);
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.WRITABLE_BOOK));
        }
    }

    static final class Level extends Objective {
        Level(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
        }

        @Override
        public Mode mode() {
            return Mode.POLLED;
        }

        @Override
        public int poll(ServerPlayer p) {
            return Rpg.data(p).level;
        }

        @Override
        public boolean showsCount() {
            return false;
        }

        @Override
        protected Component describe() {
            return Component.translatable(key(type), target);
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.EXPERIENCE_BOTTLE));
        }
    }

    static final class Reputation extends Objective {
        private final Rank rank;
        private final String culture;

        Reputation(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
            this.rank = Rank.byId(str(ctx.json(), "rank", "friend"));
            this.culture = str(ctx.json(), "culture", "");
        }

        @Override
        public Mode mode() {
            return Mode.POLLED;
        }

        @Override
        public int poll(ServerPlayer p) {
            PlayerData d = Rpg.data(p);
            VillageData vd = VillageData.get(p.server);
            int n = 0;
            for (var e : d.reputation.entrySet()) {
                if (!Rank.of(e.getValue()).atLeast(rank)) continue;
                if (!culture.isEmpty()) {
                    Village v = vd.get(e.getKey());
                    if (v == null || v.culture == null || !v.culture.toString().equals(culture.contains(":") ? culture : "hearthbound:" + culture)) continue;
                }
                n++;
            }
            return n;
        }

        @Override
        protected Component describe() {
            if (!culture.isEmpty()) {
                String c = culture.contains(":") ? culture.substring(culture.indexOf(':') + 1) : culture;
                return Component.translatable(key(type) + ".culture", target, rank.title(), Component.translatable("hearthbound.culture." + c));
            }
            return Component.translatable(key(type), target, rank.title());
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.EMERALD));
        }
    }

    static final class Discover extends Objective {
        Discover(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
        }

        @Override
        public Mode mode() {
            return Mode.POLLED;
        }

        @Override
        public int poll(ServerPlayer p) {
            return Rpg.data(p).discovered.size();
        }

        @Override
        protected Component describe() {
            return Component.translatable(key(type), target);
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.FILLED_MAP));
        }
    }

    static final class VillageTier extends Objective {
        VillageTier(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
        }

        @Override
        public Mode mode() {
            return Mode.POLLED;
        }

        @Override
        public boolean showsCount() {
            return false;
        }

        @Override
        public int poll(ServerPlayer p) {
            VillageData vd = VillageData.get(p.server);
            int best = 0;
            for (UUID id : Rpg.data(p).discovered) {
                Village v = vd.get(id);
                if (v != null) best = Math.max(best, v.tier());
            }
            return best;
        }

        @Override
        protected Component describe() {
            return Component.translatable(key(type), Component.translatable("hearthbound.tier." + Math.min(4, target)));
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.BELL));
        }
    }

    static final class Lord extends Objective {
        Lord(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
        }

        @Override
        public Mode mode() {
            return Mode.POLLED;
        }

        @Override
        public boolean showsCount() {
            return false;
        }

        @Override
        public int poll(ServerPlayer p) {
            int n = 0;
            for (Village v : VillageData.get(p.server).all()) if (p.getUUID().equals(v.lord)) n++;
            return n;
        }

        @Override
        protected Component describe() {
            return Component.translatable(key(type));
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.GOLDEN_HELMET));
        }
    }

    // ------------------------------------------------------------------ counters

    static final class Contracts extends Objective {
        private final String contractType;
        private final String culture;

        Contracts(ResourceLocation type, ObjectiveContext ctx) {
            super(type, ctx);
            this.contractType = str(ctx.json(), "contract_type", "");
            this.culture = str(ctx.json(), "culture", "");
        }

        @Override
        public Mode mode() {
            return Mode.COUNTER;
        }

        @Override
        public int onTrigger(ServerPlayer p, Object subject, int amount) {
            if (!(subject instanceof Contract c)) return 0;
            if (!contractType.isEmpty() && !c.type.name().equalsIgnoreCase(contractType)) return 0;
            if (!culture.isEmpty()) {
                Village v = VillageData.get(p.server).get(c.village);
                String want = culture.contains(":") ? culture : "hearthbound:" + culture;
                if (v == null || v.culture == null || !v.culture.toString().equals(want)) return 0;
            }
            return amount;
        }

        @Override
        protected Component describe() {
            if (!contractType.isEmpty()) {
                return Component.translatable(key(type) + ".typed", target, Component.translatable("hearthbound.contract.type." + contractType.toLowerCase(java.util.Locale.ROOT)));
            }
            return Component.translatable(key(type), target);
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(new ItemStack(Items.WRITABLE_BOOK));
        }
    }

    static final class Counter extends Objective {
        private final ItemStack icon;

        Counter(ResourceLocation type, ObjectiveContext ctx, net.minecraft.world.item.Item icon) {
            super(type, ctx);
            this.icon = new ItemStack(icon);
        }

        @Override
        public Mode mode() {
            return Mode.COUNTER;
        }

        @Override
        public int onTrigger(ServerPlayer p, Object subject, int amount) {
            return amount;
        }

        @Override
        protected Component describe() {
            return Component.translatable(key(type), target);
        }

        @Override
        protected List<ItemStack> defaultIcons() {
            return List.of(icon);
        }
    }
}
