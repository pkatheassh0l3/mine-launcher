package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.data.*;
import io.github.marcsanzdev.chestseparators.data.FunctionalPresets.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FunctionalPresetsTest {
    private ItemInfo item(String id, Kind kind, String... tags) { return new ItemInfo(id, Set.of(tags), kind); }
    @Test void mixesModsAndNeverDuplicatesItems() {
        var input = List.of(item("minecraft:bread", Kind.FOOD), item("farmersdelight:rice", Kind.FOOD),
                item("moredelight:sandwich", Kind.FOOD), item("minecraft:iron_ingot", Kind.OTHER, "c:ingots", "absoluteorder:mob_drops"));
        var entries = FunctionalPresets.build(input);
        assertEquals(2, entries.size());
        assertEquals("Comida 1", entries.getFirst().name());
        assertEquals(3, entries.getFirst().items().size());
        assertEquals("Minerales 1", entries.get(1).name());
        assertEquals(4, entries.stream().flatMap(e -> e.items().stream()).distinct().count());
    }
    @Test void classifiesDropsWithoutStealingFoodOrMaterials() {
        assertEquals(Purpose.MOB_DROPS, FunctionalPresets.classify(item("crittersandcompanions:dragonfly_wing", Kind.OTHER, "absoluteorder:mob_drops")));
        assertEquals(Purpose.MOB_DROPS, FunctionalPresets.classify(item("minecraft:rotten_flesh", Kind.FOOD)));
        assertEquals(Purpose.MOB_DROPS, FunctionalPresets.classify(item("minecraft:spider_eye", Kind.FOOD)));
        assertEquals(Purpose.FOOD, FunctionalPresets.classify(item("minecraft:beef", Kind.FOOD, "absoluteorder:mob_drops")));
        assertEquals(Purpose.MINERALS, FunctionalPresets.classify(item("minecraft:iron_ingot", Kind.OTHER, "c:ingots", "absoluteorder:mob_drops")));
        assertEquals(Purpose.NATURE, FunctionalPresets.classify(item("minecraft:bone_meal", Kind.OTHER)));
        assertNotEquals(Purpose.FOOD, FunctionalPresets.classify(item("minecraft:zombie_spawn_egg", Kind.OTHER)));
    }
    @Test void keepsDistinctFunctionsAcrossTechnicalMods() {
        assertEquals(Purpose.ENGINEERING, FunctionalPresets.classify(item("create:cogwheel", Kind.BLOCK)));
        assertEquals(Purpose.MINERALS, FunctionalPresets.classify(item("create:zinc_ingot", Kind.OTHER)));
        assertEquals(Purpose.TOOLS, FunctionalPresets.classify(item("create:wrench", Kind.OTHER)));
        assertEquals(Purpose.ENGINEERING, FunctionalPresets.classify(item("minecraft:redstone", Kind.OTHER, "c:dusts")));
        assertEquals(Purpose.EQUIPMENT, FunctionalPresets.classify(item("example:helmet", Kind.EQUIPMENT)));
        assertEquals(Purpose.STONE, FunctionalPresets.classify(item("minecraft:quartz_stairs", Kind.BLOCK)));
    }
    @Test void splitsOnlyWhenPayloadRequiresItAndKeepsAllItems() {
        List<ItemInfo> input = new ArrayList<>();
        for (int i = 0; i < 2400; i++) input.add(item((i % 2 == 0 ? "mod_a:" : "mod_b:") + "meal_" + i, Kind.FOOD));
        var entries = FunctionalPresets.build(input);
        assertTrue(entries.size() < 4);
        assertEquals(2400, entries.stream().mapToInt(e -> e.items().size()).sum());
        assertEquals(2400, entries.stream().flatMap(e -> e.items().stream()).distinct().count());
        for (var entry : entries) {
            assertTrue(entry.items().stream().mapToInt(id -> id.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 5).sum() <= FunctionalPresets.MAX_ITEM_BYTES);
            assertTrue(entry.items().stream().anyMatch(id -> id.startsWith("mod_a:")));
            assertTrue(entry.items().stream().anyMatch(id -> id.startsWith("mod_b:")));
        }
    }
    @Test void stableNamesDoNotDependOnInputOrderAndSmallGroupsStayWhole() {
        var input = List.of(item("b:bread", Kind.FOOD), item("a:bread", Kind.FOOD), item("z:sword", Kind.WEAPON));
        var reversed = new ArrayList<>(input); Collections.reverse(reversed);
        assertEquals(FunctionalPresets.build(input), FunctionalPresets.build(reversed));
        assertEquals(List.of("Comida 1", "Armas 1"), FunctionalPresets.build(input).stream().map(StoragePresetLibrary.Entry::name).toList());
    }
}
