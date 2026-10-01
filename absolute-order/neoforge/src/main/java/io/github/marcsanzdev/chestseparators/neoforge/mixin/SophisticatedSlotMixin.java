package io.github.marcsanzdev.chestseparators.neoforge.mixin;

import io.github.marcsanzdev.chestseparators.neoforge.SophisticatedCompatibility;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageInventorySlot", remap = false)
public abstract class SophisticatedSlotMixin {
    @Shadow @Final private IStorageWrapper storageWrapper;
    @Shadow @Final private int slotIndex;
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void absoluteOrder$filter(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (ClickTracker.BYPASS_ENFORCEMENT.get()) return;
        var rule = SophisticatedCompatibility.read(storageWrapper).get(slotIndex);
        if (rule != null && (ClickTracker.IS_SHIFT_CLICK.get() ? rule.allowShift() : rule.allowManual())
                && !rule.allowedItems().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
            ci.setReturnValue(false);
    }
}
