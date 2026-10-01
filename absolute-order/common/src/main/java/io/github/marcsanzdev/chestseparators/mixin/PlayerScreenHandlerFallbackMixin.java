package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When shift-clicking armor whose target armor slot is blocked by a Shift filter, vanilla's
 * {@code quickMoveStack} returns without a fallback — the item stays put. This adds a fallback that
 * inserts the armor into the main inventory or hotbar instead (like shift-clicking any non-armor item).
 *
 * <p>InventoryMenu slot layout: 0=craft output, 1-4=craft grid, 5-8=armor, 9-35=main, 36-44=hotbar,
 * 45=offhand. {@code moveItemStackTo} is reached via {@link AbstractContainerMenuAccessor}.
 */
@Mixin(InventoryMenu.class)
public abstract class PlayerScreenHandlerFallbackMixin {

    @Inject(method = "quickMoveStack", at = @At("RETURN"), cancellable = true)
    private void chestseparators$armorFallback(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (!cir.getReturnValue().isEmpty()) return;

        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (slotIndex < 0 || slotIndex >= self.slots.size()) return;
        Slot slot = self.slots.get(slotIndex);
        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty()) return;

        var armorSlot = player.getEquipmentSlotForItem(slotStack);

        int rawIndex =
                switch (armorSlot) {
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
        String itemId = BuiltInRegistries.ITEM.getKey(slotStack.getItem()).toString();
        if (wl.allowedItems().contains(itemId)) return;

        ItemStack copy = slotStack.copy();
        boolean fromHotbar = slotIndex >= 36 && slotIndex < 45;
        AbstractContainerMenuAccessor acc = (AbstractContainerMenuAccessor) (Object) this;
        if (fromHotbar) {
            acc.chestseparators$moveItemStackTo(slotStack, 9, 36, false);
        } else {
            if (!acc.chestseparators$moveItemStackTo(slotStack, 36, 45, false)) {
                acc.chestseparators$moveItemStackTo(slotStack, 9, 36, false);
            }
        }

        if (slotStack.getCount() != copy.getCount()) {
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            slot.setChanged();
            cir.setReturnValue(copy);
        }
    }
}
