package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mirrors {@link SlotWhitelistMixin} for {@link ShulkerBoxSlot}, which overrides {@code mayPlace} without
 * calling {@code super}, so the base-slot mixin never fires for Shulker Box slots.
 */
@Mixin(ShulkerBoxSlot.class)
public abstract class ShulkerBoxSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    public void onMayPlaceShulker(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot thisSlot = (Slot) (Object) this;

        if (thisSlot.container instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();

            if (whitelists != null && whitelists.containsKey(thisSlot.getContainerSlot())) {
                SlotWhitelist wl = whitelists.get(thisSlot.getContainerSlot());
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
    }
}
