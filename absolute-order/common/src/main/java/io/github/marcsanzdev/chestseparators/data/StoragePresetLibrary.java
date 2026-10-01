package io.github.marcsanzdev.chestseparators.data;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

/** Survival catalog with separate filters for physical storage rows. */
public final class StoragePresetLibrary {
    public record Entry(String key, String name, List<String> items, List<List<String>> rows, int columns) {
        public Entry(String key, String name, List<String> items) { this(key, name, items, List.of(items), 9); }
    }
    private static List<Entry> entries = List.of();
    private StoragePresetLibrary() {}
    private static final Set<String> MOB_DROPS = loadMobDrops();
    private static Set<String> loadMobDrops() {
        try (var stream = StoragePresetLibrary.class.getResourceAsStream("/absoluteorder_mob_drops.json")) {
            if (stream == null) return Set.of();
            var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8);
            return Set.of(new com.google.gson.Gson().fromJson(reader, String[].class));
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    public static void refresh() { refresh(54, 9); }
    public static void refresh(int size, int columns) {
        List<FunctionalPresets.ItemInfo> items = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || item instanceof net.minecraft.world.item.SpawnEggItem) continue;
            Set<String> tags = new HashSet<>();
            item.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location().toString()));
            if (MOB_DROPS.contains(BuiltInRegistries.ITEM.getKey(item).toString())) tags.add("absoluteorder:mob_drops");
            FunctionalPresets.Kind kind;
            if (item.getDefaultInstance().has(net.minecraft.core.component.DataComponents.FOOD)) kind = FunctionalPresets.Kind.FOOD;
            else if (item instanceof net.minecraft.world.item.SwordItem || item instanceof net.minecraft.world.item.ProjectileWeaponItem || item instanceof net.minecraft.world.item.TridentItem || item instanceof net.minecraft.world.item.MaceItem) kind = FunctionalPresets.Kind.WEAPON;
            else if (item instanceof net.minecraft.world.item.ArmorItem || item instanceof net.minecraft.world.item.ElytraItem || item instanceof net.minecraft.world.item.ShieldItem) kind = FunctionalPresets.Kind.EQUIPMENT;
            else if (item instanceof net.minecraft.world.item.DiggerItem || item instanceof net.minecraft.world.item.FishingRodItem || item instanceof net.minecraft.world.item.ShearsItem) kind = FunctionalPresets.Kind.TOOL;
            else if (item instanceof net.minecraft.world.item.PotionItem) kind = FunctionalPresets.Kind.POTION;
            else if (item instanceof net.minecraft.world.item.BlockItem) kind = FunctionalPresets.Kind.BLOCK;
            else kind = FunctionalPresets.Kind.OTHER;
            items.add(new FunctionalPresets.ItemInfo(BuiltInRegistries.ITEM.getKey(item).toString(), tags, kind));
        }
        entries = SurvivalRows.build(items, size, columns);
    }
    public static int count() { return entries.size(); }
    public static Entry get(int index) {
        int offset = index - 46;
        return offset >= 0 && offset < entries.size() ? entries.get(offset) : null;
    }

    public static ChestConfigManager.PresetPreview preview(Entry entry, int size) {
        Map<Integer, int[]> visuals = new HashMap<>();
        Map<Integer, SlotWhitelist> filters = new HashMap<>();
        if (size < 1 || entry.rows().isEmpty()) throw new IllegalArgumentException("Empty preset or storage");
        int columns = entry.columns();
        if (entry.rows().size() > (size + columns - 1) / columns) throw new IllegalArgumentException("Preset has more families than storage rows");
        int[] palette = {0xFF67B7DC, 0xFF87BC78, 0xFFC493D5, 0xFFD6AD68, 0xFF73C5B8, 0xFFDB8F92};
        List<SlotWhitelist> rules = new ArrayList<>();
        for (int r = 0; r < entry.rows().size(); r++) {
            UUID group = UUID.nameUUIDFromBytes((entry.key() + "/row/" + r).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            rules.add(new SlotWhitelist(group, entry.rows().get(r), true, true, true, 0));
        }
        for (int i = 0; i < size; i++) {
            int row = (i / columns) % rules.size();
            int color = palette[row % palette.length];
            visuals.put(i, new int[] {0, 0, 0, 0, (color & 0xFFFFFF) | 0x38000000, 0, 0, 0, 0});
            filters.put(i, rules.get(row));
        }
        return new ChestConfigManager.PresetPreview(visuals, filters);
    }
}
