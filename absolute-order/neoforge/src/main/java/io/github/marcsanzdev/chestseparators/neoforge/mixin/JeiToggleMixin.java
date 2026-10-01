package io.github.marcsanzdev.chestseparators.neoforge.mixin;
import io.github.marcsanzdev.chestseparators.client.ui.RecipeOverlayGuard;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="mezz.jei.common.config.ClientToggleState",remap=false)
public abstract class JeiToggleMixin {
    @Inject(method={"isOverlayEnabled","isBookmarkOverlayEnabled"},at=@At("HEAD"),cancellable=true)
    private void absoluteOrder$hideDuringEditing(CallbackInfoReturnable<Boolean> ci){if(RecipeOverlayGuard.active())ci.setReturnValue(false);}
    @Inject(method={"toggleOverlayEnabled","toggleBookmarkEnabled"},at=@At("HEAD"),cancellable=true)
    private void absoluteOrder$preservePreference(CallbackInfo ci){if(RecipeOverlayGuard.active())ci.cancel();}
}
