package io.github.marcsanzdev.chestseparators.neoforge;

import io.github.marcsanzdev.chestseparators.compat.StorageCompatibility;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageInventorySlot;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.inventory.Slot;
import java.util.*;

public final class SophisticatedCompatibility {
    private static final String KEY = "absolute_order_filters";
    private record Cached(net.minecraft.nbt.Tag tag, Map<Integer, SlotWhitelist> rules) {}
    private static final Map<IStorageWrapper, Cached> CACHE = new WeakHashMap<>();
    public static synchronized Map<Integer, SlotWhitelist> read(IStorageWrapper wrapper) {
        var tag = wrapper.getSettingsHandler().getNbt().get(KEY);
        if (tag == null) return Map.of();
        var cached = CACHE.get(wrapper);
        if (cached != null && cached.tag() == tag) return cached.rules();
        var rules = SlotWhitelist.MAP_CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(Map.of());
        CACHE.put(wrapper, new Cached(tag, rules));
        return rules;
    }
    public static void write(IStorageWrapper wrapper, Map<Integer, SlotWhitelist> filters) {
        SlotWhitelist.MAP_CODEC.encodeStart(NbtOps.INSTANCE, filters).result().ifPresent(tag -> {
            var settings = wrapper.getSettingsHandler();
            var access = (io.github.marcsanzdev.chestseparators.neoforge.mixin.SophisticatedSettingsAccessor) settings;
            access.absoluteOrder$saveCategory(settings.getNbt(), KEY, (net.minecraft.nbt.CompoundTag) tag);
            access.absoluteOrder$markDirty().run();
        });
    }
    public static void init() {
        StorageCompatibility.adapter = menu -> {
            if (!(menu instanceof StorageContainerMenuBase<?> storage)) return null;
            IStorageWrapper wrapper = storage.getStorageWrapper();
            var uuid = wrapper.getContentsUuid();
            if (uuid.isEmpty()) return null;
            return new StorageCompatibility.Access() {
                public UUID identity() { return uuid.get(); }
                public int size() { return wrapper.getInventoryHandler().getSlots(); }
                public boolean contains(Slot slot) {
                    return slot instanceof StorageInventorySlot && storage.isStorageInventorySlot(slot.index);
                }
                public Map<Integer, SlotWhitelist> filters() { return read(wrapper); }
                public void filters(Map<Integer, SlotWhitelist> value) {
                    Map<Integer, SlotWhitelist> checked = new HashMap<>();
                    value.forEach((index, rule) -> { if (index >= 0 && index < size()) checked.put(index, rule); });
                    write(wrapper, checked);
                }
            };
        };
    }
}
