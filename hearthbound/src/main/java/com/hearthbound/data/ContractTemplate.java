package com.hearthbound.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthbound.rpg.Rank;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A contract a village may post on its board.
 * Loaded from {@code data/<ns>/hearthbound/contracts/<id>.json}.
 * <pre>{@code
 * { "type": "deliver", "item": "minecraft:wheat", "count": [16, 32],
 *   "reward": { "coins": [8, 14], "reputation": 12, "xp": 30, "items": ["minecraft:bread*4"] },
 *   "rank": "stranger", "tier": 0, "age": "", "cultures": [], "weight": 10 }
 * }</pre>
 */
public final class ContractTemplate {
    public enum Type { DELIVER, HUNT, DEFEND, EXPLORE, DONATE, ENVOY, BUILD }

    public final ResourceLocation id;
    public final Type type;
    public final String title;
    public final Item icon;
    public final Item item;                 // DELIVER: shown item
    public final String itemMatcher;        // DELIVER: matcher (id, #tag, @mod)
    public final String entityMatcher;      // HUNT: matcher or "monster"
    public final String resource;           // DONATE
    public final int[] count;
    public final int[] coins;
    public final int reputation;
    public final int xp;
    public final List<String> rewardItems;
    public final Rank rank;
    public final int tier;
    public final String age;
    public final List<ResourceLocation> cultures = new ArrayList<>();
    public final int weight;

    public ContractTemplate(ResourceLocation id, JsonObject j) {
        this.id = id;
        Type t;
        try {
            t = Type.valueOf(Json.str(j, "type", "deliver").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            t = Type.DELIVER;
        }
        this.type = t;
        this.title = Json.str(j, "title", "");
        this.itemMatcher = Json.str(j, "item", "minecraft:wheat");
        Item shown = itemMatcher.startsWith("#") || itemMatcher.startsWith("@") || itemMatcher.contains("*") ? Items.CHEST : Json.item(itemMatcher, Items.CHEST);
        this.item = Json.item(j, "display_item", shown);
        this.entityMatcher = Json.str(j, "entity", "monster");
        this.resource = Json.str(j, "resource", "wood");
        this.count = Json.range(j, "count", 1, 1);
        JsonObject r = Json.obj(j, "reward");
        this.coins = Json.range(r, "coins", 0, 0);
        this.reputation = Json.integer(r, "reputation", 10);
        this.xp = Json.integer(r, "xp", 20);
        this.rewardItems = Json.strings(r, "items");
        this.rank = Rank.byId(Json.str(j, "rank", "stranger"));
        this.tier = Json.integer(j, "tier", 0);
        this.age = Json.str(j, "age", "");
        for (String s : Json.strings(j, "cultures")) {
            ResourceLocation rl = s.contains(":") ? ResourceLocation.tryParse(s) : ResourceLocation.fromNamespaceAndPath("hearthbound", s);
            if (rl != null) cultures.add(rl);
        }
        this.weight = Math.max(0, Json.integer(j, "weight", 10));
        Item ic = Json.item(j, "icon", null);
        this.icon = ic != null ? ic : switch (t) {
            case DELIVER -> item;
            case HUNT -> Items.IRON_SWORD;
            case DEFEND -> Items.SHIELD;
            case EXPLORE -> Items.FILLED_MAP;
            case DONATE -> Items.CHEST;
            case ENVOY -> Items.WRITABLE_BOOK;
            case BUILD -> Items.BRICKS;
        };
    }

    public Matcher<Item> itemMatcher() {
        return Matcher.of(itemMatcher, Registries.ITEM);
    }

    public Matcher<EntityType<?>> entityMatcher() {
        return Matcher.of(entityMatcher, Registries.ENTITY_TYPE);
    }

    public static List<String> strings(List<JsonElement> l) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : l) out.add(e.getAsString());
        return out;
    }
}
