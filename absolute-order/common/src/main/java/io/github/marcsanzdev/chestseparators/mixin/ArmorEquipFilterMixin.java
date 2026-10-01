package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks right-click armor equip when the target armor slot has an active Shift filter that does not
 * include the item. Vanilla's equip path bypasses {@code mayPlace} entirely, so intercepting at the
 * network-handler level is the only reliable block point.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ArmorEquipFilterMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnArmorEquip(ServerboundUseItemPacket packet, CallbackInfo ci) {
        ItemStack stack = player.getItemInHand(packet.getHand());
        if (stack.isEmpty()) return;

        var slot = player.getEquipmentSlotForItem(stack);

        int rawIndex =
                switch (slot) {
                    case FEET -> 36;
                    case LEGS -> 37;
                    case CHEST -> 38;
                    case HEAD -> 39;
                    default -> -1;
                };
        if (rawIndex == -1) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUUID());
        if (filters == null) return;

        SlotWhitelist wl = filters.get(rawIndex);
        if (wl == null || !wl.allowShift()) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!wl.allowedItems().contains(itemId)) {
            ci.cancel();
            player.containerMenu.sendAllDataToRemote();
        }
    }
}
