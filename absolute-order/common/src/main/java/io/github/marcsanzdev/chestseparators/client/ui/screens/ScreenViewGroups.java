package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ActionIconButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;

public class ScreenViewGroups extends AbstractEditorScreen {

    // Package-private so the extracted ViewGroupsClickHandler can route clicks to these widgets.
    final List<CustomWidget> popupWidgets = new ArrayList<>();
    final List<CustomWidget> selectSlotsWidgets = new ArrayList<>();

    private long btnClearAllClickTime = 0;
    private long btnClearSelectClickTime = 0;
    private long btnCopyClickTime = 0;
    private long btnPasteClickTime = 0;
    private long btnUndoClickTime = 0;
    private long btnRedoClickTime = 0;

    private final GroupBlobRenderer groupRenderer;
    private final WhitelistPreviewPanelRenderer previewPanelRenderer;
    private final ViewGroupsClickHandler clickHandler;

    public ScreenViewGroups(ChestSeparatorsEditor editor) {
        super(editor);
        this.groupRenderer = new GroupBlobRenderer(this);
        this.previewPanelRenderer = new WhitelistPreviewPanelRenderer(this);
        this.clickHandler = new ViewGroupsClickHandler(this);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickHandler.onMouseClicked(mouseX, mouseY, button);
    }

    /** Draws the always-on left whitelist preview panel. Delegates to {@link WhitelistPreviewPanelRenderer}. */
    public void renderWhitelistPreviewPanel(GuiGraphics context, int mouseX, int mouseY) {
        previewPanelRenderer.renderWhitelistPreviewPanel(context, mouseX, mouseY);
    }

    @Override
    public void init() {
        super.init();
        popupWidgets.clear();
        buildPopupWidgets();
        selectSlotsWidgets.clear();
        buildSelectSlotsWidgets();

        // Every conflict-popup button dismisses the popup, so flash the press before acting — otherwise
        // the popup closes on the same frame and the click is never seen.
        for (CustomWidget w : popupWidgets) w.deferAction = true;
    }

    @Override
    protected void buildWidgets() {
        int sx = layout.rightX;
        int sy = layout.mainY;
        int btnW = layout.btnW;
        int bH = layout.bH;

        // --- Block 1: Edit & Clear All ---
        WideButtonWidget btnEditFilter = new WideButtonWidget(
                sx,
                sy,
                btnW,
                bH,
                Component.translatable("button.chestseparators.edit_filter").getString(),
                ModTextures.ICON_SM_FILTER,
                () -> {
                    editor.playClickSound(1.0f);
                    if (session.selectedSlots.isEmpty()) {
                        editor.showStatus(Component.translatable("message.chestseparators.select_first"), ChatFormatting.RED);
                        return;
                    }
                    boolean conflictFound = false;
                    var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                    if (whitelists != null) {
                        for (int slotIdx : session.selectedSlots) {
                            if (whitelists.containsKey(slotIdx)) {
                                conflictFound = true;
                                break;
                            }
                        }
                    }
                    if (conflictFound) session.hasSelectionConflict = true;
                    else {
                        session.selectedGroupId = UUID.randomUUID();
                        transitionToEditFilter();
                    }
                });
        btnEditFilter.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.edit_filter").getString();
        btnEditFilter.texSize = 128;
        widgets.add(btnEditFilter);

        WideButtonWidget btnClearAll = new WideButtonWidget(
                sx,
                sy + 24,
                btnW,
                bH,
                Component.translatable("button.chestseparators.delete_all_filters").getString(),
                ModTextures.ICON_SM_TRASH,
                () -> {
                    btnClearAllClickTime = System.currentTimeMillis();
                    var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                    if (whitelists != null && !whitelists.isEmpty()) {
                        ChestConfigManager.getInstance().saveWhitelistSnapshot();
                        whitelists.clear();
                        editor.saveSmart();
                        editor.sendWhitelistToServer();

                        editor.syncClientInventoryWhitelists(whitelists);

                        editor.showStatus(
                                Component.translatable("message.chestseparators.all_filters_deleted"), ChatFormatting.RED);
                        editor.playClickSound(0.8f);
                    }
                });
        btnClearAll.tooltipText = Component.translatable("tooltip.chestseparators.desc.delete_all_filters")
                .getString();
        btnClearAll.keepNormalTextColor = true;
        btnClearAll.texSize = 128;
        widgets.add(btnClearAll);

        // --- Block 2: Selection Tools ---
        WideButtonWidget btnAreaSelect = new WideButtonWidget(
                sx,
                sy + 53,
                btnW,
                bH,
                Component.translatable("button.chestseparators.area_select").getString(),
                ModTextures.ICON_SM_AREA_SELECT,
                () -> {
                    session.wlToolMode = 0;
                    editor.playClickSound(1.2f);
                });
        btnAreaSelect.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.area_select").getString();
        btnAreaSelect.texSize = 128;
        widgets.add(btnAreaSelect);

        WideButtonWidget btnTraceSelect = new WideButtonWidget(
                sx,
                sy + 77,
                btnW,
                bH,
                Component.translatable("button.chestseparators.trace_select").getString(),
                ModTextures.ICON_SM_TRACE_SELECT,
                () -> {
                    session.wlToolMode = 1;
                    editor.playClickSound(1.2f);
                });
        btnTraceSelect.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.trace_select").getString();
        btnTraceSelect.texSize = 128;
        widgets.add(btnTraceSelect);

        WideButtonWidget btnClearSelect = new WideButtonWidget(
                sx,
                sy + 101,
                btnW,
                bH,
                Component.translatable("button.chestseparators.clear_selection").getString(),
                ModTextures.ICON_SM_TRASH,
                () -> {
                    btnClearSelectClickTime = System.currentTimeMillis();
                    session.selectedSlots.clear();
                    editor.playClickSound(0.8f);
                });
        btnClearSelect.tooltipText = Component.translatable("tooltip.chestseparators.desc.clear_selection")
                .getString();
        btnClearSelect.keepNormalTextColor = true;
        btnClearSelect.texSize = 128;
        widgets.add(btnClearSelect);

        // --- Block 3: Global Actions ---
        WideButtonWidget btnCopy = new WideButtonWidget(
                sx,
                sy + 130,
                btnW,
                bH,
                Component.translatable("button.chestseparators.copy_filters").getString(),
                ModTextures.ICON_SM_COPY,
                () -> {
                    btnCopyClickTime = System.currentTimeMillis();
                    ChestConfigManager.getInstance().copyWhitelistsToClipboard();
                    editor.showStatus(Component.translatable("message.chestseparators.filters_copied"), ChatFormatting.GRAY);
                    editor.playClickSound(1.0f);
                });
        btnCopy.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.copy_filters").getString();
        btnCopy.keepNormalTextColor = true;
        btnCopy.texSize = 128;
        widgets.add(btnCopy);

        WideButtonWidget btnPaste = new WideButtonWidget(
                sx,
                sy + 154,
                btnW,
                bH,
                Component.translatable("button.chestseparators.paste_filters").getString(),
                ModTextures.ICON_SM_PASTE,
                () -> {
                    btnPasteClickTime = System.currentTimeMillis();

                    if (ChestConfigManager.getInstance().hasWhitelistClipboardData()) {
                        ChestConfigManager.getInstance().saveWhitelistSnapshot();
                        ChestConfigManager.getInstance().pasteWhitelistsFromClipboard();
                        editor.saveSmart();
                        editor.sendWhitelistToServer();

                        editor.syncClientInventoryWhitelists(
                                ChestConfigManager.getInstance().getCurrentWhitelists());

                        editor.showStatus(
                                Component.translatable("message.chestseparators.filters_pasted"), ChatFormatting.GREEN);
                        editor.playClickSound(1.0f);
                    }
                });
        btnPaste.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.paste_filters").getString();
        btnPaste.keepNormalTextColor = true;
        btnPaste.texSize = 128;
        widgets.add(btnPaste);

        int halfW = (btnW - 4) / 2;
        WideButtonWidget btnUndo = new WideButtonWidget(
                sx,
                sy + 178,
                halfW,
                bH,
                Component.translatable("button.chestseparators.undo").getString(),
                ModTextures.ICON_SM_UNDO,
                () -> {
                    btnUndoClickTime = System.currentTimeMillis();
                    editor.applyUndoRedo(ChestConfigManager.getInstance().undo(), false);
                });
        btnUndo.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.undo").getString();
        btnUndo.keepNormalTextColor = true;
        btnUndo.texSize = 128;
        widgets.add(btnUndo);

        WideButtonWidget btnRedo = new WideButtonWidget(
                sx + halfW + 4,
                sy + 178,
                halfW,
                bH,
                Component.translatable("button.chestseparators.redo").getString(),
                ModTextures.ICON_SM_REDO,
                () -> {
                    btnRedoClickTime = System.currentTimeMillis();
                    editor.applyUndoRedo(ChestConfigManager.getInstance().redo(), true);
                });
        btnRedo.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.redo").getString();
        btnRedo.keepNormalTextColor = true;
        btnRedo.texSize = 128;
        widgets.add(btnRedo);
        var color = new WideButtonWidget(sx, sy + 202, btnW, bH, "Color de la zona", ModTextures.ICON_SM_PALETTE, editor::openRegionColor);
        color.texSize = 128; color.tooltipText = "Selecciona una zona y cambia su color"; widgets.add(color);
    }

    private void buildPopupWidgets() {
        ActionIconButtonWidget btnOverwrite = new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 45,
                240,
                16,
                "1. "
                        + Component.translatable("button.chestseparators.conflict.overwrite")
                                .getString(),
                null,
                0xFF852D2D,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    session.hasSelectionConflict = false;
                    session.selectedGroupId = UUID.randomUUID();
                    editor.playClickSound(1.0f);
                    transitionToEditFilter();
                });
        btnOverwrite.tooltipText = Component.translatable("tooltip.chestseparators.desc.conflict.overwrite")
                .getString();
        popupWidgets.add(btnOverwrite);

        ActionIconButtonWidget btnDeselect = new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 63,
                240,
                16,
                "2. "
                        + Component.translatable("button.chestseparators.conflict.deselect")
                                .getString(),
                null,
                0xFF2D852D,
                () -> {
                    var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                    if (whitelists != null) session.selectedSlots.removeIf(whitelists::containsKey);
                    session.hasSelectionConflict = false;
                    editor.playClickSound(1.0f);
                    if (!session.selectedSlots.isEmpty()) {
                        transitionToEditFilter();
                    } else {
                        editor.showStatus(Component.translatable("message.chestseparators.empty_selection"), ChatFormatting.RED);
                    }
                });
        btnDeselect.tooltipText = Component.translatable("tooltip.chestseparators.desc.conflict.deselect")
                .getString();
        popupWidgets.add(btnDeselect);

        ActionIconButtonWidget btnCancel = new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 81,
                240,
                16,
                "3. " + Component.translatable("button.chestseparators.cancel").getString(),
                null,
                0xFF444444,
                () -> {
                    session.hasSelectionConflict = false;
                    editor.playClickSound(0.8f);
                });
        btnCancel.tooltipText = Component.translatable("tooltip.chestseparators.desc.conflict.cancel")
                .getString();
        popupWidgets.add(btnCancel);
    }

    private void buildSelectSlotsWidgets() {
        int btnX = layout.guiX + layout.bgWidth + 4;
        int btnY = layout.guiY;

        selectSlotsWidgets.add(new ActionIconButtonWidget(
                btnX,
                btnY,
                60,
                20,
                Component.translatable("button.chestseparators.confirm").getString(),
                null,
                0xFF2D852D,
                () -> {
                    if (!session.selectedSlots.isEmpty()) {
                        transitionToEditFilter();
                    }
                    editor.playClickSound(1.0f);
                }));

        selectSlotsWidgets.add(new ActionIconButtonWidget(
                btnX,
                btnY + 25,
                60,
                20,
                Component.translatable("button.chestseparators.cancel").getString(),
                null,
                0xFF852D2D,
                () -> {
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(1.0f);
                }));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (session.hasSelectionConflict) return true;

        if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
            if (session.isDraggingLine) {
                Slot slot = editor.accessor.getFocusedSlot();
                // Confine the drag to the namespace it started in (no chest<->inventory crossover).
                if (slot != null
                        && ChestSeparatorsEditor.isEditableSlot(slot)
                        && (session.dragStartSlot == null
                                || ChestSeparatorsEditor.isPlayerSlot(slot)
                                        == ChestSeparatorsEditor.isPlayerSlot(session.dragStartSlot))) {
                    session.dragCurrentSlot = slot;

                    // Trace mode: immediately commit each slot as the cursor moves.
                    if (session.wlToolMode == 1) {
                        int k = ChestSeparatorsEditor.slotKey(slot);
                        if (session.isSelecting) session.selectedSlots.add(k);
                        else session.selectedSlots.remove(k);
                    }
                }
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0
                && session.isDraggingLine
                && (session.currentState == EditorState.VIEW_GROUPS
                        || session.currentState == EditorState.SELECT_SLOTS)) {
            // Area mode: commit the rectangular selection on mouse release. Membership is by visual box
            // (see slotsInDragBox) so crossing hotbar↔inventory selects only the cells actually swept, and
            // the selection stays confined to the namespace the drag started in.
            if (session.wlToolMode == 0 && session.dragStartSlot != null && session.dragCurrentSlot != null) {
                for (Slot s : editor.slotsInDragBox(session.dragStartSlot, session.dragCurrentSlot)) {
                    int k = ChestSeparatorsEditor.slotKey(s);
                    if (session.isSelecting) session.selectedSlots.add(k);
                    else session.selectedSlots.remove(k);
                }
            }
            session.isDraggingLine = false;
            session.dragStartSlot = null;
            session.dragCurrentSlot = null;
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {

        if (!widgets.isEmpty()) {
            // Button press animations: hold the active state for 150 ms after each click.
            widgets.get(1).isActive = (System.currentTimeMillis() - btnClearAllClickTime < 150); // Delete All Filters
            widgets.get(4).isActive = (System.currentTimeMillis() - btnClearSelectClickTime < 150); // Clear Selection
            widgets.get(5).isActive = (System.currentTimeMillis() - btnCopyClickTime < 150); // Copy

            widgets.get(6).isActive = (System.currentTimeMillis() - btnPasteClickTime < 150); // Paste
            widgets.get(6).isDisabled = !ChestConfigManager.getInstance().hasWhitelistClipboardData();

            widgets.get(7).isActive = (System.currentTimeMillis() - btnUndoClickTime < 150); // Undo
            widgets.get(7).isDisabled = !ChestConfigManager.getInstance().canUndoWhitelist();

            widgets.get(8).isActive = (System.currentTimeMillis() - btnRedoClickTime < 150); // Redo
            widgets.get(8).isDisabled = !ChestConfigManager.getInstance().canRedoWhitelist();

            // Tool toggle buttons: reflect the currently active selection mode.
            widgets.get(2).isActive = (session.wlToolMode == 0); // Area Select
            widgets.get(3).isActive = (session.wlToolMode == 1); // Trace Select
        }

        // When a conflict popup is active, pass (-1, -1) as mouse coordinates so background
        // buttons and slots do not react to hover or draw tooltips.
        int bgMouseX = session.hasSelectionConflict ? -1 : mouseX;
        int bgMouseY = session.hasSelectionConflict ? -1 : mouseY;

        renderWhitelistPreviewPanel(context, bgMouseX, bgMouseY);
        groupRenderer.renderWhitelistGroups(context, bgMouseX, bgMouseY);

        if (session.currentState == EditorState.SELECT_SLOTS) {
            renderSelectionOverlay(context);
            for (CustomWidget w : selectSlotsWidgets) w.render(context, bgMouseX, bgMouseY, delta);
        }

        // Main panel buttons are drawn last so their tooltips render on top of the chest.
        if (session.currentState == EditorState.VIEW_GROUPS) {
            super.render(context, bgMouseX, bgMouseY, delta);
        }

        if (session.hasSelectionConflict) {
            // Pass real mouse coordinates to the popup so its buttons remain clickable.
            renderConflictPopup(context, mouseX, mouseY, delta);
        }
    }

    private void renderConflictPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0xAA000000);

        boolean isDark = GlobalChestConfig.instance.darkMode;
        // Cristal panel (rounded, glass) — matches the unsaved-changes/expel popups in the filter screen.
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(
                context, layout.conflictPopupX, layout.conflictPopupY, layout.conflictPopupW, layout.conflictPopupH);

        Minecraft client = Minecraft.getInstance();
        int maxTextWidth = 240; // Max width matching the buttons

        // 1. Auto-scaling Title
        Component title = Component.translatable("gui.chestseparators.conflict_title");
        int titleWidth = client.font.width(title);
        float titleScale = 1.0f;
        title = Component.literal(client.font.plainSubstrByWidth(title.getString(), maxTextWidth));

        context.pose().pushPose();
        context.pose()
                .translate(
                        layout.conflictPopupX + layout.conflictPopupW / 2.0f,
                        layout.conflictPopupY + 12 + (4 * (1 - titleScale)), 0.0F);
        context.pose().scale(titleScale, titleScale, 1.0F);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, client.font, title, 0, 0, 0xFFFF5555);
        context.pose().popPose();

        // 2. Auto-scaling Description
        Component desc = Component.translatable("gui.chestseparators.conflict_desc");
        int descWidth = client.font.width(desc);
        float descScale = 1.0f;
        desc = Component.literal(client.font.plainSubstrByWidth(desc.getString(), maxTextWidth)); descWidth = client.font.width(desc);

        int scaledDescWidth = (int) (descWidth * descScale);
        int descX = layout.conflictPopupX + (layout.conflictPopupW - scaledDescWidth) / 2;

        context.pose().pushPose();
        context.pose().translate(descX, layout.conflictPopupY + 30 + (4 * (1 - descScale)), 0.0F);
        context.pose().scale(descScale, descScale, 1.0F);
        context.drawString(client.font, desc, 0, 0, isDark ? 0xFFDDDDDD : 0xFF333333, false);
        context.pose().popPose();

        for (CustomWidget w : popupWidgets) {
            w.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderSelectionOverlay(GuiGraphics context) {
        int guiX = layout.guiX;
        int guiY = layout.guiY;

        for (Slot slot : editor.accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;

            // selectedSlots is keyed by slotKey (player slots carry PLAYER_KEY_OFFSET), so match on that —
            // using the raw index would fail to highlight selected inventory slots.
            if (session.selectedSlots.contains(ChestSeparatorsEditor.slotKey(slot))) {
                context.fill(guiX + slot.x, guiY + slot.y, guiX + slot.x + 16, guiY + slot.y + 16, 0x7733FF33);
            }
        }
    }

    // --- MIGRATED RENDERING LOGIC ---

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (session.hasSelectionConflict) return false;

        if (session.isPreviewing && session.previewItems != null) {
            // Allow manual scroll when the cursor is over the preview panel or the chest grid.
            boolean overPanel = mouseX >= layout.listX && mouseX <= layout.listX + layout.listW;
            boolean overChest = mouseX >= layout.guiX && mouseX <= layout.guiX + layout.bgWidth;

            if (overPanel || overChest) {
                float scrollSpeed = 18f;
                int listViewH = layout.listH - 38;
                float maxListScroll = Math.max(0, session.previewItems.size() * 18 - listViewH);

                session.listScrollY = Mth.clamp(
                        session.listScrollY - (float) (verticalAmount * scrollSpeed), 0, maxListScroll);
                session.userOverrodePreviewScroll = true;
                return true;
            }
        }
        return false;
    }

    void transitionToEditFilter() {
        editor.prepareFilterMenu();

        // Snapshot the just-loaded state so we can detect unsaved changes later.
        session.originalItemsSnapshot = new ArrayList<>(session.currentAllowedItems);
        session.originalRuleManual = session.ruleManual;
        session.originalRuleShift = session.ruleShift;
        session.originalRuleHopper = session.ruleHopper;
        session.isUnsavedPopupOpen = false;

        editor.toggleState(EditorState.EDIT_FILTER);
    }
}



