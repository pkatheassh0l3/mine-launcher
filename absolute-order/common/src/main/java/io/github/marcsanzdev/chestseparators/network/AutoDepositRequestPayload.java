package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server request to auto-deposit the player's inventory into nearby filtered containers.
 * The client forwards its own config (radius, through-walls) so the behaviour matches the player's
 * settings; the server clamps the radius to a safe maximum before acting.
 *
 * <p>Ender Chest and entity (chest minecart / chest boat) filters are stored client-side only, so
 * they travel with the request: the ender filter as a single map, and entity filters keyed by entity
 * UUID. The server uses them to deposit into those containers when they are in range.
 */
public record AutoDepositRequestPayload(
        int radius,
        boolean throughWalls,
        int action,
        Map<Integer, SlotWhitelist> enderWhitelists,
        Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists)
        implements CustomPacketPayload {

    /** Deposit every matching item (double-sneak / dedicated hotkey). */
    public static final int ACTION_DEPOSIT_ALL = 0;

    public static final CustomPacketPayload.Type<AutoDepositRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "auto_deposit_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AutoDepositRequestPayload> CODEC =
            StreamCodec.ofMember(AutoDepositRequestPayload::write, AutoDepositRequestPayload::new);

    private AutoDepositRequestPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), readWlMap(buf), readEntityMap(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.radius);
        buf.writeBoolean(this.throughWalls);
        buf.writeVarInt(this.action);
        writeWlMap(buf, this.enderWhitelists);
        buf.writeVarInt(this.entityWhitelists.size());
        for (Map.Entry<UUID, Map<Integer, SlotWhitelist>> entry : this.entityWhitelists.entrySet()) {
            buf.writeUUID(entry.getKey());
            writeWlMap(buf, entry.getValue());
        }
    }

    private static void writeWlMap(RegistryFriendlyByteBuf buf, Map<Integer, SlotWhitelist> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : map.entrySet()) {
            buf.writeVarInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readWlMap(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Integer, SlotWhitelist> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int key = buf.readVarInt();
            map.put(key, SlotWhitelist.PACKET_CODEC.decode(buf));
        }
        return map;
    }

    private static Map<UUID, Map<Integer, SlotWhitelist>> readEntityMap(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<UUID, Map<Integer, SlotWhitelist>> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            map.put(uuid, readWlMap(buf));
        }
        return map;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
