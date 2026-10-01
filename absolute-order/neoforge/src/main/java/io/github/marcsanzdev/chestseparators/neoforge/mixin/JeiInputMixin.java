package io.github.marcsanzdev.chestseparators.neoforge.mixin;
import io.github.marcsanzdev.chestseparators.client.ui.RecipeOverlayGuard;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="mezz.jei.gui.input.ClientInputHandler",remap=false)
public abstract class JeiInputMixin {
    @Inject(method={"onKeyboardKeyPressedPre","onKeyboardKeyPressedPost","onKeyboardCharTypedPre","onGuiMouseClicked","onGuiMouseReleased","onGuiMouseScroll","onGuiMouseDragged"},at=@At("HEAD"),cancellable=true)
    private void absoluteOrder$leaveInputToEditor(CallbackInfoReturnable<Boolean> ci){if(RecipeOverlayGuard.active())ci.setReturnValue(false);}
    @Inject(method="onKeyboardCharTypedPost",at=@At("HEAD"),cancellable=true)
    private void absoluteOrder$leaveTextToEditor(CallbackInfo ci){if(RecipeOverlayGuard.active())ci.cancel();}
}
