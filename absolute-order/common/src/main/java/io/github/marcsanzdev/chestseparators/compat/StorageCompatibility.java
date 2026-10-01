package io.github.marcsanzdev.chestseparators.compat;

import java.util.*;
import java.util.function.Function;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;

/** Optional loader adapters: only real storage slots may participate, never upgrade/crafting slots. */
public final class StorageCompatibility {
    public static final boolean PLAYER_INVENTORY_ENABLED = false;
    public interface Access {
        UUID identity();
        int size();
        boolean contains(Slot slot);
        Map<Integer, SlotWhitelist> filters();
        void filters(Map<Integer, SlotWhitelist> value);
    }
    public static Function<AbstractContainerMenu, Access> adapter = menu -> null;
    public static Access get(AbstractContainerMenu menu) { return adapter.apply(menu); }
    private StorageCompatibility() {}
}
