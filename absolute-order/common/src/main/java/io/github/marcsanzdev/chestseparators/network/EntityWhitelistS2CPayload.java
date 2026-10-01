package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Server -> Client sync of an ENTITY container's whitelist (chest/hopper minecart), keyed by entity UUID.
// A separate payload ID from the C2S {@link EntityWhitelistPayload} because NeoForge (via Architectury)
// forbids registering one payload id for both directions.
public record EntityWhitelistS2CPayload(UUID entityUuid, Map<Integer, SlotWhitelist> whitelists)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EntityWhitelistS2CPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "entity_whitelist_sync_s2c_ascension_v1"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityWhitelistS2CPayload> CODEC =
            StreamCodec.ofMember(EntityWhitelistS2CPayload::write, EntityWhitelistS2CPayload::new);

    private EntityWhitelistS2CPayload(RegistryFriendlyByteBuf buf) {
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
