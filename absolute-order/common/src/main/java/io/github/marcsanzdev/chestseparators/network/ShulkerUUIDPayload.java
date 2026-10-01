package io.github.marcsanzdev.chestseparators.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Defines the network packet responsible for transmitting the Shulker Box UUID
// from the server to the client.
public record ShulkerUUIDPayload(UUID shulkerUUID) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShulkerUUIDPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "shulker_uuid"));

    // Custom codec utilizing lambda expressions to ensure absolute compatibility
    // without relying on potentially unstable vanilla UUID codec mappings.
    public static final StreamCodec<RegistryFriendlyByteBuf, ShulkerUUIDPayload> CODEC = StreamCodec.ofMember(
            (ShulkerUUIDPayload payload, RegistryFriendlyByteBuf buf) -> buf.writeUUID(payload.shulkerUUID()),
            (RegistryFriendlyByteBuf buf) -> new ShulkerUUIDPayload(buf.readUUID()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
