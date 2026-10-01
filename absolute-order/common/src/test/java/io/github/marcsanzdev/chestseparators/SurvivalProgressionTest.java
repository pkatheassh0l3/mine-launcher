package io.github.marcsanzdev.chestseparators;
import io.github.marcsanzdev.chestseparators.data.*;
import io.github.marcsanzdev.chestseparators.data.FunctionalPresets.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalProgressionTest {
    private ItemInfo item(String id) { return new ItemInfo(id, Set.of(), Kind.OTHER); }
    @Test void earlyUtilitiesComeBeforeLateLootAcrossPresetPages() {
        var inputs=List.of(item("minecraft:echo_shard"), item("minecraft:paper"), item("minecraft:stick"), item("minecraft:clock"));
        var entries=SurvivalRows.build(inputs,9,9);
        assertEquals(List.of("minecraft:stick","minecraft:paper","minecraft:clock","minecraft:echo_shard"),entries.stream().flatMap(e->e.items().stream()).toList());
        assertEquals(List.of("Utilidades 1","Utilidades 2","Utilidades 3","Utilidades 4"), entries.stream().map(StoragePresetLibrary.Entry::name).toList());
        var reversed=new ArrayList<>(inputs); Collections.reverse(reversed);
        assertEquals(entries,SurvivalRows.build(reversed,9,9));
    }
    @Test void keepsFamiliesTogetherButSortsTheirRareVariantsLast() {
        var input=List.of(new ItemInfo("minecraft:enchanted_golden_apple",Set.of(),Kind.FOOD),new ItemInfo("minecraft:apple",Set.of(),Kind.FOOD),new ItemInfo("minecraft:golden_apple",Set.of(),Kind.FOOD));
        var entry=SurvivalRows.build(input,27,9).getFirst();
        assertEquals(1,entry.rows().size());
        assertEquals(List.of("minecraft:apple","minecraft:golden_apple","minecraft:enchanted_golden_apple"),entry.items());
        assertEquals(27,StoragePresetLibrary.preview(entry,27).filters().size());
    }
    @Test void estimatesMaterialsAcrossModsWithoutNamespaceBias() {
        assertTrue(SurvivalProgression.score(item("other:iron_ingot")) < SurvivalProgression.score(item("minecraft:diamond")));
        assertTrue(SurvivalProgression.score(item("minecraft:diamond")) < SurvivalProgression.score(item("other:netherite_ingot")));
        assertEquals(SurvivalProgression.score(item("a:paper")),SurvivalProgression.score(item("b:paper")));
        assertTrue(SurvivalProgression.score(item("minecraft:nether_star")) > SurvivalProgression.score(item("minecraft:netherrack")));
    }
}
