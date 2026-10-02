package com.hearthbound.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthbound.rpg.Rank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * A building players can raise for a village: a library, a butcher's shop... Taking the project
 * gives a foundation stone; placing it marks a plot, and the building counts when the plot holds
 * the required blocks. Each finished level unlocks new wares in the village hall.
 * Loaded from {@code data/<ns>/hearthbound/projects/<id>.json}.
 * <pre>{@code
 * { "name": "hearthbound.project.library", "description": "hearthbound.project.library.desc",
 *   "icon": "minecraft:bookshelf",
 *   "levels": [
 *     { "size": 9, "max_size": 15, "height": 12, "min_tier": 1, "rank": "acquaintance", "age": "",
 *       "requirements": [ { "block": "minecraft:bookshelf", "count": 15 }, { "block": "#minecraft:beds", "count": 1 } ],
 *       "reward": { "reputation": 60, "coins": 40, "xp": 150 },
 *       "trades": [ { "type": "sell", "item": "minecraft:enchanted_book", "enchant": "minecraft:efficiency=1",
 *                     "price": 40, "count": 1, "rank": "acquaintance", "age": "" } ] } ] }
 * }</pre>
 */
public final class ProjectDef {
    public static final class Requirement {
        private final String block;
        private final int count;
        private final Item icon;
        private final Matcher<Block> matcher;

        Requirement(String block, int count, Item icon) {
            this.block = block;
            this.count = count;
            this.icon = icon;
            this.matcher = Matcher.of(block, Registries.BLOCK);
        }

        public String block() {
            return block;
        }

        public int count() {
            return count;
        }

        public Item icon() {
            return icon;
        }

        public boolean test(Block b) {
            return matcher.test(BuiltInRegistries.BLOCK.wrapAsHolder(b));
        }
    }

    public record HallTrade(Item item, String enchant, int count, int price, boolean sell, Rank rank, String age) {}

    public static final class Level {
        public final int size;
        public final int maxSize;
        public final int height;
        public final int minTier;
        public final Rank rank;
        public final String age;
        public final List<Requirement> requirements = new ArrayList<>();
        public final int reputation;
        public final int coins;
        public final int xp;
        public final List<HallTrade> trades = new ArrayList<>();

        Level(JsonObject j) {
            this.size = oddAtLeast(Json.integer(j, "size", 9), 5);
            this.maxSize = Math.max(size, oddAtLeast(Json.integer(j, "max_size", size + 6), 5));
            this.height = Math.max(4, Json.integer(j, "height", 12));
            this.minTier = Json.integer(j, "min_tier", 0);
            this.rank = Rank.byId(Json.str(j, "rank", "stranger"));
            this.age = Json.str(j, "age", "");
            for (JsonElement e : Json.arr(j, "requirements")) {
                if (!e.isJsonObject()) continue;
                JsonObject r = e.getAsJsonObject();
                String block = Json.str(r, "block", "minecraft:stone");
                Item icon = Json.item(r, "icon", null);
                if (icon == null) icon = block.startsWith("#") ? Items.CHEST : Json.item(block, Items.CHEST);
                requirements.add(new Requirement(block, Math.max(1, Json.integer(r, "count", 1)), icon));
            }
            JsonObject rw = Json.obj(j, "reward");
            this.reputation = Json.integer(rw, "reputation", 40);
            this.coins = Json.integer(rw, "coins", 20);
            this.xp = Json.integer(rw, "xp", 100);
            for (JsonElement e : Json.arr(j, "trades")) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                Item it = Json.item(o, "item", null);
                if (it == null || it == Items.AIR) continue;
                trades.add(new HallTrade(it, Json.str(o, "enchant", ""),
                        Math.max(1, Math.min(99, Json.integer(o, "count", 1))),
                        Math.max(1, Json.integer(o, "price", 1)),
                        !"buy".equalsIgnoreCase(Json.str(o, "type", "sell")),
                        Rank.byId(Json.str(o, "rank", "stranger")),
                        Json.str(o, "age", "")));
            }
        }

        public int total() {
            int n = 0;
            for (Requirement r : requirements) n += r.count;
            return n;
        }

        private static int oddAtLeast(int v, int min) {
            v = Math.max(min, v);
            return v % 2 == 0 ? v + 1 : v;
        }
    }

    public final ResourceLocation id;
    public final String name;
    public final String description;
    public final Item icon;
    public final int sort;
    public final List<Level> levels = new ArrayList<>();

    public ProjectDef(ResourceLocation id, JsonObject j) {
        this.id = id;
        this.name = Json.str(j, "name", "hearthbound.project." + id.getPath());
        this.description = Json.str(j, "description", name + ".desc");
        this.icon = Json.item(j, "icon", Items.BRICKS);
        this.sort = Json.integer(j, "sort", 100);
        for (JsonElement e : Json.arr(j, "levels")) if (e.isJsonObject()) levels.add(new Level(e.getAsJsonObject()));
    }

    public Level level(int n) {
        return n >= 1 && n <= levels.size() ? levels.get(n - 1) : null;
    }
}
