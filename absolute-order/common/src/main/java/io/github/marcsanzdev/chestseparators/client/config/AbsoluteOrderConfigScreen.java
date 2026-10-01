package io.github.marcsanzdev.chestseparators.client.config;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds the mod's Cloth Config screen. Loader-agnostic: the Fabric ModMenu integration and the NeoForge
 * {@code IConfigScreenFactory} both delegate here so the whole config UI lives in one place.
 */
public final class AbsoluteOrderConfigScreen {

    private AbsoluteOrderConfigScreen() {}

    /** A boolean toggle carrying the mod's icon on the left. {@code key} is the short config suffix. */
    private static IconBooleanListEntry iconToggle(
            String key, boolean value, boolean def, Consumer<Boolean> save, ResourceLocation icon) {
        return new IconBooleanListEntry(
                Component.translatable("config.chestseparators." + key),
                value,
                def,
                save,
                Component.translatable("config.chestseparators." + key + ".tooltip"),
                icon);
    }

    public static Screen buildScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.chestseparators.config"));

        // Persist the mod config, flush the key bindings the hotkeys category edited, and save vanilla options.
        builder.setSavingRunnable(() -> {
            GlobalChestConfig.saveConfig();
            Minecraft.getInstance().options.save();
            KeyMapping.resetMapping();
        });
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // --- CATEGORY 1: CLIENT / VISUALS & DEFAULTS ---
        ConfigCategory clientCategory =
                builder.getOrCreateCategory(Component.translatable("config.chestseparators.category.client"));

        // ================= INTERFACE =================
        List<AbstractConfigListEntry> toolbarEntries = new ArrayList<>();
        toolbarEntries.add(iconToggle(
                "btn_edit_lines",
                GlobalChestConfig.instance.btnEditLines,
                true,
                v -> GlobalChestConfig.instance.btnEditLines = v,
                ModTextures.ICON_SM_COMBO));
        toolbarEntries.add(iconToggle(
                "btn_filters",
                GlobalChestConfig.instance.btnFilters,
                true,
                v -> GlobalChestConfig.instance.btnFilters = v,
                ModTextures.ICON_SM_FILTER));
        toolbarEntries.add(iconToggle(
                "show_deposit_button",
                GlobalChestConfig.instance.showDepositButton,
                true,
                v -> GlobalChestConfig.instance.showDepositButton = v,
                ModTextures.ICON_SM_FILL));

        toolbarEntries.add(iconToggle(
                "btn_chest_presets",
                GlobalChestConfig.instance.btnChestPresets,
                true,
                v -> GlobalChestConfig.instance.btnChestPresets = v,
                ModTextures.ICON_SM_CHEST_PRESETS));


        List<AbstractConfigListEntry> interfaceEntries = new ArrayList<>();
        interfaceEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.show_left_panel"),
                        GlobalChestConfig.instance.showLeftPanel)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.chestseparators.show_left_panel.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.showLeftPanel = newValue)
                .build());
        interfaceEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.enable_preview_animation"),
                        GlobalChestConfig.instance.enablePreviewAnimation)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.chestseparators.enable_preview_animation.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.enablePreviewAnimation = newValue)
                .build());
        interfaceEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.show_edit_buttons"),
                        GlobalChestConfig.instance.showEditButtons)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.chestseparators.show_edit_buttons.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.showEditButtons = newValue)
                .build());
        interfaceEntries.add(entryBuilder
                .startSubCategory(Component.translatable("config.chestseparators.group.toolbar_buttons"), toolbarEntries)
                .setExpanded(false)
                .build());
        interfaceEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.close_on_click_outside"),
                        GlobalChestConfig.instance.closeOnClickOutside)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.chestseparators.close_on_click_outside.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.closeOnClickOutside = newValue)
                .build());
        clientCategory.addEntry(entryBuilder
                .startSubCategory(Component.translatable("config.chestseparators.group.interface"), interfaceEntries)
                .setExpanded(false)
                .build());

        // ================= LAYOUT =================
        List<AbstractConfigListEntry> layoutEntries = new ArrayList<>();
        layoutEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.shared_custom_colors"),
                        GlobalChestConfig.instance.sharedCustomColors)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.chestseparators.shared_custom_colors.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.sharedCustomColors = newValue)
                .build());
        layoutEntries.add(new IconIntegerSliderEntry(
                Component.translatable("config.chestseparators.line_transparency"),
                0,
                100,
                GlobalChestConfig.instance.lineTransparency,
                100,
                v -> GlobalChestConfig.instance.lineTransparency = v,
                Component.translatable("config.chestseparators.line_transparency.tooltip"),
                ModTextures.ICON_SM_PENCIL));
        layoutEntries.add(new IconIntegerSliderEntry(
                Component.translatable("config.chestseparators.bg_transparency"),
                0,
                100,
                GlobalChestConfig.instance.bgTransparency,
                40,
                v -> GlobalChestConfig.instance.bgTransparency = v,
                Component.translatable("config.chestseparators.bg_transparency.tooltip"),
                ModTextures.ICON_SM_BRUSH));
        clientCategory.addEntry(entryBuilder
                .startSubCategory(Component.translatable("config.chestseparators.group.layout"), layoutEntries)
                .setExpanded(false)
                .build());

        // ================= FILTERS =================
        // Each default-rule toggle carries the same icon its rule button uses in the filter editor. The third
        // rule is shown twice — Hopper (chest filters) and Pick Up (inventory filters) — since they default
        // independently even though they share one stored flag.
        List<AbstractConfigListEntry> filterEntries = new ArrayList<>();
        filterEntries.add(iconToggle(
                "default_rule_manual",
                GlobalChestConfig.instance.defaultRuleManual,
                true,
                v -> GlobalChestConfig.instance.defaultRuleManual = v,
                ModTextures.ICON_SM_MANUAL));
        filterEntries.add(iconToggle(
                "default_rule_shift",
                GlobalChestConfig.instance.defaultRuleShift,
                true,
                v -> GlobalChestConfig.instance.defaultRuleShift = v,
                ModTextures.ICON_SM_SHIFT));
        filterEntries.add(iconToggle(
                "default_rule_hopper",
                GlobalChestConfig.instance.defaultRuleHopper,
                true,
                v -> GlobalChestConfig.instance.defaultRuleHopper = v,
                ModTextures.ICON_SM_HOPPER));
        filterEntries.add(iconToggle(
                "default_rule_pickup",
                GlobalChestConfig.instance.defaultRulePickup,
                true,
                v -> GlobalChestConfig.instance.defaultRulePickup = v,
                ModTextures.ICON_SM_PICKUP));
        clientCategory.addEntry(entryBuilder
                .startSubCategory(Component.translatable("config.chestseparators.group.filters"), filterEntries)
                .setExpanded(false)
                .build());

        // ================= MAGNIFIER =================
        List<AbstractConfigListEntry> magnifierEntries = new ArrayList<>();
        magnifierEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.magnifier_enabled"),
                        GlobalChestConfig.instance.magnifierEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.chestseparators.magnifier_enabled.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.magnifierEnabled = newValue)
                .build());
        magnifierEntries.add(entryBuilder
                .startEnumSelector(
                        Component.translatable("config.chestseparators.magnifier_shape"),
                        GlobalChestConfig.MagnifierShape.class,
                        GlobalChestConfig.instance.magnifierShape)
                .setDefaultValue(GlobalChestConfig.MagnifierShape.CIRCLE)
                .setEnumNameProvider(shape -> Component.translatable("config.chestseparators.magnifier_shape."
                        + ((Enum<?>) shape).name().toLowerCase()))
                .setTooltip(Component.translatable("config.chestseparators.magnifier_shape.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.magnifierShape = newValue)
                .build());
        magnifierEntries.add(iconToggle(
                "magnifier_tab_lines",
                GlobalChestConfig.instance.magnifierTabLines,
                true,
                v -> GlobalChestConfig.instance.magnifierTabLines = v,
                ModTextures.ICON_SM_PENCIL));
        magnifierEntries.add(iconToggle(
                "magnifier_tab_backgrounds",
                GlobalChestConfig.instance.magnifierTabBackgrounds,
                false,
                v -> GlobalChestConfig.instance.magnifierTabBackgrounds = v,
                ModTextures.ICON_SM_BRUSH));
        magnifierEntries.add(iconToggle(
                "magnifier_tab_combo",
                GlobalChestConfig.instance.magnifierTabCombo,
                false,
                v -> GlobalChestConfig.instance.magnifierTabCombo = v,
                ModTextures.ICON_SM_COMBO));
        clientCategory.addEntry(entryBuilder
                .startSubCategory(Component.translatable("config.chestseparators.group.magnifier"), magnifierEntries)
                .setExpanded(false)
                .build());

        // Auto-deposit options, collapsed into one sub-category.
        List<AbstractConfigListEntry> autoDepositEntries = new ArrayList<>();
        autoDepositEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.lock_hotbar_on_reorder"),
                        GlobalChestConfig.instance.lockHotbarOnReorder)
                .setDefaultValue(true)
                // Two-line tooltip: a plain first line, then a dimmed clarification underneath.
                .setTooltip(
                        Component.translatable("config.chestseparators.lock_hotbar_on_reorder.tooltip"),
                        Component.translatable("config.chestseparators.lock_hotbar_on_reorder.tooltip2")
                                .withStyle(ChatFormatting.DARK_GRAY))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.lockHotbarOnReorder = newValue)
                .build());
        autoDepositEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.auto_deposit_enabled"),
                        GlobalChestConfig.instance.autoDepositEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.chestseparators.auto_deposit_enabled.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositEnabled = newValue)
                .build());
        autoDepositEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.auto_deposit_double_sneak"),
                        GlobalChestConfig.instance.autoDepositDoubleSneak)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.chestseparators.auto_deposit_double_sneak.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositDoubleSneak = newValue)
                .build());
        autoDepositEntries.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.chestseparators.auto_deposit_radius"),
                        GlobalChestConfig.instance.autoDepositRadius,
                        3,
                        32)
                .setDefaultValue(8)
                .setTooltip(Component.translatable("config.chestseparators.auto_deposit_radius.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositRadius = newValue)
                .build());
        autoDepositEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.auto_deposit_through_walls"),
                        GlobalChestConfig.instance.autoDepositThroughWalls)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.chestseparators.auto_deposit_through_walls.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositThroughWalls = newValue)
                .build());
        autoDepositEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.auto_deposit_animation"),
                        GlobalChestConfig.instance.autoDepositAnimation)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.chestseparators.auto_deposit_animation.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositAnimation = newValue)
                .build());
        autoDepositEntries.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.chestseparators.auto_deposit_trail"),
                        GlobalChestConfig.instance.autoDepositTrail)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.chestseparators.auto_deposit_trail.tooltip"))
                .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositTrail = newValue)
                .build());
        clientCategory.addEntry(entryBuilder
                .startSubCategory(Component.translatable("config.chestseparators.group.autodeposit"), autoDepositEntries)
                .setExpanded(false)
                .build());

        // --- CATEGORY 2: SERVER / MECHANICS ---
        // Server mechanics options are only exposed in singleplayer, where the server and client share a JVM.
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            ConfigCategory serverCategory =
                    builder.getOrCreateCategory(Component.translatable("config.chestseparators.category.server"));

            serverCategory.addEntry(entryBuilder
                    .startBooleanToggle(
                            Component.translatable("config.chestseparators.expel_invalid_items"),
                            GlobalChestConfig.instance.expelInvalidItems)
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("config.chestseparators.expel_invalid_items.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.expelInvalidItems = newValue)
                    .build());
        }

        // --- CATEGORY 3: HOTKEYS ---
        ConfigCategory hotkeysCategory =
                builder.getOrCreateCategory(Component.translatable("config.chestseparators.category.hotkeys"));

        // NOTE: Cloth's fillKeybindingField reaches into KeyMapping's PRIVATE 'key' field, which is only
        // reachable with Cloth's own access-widener applied — and that widener is NOT applied in this Mojmap
        // multiloader dev/runtime, so it throws IllegalAccessError (the config screen then fails to open).
        // Build the key entry from public KeyMapping API instead (read via saveString, default via
        // getDefaultKey, write via setKey) — identical behaviour, and works on Fabric AND Forge with no AW/AT.
        BiConsumer<KeyMapping, String> addKeyEntry = (keyMapping, translationKey) -> {
            com.mojang.blaze3d.platform.InputConstants.Key current =
                    com.mojang.blaze3d.platform.InputConstants.getKey(keyMapping.saveString());
            hotkeysCategory.addEntry(entryBuilder
                    .startKeyCodeField(Component.translatable(translationKey), current)
                    .setDefaultValue(keyMapping.getDefaultKey())
                    .setKeySaveConsumer(newKey -> {
                        keyMapping.setKey(newKey);
                        KeyMapping.resetMapping();
                    })
                    .setTooltip(Component.translatable(translationKey + ".tooltip"))
                    .build());
        };

        addKeyEntry.accept(ModKeyBindings.toggleMagnifierKey, "config.chestseparators.toggle_magnifier");
        addKeyEntry.accept(ModKeyBindings.autoDepositKey, "config.chestseparators.auto_deposit");
        addKeyEntry.accept(ModKeyBindings.openEditorKey, "config.chestseparators.toggle_preview_panel");
        addKeyEntry.accept(ModKeyBindings.toggleEditButtonsKey, "config.chestseparators.toggle_edit_buttons");
        addKeyEntry.accept(ModKeyBindings.showPanelModifierKey, "config.chestseparators.show_panel_modifier");
        addKeyEntry.accept(ModKeyBindings.pushKey, "config.chestseparators.push");
        addKeyEntry.accept(ModKeyBindings.pullKey, "config.chestseparators.pull");

        return builder.build();
    }
}
