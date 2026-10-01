package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the client-side equip prediction (animation flicker) when right-clicking armor
 * whose target slot has a Shift filter that blocks the item. Without this, the server cancels
 * the equip one tick later, causing a visible flash of the armor being worn momentarily.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ArmorEquipClientMixin {

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void blockArmorEquipIfFiltered(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return;

        var slot = player.getEquipmentSlotForItem(stack);

        int rawIndex =
                switch (slot) {
                    case FEET -> 36;
                    case LEGS -> 37;
                    case CHEST -> 38;
                    case HEAD -> 39;
                    default -> -1;
                };
        if (rawIndex == -1) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUUID());
        if (filters == null) return;

        SlotWhitelist wl = filters.get(rawIndex);
        if (wl == null || !wl.allowShift()) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!wl.allowedItems().contains(itemId)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
