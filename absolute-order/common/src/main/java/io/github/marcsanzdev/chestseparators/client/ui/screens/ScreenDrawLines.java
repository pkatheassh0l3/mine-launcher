package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

public class ScreenDrawLines extends AbstractEditorScreen {

    private ToolButtonWidget btnPencilArea;
    private ToolButtonWidget btnEraserArea;
    private ToolButtonWidget btnClear;
    private ToolButtonWidget btnPencilTrace;
    private ToolButtonWidget btnEraserTrace;
    private ToolButtonWidget btnColorPicker;

    public static final int[] STANDARD_PALETTE = {
        0xFF993333, 0xFFD87F33, 0xFFE5E533, 0xFF7FCC19,
        0xFF667F33, 0xFF4C7F99, 0xFF6699D8, 0xFF334CB2,
        0xFF7F3FB2, 0xFFB24CD8, 0xFFF27FA5, 0xFF664C33,
        0xFFFFFFFF, 0xFF999999, 0xFF4C4C4C, 0xFF191919
    };

    public static final String[] STANDARD_COLOR_KEYS = {
        "red", "orange", "yellow", "lime",
        "green", "cyan", "light_blue", "blue",
        "purple", "magenta", "pink", "brown",
        "white", "light_gray", "gray", "black"
    };

    long lastCustomColorClickTime = 0;
    int lastCustomColorClickIndex = -1;

    private long btnCopyClickTime = 0;
    private long btnPasteClickTime = 0;
    private long btnUndoClickTime = 0;
    private long btnRedoClickTime = 0;
    private long btnClearClickTime = 0;
    private long btnColorPickerClickTime = 0;

    private final SeparatorPreviewRenderer previewRenderer;
    private final SeparatorDragCommitter dragCommitter;
    private final SeparatorDragInput dragInput;
    private final DrawLinesClickHandler clickHandler;

    public ScreenDrawLines(ChestSeparatorsEditor editor) {
        super(editor);
        this.previewRenderer = new SeparatorPreviewRenderer(this);
        this.dragCommitter = new SeparatorDragCommitter(this);
        this.dragInput = new SeparatorDragInput(this);
        this.clickHandler = new DrawLinesClickHandler(this);
    }

    /** Applies the in-progress drag to the saved configuration. Delegates to {@link SeparatorDragCommitter}. */
    public void commitDrag(double mouseX, double mouseY) {
        dragCommitter.commit(mouseX, mouseY);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return dragInput.onDrag(mouseX, mouseY);
    }

    /**
     * Switches the active tab to a paint tool (0 = area, 1 = trace). If the eraser was active (color
     * index -1), the previously picked color is restored instead of resetting to red.
     */
    private void selectPaintTool(int toolMode) {
        int tabMode = session.currentTab;
        if (tabMode == 0) {
            session.lineToolMode = toolMode;
            if (session.lineColorIndex == -1) {
                session.lineColorIndex = session.lineColorBeforeErase;
                session.editingLineCustomIndex = session.lineCustomBeforeErase;
            }
        } else if (tabMode == 1) {
            session.bgToolMode = toolMode;
            if (session.bgColorIndex == -1) {
                session.bgColorIndex = session.bgColorBeforeErase;
                session.editingBgCustomIndex = session.bgCustomBeforeErase;
            }
        } else {
            session.comboToolMode = toolMode;
            if (session.comboColorIndex == -1) {
                session.comboColorIndex = session.comboColorBeforeErase;
                session.editingComboCustomIndex = session.comboCustomBeforeErase;
            }
        }
        editor.playClickSound(1.0f);
    }

    /**
     * Switches the active tab to an eraser tool (0 = area, 1 = trace), remembering the current color
     * so {@link #selectPaintTool} can restore it when the user switches back to a paint tool.
     */
    private void selectEraseTool(int toolMode) {
        int tabMode = session.currentTab;
        if (tabMode == 0) {
            session.lineToolMode = toolMode;
            if (session.lineColorIndex != -1) {
                session.lineColorBeforeErase = session.lineColorIndex;
                session.lineCustomBeforeErase = session.editingLineCustomIndex;
            }
            session.lineColorIndex = -1;
            session.editingLineCustomIndex = -1;
        } else if (tabMode == 1) {
            session.bgToolMode = toolMode;
            if (session.bgColorIndex != -1) {
                session.bgColorBeforeErase = session.bgColorIndex;
                session.bgCustomBeforeErase = session.editingBgCustomIndex;
            }
            session.bgColorIndex = -1;
            session.editingBgCustomIndex = -1;
        } else {
            session.comboToolMode = toolMode;
            if (session.comboColorIndex != -1) {
                session.comboColorBeforeErase = session.comboColorIndex;
                session.comboCustomBeforeErase = session.editingComboCustomIndex;
            }
            session.comboColorIndex = -1;
            session.editingComboCustomIndex = -1;
        }
        editor.playClickSound(1.0f);
    }

    /** Top edge of the left tools/colours sidebar. */
    private int sidebarTop() {
        return layout.guiY + editor.getSidebarYOffset() - 4;
    }

    /** Height of that sidebar: down to the last custom colour swatch, plus a small margin. */
    private int sidebarHeight() {
        int bottomOfLastColor = layout.paletteY + (7 * (layout.swatchSize + 4)) + layout.swatchSize;
        return (bottomOfLastColor - sidebarTop()) + 8;
    }

    @Override
    protected void buildWidgets() {
        // --- 1. RIGHT ACTION PANEL (Copy, Paste, Undo, Redo) ---
        int rightX = layout.rightX;
        int btnW = layout.btnW;
        int bH = layout.bH;

        // Centred against the left sidebar rather than pinned to the top of the container: with only four
        // buttons they otherwise sat far above the panel they visually pair with.
        int blockHeight = 3 * 24 + bH; // four rows on a 24px pitch
        int sy = Math.max(4, sidebarTop() + (sidebarHeight() - blockHeight) / 2);

        WideButtonWidget btnCopy = new WideButtonWidget(
                rightX,
                sy,
                btnW,
                bH,
                Component.translatable("button.chestseparators.copy_layout").getString(),
                ModTextures.ICON_SM_COPY,
                () -> {
                    btnCopyClickTime = System.currentTimeMillis();
                    ChestConfigManager.getInstance().copyAllToClipboard();
                    editor.showStatus(Component.translatable("message.chestseparators.layout_copied"), ChatFormatting.GRAY);
                    editor.playClickSound(1.0f);
                });
        btnCopy.keepNormalTextColor = true;
        btnCopy.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.copy_layout").getString();
        btnCopy.texSize = 128;
        widgets.add(btnCopy);

        WideButtonWidget btnPaste = new WideButtonWidget(
                rightX,
                sy + 24,
                btnW,
                bH,
                Component.translatable("button.chestseparators.paste_layout").getString(),
                ModTextures.ICON_SM_PASTE,
                () -> {
                    btnPasteClickTime = System.currentTimeMillis();
                    if (ChestConfigManager.getInstance().hasClipboardData()) {
                        ChestConfigManager.getInstance().saveSnapshot();
                        ChestConfigManager.getInstance().pasteFromClipboard();
                        editor.saveSmart();
                        editor.showStatus(Component.translatable("message.chestseparators.layout_pasted"), ChatFormatting.GREEN);
                        editor.playClickSound(1.0f);
                    }
                });
        btnPaste.keepNormalTextColor = true;
        btnPaste.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.paste_layout").getString();
        btnPaste.texSize = 128;
        widgets.add(btnPaste);

        WideButtonWidget btnUndo = new WideButtonWidget(
                rightX,
                sy + 48,
                btnW,
                bH,
                Component.translatable("button.chestseparators.undo_action").getString(),
                ModTextures.ICON_SM_UNDO,
                () -> {
                    btnUndoClickTime = System.currentTimeMillis();
                    editor.applyUndoRedo(ChestConfigManager.getInstance().undo(), false);
                });
        btnUndo.keepNormalTextColor = true;
        btnUndo.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.undo_action").getString();
        btnUndo.texSize = 128;
        widgets.add(btnUndo);

        WideButtonWidget btnRedo = new WideButtonWidget(
                rightX,
                sy + 72,
                btnW,
                bH,
                Component.translatable("button.chestseparators.redo_action").getString(),
                ModTextures.ICON_SM_REDO,
                () -> {
                    btnRedoClickTime = System.currentTimeMillis();
                    editor.applyUndoRedo(ChestConfigManager.getInstance().redo(), true);
                });
        btnRedo.keepNormalTextColor = true;
        btnRedo.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.redo_action").getString();
        btnRedo.texSize = 128;
        widgets.add(btnRedo);

        // --- 2. LEFT SIDEBAR TOOLS ---
        int sx = layout.sidebarX;
        int contentX = sx + 7;
        int currentY = layout.guiY + editor.getSidebarYOffset();
        int row2Y = currentY + 22;

        btnPencilArea = new ToolButtonWidget(
                contentX,
                currentY,
                ModTextures.PENCIL_BASE,
                Component.translatable("tooltip.chestseparators.draw_area").getString(),
                () -> selectPaintTool(0));

        btnEraserArea = new ToolButtonWidget(
                contentX + 22,
                currentY,
                ModTextures.ICON_SM_ERASER_AREA,
                Component.translatable("tooltip.chestseparators.erase_area").getString(),
                () -> selectEraseTool(0));

        btnClear = new ToolButtonWidget(
                contentX + 44,
                currentY,
                ModTextures.ICON_SM_TRASH,
                // Placeholder for the first frame only; updateWidgetStates() renames it per active tab.
                Component.translatable("tooltip.chestseparators.clear_lines").getString(),
                () -> {
                    btnClearClickTime = System.currentTimeMillis();
                    ChestConfigManager.getInstance().saveSnapshot();
                    int tabMode = session.currentTab;
                    if (tabMode == 0 || tabMode == 2)
                        ChestConfigManager.getInstance().clearAllLines();
                    if (tabMode == 1 || tabMode == 2)
                        ChestConfigManager.getInstance().clearAllBackgrounds();
                    editor.saveSmart();
                    editor.showStatus(Component.translatable("message.chestseparators.cleared"), ChatFormatting.RED);
                    editor.playClickSound(0.8f);
                });

        btnPencilTrace = new ToolButtonWidget(
                contentX,
                row2Y,
                ModTextures.PENCIL_BASE,
                Component.translatable("tooltip.chestseparators.draw_trace").getString(),
                () -> selectPaintTool(1));

        btnEraserTrace = new ToolButtonWidget(
                contentX + 22,
                row2Y,
                ModTextures.ICON_SM_ERASER_TRACE,
                Component.translatable("tooltip.chestseparators.erase_trace").getString(),
                () -> selectEraseTool(1));

        btnColorPicker = new ToolButtonWidget(
                contentX + 44,
                row2Y,
                ModTextures.ICON_SM_PALETTE,
                Component.translatable("tooltip.chestseparators.open_palette").getString(),
                () -> {
                    btnColorPickerClickTime = System.currentTimeMillis();
                    int tabMode = session.currentTab;
                    if (tabMode == 0) {
                        if (session.editingLineCustomIndex == -1) {
                            session.editingLineCustomIndex = 0;
                            session.lineColorIndex = 16;
                        }
                        editor.openColorPicker(
                                ChestConfigManager.getInstance().getCustomColors(0)[session.editingLineCustomIndex], 0);
                    } else if (tabMode == 1) {
                        if (session.editingBgCustomIndex == -1) {
                            session.editingBgCustomIndex = 0;
                            session.bgColorIndex = 16;
                        }
                        editor.openColorPicker(
                                ChestConfigManager.getInstance().getCustomColors(1)[session.editingBgCustomIndex], 1);
                    } else {
                        if (session.editingComboCustomIndex == -1) {
                            session.editingComboCustomIndex = 0;
                            session.comboColorIndex = 16;
                        }
                        editor.openColorPicker(
                                ChestConfigManager.getInstance().getCustomColors(2)[session.editingComboCustomIndex],
                                2);
                    }
                    editor.playClickSound(1.0f);
                });

        // Smooth (vector) tool icons: sample at 128px with linear filtering.
        btnPencilArea.texSize = 128;
        btnEraserArea.texSize = 128;
        btnClear.texSize = 128;
        btnPencilTrace.texSize = 128;
        btnEraserTrace.texSize = 128;
        btnColorPicker.texSize = 128;

        widgets.add(btnPencilArea);
        widgets.add(btnEraserArea);
        widgets.add(btnClear);
        widgets.add(btnPencilTrace);
        widgets.add(btnEraserTrace);
        widgets.add(btnColorPicker);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Drive the fade-to-empty preview while the Clear button is hovered. Suppressed while the colour
        // picker is open: the whole toolbar is inert then, so hovering the trash button must not paint the
        // clear wash on the slots behind the picker.
        session.clearPreviewTab = (!session.isColorPickerOpen && btnClear != null && btnClear.isHovering(mouseX, mouseY))
                ? session.currentTab : -1;

        if (session.isEyedropperActive) {
            return;
        }

        updateWidgetStates();

        // 1. Draw Left Sidebar Background
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(
                context, layout.sidebarX, sidebarTop(), layout.sidebarWidth, sidebarHeight());

        // While the color picker is open, pass (-1, -1) as mouse coordinates so background
        // buttons do not react to hover or draw tooltips behind the picker window.
        int bgMouseX = session.isColorPickerOpen ? -1 : mouseX;
        int bgMouseY = session.isColorPickerOpen ? -1 : mouseY;

        // 2. Draw Widgets (Tools and Action Panel)
        super.render(context, bgMouseX, bgMouseY, delta);

        // 3. Draw Palette
        drawPaletteArea(context, mouseX, mouseY, session.currentTab);

        // 4. Draw Tabs
        drawTabs(context, mouseX, mouseY);

        // 5. Draw Grid Previews
        previewRenderer.renderDragPreview(context, mouseX, mouseY);
        if (!session.isDraggingLine && !session.isColorPickerOpen) {
            previewRenderer.renderHoverPreview(context, mouseX, mouseY);
        }
    }

    private void updateWidgetStates() {
        int tabMode = session.currentTab;
        int tMode = (tabMode == 0) ? session.lineToolMode : (tabMode == 1 ? session.bgToolMode : session.comboToolMode);
        int cIndex = (tabMode == 0)
                ? session.lineColorIndex
                : (tabMode == 1 ? session.bgColorIndex : session.comboColorIndex);

        int dynamicColor = 0;
        if (tabMode == 0) dynamicColor = getCurrentSelectedLineColorValue();
        else if (tabMode == 1) dynamicColor = getCurrentSelectedBgColorValue();
        else if (tabMode == 2) dynamicColor = getCurrentSelectedComboColorValue();
        if (dynamicColor == 0) dynamicColor = 0xFFFFFF;

        // The trash button clears whatever the active tab edits, so its tooltip names that instead of the
        // generic "layer" — it is the only cue for what a click is about to wipe.
        btnClear.tooltipText = Component.translatable(
                        tabMode == 0
                                ? "tooltip.chestseparators.clear_lines"
                                : tabMode == 1
                                        ? "tooltip.chestseparators.clear_backgrounds"
                                        : "tooltip.chestseparators.clear_both")
                .getString();

        // Tool Active States
        btnPencilArea.isActive = (tMode == 0 && cIndex != ChestSeparatorsEditor.TOOL_ERASER_ID);
        btnPencilTrace.isActive = (tMode == 1 && cIndex != ChestSeparatorsEditor.TOOL_ERASER_ID);
        btnEraserArea.isActive = (tMode == 0 && cIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
        btnEraserTrace.isActive = (tMode == 1 && cIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);

        // Tool Icons & Colors
        // The two paint buttons show the AREA / TRACE mode icon (dashed square / wavy line) drawn ENTIRELY
        // in the current paint colour; the tool type (pencil/brush/combo) is conveyed by the active tab.
        btnPencilArea.baseIcon = ModTextures.ICON_SM_AREA_SELECT;
        btnPencilArea.maskIcon = null;
        btnPencilArea.baseUsesDynamicColor = true;
        btnPencilArea.dynamicColor = dynamicColor;

        btnPencilTrace.baseIcon = ModTextures.ICON_SM_TRACE_SELECT;
        btnPencilTrace.maskIcon = null;
        btnPencilTrace.baseUsesDynamicColor = true;
        btnPencilTrace.dynamicColor = dynamicColor;
        btnPencilTrace.baseOffsetX = 0;
        btnPencilTrace.maskOffsetX = 0;

        // Right Panel Disabled States
        // Right Panel Disabled States & Animations
        if (widgets.size() >= 4) {
            widgets.get(0).isActive = (System.currentTimeMillis() - btnCopyClickTime < 150);

            widgets.get(1).isActive = (System.currentTimeMillis() - btnPasteClickTime < 150);
            widgets.get(1).isDisabled = !ChestConfigManager.getInstance().hasClipboardData();

            widgets.get(2).isActive = (System.currentTimeMillis() - btnUndoClickTime < 150);
            widgets.get(2).isDisabled = !ChestConfigManager.getInstance().canUndo();

            widgets.get(3).isActive = (System.currentTimeMillis() - btnRedoClickTime < 150);
            widgets.get(3).isDisabled = !ChestConfigManager.getInstance().canRedo();
        }

        if (btnClear != null) {
            btnClear.isActive = (System.currentTimeMillis() - btnClearClickTime < 150);
        }

        if (btnColorPicker != null) {
            btnColorPicker.isActive = (System.currentTimeMillis() - btnColorPickerClickTime < 150);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickHandler.onMouseClicked(mouseX, mouseY, button);
    }

    public void drawSwatch(
            GuiGraphics context,
            int x,
            int y,
            int color,
            int index,
            int mouseX,
            int mouseY,
            boolean isCustom,
            int tabMode) {
        boolean disabled = session.isColorPickerOpen && !isCustom;
        int selectedIndexToCheck = (tabMode == 0)
                ? session.lineColorIndex
                : (tabMode == 1 ? session.bgColorIndex : session.comboColorIndex);
        boolean selected = (index == selectedIndexToCheck);
        boolean hover = !disabled && editor.isHovering(x, y, layout.swatchSize, layout.swatchSize, mouseX, mouseY);

        if (color == 0) {
            context.fill(x, y, x + layout.swatchSize, y + layout.swatchSize, 0xFF555555);
            int half = layout.swatchSize / 2;
            context.fill(x, y, x + half, y + half, 0xFF333333);
            context.fill(x + half, y + half, x + layout.swatchSize, y + layout.swatchSize, 0xFF333333);
            drawStandardBevel(context, x, y, layout.swatchSize, layout.swatchSize, selected);
        } else {
            context.fill(x, y, x + layout.swatchSize, y + layout.swatchSize, 0xFF000000 | color);
            drawColorBevel(context, x, y, layout.swatchSize, layout.swatchSize, color, selected);
        }

        if (hover && !selected)
            context.renderOutline(
                    x, y, layout.swatchSize, layout.swatchSize, isCustom ? 0x80FFFFFF : 0xFFFFFFFF);
        if (disabled) context.fill(x, y, x + layout.swatchSize, y + layout.swatchSize, 0x80000000);
    }

    private void drawPaletteArea(GuiGraphics context, int mouseX, int mouseY, int tabMode) {
        int pY = layout.paletteY;
        int col1X = layout.paletteCol1X;
        int col2X = layout.paletteCol2X;
        int col3X = layout.paletteCol3X;

        for (int i = 0; i < 16; i++) {
            int x = (i / 8 == 0) ? col1X : col2X;
            int y = pY + ((i % 8) * (layout.swatchSize + 4));
            drawSwatch(context, x, y, STANDARD_PALETTE[i], i, mouseX, mouseY, false, tabMode);
            if (!session.isColorPickerOpen
                    && editor.isHovering(x, y, layout.swatchSize, layout.swatchSize, mouseX, mouseY)) {
                context.renderTooltip(
                        Minecraft.getInstance().font,
                        Component.translatable("color.minecraft." + STANDARD_COLOR_KEYS[i]),
                        mouseX,
                        mouseY);
            }
        }

        int[] worldColors = ChestConfigManager.getInstance().getCustomColors(tabMode);
        for (int i = 0; i < 8; i++) {
            int y = pY + (i * (layout.swatchSize + 4));
            drawSwatch(context, col3X, y, worldColors[i], 16 + i, mouseX, mouseY, true, tabMode);
            if (!session.isColorPickerOpen
                    && editor.isHovering(col3X, y, layout.swatchSize, layout.swatchSize, mouseX, mouseY)) {
                context.renderTooltip(
                        Minecraft.getInstance().font,
                        Component.translatable("color.chestseparators.custom", (i + 1)),
                        mouseX,
                        mouseY);
            }
        }
    }

    private void drawTabs(GuiGraphics context, int mouseX, int mouseY) {
        int tabX = layout.sidebarX - 20;
        int startY = layout.guiY + editor.getSidebarYOffset() + 10;

        for (int i = 0; i < 3; i++) {
            int tabY = startY + (i * 24);
            boolean isSelected = (session.currentTab == i);
            boolean hover = !session.isColorPickerOpen && editor.isHovering(tabX, tabY, 20, 20, mouseX, mouseY);
            // The tab reaches flush to the sidebar (right edge = sidebarX) and is embedded into it: its
            // right edge has no border/gap so it merges into the panel, and a selected tab shrinks anchored
            // to that right edge so it never pulls away from the sidebar.
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.tab(
                    context,
                    tabX,
                    tabY,
                    20,
                    20,
                    hover,
                    isSelected,
                    io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ATTACH_RIGHT);

            ResourceLocation icon = (i == 0)
                    ? ModTextures.ICON_SM_PENCIL
                    : (i == 1) ? ModTextures.ICON_SM_BRUSH : ModTextures.ICON_SM_COMBO;
            if (isSelected) {
                io.github.marcsanzdev.chestseparators.client.ui.UiTheme.pushActiveContent(context, tabX, tabY, 20, 20);
            }
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context, icon, tabX + 2, tabY + 2, 0.0F, 0.0F, 16, 16, 128, 128, 128, 128, -1);
            if (isSelected) {
                context.pose().popPose();
            }

            if (hover) {
                Component name = (i == 0)
                        ? Component.translatable("tooltip.chestseparators.tab_lines")
                        : (i == 1)
                                ? Component.translatable("tooltip.chestseparators.tab_backgrounds")
                                : Component.translatable("tooltip.chestseparators.tab_combo");
                context.renderTooltip(Minecraft.getInstance().font, name, mouseX, mouseY);
            }
        }
    }

    public int getCurrentSelectedLineColorValue() {
        return resolveSelectedColor(session.lineColorIndex, 0);
    }

    public int getCurrentSelectedBgColorValue() {
        return resolveSelectedColor(session.bgColorIndex, 1);
    }

    public int getCurrentSelectedComboColorValue() {
        return resolveSelectedColor(session.comboColorIndex, 2);
    }

    /**
     * Resolves the ARGB value a color index points to: 0 for the eraser, a standard palette entry
     * for indices 0-15, or a world custom color (for the given tab) for indices 16-23.
     */
    private int resolveSelectedColor(int colorIndex, int tabMode) {
        if (colorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID) return 0;
        if (colorIndex < 16) return STANDARD_PALETTE[colorIndex];
        if (colorIndex < 24) return ChestConfigManager.getInstance().getCustomColors(tabMode)[colorIndex - 16];
        return 0;
    }
}
