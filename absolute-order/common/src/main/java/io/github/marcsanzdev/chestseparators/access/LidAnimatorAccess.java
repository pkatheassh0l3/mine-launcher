package io.github.marcsanzdev.chestseparators.access;

import net.minecraft.world.level.block.entity.ChestLidController;

/**
 * Shared duck interface exposing a container's private {@link ChestLidController}. Chests and ender chests
 * each declare their OWN {@code chestLidController} field, so each container gets its own single-target
 * accessor mixin, and both expose the controller through this common interface so the animator code can
 * stay container-agnostic.
 */
public interface LidAnimatorAccess {
    ChestLidController getLidAnimator();
}
