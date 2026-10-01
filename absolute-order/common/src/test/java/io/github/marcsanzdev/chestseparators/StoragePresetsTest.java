package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.data.*;
import io.github.marcsanzdev.chestseparators.network.GroupedWhitelistCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StoragePresetsTest {
    private StoragePresetLibrary.Entry entry() {
        return new StoragePresetLibrary.Entry("test", "Test", java.util.stream.IntStream.range(0,96)
                .mapToObj(i -> "example:storage_item_" + i).toList());
    }
    @Test void adaptsToStorageCapacityWithoutPlayerSlots() {
        for (int size : new int[]{9, 27, 54, 108, 144}) {
            var preview = StoragePresetLibrary.preview(entry(), size);
            assertEquals(size, preview.filters().size());
            assertEquals(size, preview.visual().size());
            assertTrue(preview.filters().keySet().stream().allMatch(i -> i >= 0 && i < size));
            assertEquals(1, preview.filters().values().stream().map(SlotWhitelist::groupId).distinct().count());
            assertEquals(entry().items(), preview.filters().get(0).allowedItems());
        }
    }
    @Test void largeStorageRoundTripFitsSmallPacket() {
        var filters = StoragePresetLibrary.preview(entry(), 144).filters();
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GroupedWhitelistCodec.write(buf, filters);
            assertTrue(buf.readableBytes() < 8192);
            assertEquals(filters, GroupedWhitelistCodec.read(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
    @Test void readsLegacyMaps() {
        var rule = StoragePresetLibrary.preview(entry(), 1).filters().get(0);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeInt(1); buf.writeInt(0); SlotWhitelist.PACKET_CODEC.encode(buf, rule);
            assertEquals(Map.of(0, rule), GroupedWhitelistCodec.read(buf));
        } finally { buf.release(); }
    }
    @Test void rejectsInvalidGroupReference() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeInt(-1); buf.writeVarInt(0); buf.writeVarInt(1); buf.writeVarInt(0); buf.writeVarInt(7);
            assertThrows(IllegalArgumentException.class, () -> GroupedWhitelistCodec.read(buf));
        } finally { buf.release(); }
    }
}
