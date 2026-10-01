package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.FilterPriority;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces the Hopper insertion rule on all containers that implement {@link IWhitelistProvider}. When
 * the Hopper rule is enabled for a slot, only whitelisted items may be deposited by automation; when
 * disabled, the slot accepts any item as if unfiltered.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "canPlaceItemInContainer", at = @At("HEAD"), cancellable = true)
    private static void onCanInsert(
            Container inventory, ItemStack stack, int slot, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (inventory instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();

            if (whitelists != null && whitelists.containsKey(slot)) {
                SlotWhitelist wl = whitelists.get(slot);

                // Hopper rule ON: enforce the whitelist. Rule OFF: let vanilla decide.
                if (wl.allowHopper()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (!wl.allowedItems().contains(itemId)) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }
        }

        // Same ordering rule as the shift-click quick-move: an item that has a dedicated filter slot
        // fills that slot first, so ordinary slots decline it while the dedicated one still has room.
        if (FilterPriority.shouldDefer(inventory, slot, stack)) {
            cir.setReturnValue(false);
        }
    }
}
