package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces inventory filters on the F-key offhand swap when no screen is open. There the client sends a
 * {@code ServerboundPlayerActionPacket} with {@code SWAP_ITEM_WITH_OFFHAND} rather than a slot click, so
 * {@link ScreenHandlerSwapFilterMixin} does not intercept it. Both the offhand and the selected hotbar
 * slot are checked against any active Shift-rule filter and the swap is cancelled if either side blocks.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerSwapFilterMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnOffhandSwap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUUID());
        if (filters == null || filters.isEmpty()) return;

        Inventory inv = player.getInventory();
        int hotbarIndex = inv.selected;
        ItemStack mainHandItem = inv.getItem(hotbarIndex); // moves INTO the offhand slot
        ItemStack offhandItem = inv.getItem(40); // moves INTO the selected hotbar slot

        // Offhand filter (raw index 40): does the main-hand item pass the Shift rule?
        SlotWhitelist offhandFilter = filters.get(40);
        if (offhandFilter != null && offhandFilter.allowShift() && !mainHandItem.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(mainHandItem.getItem()).toString();
            if (!offhandFilter.allowedItems().contains(id)) {
                ci.cancel();
                player.containerMenu.sendAllDataToRemote();
                return;
            }
        }

        // Selected hotbar slot filter: does the offhand item pass the Shift rule?
        SlotWhitelist hotbarFilter = filters.get(hotbarIndex);
        if (hotbarFilter != null && hotbarFilter.allowShift() && !offhandItem.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(offhandItem.getItem()).toString();
            if (!hotbarFilter.allowedItems().contains(id)) {
                ci.cancel();
                player.containerMenu.sendAllDataToRemote();
            }
        }
    }
}
