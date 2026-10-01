package io.github.marcsanzdev.chestseparators.event;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.AutoDepositRequestPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public class KeyInputHandler {

    // Double-tap-sneak gesture state for the radius auto-deposit trigger.
    private static boolean wasSneakDown = false;
    private static long lastSneakTapTime = 0L;
    private static final long DOUBLE_TAP_WINDOW_MS = 350L;

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (client.player == null) return;

            // These fire only with no screen open (chest closed). The same toggles are wired into the
            // chest screen via GenericContainerScreenMixin so they also work with the chest open.
            if (client.screen != null) {
                return;
            }

            if (client.player == null) return;
            while (ModKeyBindings.openEditorKey.consumeClick()) actionBar(client, togglePreviewPanel());
            while (ModKeyBindings.toggleMagnifierKey.consumeClick()) actionBar(client, toggleMagnifier());
            while (ModKeyBindings.toggleEditButtonsKey.consumeClick()) actionBar(client, toggleEditButtons());

            handleAutoDepositTriggers(client);
        });
    }

    // Fires the radius auto-deposit from either the dedicated (optional) hotkey or a quick
    // double-tap of the sneak key. The server performs the move and replies with the animation data.
    private static void handleAutoDepositTriggers(Minecraft client) {
        boolean sneakDown = client.options.keyShift.isDown();

        if (!GlobalChestConfig.instance.autoDepositEnabled) {
            wasSneakDown = sneakDown;
            return;
        }

        while (ModKeyBindings.autoDepositKey.consumeClick())
            triggerAutoDeposit(client, AutoDepositRequestPayload.ACTION_DEPOSIT_ALL);

        if (GlobalChestConfig.instance.autoDepositDoubleSneak && sneakDown && !wasSneakDown) {
            long now = System.currentTimeMillis();
            if (now - lastSneakTapTime <= DOUBLE_TAP_WINDOW_MS) {
                triggerAutoDeposit(client, AutoDepositRequestPayload.ACTION_DEPOSIT_ALL);
                lastSneakTapTime = 0L;
            } else {
                lastSneakTapTime = now;
            }
        }
        wasSneakDown = sneakDown;
    }

    private static void triggerAutoDeposit(Minecraft client, int action) {
        if (client.player == null || !NetworkManager.canServerReceive(AutoDepositRequestPayload.TYPE)) return;

        int radius = GlobalChestConfig.instance.autoDepositRadius;

        // Entity (chest minecart / boat) filters are client-side only, so gather the ones near the
        // player and forward them keyed by UUID; the server can't read these on its own.
        Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists = new HashMap<>();
        if (client.level != null) {
            AABB box = client.player.getBoundingBox().inflate(radius);
            for (Entity entity :
                    client.level.getEntities(client.player, box, e -> e instanceof IWhitelistProvider)) {
                Map<Integer, SlotWhitelist> wl =
                        ChestConfigManager.getInstance().readEntityWhitelists(entity.getUUID());
                if (!wl.isEmpty()) entityWhitelists.put(entity.getUUID(), wl);
            }
        }

        dev.architectury.networking.NetworkManager.sendToServer(new AutoDepositRequestPayload(
                radius,
                GlobalChestConfig.instance.autoDepositThroughWalls,
                action,
                ChestConfigManager.getInstance().readEnderWhitelists(),
                entityWhitelists));
    }

    // Each toggle flips its config flag and returns the feedback text, leaving the caller to display
    // it: the action bar when no screen is open, or the editor's status overlay when a chest is open
    // (the action bar would otherwise be hidden behind the chest GUI).

    public static Component toggleMagnifier() {
        GlobalChestConfig.instance.magnifierEnabled = !GlobalChestConfig.instance.magnifierEnabled;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.magnifierEnabled,
                "message.chestseparators.magnifier_on",
                "message.chestseparators.magnifier_off");
    }

    public static Component toggleEditButtons() {
        GlobalChestConfig.instance.showEditButtons = !GlobalChestConfig.instance.showEditButtons;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.showEditButtons,
                "message.chestseparators.edit_buttons_visible",
                "message.chestseparators.edit_buttons_hidden");
    }

    public static Component togglePreviewPanel() {
        GlobalChestConfig.instance.showLeftPanel = !GlobalChestConfig.instance.showLeftPanel;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.showLeftPanel,
                "message.chestseparators.preview_panel_visible",
                "message.chestseparators.preview_panel_hidden");
    }

    public static Component toggleDepositButton() {
        GlobalChestConfig.instance.showDepositButton = !GlobalChestConfig.instance.showDepositButton;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.showDepositButton,
                "message.chestseparators.deposit_button_visible",
                "message.chestseparators.deposit_button_hidden");
    }

    private static Component label(boolean on, String onKey, String offKey) {
        return Component.translatable(on ? onKey : offKey);
    }

    private static void actionBar(Minecraft client, Component message) {
        if (client.player != null) client.player.displayClientMessage(message.copy().withStyle(ChatFormatting.GRAY), true);
    }

    public static boolean isModifierPressed() {
        // Whether the "show panel" modifier key (default Left Alt) is currently held. Reading the mapping's
        // live pressed state keeps this loader-agnostic (no Fabric KeyBindingHelper / raw GLFW poll needed).
        // Over an open container vanilla stops updating KeyMapping down-state, so isDown() reads
        // false there; poll the bound key raw (as the Shift checks do), falling back to isDown().
        InputConstants.Key bound = InputConstants.getKey(ModKeyBindings.showPanelModifierKey.saveString());
        if (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() != GLFW.GLFW_KEY_UNKNOWN) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (GLFW.glfwGetKey(window, bound.getValue()) == GLFW.GLFW_PRESS) return true;
        }
        return ModKeyBindings.showPanelModifierKey.isDown();
    }
}
