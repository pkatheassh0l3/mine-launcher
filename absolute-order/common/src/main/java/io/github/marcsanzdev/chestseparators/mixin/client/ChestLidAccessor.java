package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.access.LidAnimatorAccess;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the chest's private lid controller so the auto-deposit feedback can briefly play the vanilla
// opening animation (client-side, cosmetic). Single-target on purpose to keep the ender-chest counterpart
// independent. See EnderChestLidAccessor and LidAnimatorAccess.
@Mixin(ChestBlockEntity.class)
public interface ChestLidAccessor extends LidAnimatorAccess {

    @Override
    @Accessor("chestLidController")
    ChestLidController getLidAnimator();
}
