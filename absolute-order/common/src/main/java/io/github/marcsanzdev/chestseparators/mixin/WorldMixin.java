package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Listens for block replacements and removes the corresponding local config file
 * when a position-keyed container (chest, trapped chest, or barrel) is destroyed on the client side.
 *
 * <p>Registered in the mixin config's {@code client} section so it only ever applies on the physical
 * client — the dedicated-server JVM never loads {@code ChestConfigManager} (a client-only class, kept off
 * the server by that client-section registration + the client entrypoint). The {@code isClientSide()} guard
 * then restricts it to the logical client side of the world (the integrated server shares the client JVM).
 */
@Mixin(Level.class)
public abstract class WorldMixin {

    @Shadow
    public abstract boolean isClientSide();

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Shadow
    public abstract ResourceKey<Level> dimension();

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void onSetBlockState(
            BlockPos pos, BlockState newState, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        // Only act on the logical client side of the world.
        if (this.isClientSide()) {
            BlockState oldState = this.getBlockState(pos);

            // Position-keyed containers whose separator config is stored by BlockPos:
            // chests (incl. trapped) and barrels. Shulker boxes (UUID-keyed) and ender chests
            // (global) use their own persistence models and must NOT be cleaned up here.
            boolean wasPosKeyedContainer =
                    oldState.getBlock() instanceof ChestBlock || oldState.getBlock() instanceof BarrelBlock;

            if (wasPosKeyedContainer && oldState.getBlock() != newState.getBlock()) {
                String dim = this.dimension().location().toString();
                io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                        .clearChest(pos, dim);
            }
        }
    }
}
