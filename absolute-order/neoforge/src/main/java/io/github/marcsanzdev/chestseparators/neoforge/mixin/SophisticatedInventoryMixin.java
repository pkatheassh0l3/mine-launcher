package io.github.marcsanzdev.chestseparators.neoforge.mixin;

import io.github.marcsanzdev.chestseparators.neoforge.SophisticatedCompatibility;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler", remap = false)
public abstract class SophisticatedInventoryMixin {
    @Shadow @Final protected IStorageWrapper storageWrapper;
    @Inject(method = {"insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;", "insertItemOnlyToSlot"},
            at = @At("HEAD"), cancellable = true)
    private void absoluteOrder$filter(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> ci) {
        var rule = SophisticatedCompatibility.read(storageWrapper).get(slot);
        if (rule != null && rule.allowHopper()
                && !rule.allowedItems().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
            ci.setReturnValue(stack);
    }
}
