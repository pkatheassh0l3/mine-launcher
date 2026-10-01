package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server request sent right after a "push into the open chest" deposit, asking the server to
 * re-sort the open container's filtered groups by their priority order. Carries no data: it always
 * targets whatever container the sending player currently has open.
 */
public record SortOpenFiltersPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SortOpenFiltersPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "sort_open_filters"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SortOpenFiltersPayload> CODEC =
            StreamCodec.unit(new SortOpenFiltersPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
