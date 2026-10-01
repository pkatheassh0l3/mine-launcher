package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Server -> Client broadcast of a block container's whitelist (open push / re-sync to other viewers).
// A separate payload ID from the C2S {@link WhitelistPayload} because NeoForge (via Architectury) forbids
// registering one payload id for both directions; Fabric tolerated it but NeoForge does not.
public record WhitelistS2CPayload(BlockPos pos, Map<Integer, SlotWhitelist> whitelists)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WhitelistS2CPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "whitelist_sync_s2c_ascension_v1"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WhitelistS2CPayload> CODEC =
            StreamCodec.ofMember(WhitelistS2CPayload::write, WhitelistS2CPayload::new);

    private WhitelistS2CPayload(RegistryFriendlyByteBuf buf) {
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
