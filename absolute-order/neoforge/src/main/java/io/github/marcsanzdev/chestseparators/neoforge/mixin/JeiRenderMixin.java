package io.github.marcsanzdev.chestseparators.neoforge.mixin;
import io.github.marcsanzdev.chestseparators.client.ui.RecipeOverlayGuard;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="mezz.jei.gui.events.GuiEventHandler",remap=false)
public abstract class JeiRenderMixin {
    @Inject(method={"drawForScreenBackground","drawForScreenForeground"},at=@At("HEAD"),cancellable=true)
    private void absoluteOrder$leaveScreenToEditor(CallbackInfo ci){if(RecipeOverlayGuard.active())ci.cancel();}
}
