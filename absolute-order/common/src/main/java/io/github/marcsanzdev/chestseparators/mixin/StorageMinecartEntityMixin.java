package io.github.marcsanzdev.chestseparators.mixin;

import dev.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.EntityWhitelistS2CPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds {@link IWhitelistProvider} to chest/hopper minecarts. Unlike other entity containers, hoppers CAN
 * insert into these, so the filter is synced to the server by entity UUID (see {@code EntityWhitelistPayload})
 * and persisted here in the entity's own NBT, making the Hopper Insert rule enforce on minecarts (via
 * {@code HopperBlockEntityMixin}) and survive reload. On open it pushes the stored filter to the opener
 * ({@link EntityWhitelistS2CPayload} S2C) so the editor shows the server-authoritative state.
 */
@Mixin(AbstractMinecartContainer.class)
public abstract class StorageMinecartEntityMixin implements IWhitelistProvider {

    @Unique
    private Map<Integer, SlotWhitelist> chestSeparatorsWhitelists = new HashMap<>();

    @Override
    public Map<Integer, SlotWhitelist> getWhitelists() {
        return this.chestSeparatorsWhitelists;
    }

    @Override
    public void setWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        this.chestSeparatorsWhitelists = new HashMap<>(whitelists);
    }

    /**
     * Pushes the stored filter to the opening player so the editor displays the server-authoritative state,
     * mirroring the block-chest handler in LootableContainerBlockEntityMixin.
     */
    @Inject(
            method =
                    "createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            at = @At("RETURN"))
    private void chestseparators$onOpenMenu(
            int syncId, Inventory playerInventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        Entity self = (Entity) (Object) this;
        if (cir.getReturnValue() != null
                && self.level() != null
                && !self.level().isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && NetworkManager.canPlayerReceive(serverPlayer, EntityWhitelistS2CPayload.TYPE)) {
            NetworkManager.sendToPlayer(
                    serverPlayer, new EntityWhitelistS2CPayload(self.getUUID(), this.chestSeparatorsWhitelists));
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void chestseparators$writeWhitelists(CompoundTag tag, CallbackInfo ci) {
        if (!this.chestSeparatorsWhitelists.isEmpty()) {
            SlotWhitelist.MAP_CODEC
                    .encodeStart(NbtOps.INSTANCE, this.chestSeparatorsWhitelists)
                    .result()
                    .ifPresent(encoded -> tag.put("ChestSeparatorsWhitelists", encoded));
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void chestseparators$readWhitelists(CompoundTag tag, CallbackInfo ci) {
        this.chestSeparatorsWhitelists.clear();
        if (tag.contains("ChestSeparatorsWhitelists")) {
            SlotWhitelist.MAP_CODEC
                    .parse(NbtOps.INSTANCE, tag.get("ChestSeparatorsWhitelists"))
                    .result()
                    .ifPresent(this.chestSeparatorsWhitelists::putAll);
        }
    }
}
