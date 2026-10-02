package io.github.marcsanzdev.chestseparators.data;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Each item has exactly one purpose; namespaces never create separate catalogs. */
public final class FunctionalPresets {
    public enum Purpose {
        FOOD("comida", "Comida"), WEAPONS("armas", "Armas"), EQUIPMENT("equipo", "Equipo"),
        TOOLS("herramientas", "Herramientas"), MINERALS("minerales", "Minerales"),
        ENGINEERING("ingenieria", "Ingeniería"), BUILDING("construccion", "Construcción"),
        WOOD("madera", "Derivados de la madera"), STONE("piedra", "Derivados de la piedra"),
        NATURE("naturaleza", "Naturaleza"), MOB_DROPS("drops", "Drops de mobs"), ALCHEMY("alquimia", "Alquimia"), UTILITIES("utilidades", "Utilidades");
        public final String key, label;
        Purpose(String key, String label) { this.key = key; this.label = label; }
    }
    public enum Kind { FOOD, WEAPON, EQUIPMENT, TOOL, POTION, BLOCK, OTHER }
    public record ItemInfo(String id, Set<String> tags, Kind kind) {}
    public static final int MAX_ITEM_BYTES = 20_000;
    private FunctionalPresets() {}

    private static boolean tagged(ItemInfo item, String... families) {
        for (String tag : item.tags()) {
            String path = tag.substring(tag.indexOf(':') + 1);
            for (String family : families) if (path.equals(family) || path.startsWith(family + "/")) return true;
        }
        return false;
    }
    private static boolean word(String path, String... words) {
        String normalized = "_" + path.replace('/', '_') + "_";
        for (String word : words) if (normalized.contains("_" + word + "_")) return true;
        return false;
    }
    public static Purpose classify(ItemInfo item) {
        String path = item.id().substring(item.id().indexOf(':') + 1);
        if (path.equals("fermented_spider_eye")) return Purpose.ALCHEMY;
        if (path.equals("resin_clump")) return Purpose.MOB_DROPS;
        if (path.equals("creaking_heart")) return Purpose.UTILITIES;
        if (path.equals("bone_meal")) return Purpose.NATURE;
        if (Set.of("rotten_flesh", "spider_eye").contains(path)) return Purpose.MOB_DROPS;
        if (item.kind() == Kind.FOOD || tagged(item, "foods", "food", "cooking_ingredients", "flours", "dough", "milk", "eggs")
                || word(path, "flour", "dough", "wheat", "rice", "sugar", "salt", "milk", "cheese", "butter", "spice", "coffee", "tea")) return Purpose.FOOD;
        if (item.kind() == Kind.WEAPON || tagged(item, "weapons", "arrows", "swords", "tools/bow", "tools/crossbow", "tools/sword")
                || word(path, "sword", "spear", "dagger", "bow", "crossbow", "arrow", "trident", "mace", "bullet", "gun", "musket", "rifle", "ammunition")) return Purpose.WEAPONS;
        if (item.kind() == Kind.EQUIPMENT || tagged(item, "armors", "armor", "enchantable/armor")
                || word(path, "helmet", "chestplate", "leggings", "boots", "shield", "elytra", "goggles", "diving", "amulet", "necklace")) return Purpose.EQUIPMENT;
        if (item.kind() == Kind.TOOL || tagged(item, "tools")
                || word(path, "pickaxe", "axe", "shovel", "hoe", "shears", "wrench", "hammer", "saw", "knife", "fishing_rod", "brush", "bucket")) return Purpose.TOOLS;
        if (tagged(item, "logs", "planks", "wooden_slabs", "wooden_stairs", "wooden_fences", "wooden_doors", "wooden_trapdoors", "wooden_buttons", "wooden_pressure_plates", "signs", "hanging_signs", "boats")
                || word(path, "planks", "log", "wood", "wooden", "sign", "boat", "raft")
                || (word(path, "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped")
                    && word(path, "slab", "stairs", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate", "stem", "hyphae", "mosaic"))) return Purpose.WOOD;
        if (item.kind() == Kind.BLOCK && !tagged(item, "ores", "storage_blocks") && !word(path, "ore")
                && (tagged(item, "stones", "cobblestones") || word(path, "stone", "cobblestone", "deepslate", "blackstone", "sandstone", "granite", "diorite", "andesite", "tuff", "calcite", "basalt", "prismarine", "purpur", "quartz", "nether_brick", "nether_bricks"))) return Purpose.STONE;
        if (tagged(item, "bones", "leathers", "feathers", "slime_balls", "ender_pearls", "gunpowders")
                || word(path, "bone", "bones", "leather", "feather", "slime_ball", "slimeball", "magma_cream", "ender_pearl", "gunpowder", "string", "blaze_rod", "blaze_powder", "ghast_tear", "shulker_shell", "phantom_membrane", "nautilus_shell", "prismarine_shard", "prismarine_crystals", "ink_sac", "scute", "fang", "antler", "pelt", "hide", "rabbit_foot", "spider_eye", "wither_skeleton_skull", "creeper_head", "zombie_head", "skeleton_skull", "nether_star", "breeze_rod", "wind_charge")) return Purpose.MOB_DROPS;
        if (item.kind() == Kind.POTION || tagged(item, "potions", "enchanting_fuels", "enchantables")
                || word(path, "potion", "elixir", "enchanted_book", "experience_bottle", "blaze_powder", "blaze_rod", "ghast_tear", "nether_wart", "fermented_spider_eye", "brewing_stand", "enchanting_table")) return Purpose.ALCHEMY;
        if (word(path, "redstone", "repeater", "comparator")) return Purpose.ENGINEERING;
        if (tagged(item, "ores", "ingots", "nuggets", "gems", "raw_materials", "dusts", "storage_blocks")
                || word(path, "ore", "ingot", "nugget", "gem", "amethyst_shard", "coal", "charcoal", "raw_iron", "raw_copper", "raw_gold", "scrap", "zinc", "brass", "lapis_lazuli")) return Purpose.MINERALS;
        if (tagged(item, "seeds", "saplings", "flowers", "leaves", "crops", "dyes")
                || word(path, "seed", "seeds", "sapling", "leaves", "flower", "dye", "spawn_egg", "coral", "mushroom", "fungus", "moss", "vine", "cactus", "bamboo", "bone_meal", "grass", "fern", "lily", "tulip", "orchid")) return Purpose.NATURE;
        if (tagged(item, "chests", "barrels", "shulker_boxes", "storage", "redstone", "gears", "plates", "wires")
                || word(path, "chest", "barrel", "backpack", "shulker_box", "upgrade", "redstone", "repeater", "comparator", "piston", "hopper", "dispenser", "dropper", "rail", "rails", "minecart", "boat", "furnace", "smoker", "blast_furnace", "crafter", "lever", "button", "pressure_plate", "gear", "cogwheel", "shaft", "motor", "engine", "pump", "pipe", "tank", "battery", "wire", "cable", "circuit", "machine", "contraption", "propeller", "turbine", "valve", "transmitter", "receiver", "belt", "track", "signal", "boiler")) return Purpose.ENGINEERING;
        // Technical mods also supply components without vanilla behavior or conventional tags.
        String namespace = item.id().substring(0, item.id().indexOf(':'));
        if (Set.of("create", "railways", "create_aeronautics", "aeronautics", "sophisticatedbackpacks", "sophisticatedstorage", "sophisticatedcore").contains(namespace)) return Purpose.ENGINEERING;
        if (item.kind() != Kind.BLOCK && tagged(item, "mob_drops", "animal_drops")) return Purpose.MOB_DROPS;
        if (item.kind() == Kind.BLOCK || tagged(item, "logs", "planks", "stones", "cobblestones", "sands", "bricks", "glass", "wools")) return Purpose.BUILDING;
        return Purpose.UTILITIES;
    }
    public static List<StoragePresetLibrary.Entry> build(Collection<ItemInfo> items) {
        Map<Purpose, SortedSet<String>> groups = new EnumMap<>(Purpose.class);
        Comparator<String> byObject = Comparator.comparing((String id) -> id.substring(id.indexOf(':') + 1)).thenComparing(Comparator.naturalOrder());
        Set<String> seen = new HashSet<>();
        for (ItemInfo item : items) if (seen.add(item.id())) groups.computeIfAbsent(classify(item), key -> new TreeSet<>(byObject)).add(item.id());
        List<StoragePresetLibrary.Entry> entries = new ArrayList<>();
        groups.forEach((purpose, ids) -> {
            List<String> chunk = new ArrayList<>();
            int bytes = 0, part = 1;
            for (String id : ids) {
                int cost = id.getBytes(StandardCharsets.UTF_8).length + 5;
                if (!chunk.isEmpty() && bytes + cost > MAX_ITEM_BYTES) {
                    entries.add(entry(purpose, part++, chunk)); chunk = new ArrayList<>(); bytes = 0;
                }
                chunk.add(id); bytes += cost;
            }
            if (!chunk.isEmpty()) entries.add(entry(purpose, part, chunk));
        });
        return List.copyOf(entries);
    }
    private static StoragePresetLibrary.Entry entry(Purpose purpose, int part, List<String> items) {
        return new StoragePresetLibrary.Entry("function/" + purpose.key + "/" + part, purpose.label + " " + part, List.copyOf(items));
    }
}
