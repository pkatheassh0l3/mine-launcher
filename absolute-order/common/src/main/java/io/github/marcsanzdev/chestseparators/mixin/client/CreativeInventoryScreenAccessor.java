package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private {@code selectTab} so the editor can jump the creative screen to its
 *  inventory tab when a toolbar button is used (the player inventory is only present on that tab). */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeInventoryScreenAccessor {

    @Invoker("selectTab")
    void chestseparators$setSelectedTab(CreativeModeTab group);
}
