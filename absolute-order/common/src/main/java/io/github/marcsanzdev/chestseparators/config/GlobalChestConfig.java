package io.github.marcsanzdev.chestseparators.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class GlobalChestConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Resolved lazily rather than in a static initializer so that merely reading config values does
    // not touch the loader config directory (which is unavailable in unit tests). Architectury's
    // Platform.getConfigFolder() gives the same path on every loader.
    private static File configFile() {
        return new File(Platform.getConfigFolder().toFile(), "chestseparators_config.json");
    }

    public static ConfigData instance = new ConfigData();

    /** Shape of the painting/eyedropper magnifier loupe. */
    public enum MagnifierShape {
        CIRCLE,
        SQUARE
    }

    public static class ConfigData {
        // --- CLIENT / VISUALS ---
        public boolean darkMode = true;
        public boolean showLeftPanel = true;
        public boolean enablePreviewAnimation = true;
        // --- EDITOR TOOLBAR (right-side vertical dock) ---
        // showEditButtons is the MASTER toggle for the whole toolbar. Each icon below can be turned on
        // or off individually from the mod settings (ModMenu); there is no keybind for this.
        public boolean showEditButtons = true;
        public boolean showDepositButton = true;
        public boolean btnEditLines = true;
        public boolean btnFilters = true;
        public boolean btnFillFromChest = true;
        public boolean btnInventoryPresets = true;
        public boolean btnChestPresets = true;
        public boolean closeOnClickOutside = true;
        // When true, the custom-color column (third palette column) is shared across the Lines,
        // Backgrounds and Combo tabs. When false, each tab keeps its own custom colors.
        public boolean sharedCustomColors = true;
        // Zoom loupe shown while painting separators or using the eyedropper. Off by default; players
        // are hinted that they can enable it (key L or config) if precise editing is tricky.
        public boolean magnifierEnabled = false;
        public MagnifierShape magnifierShape = MagnifierShape.CIRCLE;
        // Which painting tabs show the loupe (the eyedropper always shows it when enabled). Lines on by
        // default; backgrounds/combo off by default but left for the user to opt into.
        public boolean magnifierTabLines = true;
        public boolean magnifierTabBackgrounds = false;
        public boolean magnifierTabCombo = false;
        public int bgTransparency = 40;
        public int lineTransparency = 100;

        // When a push/pull auto-organizes the player inventory's filtered groups by priority, keep the
        // hotbar (slots 0-8) untouched so an item you might be holding never gets moved out from under you.
        // Turn it off to let the hotbar auto-sort too, accepting that risk.
        public boolean lockHotbarOnReorder = true;

        // --- AUTO-DEPOSIT TO NEARBY CHESTS ---
        // Master switch for the radius auto-deposit feature: stand in your storage area, trigger it,
        // and every inventory item is sorted into nearby chests whose filters list it.
        public boolean autoDepositEnabled = true;
        // Enable the double-tap-sneak gesture as a trigger (in addition to the optional hotkey).
        public boolean autoDepositDoubleSneak = true;
        // Search radius in blocks around the player for filtered containers.
        public int autoDepositRadius = 8;
        // When false (default) a chest is skipped if a solid block obstructs the straight line between
        // the player and the chest. When true, items can be deposited through walls.
        public boolean autoDepositThroughWalls = false;
        // Show the 3D items flying from the player to each chest. When off, the deposit still happens
        // (with sound + message) but no items are animated.
        public boolean autoDepositAnimation = true;
        // Leave a particle trail behind each flying item. Only applies when the animation is on.
        public boolean autoDepositTrail = true;

        // --- PLAYER INVENTORY DECORATIONS / FILTERS ---
        // The active inventory layout (separators, backgrounds and filters) is always stored per-world
        // (and, being client-side, per account), so it never bleeds across worlds/servers. Inventory
        // presets, by contrast, are global reusable templates. See ChestConfigManager#getInventoryFile
        // and #getInventoryPresetFile.

        // How many inventory preset slots are available (bound to number keys 1-9 in the editor).
        public int inventoryPresetCount = 9;

        // --- SERVER / MECHANICS ---
        public boolean defaultRuleManual = true;
        public boolean defaultRuleShift = true;
        public boolean defaultRuleHopper = true;
        // The third insertion rule is context-dependent: "Hopper" on a chest filter, "Pick Up" on an
        // inventory filter (both share SlotWhitelist#allowHopper). This holds the default for the Pick Up
        // side so inventory groups can default independently from chest groups.
        public boolean defaultRulePickup = true;
        public boolean expelInvalidItems = false;
    }

    public static boolean isShowEditButton() {
        return instance.showEditButtons;
    }

    public static void loadConfig() {
        File configFile = configFile();
        if (configFile.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                instance = GSON.fromJson(reader, ConfigData.class);
            } catch (Exception e) {
                e.printStackTrace();
                instance = new ConfigData();
            }
        } else {
            saveConfig();
        }
    }

    public static void saveConfig() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
