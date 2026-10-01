package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Payload for synchronizing whitelist data of ENTITY containers (chest/hopper minecarts) between the
// Server and the Client, keyed by the entity's UUID instead of a BlockPos.
//
// Used bidirectionally: C2S when the client saves a minecart filter (so the server can enforce the Hopper
// rule and persist it in the entity's NBT), and S2C when the container GUI opens (so the client shows the
// server-authoritative filter).
public record EntityWhitelistPayload(UUID entityUuid, Map<Integer, SlotWhitelist> whitelists)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EntityWhitelistPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "entity_whitelist_sync_ascension_v1"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityWhitelistPayload> CODEC =
            StreamCodec.ofMember(EntityWhitelistPayload::write, EntityWhitelistPayload::new);

    private EntityWhitelistPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), readMap(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.entityUuid);
        GroupedWhitelistCodec.write(buf, this.whitelists);
    }

    private static Map<Integer, SlotWhitelist> readMap(RegistryFriendlyByteBuf buf) {
        return GroupedWhitelistCodec.read(buf);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
