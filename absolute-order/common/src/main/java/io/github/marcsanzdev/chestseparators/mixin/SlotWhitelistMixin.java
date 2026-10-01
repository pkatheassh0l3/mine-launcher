package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link Slot#mayPlace} to enforce whitelist rules for direct (cursor) and shift-click
 * insertions. Uses {@link ClickTracker#IS_SHIFT_CLICK} to distinguish the two, since both flow through
 * {@code mayPlace}. If the rule toggle is OFF the slot behaves as vanilla; if ON and the item is not
 * whitelisted, insertion is blocked.
 */
@Mixin(Slot.class)
public abstract class SlotWhitelistMixin {

    @Shadow
    @Final
    public Container container;

    @Shadow
    public abstract int getContainerSlot();

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    public void onMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // The editor probes mayPlace to detect vanilla slot restrictions; don't let our own filter
        // enforcement pollute that probe (it would hide every unfiltered item when editing a filter).
        if (ClickTracker.BYPASS_ENFORCEMENT.get()) return;

        Map<Integer, SlotWhitelist> whitelists = null;

        if (this.container instanceof IWhitelistProvider provider) {
            // Block containers and entities carry their whitelist on the inventory itself.
            whitelists = provider.getWhitelists();
        } else if (this.container instanceof Inventory pinv) {
            // The player's own inventory filters live server-side, keyed by raw inventory index
            // (which matches Slot#getContainerSlot). Enforced here for manual and shift-click placement.
            whitelists = ChestSeparatorsState.INVENTORY_FILTERS.get(pinv.player.getUUID());
        }

        if (whitelists != null && whitelists.containsKey(this.getContainerSlot())) {
            SlotWhitelist wl = whitelists.get(this.getContainerSlot());
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            boolean isAllowedItem = wl.allowedItems().contains(itemId);
            boolean isShift = ClickTracker.IS_SHIFT_CLICK.get();

            if (isShift) {
                if (wl.allowShift() && !isAllowedItem) {
                    cir.setReturnValue(false);
                }
            } else {
                if (wl.allowManual() && !isAllowedItem) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
