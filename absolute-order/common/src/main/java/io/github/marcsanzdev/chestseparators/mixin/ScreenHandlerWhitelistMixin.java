package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import io.github.marcsanzdev.chestseparators.util.FilterPriority;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts the {@code Slot.mayPlace} check inside the {@code AbstractContainerMenu.moveItemStackTo}
 * loop (the shift-click quick-move flow). This redirect enforces the Shift rule on each target slot
 * independently, complementing {@link SlotWhitelistMixin}, and applies the filter-priority ordering.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerWhitelistMixin {

    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Redirect(
            method = "moveItemStackTo",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean enforceWhitelistOnShiftClick(Slot slot, ItemStack stack) {
        // Respect vanilla constraints first (also triggers SlotWhitelistMixin).
        if (!slot.mayPlace(stack)) {
            return false;
        }

        if (slot.container instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();
            int slotIndex = slot.getContainerSlot();

            if (whitelists != null && whitelists.containsKey(slotIndex)) {
                SlotWhitelist whitelist = whitelists.get(slotIndex);

                // Shift rule ON: enforce the item filter. Shift rule OFF: treat as vanilla.
                if (whitelist.allowShift()) {
                    String incomingItemId =
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (!whitelist.allowedItems().contains(incomingItemId)) {
                        return false;
                    }
                }
            }
        }

        // Ordering rule: while a slot dedicated to this item still has room, ordinary slots decline it
        // so vanilla keeps scanning and drops the item into its dedicated slot.
        int[] range = ClickTracker.INSERT_RANGE.get();
        if (range != null && FilterPriority.shouldDefer(this.slots, range[0], range[1], slot, stack)) {
            return false;
        }

        return true;
    }
}
