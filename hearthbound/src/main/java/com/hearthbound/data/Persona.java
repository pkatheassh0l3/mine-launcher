package com.hearthbound.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthbound.village.Role;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The life story of a settler: who they are, what they like and the chain of personal
 * quests that tells their story. Loaded from {@code data/<ns>/hearthbound/personas/<id>.json}.
 * Texts are translation keys built from the id ({@code hearthbound.persona.<path>.*}), or
 * literal overrides in the file.
 * <pre>{@code
 * { "roles": ["farmer", "villager"], "gender": "male", "cultures": [], "weight": 10,
 *   "icon": "minecraft:bread", "likes": ["minecraft:cake", "#minecraft:flowers"],
 *   "stages": [
 *     { "type": "deliver", "item": "minecraft:bread", "count": 16, "min_bond": 0,
 *       "reward": { "bond": 15, "coins": 20, "xp": 40, "items": ["minecraft:emerald*2"] } },
 *     { "type": "hunt", "entity": "minecraft:zombie", "count": 8, "min_bond": 20, "reward": { ... } },
 *     { "type": "envoy", "min_bond": 45, "reward": { "bond": 30, "keepsake": { "item": "minecraft:iron_sword",
 *       "name": "hearthbound.keepsake.x", "enchant": "minecraft:sharpness=2" } } } ] }
 * }</pre>
 */
public final class Persona {
    public enum Type { DELIVER, HUNT, DEFEND, EXPLORE, ENVOY }

    public static final class Stage {
        public final Type type;
        public final String target;
        public final Item icon;
        public final int count;
        public final int minBond;
        public final int bond;
        public final int coins;
        public final int xp;
        public final List<String> items;
        public final String keepsakeItem;
        public final String keepsakeName;
        public final String keepsakeEnchant;

        Stage(JsonObject j) {
            Type t;
            try {
                t = Type.valueOf(Json.str(j, "type", "deliver").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                t = Type.DELIVER;
            }
            this.type = t;
            this.target = switch (t) {
                case DELIVER -> Json.str(j, "item", "minecraft:bread");
                case HUNT -> Json.str(j, "entity", "monster");
                default -> "";
            };
            Item shown = t == Type.DELIVER && !target.startsWith("#") && !target.startsWith("@") ? Json.item(target, Items.CHEST) : null;
            if (t == Type.DELIVER && target.startsWith("#")) {
                shown = switch (target) {
                    case "#minecraft:wool" -> Items.WHITE_WOOL;
                    case "#minecraft:small_flowers", "#minecraft:flowers" -> Items.POPPY;
                    case "#minecraft:candles" -> Items.CANDLE;
                    case "#minecraft:logs" -> Items.OAK_LOG;
                    default -> null;
                };
            }
            Item ic = Json.item(j, "icon", null);
            this.icon = ic != null ? ic : shown != null ? shown : switch (t) {
                case HUNT -> Items.IRON_SWORD;
                case DEFEND -> Items.SHIELD;
                case EXPLORE -> Items.COMPASS;
                case ENVOY -> Items.WRITABLE_BOOK;
                default -> Items.CHEST;
            };
            this.count = Math.max(1, Json.integer(j, "count", 1));
            this.minBond = Json.integer(j, "min_bond", 0);
            JsonObject r = Json.obj(j, "reward");
            this.bond = Json.integer(r, "bond", 15);
            this.coins = Json.integer(r, "coins", 0);
            this.xp = Json.integer(r, "xp", 30);
            this.items = Json.strings(r, "items");
            JsonObject k = Json.obj(r, "keepsake");
            this.keepsakeItem = Json.str(k, "item", "");
            this.keepsakeName = Json.str(k, "name", "");
            this.keepsakeEnchant = Json.str(k, "enchant", "");
        }
    }

    public final ResourceLocation id;
    public final List<Role> roles = new ArrayList<>();
    public final String gender; // any, male, female
    public final List<ResourceLocation> cultures = new ArrayList<>();
    public final int weight;
    public final Item icon;
    public final List<String> likes;
    public final List<Stage> stages = new ArrayList<>();
    public final int talkLines;
    /** Story dialogues per phase (before chapter 1, after chapter 1, after chapter 2, after the end). */
    public final int[] dialogues = new int[4];
    /** Paragraphs of this story's own biography (2 when it is combined with a trade origin). */
    public final int bios;
    /** The first paragraph comes from the settler's trade origins (generated life stories). */
    public final boolean origin;
    /** Has a "<key>.source" text naming the book it comes from. */
    public final boolean source;

    public Persona(ResourceLocation id, JsonObject j) {
        this.id = id;
        for (String s : Json.strings(j, "roles")) roles.add(Role.byId(s));
        this.gender = Json.str(j, "gender", "any").toLowerCase(Locale.ROOT);
        for (String s : Json.strings(j, "cultures")) {
            ResourceLocation rl = s.contains(":") ? ResourceLocation.tryParse(s) : ResourceLocation.fromNamespaceAndPath("hearthbound", s);
            if (rl != null) cultures.add(rl);
        }
        this.weight = Math.max(0, Json.integer(j, "weight", 10));
        this.icon = Json.item(j, "icon", Items.BOOK);
        this.likes = Json.strings(j, "likes");
        for (JsonElement e : Json.arr(j, "stages")) if (e.isJsonObject()) stages.add(new Stage(e.getAsJsonObject()));
        this.talkLines = Math.max(1, Json.integer(j, "talk_lines", 4));
        List<com.google.gson.JsonElement> dl = Json.arr(j, "dialogues");
        for (int i = 0; i < Math.min(4, dl.size()); i++) {
            try {
                dialogues[i] = Math.max(0, dl.get(i).getAsInt());
            } catch (Exception ignored) {
            }
        }
        this.bios = Math.max(0, Json.integer(j, "bios", 3));
        this.origin = Json.bool(j, "origin", false);
        this.source = Json.bool(j, "source", false);
    }

    public boolean fits(Role role, boolean female, ResourceLocation culture) {
        if (!roles.isEmpty() && !roles.contains(role)) return false;
        if (gender.equals("male") && female) return false;
        if (gender.equals("female") && !female) return false;
        return cultures.isEmpty() || cultures.contains(culture);
    }

    public boolean fitsIgnoringRole(boolean female, ResourceLocation culture) {
        if (gender.equals("male") && female) return false;
        if (gender.equals("female") && !female) return false;
        return cultures.isEmpty() || cultures.contains(culture);
    }

    /** Translation key prefix for all of this persona's texts. */
    public String key() {
        return key(id);
    }

    public static String key(ResourceLocation id) {
        return "hearthbound.persona." + id.getPath().replace('/', '.');
    }
}
