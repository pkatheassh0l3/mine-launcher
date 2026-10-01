package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Payload for synchronizing whitelist data between the Server and the Client.
// Used bidirectionally (C2S for saving, S2C for loading the GUI).
public record WhitelistPayload(BlockPos pos, Map<Integer, SlotWhitelist> whitelists) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WhitelistPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "whitelist_sync_ascension_v1"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WhitelistPayload> CODEC =
            StreamCodec.ofMember(WhitelistPayload::write, WhitelistPayload::new);

    private WhitelistPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), readMap(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
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
