package io.github.marcsanzdev.chestseparators;

import dev.architectury.event.events.client.ClientPlayerEvent;
import io.github.marcsanzdev.chestseparators.client.ui.AutoDepositAnimator;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.event.KeyInputHandler;
import io.github.marcsanzdev.chestseparators.network.ModClientNetworking;

/**
 * Common (loader-agnostic) client bootstrap, run from each platform's client entry point. Registers the
 * keybinds, the client tick/input poll, the S2C receivers and the auto-deposit render/tick hooks, then
 * wires the world-join handler that primes the local config and syncs the inventory filters up to the
 * server so the Pick Up rule works before the editor is ever opened.
 */
public final class AbsoluteOrderClient {

    private AbsoluteOrderClient() {}

    public static void init() {
        ModKeyBindings.register();
        KeyInputHandler.register();
        ModClientNetworking.register();
        AutoDepositAnimator.register();

        // On joining a world, load the inventory profile into the render cache and push the inventory
        // filters to the server so the Pick Up rule works immediately (before the editor is opened).
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            // Create the ready-made presets on first run (no-op afterwards), in a context where the client
            // run directory is guaranteed available.
            ChestConfigManager.getInstance().seedDefaultPresetsIfNeeded();
            ChestConfigManager.getInstance().loadInventoryProfile();
            ModClientNetworking.sendInventoryFilters();
        });
    }
}
