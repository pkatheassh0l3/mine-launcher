package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Sent by the server to inform the client if the lock was granted or denied.
public record EditorLockResponsePayload(BlockPos pos, boolean granted) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EditorLockResponsePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "editor_lock_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorLockResponsePayload> CODEC =
            StreamCodec.ofMember(EditorLockResponsePayload::write, EditorLockResponsePayload::new);

    private EditorLockResponsePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.granted);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
