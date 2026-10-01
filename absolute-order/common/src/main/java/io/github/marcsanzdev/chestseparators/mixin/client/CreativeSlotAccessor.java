package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the wrapped slot inside {@code CreativeModeInventoryScreen$SlotWrapper}.
 *
 * <p>On the creative inventory tab every player slot is a {@code SlotWrapper} that wraps the real
 * {@code InventoryMenu} slot. Its own {@code getContainerSlot()} returns the wrapper's position in the
 * creative handler (armor 5-8, hotbar 36-44, offhand 45) — the handler slot IDs, NOT the inventory
 * indices the server enforces filters by (hotbar 0-8, armor 36-39, offhand 40). Reading the wrapped
 * slot's {@code getContainerSlot()} instead yields the canonical index, exactly what the survival inventory
 * and chests report, so a filter/layout made in creative lands on the right slot everywhere.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface CreativeSlotAccessor {

    @Accessor("target")
    Slot chestseparators$getWrappedSlot();
}
