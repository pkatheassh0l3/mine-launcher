package io.github.marcsanzdev.chestseparators.mixin;

import dev.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.WhitelistS2CPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements {@link IWhitelistProvider} on all standard lootable containers (Chests, Barrels, Shulker
 * Boxes — every subclass of {@code RandomizableContainerBlockEntity}) and owns the canonical NBT
 * persistence for whitelist data under the "ChestSeparatorsWhitelists" key.
 *
 * <p>On open it pushes the saved whitelist to the opener (S2C) so the editor shows the server-authoritative
 * state immediately, without a {@code WhitelistRequestPayload} round-trip. The S2C payload type is a
 * dedicated one ({@link WhitelistS2CPayload}) registered by the client receiver at client init — NeoForge
 * forbids a single payload id for both directions, so the C2S save and this S2C push use different ids.
 */
@Mixin(RandomizableContainerBlockEntity.class)
public abstract class LootableContainerBlockEntityMixin extends BlockEntity implements IWhitelistProvider {

    @Unique
    private Map<Integer, SlotWhitelist> chestSeparatorsWhitelists = new HashMap<>();

    public LootableContainerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Map<Integer, SlotWhitelist> getWhitelists() {
        return this.chestSeparatorsWhitelists;
    }

    @Override
    public void setWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        this.chestSeparatorsWhitelists = new HashMap<>(whitelists);
        this.setChanged();
    }

    /**
     * Pushes the saved whitelist to the opening player whenever the container GUI is created, so the client
     * shows the server-authoritative filters immediately (and co-viewers in multiplayer stay in sync).
     */
    @Inject(method = "createMenu", at = @At("RETURN"))
    private void chestseparators$onOpenMenu(
            int syncId, Inventory playerInventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if (cir.getReturnValue() != null
                && this.getLevel() != null
                && !this.getLevel().isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && NetworkManager.canPlayerReceive(serverPlayer, WhitelistS2CPayload.TYPE)) {
            NetworkManager.sendToPlayer(
                    serverPlayer, new WhitelistS2CPayload(this.getBlockPos(), this.chestSeparatorsWhitelists));
        }
    }

    // --- NBT world-save persistence (Codec-based; one shared on-disk shape with the data component) ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.chestSeparatorsWhitelists.isEmpty()) {
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            SlotWhitelist.MAP_CODEC
                    .encodeStart(ops, this.chestSeparatorsWhitelists)
                    .result()
                    .ifPresent(encoded -> tag.put("ChestSeparatorsWhitelists", encoded));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.chestSeparatorsWhitelists.clear();
        if (tag.contains("ChestSeparatorsWhitelists")) {
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            SlotWhitelist.MAP_CODEC
                    .parse(ops, tag.get("ChestSeparatorsWhitelists"))
                    .result()
                    .ifPresent(this.chestSeparatorsWhitelists::putAll);
        }
    }
}
