package io.github.marcsanzdev.chestseparators;
import io.github.marcsanzdev.chestseparators.data.*;
import io.github.marcsanzdev.chestseparators.data.FunctionalPresets.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalRowsTest {
    @Test void woodAndStoneHaveTheirOwnMixedCategoriesAndRows() {
        var input = List.of(new ItemInfo("minecraft:oak_planks", Set.of(), Kind.BLOCK),
            new ItemInfo("other:oak_door", Set.of("minecraft:wooden_doors"), Kind.BLOCK),
            new ItemInfo("minecraft:spruce_stairs", Set.of(), Kind.BLOCK),
            new ItemInfo("minecraft:stone_bricks", Set.of(), Kind.BLOCK),
            new ItemInfo("other:granite_slab", Set.of(), Kind.BLOCK));
        var entries = SurvivalRows.build(input, 54, 9);
        assertEquals(List.of("Derivados de la madera 1", "Derivados de la piedra 1"), entries.stream().map(StoragePresetLibrary.Entry::name).toList());
        assertEquals(List.of("minecraft:oak_planks", "other:oak_door"), entries.getFirst().rows().getFirst());
        assertEquals(2, entries.getFirst().rows().size());
        assertEquals(2, entries.get(1).rows().size());
        assertEquals(Purpose.TOOLS, FunctionalPresets.classify(new ItemInfo("minecraft:stone_pickaxe", Set.of(), Kind.TOOL)));
        assertEquals(Purpose.MINERALS, FunctionalPresets.classify(new ItemInfo("minecraft:deepslate_iron_ore", Set.of("c:ores"), Kind.BLOCK)));
        assertEquals(Purpose.NATURE, FunctionalPresets.classify(new ItemInfo("minecraft:oak_sapling", Set.of("minecraft:saplings"), Kind.BLOCK)));
    }
    private ItemInfo mineral(String id) { return new ItemInfo(id, Set.of("c:gems"), Kind.OTHER); }
    @Test void separatesIronAndDiamondsAndCombinesModsInTheSameRow() {
        var entries = SurvivalRows.build(List.of(mineral("minecraft:iron_ingot"), mineral("other:iron_nugget"),
            mineral("minecraft:diamond"), mineral("minecraft:gold_ingot")), 27, 9);
        assertEquals(1, entries.size());
        var preview = StoragePresetLibrary.preview(entries.getFirst(), 27);
        assertTrue(preview.filters().get(0).allowedItems().contains("minecraft:iron_ingot"));
        assertTrue(preview.filters().get(18).allowedItems().contains("minecraft:diamond"));
        assertEquals(3, preview.filters().values().stream().map(SlotWhitelist::groupId).distinct().count());
        for (int start = 0; start < 27; start += 9) {
            var rule = preview.filters().get(start);
            for (int i = start; i < start + 9; i++) assertEquals(rule, preview.filters().get(i));
            if (rule.allowedItems().contains("minecraft:iron_ingot")) {
                assertTrue(rule.allowedItems().contains("other:iron_nugget"));
                assertFalse(rule.allowedItems().contains("minecraft:diamond"));
            }
        }
    }
    @Test void excludesCreativeAndUnobtainableItemsButKeepsSurvivalResources() {
        for (String id : List.of("minecraft:barrier", "minecraft:bedrock", "minecraft:zombie_spawn_egg", "minecraft:trial_spawner",
            "minecraft:infested_stone", "create:creative_motor", "mod:debug_tool")) assertFalse(SurvivalRows.eligible(mineral(id)), id);
        for (String id : List.of("minecraft:diamond", "minecraft:dragon_egg", "minecraft:nether_star", "create:water_wheel"))
            assertTrue(SurvivalRows.eligible(mineral(id)), id);
        assertFalse(SurvivalRows.eligible(new ItemInfo("mod:special", Set.of("absoluteorder:exclude_survival"), Kind.OTHER)));
    }
    @Test void bulkPresetsFillTheEntireChestWithoutMixingStoneAndDirt() {
        var entries = SurvivalRows.build(List.of(mineral("minecraft:dirt"), mineral("minecraft:stone")), 54, 9);
        assertEquals(2, entries.size());
        for (var entry : entries) {
            var p = StoragePresetLibrary.preview(entry, 54);
            assertEquals(54, p.filters().size());
            assertTrue(p.filters().values().stream().allMatch(r -> r.allowedItems().equals(entry.items())));
            assertEquals(1, entry.items().size());
        }
    }
    @Test void pagesFollowActualRowsIncludingWideAndPartialRows() {
        var input = List.of(mineral("minecraft:iron_ingot"), mineral("minecraft:diamond"), mineral("minecraft:gold_ingot"),
            mineral("minecraft:copper_ingot"), mineral("minecraft:emerald"), mineral("minecraft:netherite_ingot"), mineral("minecraft:coal"));
        for (int[] geometry : List.of(new int[]{27,9},new int[]{54,9},new int[]{144,12},new int[]{25,12})) {
            int size=geometry[0], columns=geometry[1], rows=(size+columns-1)/columns;
            var entries=SurvivalRows.build(input,size,columns);
            assertEquals((7+rows-1)/rows,entries.size());
            assertEquals(7,entries.stream().flatMap(e->e.items().stream()).distinct().count());
            for(var entry:entries) {
                var preview=StoragePresetLibrary.preview(entry,size);
                assertEquals(size,preview.filters().size());
                for(int i=0;i<size;i++) assertEquals(entry.rows().get((i/columns)%entry.rows().size()),preview.filters().get(i).allowedItems());
            }
        }
    }
}
