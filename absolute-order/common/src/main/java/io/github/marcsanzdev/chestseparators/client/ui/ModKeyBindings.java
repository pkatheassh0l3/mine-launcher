package io.github.marcsanzdev.chestseparators.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static KeyMapping openEditorKey;
    public static KeyMapping showPanelModifierKey;
    public static KeyMapping toggleEditButtonsKey;
    public static KeyMapping toggleMagnifierKey;
    public static KeyMapping pushKey;
    public static KeyMapping pullKey;
    public static KeyMapping autoDepositKey;

    // 1.20.1 KeyMapping categories are plain translation-key strings, not the E5 KeyMapping.Category type.
    private static final String CATEGORY = "key.categories.chestseparators";

    public static void register() {
        // Passing GLFW.GLFW_KEY_... directly to the constructor sets that value as the "Reset" default.
        // Architectury's KeyMappingRegistry.register returns void, so each mapping is built first, then registered.

        openEditorKey = new KeyMapping(
                "key.chestseparators.toggle_preview_panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);
        KeyMappingRegistry.register(openEditorKey);

        showPanelModifierKey = new KeyMapping(
                "key.chestseparators.show_panel_modifier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
        KeyMappingRegistry.register(showPanelModifierKey);

        toggleMagnifierKey = new KeyMapping(
                "key.chestseparators.toggle_magnifier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
        KeyMappingRegistry.register(toggleMagnifierKey);

        toggleEditButtonsKey = new KeyMapping(
                "key.chestseparators.toggle_edit_buttons", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
        KeyMappingRegistry.register(toggleEditButtonsKey);

        // Push inventory items INTO the open chest's filters. Hold Shift to also push into empty slots.
        pushKey = new KeyMapping("key.chestseparators.push", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_S, CATEGORY);
        KeyMappingRegistry.register(pushKey);

        // Pull the open chest's items INTO the inventory's filters. Hold Shift to also pull unfiltered items.
        pullKey = new KeyMapping("key.chestseparators.pull", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_D, CATEGORY);
        KeyMappingRegistry.register(pullKey);

        // Unbound by default: the primary trigger is the double-tap-sneak gesture, so this hotkey is an
        // optional alternative the player may bind to avoid any conflict with other movement keys.
        autoDepositKey = new KeyMapping(
                "key.chestseparators.auto_deposit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
        KeyMappingRegistry.register(autoDepositKey);
    }
}
