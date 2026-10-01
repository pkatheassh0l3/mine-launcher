package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the protected {@code moveItemStackTo} so the armor shift-click fallback can reuse it. */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {

    @Invoker("moveItemStackTo")
    boolean chestseparators$moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean fromLast);
}
