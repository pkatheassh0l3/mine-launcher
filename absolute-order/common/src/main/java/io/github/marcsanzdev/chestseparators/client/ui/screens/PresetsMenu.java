package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.UiTheme;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.PressAnim;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import io.github.marcsanzdev.chestseparators.client.input.CharacterEvent;
import io.github.marcsanzdev.chestseparators.client.input.KeyEvent;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The dedicated presets overlay, docked on the left and styled like the mod's other windows. Each preset
 * row has a saved/empty indicator plus Load / Save / Delete buttons. Hovering a saved row's Load button
 * previews that preset on the real container slots (alternating layout/filters); hovering Delete previews
 * the same preset tinted red to signal removal. Saving over an occupied slot opens a confirmation popup.
 * Presets are paged ({@link #PER_PAGE} per page, {@link #pageCount()} pages) with ‹ › arrows. Drives the
 * inventory or the chest preset store per {@code session.presetsMenuChestMode}.
 *
 * <p>The render is split into two phases so EditorRenderer can inject the saved-lines layer between the
 * dim overlay ({@link #renderBackground}) and the panel itself ({@link #renderPanel}).
 */
public final class PresetsMenu {

    private int panelW() { return Math.max(180, Math.min(250, editor.accessor.getX() - editor.layout.gap - 4)); }
    private static final int HEADER_H = 74;
    private boolean communityTab;
    private final CommunityPresetsPanel community = new CommunityPresetsPanel();
    private static final int ROW_H = 22;
    private static final int FOOTER_H = 24;

    // Compact icon-only action buttons per row (Load / Save / Delete), each with a tooltip.
    private static final int ICON = 20;

    /** 9 presets per page across 5 pages = 45 total slots. */
    private static final int PER_PAGE = 7;

    private EditBox searchField;
    private final List<Integer> visibleSlots = new ArrayList<>();
    private int pageCount() { return Math.max(1, (visibleSlots.size() + PER_PAGE - 1) / PER_PAGE); }
    private void filterPresets(String query) {
        visibleSlots.clear();
        String text = query.toLowerCase(java.util.Locale.ROOT).strip();
        int count = 45 + io.github.marcsanzdev.chestseparators.data.StoragePresetLibrary.count();
        List<Integer> ordered = new ArrayList<>();
        for (int slot = 46; slot <= count; slot++) ordered.add(slot);

        for (int slot : ordered) {
            if (!exists(slot) || ChestConfigManager.getInstance().isBundledChestExample(chestSize(), slot)) continue;
            String name = presetName(slot);
            var entry = io.github.marcsanzdev.chestseparators.data.StoragePresetLibrary.get(slot);
            if (text.isEmpty() || (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(text)) || (entry != null && entry.key().contains(text))) visibleSlots.add(slot);
        }

        currentPage = 0;
    }

    // Save-indicator feedback colours.
    private static final int GREEN_BG = 0x4633C05A;
    private static final int GREEN_BORDER = 0xFF57E06A;
    private static final int GREEN_TICK = 0xFF6BF07E;
    private static final int ORANGE_BG = 0xF0C2721C;
    private static final int ORANGE_BORDER = 0xFFE59A3C;

    // Which row's Load/Delete button is currently being previewed, so each new hover restarts its detail
    // list from the top rather than continuing where the previous preset left off.
    private int lastPreviewKey = Integer.MIN_VALUE;

    /** Updated by renderBackground(); read by isPreviewActive() and EditorRenderer. */
    private int currentPreviewRow = -1;

    // --- Preset detail panel ---
    // The on-slot preview shows WHERE each filter sits but not WHAT it accepts, so while a Load button is
    // hovered a second panel lists every group of that preset with the items it allows. Read straight from
    // the preset file, so nothing is applied to the container.
    private static final int DETAIL_W = 158;
    private static final int DETAIL_LINE_H = 18;

    /** Preset currently hovered over its Load button, or null. Set by renderBackground(). */
    private ChestConfigManager.PresetPreview hoveredPreview;

    // Gentle auto-scroll for detail lists taller than the panel (same language as the filter preview).
    private float detailScrollY = 0f;
    private boolean detailScrollDown = true;
    private long detailLastTime = 0L;
    // Once the player scrolls the detail list themselves, the auto-scroll stops (until a new preset is
    // hovered), same as the item-import preview lets you take over the wheel.
    private boolean detailUserScrolled = false;

    /** Zero-based page currently shown. */
    private int currentPage = 0;

    /** 1-based slot awaiting an overwrite confirmation, or -1 when the popup is closed. */
    private int pendingOverwriteSlot = -1;

    // --- Inline rename ---
    /** 1-based slot whose name is being edited inline, or -1 when not renaming. */
    private int renamingSlot = -1;
    /** The text field shown over a row's name while renaming; null when not renaming. */
    private EditBox renameField;

    private static final int NAME_MAX_LEN = 64;

    // Page-arrow hit rects (updated each render).
    private int navPrevX, navNextX, navArrowY;

    // Popup buttons, non-null only while the confirmation popup is open.
    private WideButtonWidget popupConfirmBtn;
    private WideButtonWidget popupCancelBtn;

    private final ChestSeparatorsEditor editor;

    /** Rebuilt every render; reused by {@link #onClick} so hit-testing matches what was drawn. */
    private final List<ToolButtonWidget> clickables = new ArrayList<>();

    public PresetsMenu(ChestSeparatorsEditor editor) {
        this.editor = editor;
    }

    /** True when a Load/Delete-button hover preview is currently being painted on the real slots. */
    public boolean isPreviewActive() {
        if (communityTab) return false;
        return currentPreviewRow >= 0;
    }

    private boolean chestMode() {
        return true; // This distribution exposes storage presets only.
    }

    /** The open container's slot count (27 vs 54); picks which chest-preset set the menu shows. */
    private int chestSize() {
        return editor.chestPresetSize();
    }

    private boolean exists(int slot) {
        if (slot < 1) return false;
        if (slot >= 46) return io.github.marcsanzdev.chestseparators.data.StoragePresetLibrary.get(slot) != null;
        return chestMode()
                ? ChestConfigManager.getInstance().chestPresetExists(chestSize(), slot)
                : ChestConfigManager.getInstance().inventoryPresetExists(slot);
    }

    /** The custom name of the current-mode preset {@code slot}, or null when empty/unnamed. */
    private String presetName(int slot) {
        if (slot >= 46) { var entry = io.github.marcsanzdev.chestseparators.data.StoragePresetLibrary.get(slot); return entry == null ? null : entry.name(); }
        return chestMode()
                ? ChestConfigManager.getInstance().getChestPresetName(chestSize(), slot)
                : ChestConfigManager.getInstance().getInventoryPresetName(slot);
    }

    /** True when the slot carries a display name (a default or a user rename); those skip "Preset N". */
    private boolean hasName(int slot) {
        String n = presetName(slot);
        return n != null && !n.isEmpty();
    }

    /** Resets the menu to page 1 and ends any rename — called when the presets set (mode) changes. */
    public void resetView() {
        community.reset();
        io.github.marcsanzdev.chestseparators.data.StoragePresetLibrary.refresh(chestSize(), io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns());
        currentPage = 0;
        cancelRename();
        searchField = new EditBox(Minecraft.getInstance().font, 0, 0, panelW() - 20, 16, Component.literal("Buscar presets"));
        searchField.setHint(Component.literal("Buscar función o categoría…"));
        searchField.setMaxLength(100);
        searchField.setTextColor(UiTheme.TEXT);
        searchField.setResponder(this::filterPresets);
        filterPresets("");
    }

    /** 1-based preset slot for visible row {@code i} on the current page. */
    private int slotOfRow(int i) {
        int offset = currentPage * PER_PAGE + i;
        return offset >= 0 && offset < visibleSlots.size() ? visibleSlots.get(offset) : -1;
    }

    private int panelH() {
        return HEADER_H + PER_PAGE * ROW_H + FOOTER_H;
    }

    /**
     * Docked just left of the container GUI with the mod's standard gap, like every other side panel.
     */
    private int panelX() {
        return Math.max(2, editor.accessor.getX() - panelW() - editor.layout.gap);
    }

    private int panelY(int screenH) {
        return Math.max(8, (screenH - panelH()) / 2);
    }

    private int rowY(int screenH, int i) {
        return panelY(screenH) + HEADER_H + i * ROW_H;
    }

    private int deleteX() {
        return panelX() + panelW() - 8 - ICON;
    }

    private int saveX() {
        return deleteX() - 4 - ICON;
    }

    private int loadX() {
        return deleteX();
    }

    private void doSave(int slot) {
        if (chestMode()) editor.saveChestPresetSlot(slot);
        else editor.saveInventoryPresetSlot(slot);
    }

    /**
     * Phase 1: full-screen dim overlay and optional on-slot Load/Delete preview.
     * Must be called before {@link #renderPanel} so EditorRenderer can paint the saved lines between.
     */
    public void renderBackground(GuiGraphics context, int screenW, int screenH, int mouseX, int mouseY) {
        if (communityTab) { currentPreviewRow = -1; hoveredPreview = null; return; }
        // Light dim so the real container slots stay clearly visible for the on-slot preview.
        context.fill(0, 0, screenW, screenH, 0x55000000);

        // While the confirmation popup is up, freeze the on-slot preview.
        if (pendingOverwriteSlot >= 0) {
            currentPreviewRow = -1;
            lastPreviewKey = Integer.MIN_VALUE;
            hoveredPreview = null;
            return;
        }

        // Load previews onto the real slots; Delete only shows its "will be deleted" badge (its row
        // feedback — the whole chip fading — is drawn in drawSaveIndicator, not on the container).
        int previewRow = -1;
        int deleteRow = -1;
        for (int i = 0; i < PER_PAGE; i++) {
            int slot = slotOfRow(i);
            if (slot < 1) continue;
            if (!exists(slot)) continue;
            int by = rowY(screenH, i) + (ROW_H - ICON) / 2;
            if (inside(mouseX, mouseY, loadX(), by, ICON, ICON)) {
                previewRow = i;
                break;
            }
            if (false && inside(mouseX, mouseY, deleteX(), by, ICON, ICON)) {
                deleteRow = i;
                break;
            }
        }

        // Restart the layout<->filters cycle from the layout view whenever the hovered Load changes.
        long now = System.currentTimeMillis();
        if (previewRow != lastPreviewKey) {
            lastPreviewKey = previewRow;
            // A different preset is being inspected: start its detail list from the top and resume auto-scroll.
            detailScrollY = 0f;
            detailScrollDown = true;
            detailLastTime = now;
            detailUserScrolled = false;
        }
        currentPreviewRow = previewRow;
        hoveredPreview = null;

        if (previewRow >= 0) {
            int slot = slotOfRow(previewRow);
            ChestConfigManager.PresetPreview preview = chestMode()
                    ? io.github.marcsanzdev.chestseparators.data.StoragePresetLibrary.preview(io.github.marcsanzdev.chestseparators.data.StoragePresetLibrary.get(slot), chestSize())
                    : ChestConfigManager.getInstance().readInventoryPresetPreview(slot);
            if (preview != null) {
                hoveredPreview = preview;
                // Show BOTH layers at once: the layout (backgrounds + separator lines) underneath, and the
                // colour filter-group blobs on top — no more alternating between the two. The right-side
                // detail panel keeps listing each group's items.
                renderPreviewLayout(context, preview, chestMode());
                renderFilterBlobs(context, preview, chestMode());
            }
        } else if (deleteRow >= 0) {
            drawDeleteBadge(context);
        }
    }

    /**
     * Phase 2: beveled panel, rows, buttons, page navigation and (if pending) the overwrite popup.
     * Call after {@link #renderBackground} (and after any lines injected by EditorRenderer).
     */
    public void renderPanel(GuiGraphics context, int screenW, int screenH, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        boolean isDark = GlobalChestConfig.instance.darkMode;
        int px = panelX();
        int py = panelY(screenH);
        int ph = panelH();

        // Detail of the hovered preset first, so the presets panel always wins any overlap.
        renderDetailPanel(context, screenH);

        UiTheme.panel(context, px, py, panelW(), ph);

        // Chest presets are split by container size, so the title carries the slot count (e.g. "… (54)")
        // to make clear which set — single vs double — is currently shown.
        String title = Component.translatable(
                        chestMode() ? "gui.chestseparators.chest_presets_title" : "gui.chestseparators.presets_title")
                .getString();
        if (chestMode()) title = "PRESETS · " + chestSize() + " ranuras";
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, 
                client.font, Component.literal(title), px + panelW() / 2, py + 9, UiTheme.TEXT);

        int tabW = (panelW() - 20) / 2;
        UiTheme.segmentedTab(context, px + 8, py + 28, tabW, 20, inside(mouseX, mouseY, px+8, py+28, tabW, 20), !communityTab);
        UiTheme.segmentedTab(context, px + 12 + tabW, py + 28, tabW, 20, inside(mouseX, mouseY, px+12+tabW, py+28, tabW, 20), communityTab);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, client.font, Component.literal("Por defecto"), px + 8 + tabW / 2, py + 34, UiTheme.TEXT);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, client.font, Component.literal("Comunidad"), px + 12 + tabW + tabW / 2, py + 34, UiTheme.TEXT);
        if (communityTab) { community.render(context, px, py, panelW(), ph, mouseX, mouseY, editor); return; }
        if (searchField != null) {
            UiTheme.inset(context, px + 10, py + 52, panelW() - 20, 16);
            searchField.setBordered(false); searchField.setWidth(panelW() - 32);
            searchField.setX(px + 16);
            searchField.setY(py + 56);
            searchField.render(context, mouseX, mouseY, 0);
        }
        // Hover feedback (indicator, popup) is suppressed while the popup owns input.
        boolean popupOpen = pendingOverwriteSlot >= 0;
        int hoverX = popupOpen ? -1 : mouseX;
        int hoverY = popupOpen ? -1 : mouseY;

        clickables.clear();
        String nameTooltip = null;
        if (visibleSlots.isEmpty()) io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, client.font, Component.literal("Sin resultados"), px + panelW() / 2, py + HEADER_H + 12, UiTheme.TEXT_MUTED);
        // "Preset N" numbering skips the named presets: whatever their slot index, the unnamed slots read
        // as Preset 1..40 (5 of the 45 are the named defaults). Count how many unnamed slots precede this
        // page, then run a local counter down the rows.
        int unnamedCounter = 0;
        for (int s = 1; s < slotOfRow(0); s++) if (!hasName(s)) unnamedCounter++;

        for (int i = 0; i < PER_PAGE; i++) {
            int slot = slotOfRow(i);
            if (slot < 1) continue;
            int ry = rowY(screenH, i);
            boolean saved = exists(slot);
            boolean named = hasName(slot);
            if (!named) unnamedCounter++;
            int displayNumber = unnamedCounter; // only meaningful for unnamed rows

            int by = ry + (ROW_H - ICON) / 2;
            UiTheme.card(context, px + 5, ry + 1, panelW() - 10, ROW_H - 2, inside(hoverX,hoverY,px+5,ry,panelW()-10,ROW_H));
            boolean hoveringSave = inside(hoverX, hoverY, saveX(), by, ICON, ICON);
            boolean hoveringDelete = false && inside(hoverX, hoverY, deleteX(), by, ICON, ICON);

            drawSaveIndicator(context, px + 8, ry + (ROW_H - 14) / 2, saved, hoveringSave, hoveringDelete);

            int nameX = px + 28;
            int nameW = loadX() - 4 - nameX;
            if (renamingSlot == slot && renameField != null) {
                // Inline editor: an inset box holding the text field, replacing the label for this row.
                UiTheme.inset(context, nameX - 2, ry + (ROW_H - 14) / 2, nameW + 2, 14);
                renameField.setX(nameX + 1);
                renameField.setY(ry + (ROW_H - 8) / 2);
                renameField.setWidth(nameW - 2);
                renameField.render(context, hoverX, hoverY, 0);
            } else {
                String custom = saved ? presetName(slot) : null;
                Component label = custom != null && !custom.isEmpty()
                        ? Component.literal(custom)
                        : Component.translatable("gui.chestseparators.preset_slot", displayNumber);
                // Saved rows are clickable-to-rename: their name reads blue, turning white on hover so the
                // affordance is clear. Empty rows keep the plain muted label (nothing to edit there).
                boolean nameHover = saved && inside(hoverX, hoverY, nameX, ry, nameW, ROW_H);
                int nameColor;
                if (!saved) {
                    nameColor = isDark ? 0xFF888888 : 0xFF707070;
                } else {
                    nameColor = nameHover ? UiTheme.ACCENT : UiTheme.TEXT;
                }
                String fullName = label.getString();
                if (nameHover) nameTooltip = fullName;
                String visibleName = client.font.plainSubstrByWidth(fullName, nameW);
                if (!visibleName.equals(fullName)) visibleName = client.font.plainSubstrByWidth(fullName, nameW - client.font.width("…")) + "…";
                context.drawString(client.font, visibleName, nameX, ry + (ROW_H - 8) / 2, nameColor, false);
            }

            ToolButtonWidget load = new ToolButtonWidget(
                    loadX(),
                    by,
                    ModTextures.ICON_SM_IMPORT,
                    Component.translatable("button.chestseparators.preset_load").getString(),
                    () -> {
                        if (chestMode()) {
                            var preview = io.github.marcsanzdev.chestseparators.data.StoragePresetLibrary.preview(io.github.marcsanzdev.chestseparators.data.StoragePresetLibrary.get(slot), chestSize());
                            ChestConfigManager.getInstance().applySharedLayout(new io.github.marcsanzdev.chestseparators.data.CommunityData.Layout(preview.visual(), preview.filters()));
                            io.github.marcsanzdev.chestseparators.client.ui.CommunityClient.applyingPreset=true;
                            try { editor.saveSmart(); editor.sendWhitelistToServer(); } finally { io.github.marcsanzdev.chestseparators.client.ui.CommunityClient.applyingPreset=false; }
                        }
                        else {
                            editor.loadInventoryPresetSlot(slot);
                            editor.toggleState(io.github.marcsanzdev.chestseparators.client.EditorState.HIDDEN);
                        }
                    });
            load.texSize = 128;
            load.tintByState = true;
            load.isDisabled = !saved;

            ToolButtonWidget save = new ToolButtonWidget(
                    saveX(),
                    by,
                    ModTextures.ICON_SM_SAVE,
                    Component.translatable("button.chestseparators.preset_save").getString(),
                    () -> {
                        // Overwriting an occupied slot asks first; a fresh slot saves immediately.
                        if (exists(slot)) pendingOverwriteSlot = slot;
                        else doSave(slot);
                    });
            save.texSize = 128;
            save.tintByState = true;
            save.isDisabled = true;

            ToolButtonWidget delete = new ToolButtonWidget(
                    deleteX(),
                    by,
                    ModTextures.ICON_SM_TRASH,
                    Component.translatable("button.chestseparators.delete").getString(),
                    () -> {
                        if (chestMode()) editor.deleteChestPresetSlot(slot);
                        else editor.deleteInventoryPresetSlot(slot);
                    });
            delete.texSize = 128;
            delete.tintByState = true;
            delete.isDisabled = true;

            load.render(context, hoverX, hoverY, 0);


            clickables.add(load);


        }

        if (nameTooltip != null && !popupOpen) context.renderTooltip(client.font, Component.literal(nameTooltip), mouseX, mouseY);
        // ---- Footer: page navigation (dots + arrows). The panel is closed via its toolbar toggle or ESC. ----
        int footerY = py + HEADER_H + PER_PAGE * ROW_H;
        renderPageNav(context, px, footerY, hoverX, hoverY);

        // ---- Overwrite confirmation popup (on top of everything) ----
        if (popupOpen) {
            renderOverwritePopup(context, screenW, screenH, mouseX, mouseY);
        } else {
            popupConfirmBtn = null;
            popupCancelBtn = null;
        }
    }

    /** Convenience: calls both phases in order (used when no line injection is needed). */
    public void render(GuiGraphics context, int screenW, int screenH, int mouseX, int mouseY) {
        renderBackground(context, screenW, screenH, mouseX, mouseY);
        renderPanel(context, screenW, screenH, mouseX, mouseY);
    }

    // ---- Save indicator ----

    /**
     * The 14×14 chip at the start of each row. Empty vs saved, with a distinct Save-hover preview for each:
     * a bright green tick when hovering Save on an empty slot (you will add), an orange warning chip when
     * hovering Save on an occupied slot (you will overwrite).
     */
    private void drawSaveIndicator(
            GuiGraphics context, int x, int y, boolean saved, boolean hoveringSave, boolean hoveringDelete) {
        if (saved) {
            if (hoveringDelete) {
                // Delete preview: fade the WHOLE chip (blue fill, border and tick) toward transparency,
                // hinting that all of it — the preset in this slot — is about to be removed.
                int fade = 0x40 << 24;
                UiTheme.roundRect(context, x, y, 14, 14, (UiTheme.ACCENT_BG & 0x00FFFFFF) | fade);
                UiTheme.roundBorder(context, x, y, 14, 14, (UiTheme.ACCENT_BORDER & 0x00FFFFFF) | fade);
                drawIcon(context, ModTextures.ICON_SM_CHECK, x + 1, y + 1, 0x40FFFFFF);
            } else if (hoveringSave) {
                UiTheme.roundRect(context, x, y, 14, 14, ORANGE_BG);
                UiTheme.roundBorder(context, x, y, 14, 14, ORANGE_BORDER);
                drawIcon(context, ModTextures.ICON_SM_CONFLICT, x + 1, y + 1, 0xFFFFFFFF);
            } else {
                UiTheme.roundRect(context, x, y, 14, 14, UiTheme.ACCENT_BG);
                UiTheme.roundBorder(context, x, y, 14, 14, UiTheme.ACCENT_BORDER);
                drawIcon(context, ModTextures.ICON_SM_CHECK, x + 1, y + 1, UiTheme.ON_ACCENT);
            }
        } else {
            if (hoveringSave) {
                UiTheme.roundRect(context, x, y, 14, 14, GREEN_BG);
                UiTheme.roundBorder(context, x, y, 14, 14, GREEN_BORDER);
                drawIcon(context, ModTextures.ICON_SM_CHECK, x + 1, y + 1, GREEN_TICK);
            } else {
                UiTheme.inset(context, x, y, 14, 14);
            }
        }
    }

    private void drawIcon(GuiGraphics context, net.minecraft.resources.ResourceLocation icon, int x, int y, int color) {
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context, icon, x, y, 0.0F, 0.0F, 12, 12, 128, 128, 128, 128, color);
    }

    // ---- Page navigation ----

    private void renderPageNav(GuiGraphics context, int px, int footerY, int mouseX, int mouseY) {
        int cx = px + panelW() / 2;
        navArrowY = footerY + 2;
        navPrevX = cx - 58;
        navNextX = cx + 42;

        // Pagination wraps around, so both arrows are always active.
        boolean hoverPrev = inside(mouseX, mouseY, navPrevX, navArrowY, 16, 16);
        boolean hoverNext = inside(mouseX, mouseY, navNextX, navArrowY, 16, 16);

        drawArrowButton(context, navPrevX, navArrowY, true, true, hoverPrev);
        drawArrowButton(context, navNextX, navArrowY, false, true, hoverNext);

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, Minecraft.getInstance().font, Component.literal((currentPage + 1) + " / " + pageCount()), cx, navArrowY + 4, UiTheme.TEXT);
    }

    private void drawArrowButton(GuiGraphics context, int x, int y, boolean left, boolean enabled, boolean hover) {
        if (enabled) {
            UiTheme.button(context, x, y, 16, 16, hover, PressAnim.active(x, y));
        } else {
            UiTheme.roundRect(context, x, y, 16, 16, 0x0AFFFFFF);
            UiTheme.roundBorder(context, x, y, 16, 16, 0x14FFFFFF);
        }
        drawArrow(context, x, y, 16, left, enabled ? UiTheme.TEXT : 0xFF6A6A72);
    }

    /** A small filled triangle pointing left or right, centred in a {@code size}×{@code size} box. */
    private void drawArrow(GuiGraphics context, int x, int y, int size, boolean left, int color) {
        int midY = y + size / 2;
        int half = 4;
        int startX = x + size / 2 - half / 2; // leftmost column of the triangle
        for (int i = 0; i <= half; i++) {
            int px = startX + i;
            // Left arrow: tip (short) at the left, base (tall) at the right; right arrow: mirrored.
            int hh = left ? i : (half - i);
            context.fill(px, midY - hh, px + 1, midY + hh + 1, color);
        }
    }

    // ---- Overwrite confirmation popup ----

    private void renderOverwritePopup(GuiGraphics context, int screenW, int screenH, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        // Darken the whole screen so the popup reads as modal.
        context.fill(0, 0, screenW, screenH, 0x99000000);

        int pw = 214;
        int pph = 96;
        int px = (screenW - pw) / 2;
        int py = (screenH - pph) / 2;
        UiTheme.panel(context, px, py, pw, pph);

        drawIcon(context, ModTextures.ICON_SM_CONFLICT, px + pw / 2 - 6, py + 8, ORANGE_BORDER);

        Component title = Component.translatable("gui.chestseparators.preset_overwrite_title", pendingOverwriteSlot);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, client.font, title, px + pw / 2, py + 26, 0xFFFFC24A);

        Component body = Component.translatable("gui.chestseparators.preset_overwrite_body");
        for (net.minecraft.util.FormattedCharSequence line : client.font.split(body, pw - 24)) {
            // Single expected line; wrapLines guards against long translations.
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, client.font, line, px + pw / 2, py + 42, 0xFFCFCFD6);
            break;
        }

        int bw = 84;
        int bh = 18;
        int gap = 8;
        int startX = px + (pw - (2 * bw + gap)) / 2;
        int by = py + pph - bh - 10;

        popupConfirmBtn = new WideButtonWidget(
                startX,
                by,
                bw,
                bh,
                Component.translatable("button.chestseparators.confirm").getString(),
                ModTextures.ICON_SM_CHECK,
                () -> {
                    doSave(pendingOverwriteSlot);
                    pendingOverwriteSlot = -1;
                });
        popupConfirmBtn.texSize = 128;
        popupCancelBtn = new WideButtonWidget(
                startX + bw + gap,
                by,
                bw,
                bh,
                Component.translatable("button.chestseparators.cancel").getString(),
                ModTextures.ICON_SM_CANCEL,
                () -> pendingOverwriteSlot = -1);
        popupCancelBtn.texSize = 128;

        popupConfirmBtn.render(context, mouseX, mouseY, 0);
        popupCancelBtn.render(context, mouseX, mouseY, 0);
    }

    /** Badge shown while hovering a Delete button, warning that the preset will be removed. */
    private void drawDeleteBadge(GuiGraphics context) {
        drawBadge(context, Component.translatable("gui.chestseparators.preview_delete"), 0xFFFF6060);
    }

    /**
     * A badge in the mod's Cristal style (rounded, bordered panel), centred over the chest and tucked just
     * above the top toolbar so the hint sits near the buttons rather than floating at the very top.
     */
    private void drawBadge(GuiGraphics context, Component label, int textColor) {
        Minecraft client = Minecraft.getInstance();
        int w = client.font.width(label) + 18;
        int h = 18;
        int cx = editor.accessor.getX() + editor.accessor.getBackgroundWidth() / 2;
        int x = cx - w / 2;
        int y = Math.max(2, editor.accessor.getY() - 51);
        UiTheme.panel(context, x, y, w, h);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, client.font, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    // ---- Preset detail panel (which items each filter of the preset accepts) ----

    /** One line of the detail list: either a group header (colour chip + slot count) or an item. */
    private record DetailRow(boolean header, int color, int slotCount, String itemId) {}

    /**
     * Docked just left of the presets panel so you get preset list → detail → container. When there is no
     * room left of it (narrow window), it falls back to the free space right of the container.
     */
    private int detailX() {
        int left = panelX() - DETAIL_W - editor.layout.gap;
        if (left >= 2) return left;
        return editor.accessor.getX() + editor.accessor.getBackgroundWidth() + editor.layout.gap;
    }

    /** Flattens the preset's filters into group headers followed by their allowed items. */
    private List<DetailRow> buildDetailRows(ChestConfigManager.PresetPreview preview) {
        Map<Integer, SlotWhitelist> filters = preview.filters();
        // Group the slots, keeping groups ordered by their first slot so the list matches the container.
        Map<UUID, List<Integer>> groups = new LinkedHashMap<>();
        List<Integer> slotKeys = new ArrayList<>(filters.keySet());
        if (chestMode()) {
            slotKeys.sort(null);
        } else {
            // Inventory presets list groups by TRUE importance: armor + offhand first, then the hotbar,
            // then the main grid (top-left → bottom-right). Inventory keys are realIndex values:
            // 0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand.
            slotKeys.sort(java.util.Comparator.comparingInt((Integer k) -> k >= 36 ? 0 : (k <= 8 ? 1 : 2))
                    .thenComparingInt(k -> k));
        }
        for (int key : slotKeys) {
            groups.computeIfAbsent(filters.get(key).groupId(), g -> new ArrayList<>())
                    .add(key);
        }

        Map<UUID, Integer> colors = assignPreviewGroupColors(preview);
        List<DetailRow> rows = new ArrayList<>();
        for (Map.Entry<UUID, List<Integer>> entry : groups.entrySet()) {
            SlotWhitelist wl = filters.get(entry.getValue().get(0));
            rows.add(new DetailRow(
                    true,
                    colors.getOrDefault(entry.getKey(), 0x99888888),
                    entry.getValue().size(),
                    null));
            if (wl.allowedItems().isEmpty()) {
                rows.add(new DetailRow(false, 0, 0, null)); // "empty filter" line
            } else {
                for (String id : wl.allowedItems()) rows.add(new DetailRow(false, 0, 0, id));
            }
        }
        return rows;
    }

    /** Draws the hovered preset's filters and their items. No-op when nothing is hovered. */
    private void renderDetailPanel(GuiGraphics context, int screenH) {
        ChestConfigManager.PresetPreview preview = hoveredPreview;
        if (preview == null) return;
        Map<Integer, SlotWhitelist> filters = preview.filters();
        if (filters == null || filters.isEmpty()) return;

        List<DetailRow> rows = buildDetailRows(preview);
        int totalItems = 0;
        for (DetailRow r : rows) if (!r.header() && r.itemId() != null) totalItems++;

        Minecraft mc = Minecraft.getInstance();
        int x = detailX();
        int y = panelY(screenH);
        int w = DETAIL_W;
        int h = panelH();

        UiTheme.panel(context, x, y, w, h);

        // Header: how many items this preset's filters accept in total.
        context.pose().pushPose();
        float scale = 1.0f;
        context.pose().scale(scale, scale, 1.0F);
        context.drawString(
                mc.font,
                Component.translatable("gui.chestseparators.items_count", totalItems),
                (int) ((x + 8) / scale),
                (int) ((y + 8) / scale),
                UiTheme.TEXT,
                false);
        context.pose().popPose();

        int viewY = y + 22;
        int viewH = h - 30;
        UiTheme.inset(context, x + 6, viewY, w - 12, viewH);

        int contentH = rows.size() * DETAIL_LINE_H;
        float maxScroll = Math.max(0, contentH - (viewH - 4));

        long now = System.currentTimeMillis();
        if (detailLastTime == 0L) detailLastTime = now;
        float dt = (now - detailLastTime) / 1000f;
        detailLastTime = now;
        if (GlobalChestConfig.instance.enablePreviewAnimation && maxScroll > 0 && !detailUserScrolled) {
            float speed = 22f;
            if (detailScrollDown) {
                detailScrollY += speed * dt;
                if (detailScrollY >= maxScroll) {
                    detailScrollY = maxScroll;
                    detailScrollDown = false;
                }
            } else {
                detailScrollY -= speed * dt;
                if (detailScrollY <= 0) {
                    detailScrollY = 0;
                    detailScrollDown = true;
                }
            }
        }
        detailScrollY = Mth.clamp(detailScrollY, 0, maxScroll);

        context.enableScissor(x + 6, viewY + 1, x + w - 6, viewY + viewH - 1);
        int first = (int) (detailScrollY / DETAIL_LINE_H);
        int visible = (viewH / DETAIL_LINE_H) + 2;
        for (int i = 0; i < visible; i++) {
            int idx = first + i;
            if (idx < 0 || idx >= rows.size()) break;
            DetailRow row = rows.get(idx);
            int ry = viewY + 2 + (i * DETAIL_LINE_H) - (int) (detailScrollY % DETAIL_LINE_H);

            if (row.header()) {
                // Colour chip matching the on-slot blob, plus how many slots the group covers.
                // The group palette is translucent (for the on-slot blobs); the detail chip wants it solid.
                UiTheme.roundRect(context, x + 10, ry + 4, 10, 10, row.color() | 0xFF000000);
                UiTheme.roundBorder(context, x + 10, ry + 4, 10, 10, 0x66FFFFFF);
                context.pose().pushPose();
                context.pose().scale(scale, scale, 1.0F);
                context.drawString(
                        mc.font,
                        "x" + row.slotCount(),
                        (int) ((x + 24) / scale),
                        (int) ((ry + 6) / scale),
                        UiTheme.TEXT,
                        false);
                context.pose().popPose();
                continue;
            }

            if (row.itemId() == null) {
                context.pose().pushPose();
                float s = 1.0f;
                context.pose().scale(s, s, 1.0F);
                context.drawString(
                        mc.font,
                        Component.translatable("gui.chestseparators.empty_whitelist"),
                        (int) ((x + 24) / s),
                        (int) ((ry + 6) / s),
                        UiTheme.TEXT_MUTED,
                        false);
                context.pose().popPose();
                continue;
            }

            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(row.itemId()));
            context.renderItem(item.getDefaultInstance(), x + 22, ry);

            String name = item.getDescription().getString();
            context.pose().pushPose();
            float s = 1.0f;
            context.pose().scale(s, s, 1.0F);
            int maxNameW = (int) ((w - 52) / s);
            if (mc.font.width(name) > maxNameW) {
                name = mc.font.plainSubstrByWidth(name, maxNameW - 6) + "...";
            }
            context.drawString(mc.font, name, (int) ((x + 42) / s), (int) ((ry + 5) / s), UiTheme.TEXT, false);
            context.pose().popPose();
        }
        context.disableScissor();

        if (maxScroll > 0) {
            int barX = x + w - 12;
            int barH = viewH - 2;
            int thumbH = Math.max(10, (int) ((viewH / (float) Math.max(1, contentH)) * barH));
            int thumbY = viewY + 1 + (int) ((detailScrollY / maxScroll) * (barH - thumbH));
            UiTheme.scrollbar(context, barX, viewY + 1, 6, barH, thumbY, thumbH);
        }
    }

    // ---- On-slot preview ----

    private void renderPreviewLayout(GuiGraphics context, ChestConfigManager.PresetPreview preview, boolean chestKind) {
        int guiX = editor.accessor.getX();
        int guiY = editor.accessor.getY();
        for (Slot slot : editor.accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
            boolean player = ChestSeparatorsEditor.isPlayerSlot(slot);
            if (chestKind == player) continue;
            int idx = ChestSeparatorsEditor.realIndex(slot);
            int x = guiX + slot.x;
            int y = guiY + slot.y;

            int[] cc = ChestConfigManager.previewColors(preview.visual().get(idx));
            if (cc[4] != 0) context.fill(x, y, x + 16, y + 16, (cc[4] & 0x00FFFFFF) | 0x99000000);
            if (cc[0] != 0) context.fill(x - 1, y - 1, x + 17, y, (cc[0] & 0x00FFFFFF) | 0xFF000000);
            if (cc[1] != 0) context.fill(x - 1, y + 16, x + 17, y + 17, (cc[1] & 0x00FFFFFF) | 0xFF000000);
            if (cc[2] != 0) context.fill(x - 1, y - 1, x, y + 17, (cc[2] & 0x00FFFFFF) | 0xFF000000);
            if (cc[3] != 0) context.fill(x + 16, y - 1, x + 17, y + 17, (cc[3] & 0x00FFFFFF) | 0xFF000000);
        }
    }

    /**
     * Filter view of the preview. Draws each group through the SAME blob passes the live filter editor uses
     * (GroupBlobRenderer.drawBlobGroupStatic), so a filter looks pixel-identical everywhere in the mod. The
     * translucent group palette lets the layout drawn underneath still show through.
     */
    private void renderFilterBlobs(GuiGraphics context, ChestConfigManager.PresetPreview preview, boolean chestKind) {
        int guiX = editor.accessor.getX();
        int guiY = editor.accessor.getY();
        Map<Integer, SlotWhitelist> filters = preview.filters();
        Map<UUID, Integer> groupColors = assignPreviewGroupColors(preview);

        // Group the filtered cells, and build a realIndex -> Slot resolver (the exact realIndex mapping the
        // live editor uses) so the shared blob renderer can locate each cell on screen.
        Map<UUID, Set<Integer>> groupKeys = new LinkedHashMap<>();
        Map<Integer, Slot> keyToSlot = new HashMap<>();
        for (Slot slot : editor.accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
            if (chestKind == ChestSeparatorsEditor.isPlayerSlot(slot)) continue;
            int key = ChestSeparatorsEditor.realIndex(slot);
            SlotWhitelist wl = filters.get(key);
            if (wl == null) continue;
            keyToSlot.put(key, slot);
            groupKeys.computeIfAbsent(wl.groupId(), g -> new HashSet<>()).add(key);
        }

        for (Map.Entry<UUID, Set<Integer>> entry : groupKeys.entrySet()) {
            int color = groupColors.getOrDefault(entry.getKey(), 0x99888888);
            GroupBlobRenderer.drawBlobGroupStatic(context, entry.getValue(), color, guiX, guiY, keyToSlot::get);
        }
    }

    /**
     * Group colours for a preset preview, mirroring the live editor exactly: a group painted with a colour
     * unanimous across all its slots (in the PRESET's own layout) keeps that colour; the rest fall back to
     * the shared palette, skipping colours already taken. So a preset's blobs match the layout it stored.
     */
    private Map<UUID, Integer> assignPreviewGroupColors(ChestConfigManager.PresetPreview preview) {
        Map<UUID, Set<Integer>> groupKeys = new LinkedHashMap<>();
        for (Map.Entry<Integer, SlotWhitelist> e : preview.filters().entrySet()) {
            groupKeys
                    .computeIfAbsent(e.getValue().groupId(), g -> new HashSet<>())
                    .add(e.getKey());
        }
        List<UUID> order = new ArrayList<>(groupKeys.keySet());
        order.sort(UUID::compareTo);

        Map<UUID, Integer> assigned = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        // 1. Explicit layout colours first (matches GroupBlobRenderer#getExplicitGroupColor).
        for (UUID g : order) {
            int explicit = presetExplicitGroupColor(groupKeys.get(g), preview);
            if (explicit != 0) {
                assigned.put(g, explicit);
                used.add(explicit);
            }
        }
        // 2. Palette for the rest, skipping colours already used.
        int idx = 0;
        int[] palette = GroupBlobRenderer.GROUP_PALETTE;
        for (UUID g : order) {
            if (assigned.containsKey(g)) continue;
            while (idx < palette.length && used.contains(palette[idx])) idx++;
            int color = idx < palette.length ? palette[idx++] : palette[Math.abs(g.hashCode()) % palette.length];
            used.add(color);
            assigned.put(g, color);
        }
        return assigned;
    }

    /**
     * The colour a group is painted with in a preset's stored layout, if unanimous across all its slots —
     * the preview equivalent of the live editor's getExplicitGroupColor, reading the preset's visual data.
     * Returns 0 when the group has no single shared layout colour.
     */
    private static int presetExplicitGroupColor(Set<Integer> groupKeys, ChestConfigManager.PresetPreview preview) {
        Set<Integer> common = null;
        for (int key : groupKeys) {
            int[] cc = ChestConfigManager.previewColors(preview.visual().get(key));
            Set<Integer> slotColors = new HashSet<>();
            for (int i = 0; i < 5 && i < cc.length; i++) {
                int c = cc[i] & 0x00FFFFFF;
                if (c != 0) slotColors.add(c);
            }
            if (slotColors.isEmpty()) return 0; // a slot with no colour means the group has no unanimous one
            if (common == null) common = slotColors;
            else common.retainAll(slotColors);
            if (common.isEmpty()) return 0;
        }
        return (common != null && !common.isEmpty()) ? (common.iterator().next() | 0x99000000) : 0;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- Inline rename ----

    /**
     * Wheel over the preset menu while a Load preview is showing scrolls its detail list (the items each
     * filter accepts), and hands control from the auto-scroll to the player — just like the item-import
     * preview. Returns true when it consumed the scroll.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (communityTab) return community.scroll(verticalAmount);
        if (hoveredPreview == null) return false;
        detailScrollY = Math.max(0, detailScrollY - (float) verticalAmount * 18f);
        detailUserScrolled = true; // upper bound is clamped while rendering the detail panel
        return true;
    }

    /** True while a preset name is being edited, so the parent screen routes typing here. */
    public boolean isRenaming() {
        if (communityTab) return community.focused();
        return renamingSlot >= 0;
    }

    /** Starts editing {@code slot}'s name, seeding the field with its current name. */
    private void beginRename(int slot, double clickX) {
        if (!communityTab) return;
        String current = presetName(slot);
        renamingSlot = slot;
        // Create the field at its real geometry FIRST: if it were built at a placeholder width, setText
        // would scroll it so far that only the last letter shows (the reported bug). The row's name box
        // does not depend on the screen height, so it can be sized here.
        int nameX = panelX() + 28;
        int fieldX = nameX + 1;
        int fieldW = (loadX() - 4 - nameX) - 2;
        renameField = new EditBox(
                Minecraft.getInstance().font, fieldX, 0, fieldW, 12, Component.literal(""));
        renameField.setBordered(false);
        renameField.setMaxLength(NAME_MAX_LEN);
        renameField.setValue(current == null ? "" : current);
        renameField.setFocused(true);

        // Place the caret at the letter that was clicked, not blindly at the end.
        String text = renameField.getValue();
        var tr = Minecraft.getInstance().font;
        int rel = (int) Math.max(0, clickX - fieldX);
        int caret = tr.plainSubstrByWidth(text, rel).length();
        renameField.moveCursorTo(caret, false);
        editor.playClickSound(1.0f);
    }

    /** Saves the edited name (empty clears back to the default label) and leaves rename mode. */
    private void commitRename() {
        if (renamingSlot >= 0 && renameField != null) {
            if (chestMode()) {
                ChestConfigManager.getInstance().setChestPresetName(chestSize(), renamingSlot, renameField.getValue());
            } else {
                ChestConfigManager.getInstance().setInventoryPresetName(renamingSlot, renameField.getValue());
            }
            editor.playClickSound(1.1f);
        }
        renamingSlot = -1;
        renameField = null;
    }

    /** Ends any active rename without saving (used when the menu closes or its page/mode changes). */
    public void cancelRename() {
        renamingSlot = -1;
        renameField = null;
    }

    /** Keyboard while renaming: Enter commits, Escape cancels, everything else edits the field. */
    public boolean keyPressed(KeyEvent input) {
        if (communityTab) return community.key(input);
        if (pendingOverwriteSlot < 0 && searchField != null && searchField.isFocused() && !isRenaming()) {
            if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { searchField.setFocused(false); return true; }
            searchField.keyPressed(input.key(), input.scancode(), input.modifiers());
            return true;
        }
        if (!isRenaming() || renameField == null) return false;
        int key = input.key();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            commitRename();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            cancelRename();
            return true;
        }
        renameField.keyPressed(input.key(), input.scancode(), input.modifiers());
        return true;
    }

    public boolean charTyped(CharacterEvent input) {
        if (communityTab) return community.character(input);
        if (pendingOverwriteSlot < 0 && searchField != null && searchField.isFocused() && !isRenaming()) {
            searchField.charTyped((char) input.codepoint(), input.modifiers());
            return true;
        }
        if (!isRenaming() || renameField == null) return false;
        renameField.charTyped((char) input.codepoint(), input.modifiers());
        return true;
    }

    /** Routes a click inside the open menu. Returns true when the click was consumed. */
    public boolean onClick(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (button != 0) return true;
        int px = panelX(), py = panelY(screenH), tabW = (panelW() - 20) / 2;
        if (inside(mouseX, mouseY, px + 8, py + 28, panelW() - 16, 20)) {
            communityTab = mouseX >= px + 10 + tabW;
            cancelRename(); community.blur(); pendingOverwriteSlot = -1; currentPreviewRow = -1; hoveredPreview = null;
            if (communityTab) community.reset();
            return true;
        }
        if (communityTab) return community.click(mouseX, mouseY, button);

        // The confirmation popup captures all input until resolved.
        if (pendingOverwriteSlot >= 0) {
            if (popupConfirmBtn != null && popupConfirmBtn.mouseClicked(mouseX, mouseY, button)) {
                editor.playClickSound(1.2f);
                return true;
            }
            if (popupCancelBtn != null && popupCancelBtn.mouseClicked(mouseX, mouseY, button)) {
                editor.playClickSound(0.9f);
                return true;
            }
            return true;
        }

        if (searchField != null) {
            if (searchField.isMouseOver(mouseX, mouseY)) {
                if (isRenaming()) commitRename();
                searchField.setFocused(true);
                searchField.mouseClicked(mouseX, mouseY, button);
                return true;
            }
            searchField.setFocused(false);
        }
        // A rename in progress: a click inside its field just moves the caret; any other click commits it
        // and then continues as a normal click (so clicking a button or another name works in one go).
        if (isRenaming()) {
            if (renameField != null && renameField.isMouseOver(mouseX, mouseY)) {
                renameField.setFocused(true);
                // Reposition the caret to the clicked letter. EditBox.mouseClicked's signature
                // changed in 1.21.11 and no longer moves the caret from a raw (x,y), so place it manually
                // from the click X — the same computation beginRename uses when the field first opens.
                var tr = Minecraft.getInstance().font;
                int rel = (int) Math.max(0, mouseX - renameField.getX());
                int caret = tr.plainSubstrByWidth(renameField.getValue(), rel).length();
                renameField.moveCursorTo(caret, false);
                return true;
            }
            commitRename();
        }

        // Page arrows — circular: wrap past either end. Changing page ends any rename (slots shift).
        if (inside(mouseX, mouseY, navPrevX, navArrowY, 16, 16)) {
            cancelRename();
            currentPage = (currentPage - 1 + pageCount()) % pageCount();
            PressAnim.press(navPrevX, navArrowY);
            editor.playClickSound(1.0f);
            return true;
        }
        if (inside(mouseX, mouseY, navNextX, navArrowY, 16, 16)) {
            cancelRename();
            currentPage = (currentPage + 1) % pageCount();
            PressAnim.press(navNextX, navArrowY);
            editor.playClickSound(1.0f);
            return true;
        }

        for (ToolButtonWidget b : clickables) {
            if (!b.isDisabled && b.mouseClicked(mouseX, mouseY, button)) {
                editor.playClickSound(1.1f);
                return true;
            }
        }

        // Click a saved row's name to edit it inline.
        for (int i = 0; i < PER_PAGE; i++) {
            int slot = slotOfRow(i);
            if (slot < 1) continue;
            if (!exists(slot)) continue;
            int ry = rowY(screenH, i);
            int nameX = panelX() + 28;
            int nameW = loadX() - 4 - nameX;
            if (inside(mouseX, mouseY, nameX, ry, nameW, ROW_H)) {
                beginRename(slot, mouseX);
                return true;
            }
        }

        boolean insidePanel = inside(mouseX, mouseY, panelX(), panelY(screenH), panelW(), panelH());
        // Click-outside-to-close: when enabled, a click beyond the panel dismisses the presets menu.
        if (!insidePanel && GlobalChestConfig.instance.closeOnClickOutside) {
            editor.getSession().isPresetsMenuOpen = false;
            editor.playCloseSound();
            return true;
        }
        return insidePanel;
    }
}



