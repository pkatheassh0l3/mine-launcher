package com.hearthbound.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small lenient JSON helpers: every field is optional and has a default. */
public final class Json {
    private Json() {}

    public static String str(JsonObject o, String k, String def) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    public static int integer(JsonObject o, String k, int def) {
        try {
            return o != null && o.has(k) ? o.get(k).getAsInt() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static double dbl(JsonObject o, String k, double def) {
        try {
            return o != null && o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean bool(JsonObject o, String k, boolean def) {
        try {
            return o != null && o.has(k) ? o.get(k).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    public static List<JsonElement> arr(JsonObject o, String k) {
        List<JsonElement> out = new ArrayList<>();
        if (o == null || !o.has(k)) return out;
        JsonElement e = o.get(k);
        if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) out.add(x);
        else out.add(e);
        return out;
    }

    public static List<String> strings(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr(o, k)) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }

    /** {@code [min, max]} or a single number. */
    public static int[] range(JsonObject o, String k, int defMin, int defMax) {
        if (o == null || !o.has(k)) return new int[]{defMin, defMax};
        JsonElement e = o.get(k);
        try {
            if (e.isJsonArray()) {
                JsonArray a = e.getAsJsonArray();
                int a0 = a.get(0).getAsInt();
                int a1 = a.size() > 1 ? a.get(1).getAsInt() : a0;
                return new int[]{Math.min(a0, a1), Math.max(a0, a1)};
            }
            int v = e.getAsInt();
            return new int[]{v, v};
        } catch (Exception ex) {
            return new int[]{defMin, defMax};
        }
    }

    public static int color(JsonObject o, String k, int def) {
        String s = str(o, k, null);
        if (s == null) return def;
        try {
            return 0xFF000000 | Integer.parseInt(s.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static Map<String, Integer> intMap(JsonObject o, String k) {
        Map<String, Integer> out = new LinkedHashMap<>();
        JsonObject m = obj(o, k);
        for (var e : m.entrySet()) {
            try {
                out.put(e.getKey(), e.getValue().getAsInt());
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static Item item(String id, Item def) {
        if (id == null) return def;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return def;
        return BuiltInRegistries.ITEM.getOptional(rl).orElse(def);
    }

    public static Item item(JsonObject o, String k, Item def) {
        return item(str(o, k, null), def);
    }

    /** Parses "minecraft:oak_log[axis=y]" style strings; falls back to {@code def}. */
    public static BlockState state(String s, BlockState def) {
        if (s == null || s.isBlank()) return def;
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState();
        } catch (Exception e) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) {
                Block b = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
                if (b != null && b != Blocks.AIR) return b.defaultBlockState();
            }
            return def;
        }
    }

    public static Item orAir(Item i) {
        return i == null ? Items.AIR : i;
    }
}
