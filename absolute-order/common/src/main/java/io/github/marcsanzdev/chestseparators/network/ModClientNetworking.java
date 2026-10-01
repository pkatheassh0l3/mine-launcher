package io.github.marcsanzdev.chestseparators.network;

import dev.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.AutoDepositAnimator;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Registers all client-side S2C packet handlers for the mod. Each handler runs on the main client thread
 * via {@code context.queue(...)}. The matching S2C payload types are registered on the dedicated server in
 * {@link ModNetworking#init()} (so {@code canPlayerReceive} is accurate before any send).
 */
public class ModClientNetworking {

    /** Sends the player's current inventory filters to the server (for the Pick Up rule enforcement). */
    public static void sendInventoryFilters() {
        if (NetworkManager.canServerReceive(InventoryFiltersPayload.TYPE)) {
            NetworkManager.sendToServer(new InventoryFiltersPayload(
                    new java.util.HashMap<>(ChestConfigManager.getInstance().getPlayerInventoryFilters())));
        }
    }

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CommunityReply.TYPE, CommunityReply.CODEC, (payload, context) -> context.queue(() -> io.github.marcsanzdev.chestseparators.client.ui.CommunityClient.receive(payload)));

        // Receives the Shulker Box UUID from the server when a Shulker is opened.
        // Stored immediately so it is available when the screen initializes.
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C, ShulkerUUIDPayload.TYPE, ShulkerUUIDPayload.CODEC, (payload, context) -> {
                    context.queue(() -> {
                        ChestPosStorage.lastOpenedShulkerUUID = payload.shulkerUUID();

                        // Hot-update the editor if the screen is already open (rare but possible).
                        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
                        if (editor != null && editor.session.isShulkerBox) {
                            editor.session.currentShulkerUUID = payload.shulkerUUID();
                            ChestConfigManager.getInstance().loadShulkerConfig(payload.shulkerUUID());
                        }
                    });
                });

        // Receives the server's response to an editor lock request.
        // On success, transitions the editor to VIEW_GROUPS mode.
        // On failure, shows an error status message and plays a rejection sound.
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                EditorLockResponsePayload.TYPE,
                EditorLockResponsePayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
                        if (editor != null
                                && editor.session.currentChestPos != null
                                && editor.session.currentChestPos.equals(payload.pos())) {
                            if (payload.granted()) {
                                editor.hasEditorLock = true;
                                editor.toggleState(EditorState.VIEW_GROUPS);
                                editor.playClickSound(1.0f);
                            } else {
                                editor.showStatus(
                                        Component.translatable("message.chestseparators.locked"), ChatFormatting.RED);
                                editor.playClickSound(0.5f);
                            }
                        }
                    });
                });

        // Receives the result of a radius transfer and animates the items flying (to chests for a
        // deposit, or back to the player for a grab when reverse is set).
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                AutoDepositResultPayload.TYPE,
                AutoDepositResultPayload.CODEC,
                (payload, context) -> context.queue(
                        () -> AutoDepositAnimator.addFlights(payload.flights(), payload.reverse())));

        // Server-authoritative whitelist for a BLOCK container (chest/barrel/…): applied to the client's
        // physical block entity AND the open dummy GUI inventory to kill the one-tick filter flicker.
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C, WhitelistS2CPayload.TYPE, WhitelistS2CPayload.CODEC, (payload, context) -> {
                    context.queue(() -> {
                        Player player = context.getPlayer();
                        if (player == null || player.level() == null) return;

                        var custom = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(player.containerMenu);
                        if (custom != null) {
                            custom.filters(payload.whitelists());
                            ChestConfigManager.getInstance().setCurrentWhitelists(payload.whitelists());
                            return;
                        }
                        // Detect Ender Chest by inspecting the open menu's inventory slots, not the block
                        // position — the payload's pos points to the block the server used, which may differ
                        // from the Ender Chest position in edge cases.
                        boolean isEnderChest = false;
                        if (player.containerMenu != null) {
                            for (Slot slot : player.containerMenu.slots) {
                                if (slot.container instanceof PlayerEnderChestContainer) {
                                    isEnderChest = true;
                                    break;
                                }
                            }
                        }
                        if (isEnderChest) return;

                        // 1. Store data for UI rendering. setCurrentWhitelists replaces the whole working map
                        // with only the container's filters; re-mirror the player-inventory filters so the
                        // preview panel can still show an inventory group's filter while a chest is open.
                        ChestConfigManager.getInstance().setCurrentWhitelists(payload.whitelists());
                        ChestSeparatorsEditor editorForMirror = ChestSeparatorsEditor.getInstance();
                        if (editorForMirror != null && !editorForMirror.session.isInventoryScreenContext) {
                            ChestConfigManager.getInstance().mirrorInventoryIntoCurrent();
                        }

                        // 2. Inject into the client-side physical block entity.
                        BlockEntity be = player.level().getBlockEntity(payload.pos());
                        if (be instanceof IWhitelistProvider provider) {
                            provider.setWhitelists(payload.whitelists());
                        }

                        // 3. THE FLICKER KILLER: inject into the client's dummy GUI inventory.
                        if (player.containerMenu instanceof ChestMenu genericHandler
                                && genericHandler.getContainer() instanceof IWhitelistProvider provider) {
                            provider.setWhitelists(payload.whitelists());
                        }
                    });
                });

        // Server-authoritative whitelist for ENTITY containers (chest minecarts), pushed when the GUI opens
        // and after a save. Mirrors the block-chest handler above but keyed by entity UUID.
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                EntityWhitelistS2CPayload.TYPE,
                EntityWhitelistS2CPayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        Player player = context.getPlayer();
                        if (player == null) return;

                        // An empty payload means the server entity has no stored filter yet — the migration
                        // case for minecarts filtered before this version (their filter lives only in the
                        // local .dat, never synced): ignore it so the local filter is preserved and displayed,
                        // and it will sync up to the server on the next save.
                        if (payload.whitelists().isEmpty()) return;

                        // Inject into the client's open container inventory (the minecart) to keep client-side
                        // prediction consistent and avoid a flicker.
                        if (player.containerMenu instanceof ChestMenu genericHandler
                                && genericHandler.getContainer() instanceof IWhitelistProvider provider) {
                            provider.setWhitelists(payload.whitelists());
                        }

                        // If the editor is open on exactly this minecart, make its displayed filters match the
                        // server, then re-mirror the player inventory (offset keys) so they are not dropped.
                        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
                        if (editor != null
                                && editor.session.isMinecartChest
                                && payload.entityUuid().equals(editor.session.currentEntityUUID)) {
                            ChestConfigManager.getInstance().setCurrentWhitelists(payload.whitelists());
                            ChestConfigManager.getInstance().mirrorInventoryIntoCurrent();
                        }
                    });
                });
    }
}
