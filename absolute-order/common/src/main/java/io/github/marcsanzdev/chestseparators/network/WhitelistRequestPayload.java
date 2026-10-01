package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client-to-Server request packet to fetch whitelist data for a specific chest.
public record WhitelistRequestPayload(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WhitelistRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "whitelist_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WhitelistRequestPayload> CODEC =
            StreamCodec.ofMember(WhitelistRequestPayload::write, WhitelistRequestPayload::new);

    private WhitelistRequestPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
