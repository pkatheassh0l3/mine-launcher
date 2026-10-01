package io.github.marcsanzdev.chestseparators.neoforge.mixin;

import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase", remap = false)
public abstract class SophisticatedMenuMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void absoluteOrder$beginShift(Player player, int slot, CallbackInfoReturnable<ItemStack> ci) {
        ClickTracker.IS_SHIFT_CLICK.set(true);
    }
    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void absoluteOrder$endShift(Player player, int slot, CallbackInfoReturnable<ItemStack> ci) {
        ClickTracker.IS_SHIFT_CLICK.set(false);
    }
}
