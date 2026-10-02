package io.github.marcsanzdev.chestseparators.data;

import java.util.*;
import static io.github.marcsanzdev.chestseparators.data.FunctionalPresets.*;

/** Survival-oriented families, one filter per physical row. */
public final class SurvivalRows {
    private SurvivalRows() {}
    private static final Set<String> UNOBTAINABLE = Set.of("air", "barrier", "bedrock", "command_block", "chain_command_block",
        "repeating_command_block", "command_block_minecart", "structure_block", "structure_void", "jigsaw", "light", "debug_stick",
        "spawner", "trial_spawner", "vault", "end_portal_frame", "budding_amethyst", "reinforced_deepslate", "petrified_oak_slab",
        "knowledge_book", "bundle", "player_head", "dirt_path", "farmland", "frogspawn", "suspicious_sand", "suspicious_gravel");
    public static boolean eligible(ItemInfo item) {
        String path = path(item.id());
        if (item.id().equals("create:handheld_worldshaper")) return false;
        if (item.id().startsWith("minecraft:") && (UNOBTAINABLE.contains(path) || path.startsWith("infested_"))) return false;
        if (path.endsWith("spawn_egg") || token(path, "creative", "debug", "infinite", "infinity", "unbreakable", "test", "placeholder")) return false;
        return item.tags().stream().noneMatch(t -> t.equals("absoluteorder:exclude_survival") || t.equals("c:creative_only"));
    }
    private static String path(String id) { return id.substring(id.indexOf(':') + 1); }
    private static boolean token(String path, String... words) {
        for (String word : words) if (("_" + path + "_").contains("_" + word + "_")) return true;
        return false;
    }
    public static String family(ItemInfo item) {
        String path = path(item.id());
        Purpose purpose = classify(item);
        if (purpose == Purpose.MINERALS) {
            for (String material : List.of("iron", "diamond", "gold", "copper", "emerald", "netherite", "coal", "quartz", "lapis", "amethyst", "zinc", "brass", "silver", "tin", "lead", "steel", "uranium", "osmium", "nickel", "aluminum"))
                if (token(path, material)) return material;
            for (String tag : new TreeSet<>(item.tags())) {
                String p = path(tag);
                if (p.matches("(ingots|ores|nuggets|gems|raw_materials|storage_blocks|dusts)/[^/]+")) return p.substring(p.indexOf('/') + 1);
            }
        }
        if (purpose == Purpose.BUILDING || purpose == Purpose.NATURE || purpose == Purpose.WOOD || purpose == Purpose.STONE) {
            for (String material : List.of("dark_oak", "pale_oak", "pale_moss", "pale_hanging_moss", "resin", "eyeblossom", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped", "deepslate", "blackstone", "sandstone", "quartz", "granite", "diorite", "andesite", "tuff", "calcite", "basalt", "prismarine", "purpur", "end_stone", "nether_brick", "mud", "copper", "glass", "wool", "concrete", "terracotta", "ice", "snow", "sand", "gravel", "stone", "brick", "log", "planks", "leaves", "sapling", "seed", "seeds", "flower", "dye", "coral", "mushroom"))
                if (token(path, material)) return material;
        }
        if (purpose == Purpose.FOOD) {
            for (String food : List.of("beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon", "potato", "carrot", "apple", "rice", "tomato", "cabbage", "onion", "wheat", "bread", "sandwich", "soup", "stew", "salad", "pie", "cake", "cookie", "berries", "melon", "pumpkin", "milk", "egg"))
                if (token(path, food)) return food;
        }
        if (purpose == Purpose.TOOLS || purpose == Purpose.WEAPONS || purpose == Purpose.EQUIPMENT || purpose == Purpose.ENGINEERING) {
            for (String type : List.of("pickaxe", "axe", "shovel", "hoe", "sword", "bow", "crossbow", "arrow", "helmet", "chestplate", "leggings", "boots", "shield", "bucket", "wrench", "backpack", "chest", "barrel", "upgrade", "gear", "cogwheel", "shaft", "pipe", "tank", "rail", "track", "redstone", "button", "pressure_plate", "door", "trapdoor"))
                if (token(path, type)) return type;
        }
        // Decorative variants and collectibles stay together instead of consuming a row per color/design.
        for (String type : List.of("carpet", "bed", "banner", "candle", "shulker_box", "stained_glass", "concrete_powder",
            "coral", "mushroom", "flower", "tulip", "orchid", "music_disc", "smithing_template", "banner_pattern", "pottery_sherd",
            "firework", "firework_rocket", "firework_star", "dye", "book", "map", "sherd", "sponge", "anvil", "fence", "fence_gate"))
            if (token(path, type)) return type;
        // Identically named objects from different mods belong in the same row.
        return path;
    }
    public static List<StoragePresetLibrary.Entry> build(Collection<ItemInfo> input, int size, int columns) {
        if (size < 1 || columns < 1) throw new IllegalArgumentException("Invalid storage geometry");
        int rowCount = (size + columns - 1) / columns;
        Map<Purpose, Map<String, SortedSet<String>>> groups = new EnumMap<>(Purpose.class);
        Map<String, SortedSet<String>> bulk = new TreeMap<>();
        Map<String, Integer> scores = new HashMap<>();
        for (ItemInfo item : input) scores.put(item.id(), SurvivalProgression.score(item));
        Comparator<String> byProgression = Comparator.comparingInt((String id) -> scores.getOrDefault(id, 180)).thenComparing(Comparator.naturalOrder());
        for (ItemInfo item : input) {
            if (!eligible(item)) continue;
            String p = path(item.id());
            if (Set.of("dirt", "coarse_dirt", "rooted_dirt", "stone", "cobblestone", "cobbled_deepslate", "netherrack").contains(p)) {
                bulk.computeIfAbsent(p, k -> new TreeSet<>(byProgression)).add(item.id()); continue;
            }
            groups.computeIfAbsent(classify(item), k -> new TreeMap<>()).computeIfAbsent(family(item), k -> new TreeSet<>(byProgression)).add(item.id());
        }
        List<StoragePresetLibrary.Entry> result = new ArrayList<>();
        groups.forEach((purpose, families) -> {
            List<List<String>> rows = new ArrayList<>();
            int part = 1, bytes = 0;
            var orderedFamilies = new ArrayList<>(families.entrySet());
            orderedFamilies.sort(Comparator.comparingInt((Map.Entry<String, SortedSet<String>> e) -> e.getValue().stream().mapToInt(scores::get).min().orElse(180)).thenComparing(Map.Entry::getKey));
            for (var familyEntry : orderedFamilies) {
                var family = familyEntry.getValue();
                // Exceptionally large families also respect the packet budget.
                List<String> chunk = new ArrayList<>(); int cost = 0;
                List<List<String>> chunks = new ArrayList<>();
                for (String id : family) {
                    int n = id.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 5;
                    if (!chunk.isEmpty() && cost + n > MAX_ITEM_BYTES) { chunks.add(List.copyOf(chunk)); chunk.clear(); cost = 0; }
                    chunk.add(id); cost += n;
                }
                if (!chunk.isEmpty()) chunks.add(List.copyOf(chunk));
                for (var row : chunks) {
                    int n = row.stream().mapToInt(id -> id.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 5).sum();
                    if (!rows.isEmpty() && (rows.size() == rowCount || bytes + n > MAX_ITEM_BYTES)) {
                        result.add(entry(purpose, part++, rows, size, columns)); rows = new ArrayList<>(); bytes = 0;
                    }
                    rows.add(row); bytes += n;
                }
            }
            if (!rows.isEmpty()) result.add(entry(purpose, part, rows, size, columns));
        });
        Map<String,String> names = Map.of("dirt", "Tierra", "coarse_dirt", "Tierra estéril", "rooted_dirt", "Tierra enraizada", "stone", "Piedra", "cobblestone", "Roca", "cobbled_deepslate", "Pizarra rocosa", "netherrack", "Netherrack");
        bulk.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, SortedSet<String>> e) -> e.getValue().stream().mapToInt(scores::get).min().orElse(180)).thenComparing(Map.Entry::getKey)).forEach(e -> { String key = e.getKey(); var ids = e.getValue(); result.add(new StoragePresetLibrary.Entry("survival-rows/bulk/" + key, names.get(key) + " — cofre completo", List.copyOf(ids))); });
        return List.copyOf(result);
    }
    private static StoragePresetLibrary.Entry entry(Purpose purpose, int part, List<List<String>> rows, int size, int columns) {
        return new StoragePresetLibrary.Entry("survival-rows/" + size + "x" + columns + "/" + purpose.key + "/" + part,
            purpose.label + " " + part, rows.stream().flatMap(Collection::stream).toList(), List.copyOf(rows), columns);
    }
}
