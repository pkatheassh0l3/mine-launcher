package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts the player's block and entity interactions to capture the context needed
 * for the mod's editor UI before the container screen is opened.
 *
 * <p>Minecraft's architecture separates the interaction event from the screen initializer,
 * so {@link ChestPosStorage} acts as a transient bridge between the two call sites.
 */
@Mixin(MultiPlayerGameMode.class)
public class ChestInteractionMixin {

    /**
     * Captures the clicked block position and, if present, the Shulker UUID from its block entity.
     * This runs before the server processes the interaction, so the data is ready by the time
     * the resulting container screen is initialized on the client.
     */
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void captureChestPos(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND) {
            BlockPos clickedPos = hitResult.getBlockPos();

            ChestPosStorage.lastClickedPos = clickedPos;
            ChestPosStorage.isEntityOpened = false;
            ChestPosStorage.lastOpenedShulkerUUID = null;

            if (Minecraft.getInstance().level != null) {
                ChestPosStorage.lastClickedDimension = Minecraft.getInstance()
                        .level
                        .dimension()
                        .location()
                        .toString();

                net.minecraft.world.level.block.entity.BlockEntity be =
                        Minecraft.getInstance().level.getBlockEntity(clickedPos);
                if (be instanceof io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider provider) {
                    ChestPosStorage.lastOpenedShulkerUUID = provider.getShulkerUUID();
                }
            }
        }
    }

    /**
     * Captures the UUID of entity-based inventories (Chest Minecarts, Chest Boats,
     * Donkeys, Llamas, etc.) so the editor can use UUID-keyed storage instead of BlockPos.
     */
    @Inject(method = "interact", at = @At("HEAD"))
    private void captureEntity(
            Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND) {
            if (entity instanceof ContainerEntity
                    || entity.getClass().getName().contains("Chest")) {
                ChestPosStorage.lastClickedEntityUUID = entity.getUUID();
                ChestPosStorage.isEntityOpened = true;
                // Chest/hopper minecarts accept hopper input, so the Hopper rule is available for them.
                ChestPosStorage.isMinecartEntity = entity instanceof AbstractMinecartContainer;
                ChestPosStorage.lastOpenedShulkerUUID = null;

                if (Minecraft.getInstance().level != null) {
                    ChestPosStorage.lastClickedDimension = Minecraft.getInstance()
                            .level
                            .dimension()
                            .location()
                            .toString();
                }
            }
        }
    }
}
