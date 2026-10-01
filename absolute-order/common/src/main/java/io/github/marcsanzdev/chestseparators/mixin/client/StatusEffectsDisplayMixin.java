package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While a mod sub-screen (layout / filters / presets) is open, the vanilla potion-effect panel drawn beside
// the inventory sits behind the editor's own panels and menus, making them unreadable. Suppress the effect
// panel entirely while editing; it comes back the moment the editor closes. In 1.20.1 the effect panel is
// drawn by EffectRenderingInventoryScreen#renderEffects (there is no E5 EffectsInInventory helper).
@Mixin(EffectRenderingInventoryScreen.class)
public class StatusEffectsDisplayMixin {

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void chestseparators$hideWhileEditing(GuiGraphics context, int mouseX, int mouseY, CallbackInfo ci) {
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        if (editor != null && (editor.isEditMode() || editor.getSession().isPresetsMenuOpen)) {
            ci.cancel();
        }
    }
}
