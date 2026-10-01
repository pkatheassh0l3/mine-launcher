package io.github.marcsanzdev.chestseparators.mixin;

import dev.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.network.ShulkerUUIDPayload;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects a persistent mod UUID into each Shulker Box block entity: generated lazily on the server and
 * persisted to world NBT. On open it transmits that UUID to the opener ({@link ShulkerUUIDPayload} S2C)
 * so the client can key the shulker's local filter config; a capability check keeps vanilla-only clients
 * from receiving an unknown packet.
 */
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxBlockEntityMixin extends BlockEntity implements IShulkerUUIDProvider {

    @Unique
    private UUID chestSeparatorsUUID;

    public ShulkerBoxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public UUID getShulkerUUID() {
        if (this.chestSeparatorsUUID == null) {
            // On the client, never generate a UUID — wait for the authoritative S2C payload.
            if (this.level != null && this.level.isClientSide()) {
                return null;
            }
            this.chestSeparatorsUUID = UUID.randomUUID();
            this.setChanged();
        }
        return this.chestSeparatorsUUID;
    }

    @Override
    public void setShulkerUUID(UUID uuid) {
        this.chestSeparatorsUUID = uuid;
        this.setChanged();
    }

    /** Transmits the Shulker UUID to the opening player so the editor can load its UUID-keyed local config. */
    @Inject(method = "startOpen", at = @At("HEAD"))
    private void chestseparators$onShulkerOpened(Player player, CallbackInfo ci) {
        if (this.getLevel() != null
                && !this.getLevel().isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && NetworkManager.canPlayerReceive(serverPlayer, ShulkerUUIDPayload.TYPE)) {
            NetworkManager.sendToPlayer(serverPlayer, new ShulkerUUIDPayload(this.getShulkerUUID()));
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    protected void onLoadAdditional(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        String uuidString = tag.getString("ChestSeparatorsUUID");
        if (!uuidString.isEmpty()) {
            try {
                this.chestSeparatorsUUID = UUID.fromString(uuidString);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    protected void onSaveAdditional(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (this.chestSeparatorsUUID != null) {
            tag.putString("ChestSeparatorsUUID", this.chestSeparatorsUUID.toString());
        }
    }
}
