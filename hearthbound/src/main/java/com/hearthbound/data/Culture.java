package com.hearthbound.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthbound.rpg.Rank;
import com.hearthbound.village.Role;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A culture: how a people builds, names itself, trades and looks.
 * Loaded from {@code data/<ns>/hearthbound/cultures/<id>.json}. See the README for every field.
 */
public final class Culture {
    public enum RoofStyle { GABLE, STEEP, FLAT, TIERED }

    public final ResourceLocation id;
    public final String name;
    public final String description;
    public final String people;
    public final int color;
    public final Item icon;
    public final int weight;
    public final List<Matcher<Biome>> biomes;
    public final Map<String, BlockState> palette = new HashMap<>();
    public final RoofStyle roof;
    public final float modelScale;
    public final boolean ears;
    public final String skin;
    public final List<String> villagePrefix;
    public final List<String> villageSuffix;
    public final List<String> maleNames;
    public final List<String> femaleNames;
    public final List<String> surnames;
    public final List<ResourceLocation> plan;
    public final List<Trade> trades = new ArrayList<>();
    public final List<Blessing> blessings = new ArrayList<>();
    public final boolean natural;
    /** building id path → schematic ids used for it. */
    public final Map<String, List<String>> structures = new HashMap<>();
    public final double structureChance;
    /** Other culture id path → base relation (−100..100). */
    public final Map<String, Integer> relations = new HashMap<>();
    /** role id (or "*") → texture paths ("default" = the generated skin). */
    public final Map<String, List<String>> skins = new HashMap<>();

    public Culture(ResourceLocation id, JsonObject j) {
        this.id = id;
        this.name = Json.str(j, "name", "hearthbound.culture." + id.getPath());
        this.description = Json.str(j, "description", "hearthbound.culture." + id.getPath() + ".desc");
        this.people = Json.str(j, "people", "hearthbound.culture." + id.getPath() + ".people");
        this.color = Json.color(j, "color", 0xFFE0B25A);
        this.icon = Json.item(j, "icon", Items.BELL);
        this.weight = Math.max(0, Json.integer(j, "weight", 10));
        this.natural = Json.bool(j, "natural", true);
        this.biomes = new ArrayList<>();
        for (String s : Json.strings(j, "biomes")) biomes.add(Matcher.of(s, Registries.BIOME));

        JsonObject pal = Json.obj(j, "palette");
        for (var e : pal.entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                BlockState st = Json.state(e.getValue().getAsString(), null);
                if (st != null) palette.put(e.getKey(), st);
            }
        }
        RoofStyle rs;
        try {
            rs = RoofStyle.valueOf(Json.str(j, "roof", "gable").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            rs = RoofStyle.GABLE;
        }
        this.roof = rs;

        JsonObject model = Json.obj(j, "model");
        this.modelScale = (float) Json.dbl(model, "scale", 0.9375);
        this.ears = Json.bool(model, "ears", false);
        this.skin = Json.str(model, "skin", id.getPath());

        JsonObject names = Json.obj(j, "names");
        this.villagePrefix = Json.strings(names, "village_prefix");
        this.villageSuffix = Json.strings(names, "village_suffix");
        this.maleNames = Json.strings(names, "male");
        this.femaleNames = Json.strings(names, "female");
        this.surnames = Json.strings(names, "family");

        this.plan = new ArrayList<>();
        for (String s : Json.strings(j, "plan")) {
            ResourceLocation rl = s.contains(":") ? ResourceLocation.tryParse(s) : ResourceLocation.fromNamespaceAndPath(id.getNamespace(), s);
            if (rl != null) plan.add(rl);
        }

        for (JsonElement e : Json.arr(j, "trades")) {
            if (e.isJsonObject()) {
                Trade t = Trade.parse(e.getAsJsonObject());
                if (t != null) trades.add(t);
            }
        }
        JsonObject st = Json.obj(j, "structures");
        for (var e : st.entrySet()) structures.put(e.getKey(), Json.strings(st, e.getKey()));
        this.structureChance = Json.dbl(j, "structure_chance", 1.0);
        relations.putAll(Json.intMap(j, "relations"));
        JsonObject sk = Json.obj(j, "skins");
        for (var e : sk.entrySet()) skins.put(e.getKey(), Json.strings(sk, e.getKey()));
        for (JsonElement e : Json.arr(j, "blessings")) {
            if (e.isJsonObject()) {
                Blessing b = Blessing.parse(e.getAsJsonObject());
                if (b != null) blessings.add(b);
            }
        }
    }

    /** Picks a texture for a settler of this role ("" = the generated default). */
    public String randomSkin(RandomSource r, com.hearthbound.village.Role role) {
        List<String> pool = new ArrayList<>(skins.getOrDefault(role.id(), List.of()));
        pool.addAll(skins.getOrDefault("*", List.of()));
        if (pool.isEmpty()) return "";
        String s = pool.get(r.nextInt(pool.size()));
        return "default".equals(s) ? "" : s;
    }

    public Component title() {
        return Component.translatable(name);
    }

    public BlockState block(String key, BlockState def) {
        return palette.getOrDefault(key, def);
    }

    public BlockState block(String key) {
        return palette.getOrDefault(key, Blocks.OAK_PLANKS.defaultBlockState());
    }

    public boolean likes(Holder<Biome> biome) {
        return biomes.isEmpty() || Matcher.any(biomes, biome);
    }

    public String randomVillageName(RandomSource r) {
        String a = villagePrefix.isEmpty() ? "New" : villagePrefix.get(r.nextInt(villagePrefix.size()));
        String b = villageSuffix.isEmpty() ? "hold" : villageSuffix.get(r.nextInt(villageSuffix.size()));
        return a + b;
    }

    public String randomName(RandomSource r, boolean female) {
        List<String> firsts = female ? femaleNames : maleNames;
        if (firsts.isEmpty()) firsts = female ? maleNames : femaleNames;
        String first = firsts.isEmpty() ? "Settler" : firsts.get(r.nextInt(firsts.size()));
        if (surnames.isEmpty()) return first;
        return first + " " + surnames.get(r.nextInt(surnames.size()));
    }

    /** One trade line. {@code sell}: the village sells to the player; otherwise the village buys. */
    /**
     * {@code building}: the player building (project id) that unlocks this trade, at {@code buildingLevel};
     * empty for trades that only need a settler of {@code role}.
     */
    public record Trade(Item item, int count, int price, boolean sell, Role role, Rank rank, int tier, String age, String building, int buildingLevel) {
        static Trade parse(JsonObject o) {
            Item it = Json.item(o, "item", null);
            if (it == null || it == Items.AIR) return null;
            return new Trade(it,
                    Math.max(1, Json.integer(o, "count", 1)),
                    Math.max(1, Json.integer(o, "price", 1)),
                    !"buy".equalsIgnoreCase(Json.str(o, "type", "sell")),
                    Role.byId(Json.str(o, "role", "merchant")),
                    Rank.byId(Json.str(o, "rank", "stranger")),
                    Json.integer(o, "tier", 0),
                    Json.str(o, "age", ""),
                    Json.str(o, "building", ""),
                    Math.max(1, Json.integer(o, "building_level", 1)));
        }

        public ResourceLocation itemId() {
            return BuiltInRegistries.ITEM.getKey(item);
        }
    }

    /** A temporary effect a priest can grant for coins. */
    public record Blessing(Holder<MobEffect> effect, int amplifier, String name, Rank rank, String age) {
        static Blessing parse(JsonObject o) {
            ResourceLocation rl = ResourceLocation.tryParse(Json.str(o, "effect", ""));
            if (rl == null) return null;
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(rl);
            if (holder.isEmpty()) return null;
            return new Blessing(holder.get(), Math.max(0, Json.integer(o, "amplifier", 0)),
                    Json.str(o, "name", "hearthbound.blessing." + rl.getPath()),
                    Rank.byId(Json.str(o, "rank", "acquaintance")),
                    Json.str(o, "age", ""));
        }
    }
}
