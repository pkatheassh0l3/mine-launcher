package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.access.LidAnimatorAccess;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Ender-chest counterpart of ChestLidAccessor. Kept as its own single-target accessor so each container
// exposes its own chestLidController through the shared LidAnimatorAccess interface.
@Mixin(EnderChestBlockEntity.class)
public interface EnderChestLidAccessor extends LidAnimatorAccess {

    @Override
    @Accessor("chestLidController")
    ChestLidController getLidAnimator();
}
