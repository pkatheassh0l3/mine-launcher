package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces inventory filters on {@code SWAP} clicks (the number/F keys), which bypass {@code mayPlace}
 * by swapping items directly. Treats the swap as a shift-click: if either the source slot or the swap
 * target has the Shift rule enabled and the incoming item is not whitelisted, the swap is cancelled.
 * The swap target is identified by {@code button}: 0-8 = hotbar raw indices, 40 = offhand.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerSwapFilterMixin {

    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnSwap(
            int slotIndex, int button, ClickType actionType, Player player, CallbackInfo ci) {
        if (actionType != ClickType.SWAP) return;
        if (slotIndex < 0 || slotIndex >= slots.size()) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUUID());
        if (filters == null || filters.isEmpty()) return;

        Slot sourceSlot = slots.get(slotIndex);
        ItemStack sourceItem = sourceSlot.getItem(); // moves INTO the swap-target slot

        // button 0-8 = hotbar raw index; button 40 = offhand raw index.
        int targetRawIndex = (button >= 0 && button <= 8) ? button : 40;
        ItemStack targetItem = player.getInventory().getItem(targetRawIndex); // moves INTO sourceSlot

        // Can sourceItem enter the swap-target slot?
        SlotWhitelist wlTarget = filters.get(targetRawIndex);
        if (wlTarget != null && wlTarget.allowShift() && !sourceItem.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(sourceItem.getItem()).toString();
            if (!wlTarget.allowedItems().contains(id)) {
                ci.cancel();
                return;
            }
        }

        // Can targetItem enter the source slot (only relevant when it is a player-inventory slot)?
        if (sourceSlot.container instanceof Inventory) {
            SlotWhitelist wlSource = filters.get(sourceSlot.getContainerSlot());
            if (wlSource != null && wlSource.allowShift() && !targetItem.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(targetItem.getItem()).toString();
                if (!wlSource.allowedItems().contains(id)) {
                    ci.cancel();
                }
            }
        }
    }
}
