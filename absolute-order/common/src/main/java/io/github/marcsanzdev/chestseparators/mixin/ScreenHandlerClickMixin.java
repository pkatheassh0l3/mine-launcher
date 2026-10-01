package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sets the {@link ClickTracker#IS_SHIFT_CLICK} thread-local for the duration of {@code moveItemStackTo},
 * the internal method vanilla calls during shift-click quick-move, so {@link SlotWhitelistMixin} can tell
 * shift-clicks from direct cursor insertions (both arrive at {@code Slot#mayPlace}). Also publishes the
 * destination range so the filter-priority rule only defers to slots this insertion can reach.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerClickMixin {

    @Inject(method = "moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z", at = @At("HEAD"))
    protected void onInsertItemBegin(
            ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        ClickTracker.IS_SHIFT_CLICK.set(true);
        ClickTracker.INSERT_RANGE.set(new int[] {startIndex, endIndex});
    }

    @Inject(method = "moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z", at = @At("RETURN"))
    protected void onInsertItemEnd(
            ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        ClickTracker.IS_SHIFT_CLICK.set(false);
        ClickTracker.INSERT_RANGE.remove();
    }
}
