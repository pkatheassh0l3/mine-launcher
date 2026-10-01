package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transfers mod data between placed BlockEntities and dropped ItemStacks via Minecraft's Data Component
 * system, so a container's whitelist (and a Shulker's UUID) survives being broken and re-placed.
 *
 * <ul>
 *   <li>{@code collectImplicitComponents} — copies whitelist/UUID data into the dropped ItemStack's components.</li>
 *   <li>{@code applyComponentsFromItemStack} — restores that data back into the BlockEntity when placed.</li>
 * </ul>
 *
 * <p>The restore side hooks {@code applyComponentsFromItemStack(ItemStack)} rather than the
 * {@code applyImplicitComponents(BlockEntity.DataComponentInput)} used on later versions: in 1.21.1 that
 * inner type is {@code protected} and cannot be named from this package, whereas the ItemStack overload is
 * public and exposes the same components directly.
 *
 * <p>NBT world-save persistence is handled separately by {@link LootableContainerBlockEntityMixin}.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityComponentMixin {

    @Inject(method = "collectImplicitComponents", at = @At("TAIL"))
    protected void onCollectImplicitComponents(DataComponentMap.Builder builder, CallbackInfo ci) {
        if ((Object) this instanceof IShulkerUUIDProvider shulkerProvider) {
            UUID uuid = shulkerProvider.getShulkerUUID();
            if (uuid != null) {
                builder.set(ChestSeparatorsComponents.SHULKER_UUID.get(), uuid.toString());
            }
        }

        if ((Object) this instanceof IWhitelistProvider whitelistProvider) {
            Map<Integer, SlotWhitelist> map = whitelistProvider.getWhitelists();
            if (map != null && !map.isEmpty()) {
                builder.set(ChestSeparatorsComponents.SLOT_WHITELISTS.get(), map);
            }
        }
    }

    @Inject(method = "applyComponentsFromItemStack", at = @At("TAIL"))
    protected void onApplyComponentsFromItemStack(ItemStack stack, CallbackInfo ci) {
        if ((Object) this instanceof IShulkerUUIDProvider shulkerProvider) {
            String uuidStr = stack.get(ChestSeparatorsComponents.SHULKER_UUID.get());
            if (uuidStr != null && !uuidStr.isEmpty()) {
                try {
                    shulkerProvider.setShulkerUUID(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if ((Object) this instanceof IWhitelistProvider whitelistProvider) {
            Map<Integer, SlotWhitelist> map = stack.get(ChestSeparatorsComponents.SLOT_WHITELISTS.get());
            if (map != null && !map.isEmpty()) {
                whitelistProvider.setWhitelists(map);
            }
        }
    }
}
