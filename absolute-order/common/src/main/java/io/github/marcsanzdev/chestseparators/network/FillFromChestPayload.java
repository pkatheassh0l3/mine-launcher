package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server request to fill the player's inventory from the currently open container (the
 * "fill inventory" button in the chest editor). The server reads the open menu's container and pulls
 * items the player's inventory filters want. The block position is used only as the origin of the
 * cosmetic fly-back animation.
 */
public record FillFromChestPayload(BlockPos animPos, boolean includeEmpty, boolean lockHotbar)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FillFromChestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "fill_from_chest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FillFromChestPayload> CODEC =
            StreamCodec.ofMember(FillFromChestPayload::write, FillFromChestPayload::new);

    private FillFromChestPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.animPos);
        buf.writeBoolean(this.includeEmpty);
        // Client-side setting (GlobalChestConfig is client-only), forwarded so the server-side re-sort can
        // honour it without referencing the client config class.
        buf.writeBoolean(this.lockHotbar);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
