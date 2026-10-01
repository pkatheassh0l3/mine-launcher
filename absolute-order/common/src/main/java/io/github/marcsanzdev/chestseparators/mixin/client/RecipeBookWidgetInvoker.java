package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// RecipeBookComponent#setVisible is protected; this invoker lets the editor close the book (setVisible(false))
// while a mod sub-screen is open. See GenericContainerScreenMixin and RecipeBookScreenAccessor.
@Mixin(RecipeBookComponent.class)
public interface RecipeBookWidgetInvoker {

    @Invoker("setVisible")
    void chestseparators$setOpen(boolean open);
}
