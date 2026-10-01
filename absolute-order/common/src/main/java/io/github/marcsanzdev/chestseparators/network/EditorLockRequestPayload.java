package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Sent by the client to request a lock on a chest before opening the editor,
// or to release the lock when closing the editor.
public record EditorLockRequestPayload(BlockPos pos, boolean isLocking) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EditorLockRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "editor_lock_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorLockRequestPayload> CODEC =
            StreamCodec.ofMember(EditorLockRequestPayload::write, EditorLockRequestPayload::new);

    private EditorLockRequestPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.isLocking);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
