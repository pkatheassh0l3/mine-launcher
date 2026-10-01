package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server sync of the player's inventory filters (client-side only otherwise). The server
 * caches them per player so it can enforce the "Pick Up" rule: filtered inventory slots only accept
 * their item when items are picked up from the ground. Keyed by player-inventory slot index.
 */
public record InventoryFiltersPayload(Map<Integer, SlotWhitelist> filters) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<InventoryFiltersPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "inventory_filters"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryFiltersPayload> CODEC =
            StreamCodec.ofMember(InventoryFiltersPayload::write, InventoryFiltersPayload::new);

    private InventoryFiltersPayload(RegistryFriendlyByteBuf buf) {
        this(readMap(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.filters.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : this.filters.entrySet()) {
            buf.writeVarInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readMap(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Integer, SlotWhitelist> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int key = buf.readVarInt();
            map.put(key, SlotWhitelist.PACKET_CODEC.decode(buf));
        }
        return map;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
