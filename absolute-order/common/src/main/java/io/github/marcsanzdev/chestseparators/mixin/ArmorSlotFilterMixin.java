package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends inventory-filter enforcement to {@code ArmorSlot}, which overrides {@code mayPlace} without
 * delegating to its superclass, so {@link SlotWhitelistMixin} on {@code Slot} cannot intercept armor-slot
 * clicks. Applies the same Manual/Shift rule logic. ArmorSlot's members are inherited from {@link Slot}
 * and are reached by casting through {@code Slot} (safe — ArmorSlot extends Slot).
 */
@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class ArmorSlotFilterMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    public void onMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ClickTracker.BYPASS_ENFORCEMENT.get()) return;

        Slot self = (Slot) (Object) this;
        if (!(self.container instanceof Inventory pinv)) return;

        Map<Integer, SlotWhitelist> whitelists = ChestSeparatorsState.INVENTORY_FILTERS.get(pinv.player.getUUID());
        if (whitelists == null) return;

        int slotIndex = self.getContainerSlot();
        SlotWhitelist wl = whitelists.get(slotIndex);
        if (wl == null) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        boolean isAllowedItem = wl.allowedItems().contains(itemId);
        boolean isShift = ClickTracker.IS_SHIFT_CLICK.get();

        if (isShift) {
            if (wl.allowShift() && !isAllowedItem) cir.setReturnValue(false);
        } else {
            if (wl.allowManual() && !isAllowedItem) cir.setReturnValue(false);
        }
    }
}
