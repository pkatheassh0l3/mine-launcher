package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.UiColors;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ActionIconButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class ScreenEditFilter extends AbstractEditorScreen {

    // Package-private so the extracted FilterClickHandler can route clicks to the popups.
    final List<CustomWidget> unsavedPopupWidgets = new ArrayList<>();
    final List<CustomWidget> expelPopupWidgets = new ArrayList<>();

    /** How long the held row takes to fly from where it was grabbed to the cursor. */
    static final long PICKUP_MS = 140L;
    /** How long the list takes to settle after a drop, rows sliding to their new places. */
    static final long DROP_MS = 170L;
    /** How long a row takes to shrink away after being dragged out of the filter onto the grid. */
    static final long DELETE_MS = 220L;

    private final FilterPanelRenderer panelRenderer;
    private final FilterClickHandler clickHandler;

    public ScreenEditFilter(ChestSeparatorsEditor editor) {
        super(editor);
        this.panelRenderer = new FilterPanelRenderer(this);
        this.clickHandler = new FilterClickHandler(this);
    }

    public void requestFinish() {
        session.isUnsavedPopupOpen = false;
        session.isExpelPopupOpen = false;
        session.finishingRegion = true;
        editor.openRegionColor();
    }

    public void completeSave() {
        if (!session.finishingRegion || session.pendingRegionColor == null) return;
        ChestConfigManager.getInstance().saveWhitelistSnapshot();
        editor.saveCurrentGroup();
        session.selectedSlots.clear();
        session.finishingRegion = false;
        editor.toggleState(EditorState.VIEW_GROUPS);
        editor.playClickSound(1.0f);
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickHandler.onMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void init() {
        super.init();
        session.isUnsavedPopupOpen = false;
        session.isExpelPopupOpen = false;

        unsavedPopupWidgets.clear();
        buildUnsavedPopupWidgets();

        expelPopupWidgets.clear();
        buildExpelPopupWidgets();

        // Every popup button closes the popup, so flash it before acting (else the press is never seen).
        for (CustomWidget w : unsavedPopupWidgets) w.deferAction = true;
        for (CustomWidget w : expelPopupWidgets) w.deferAction = true;
    }

    private void buildUnsavedPopupWidgets() {
        unsavedPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 45,
                240,
                16,
                "1. "
                        + Component.translatable("button.chestseparators.save_and_exit")
                                .getString(),
                null,
                0xFF2D852D,
                () -> {
                    requestFinish();
                }));

        unsavedPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 63,
                240,
                16,
                "2. "
                        + Component.translatable("button.chestseparators.discard_changes")
                                .getString(),
                null,
                0xFF852D2D,
                () -> {
                    session.selectedSlots.clear();
                    session.isUnsavedPopupOpen = false;
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(1.0f);
                }));

        unsavedPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 81,
                240,
                16,
                "3. " + Component.translatable("button.chestseparators.cancel").getString(),
                null,
                0xFF444444,
                () -> {
                    session.isUnsavedPopupOpen = false;
                    editor.playClickSound(0.8f);
                }));
    }

    private void buildExpelPopupWidgets() {
        expelPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 63,
                240,
                16,
                "1. " + Component.translatable("button.chestseparators.accept_expel").getString(),
                null,
                0xFF2D852D,
                () -> {
                    requestFinish();
                }));

        expelPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 81,
                240,
                16,
                "2. " + Component.translatable("button.chestseparators.cancel").getString(),
                null,
                0xFF444444,
                () -> {
                    session.isExpelPopupOpen = false;
                    editor.playClickSound(0.8f);
                }));
    }

    /**
     * The item being dragged, drawn following the cursor: slightly smaller than in the list and washed
     * down so it reads as "held" rather than as another entry. Items are rendered by their own pipeline
     * and cannot simply be given an alpha, so the translucency is faked by laying the panel colour back
     * over it — the same trick the push/pull ghost preview uses.
     */
    private void drawReorderGhost(GuiGraphics context) {
        drawDeleteAnim(context);

        if (session.reorderDragItem != null) {
            // Pick-up: the row travels from wherever it was grabbed to the cursor, and the bar
            // (background, name, cross) fades in around the icon on the way — so a grid item visibly
            // becomes an entry instead of popping into one.
            float t = net.minecraft.util.Mth.clamp(
                    (System.currentTimeMillis() - session.reorderStartTime) / (float) PICKUP_MS, 0f, 1f);
            float eased = 1f - (1f - t) * (1f - t) * (1f - t); // ease-out cubic
            int rowW = layout.listW - 20;
            int restX = session.reorderMouseX - (int) (rowW * 0.9f) / 2;
            int restY = session.reorderMouseY - (int) (18 * 0.9f) / 2;
            drawGhostRow(
                    context,
                    session.reorderDragItem,
                    Math.round(session.reorderOriginX + (restX - session.reorderOriginX) * eased),
                    Math.round(session.reorderOriginY + (restY - session.reorderOriginY) * eased),
                    eased,
                    1f,
                    isRemovalDrop(session.reorderMouseX, session.reorderMouseY));
            return;
        }

        if (session.dropGhostItem == null) return;
        float t = (System.currentTimeMillis() - session.dropGhostStart) / (float) DROP_MS;
        if (t >= 1f) {
            session.dropGhostItem = null;
            return;
        }
        // Landing: the exact reverse of the pick-up — it flies into its new row and the bar fades away.
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        drawGhostRow(
                context,
                session.dropGhostItem,
                Math.round(session.dropGhostFromX + (session.dropGhostToX - session.dropGhostFromX) * eased),
                Math.round(session.dropGhostFromY + (session.dropGhostToY - session.dropGhostFromY) * eased),
                1f - eased,
                1f,
                false);
    }

    /** The dropped row collapsing in on itself, the visual counterpart of leaving the filter. */
    private void drawDeleteAnim(GuiGraphics context) {
        if (session.deleteAnimItem == null) return;
        float t = (System.currentTimeMillis() - session.deleteAnimStart) / (float) DELETE_MS;
        if (t >= 1f) {
            session.deleteAnimItem = null;
            return;
        }
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        drawGhostRow(
                context,
                session.deleteAnimItem,
                session.deleteAnimX,
                session.deleteAnimY,
                1f - eased,
                1f - eased,
                true);
    }

    /**
     * Draws the floating row at {@code (gx, gy)}. {@code presence} fades the bar around the item icon,
     * {@code shrink} scales the whole row about its own centre, and {@code danger} tints it red to say
     * that letting go here drops the item out of the filter.
     */
    private void drawGhostRow(
            GuiGraphics context, String itemId, int gx, int gy, float presence, float shrink, boolean danger) {
        Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(itemId));
        if (item == null) return;

        Minecraft client = Minecraft.getInstance();
        int rowW = layout.listW - 20; // the row's width inside the list's inset
        int rowH = 18;
        shrink = net.minecraft.util.Mth.clamp(shrink, 0f, 1f);
        float scale = 0.9f * shrink;
        int barAlpha = (int) (0xFF * net.minecraft.util.Mth.clamp(presence, 0f, 1f));

        // Shrinking pulls the row towards its own centre rather than towards its top-left corner, so it
        // collapses in place instead of sliding up and to the left as it goes.
        int drawnW = (int) (rowW * 0.9f * shrink);
        int drawnH = (int) (rowH * 0.9f * shrink);
        gx += ((int) (rowW * 0.9f) - drawnW) / 2;
        gy += ((int) (rowH * 0.9f) - drawnH) / 2;

        context.pose().pushPose();
        context.pose().translate(gx, gy, 0.0F);
        context.pose().scale(scale, scale, 1.0F);

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(
                context, 0, 0, rowW, rowH, ((barAlpha * 0xC8 / 0xFF) << 24) | (danger ? 0x2A1214 : 0x121218));
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(
                context,
                0,
                0,
                rowW,
                rowH,
                danger ? (barAlpha << 24) | 0xFF5555 : ((barAlpha * 0x55 / 0xFF) << 24) | 0xFFFFFF);

        context.renderItem(item.getDefaultInstance(), 3, 1);

        String name = item.getDescription().getString();
        context.pose().pushPose();
        float textScale = 1.0f;
        context.pose().scale(textScale, textScale, 1.0F);
        int maxNameW = (int) ((rowW - 42) / textScale);
        if (client.font.width(name) > maxNameW) {
            name = client.font.plainSubstrByWidth(name, maxNameW - 6) + "...";
        }
        context.drawString(
                client.font,
                name,
                (int) (22 / textScale),
                (int) (6 / textScale),
                (barAlpha << 24) | (danger ? 0xFFAAAA : 0xFFFFFF),
                false);
        context.pose().popPose();

        context.drawString(
                client.font, "x", rowW - 13, 5, (barAlpha << 24) | (danger ? 0xFF5555 : 0xAAAAAA), false);
        context.pose().popPose();

        // Items are drawn by their own pipeline and take no alpha, so the "held" translucency is faked by
        // laying the panel colour back over the finished row. Over the grid the wash turns red, which is
        // the whole preview: the row you are holding reads as "about to be thrown away".
        context.fill(gx, gy, gx + drawnW, gy + drawnH, danger ? 0x66B02020 : 0x55121218);
    }

    /** True while the cursor is over the item grid, where letting go throws the row out of the filter. */
    boolean isOverGrid(double mouseX, double mouseY) {
        return mouseX >= layout.mainX
                && mouseX <= layout.mainX + layout.mainW
                && mouseY >= layout.mainY
                && mouseY <= layout.mainY + layout.mainH;
    }

    /**
     * True when releasing right now would remove the held item instead of repositioning it: only rows
     * picked up from the filter's own list can be thrown away, so an item dragged in from the grid never
     * turns red on its way back over it.
     */
    boolean isRemovalDrop(double mouseX, double mouseY) {
        return session.reorderDragItem != null && !session.reorderFromGrid && isOverGrid(mouseX, mouseY);
    }

    /** Row index the cursor is currently over in the visible list, clamped to "past the last row". */
    int reorderTargetRow(double mouseY) {
        int row = (int) ((mouseY - layout.listViewY + session.listScrollY) / 18);
        return Math.max(0, Math.min(row, session.visibleLeftListItems.size()));
    }

    /**
     * Drops the dragged item at the cursor's row, shifting the rest along. The list on screen may be
     * filtered by the search box, so the drop point is resolved through the item that is currently shown
     * at that row and then translated back to its position in the filter's own list.
     */
    /** True while the cursor is over the filter list, the only place a drop can land. */
    boolean isOverList(double mouseX, double mouseY) {
        return mouseX >= layout.listX
                && mouseX <= layout.listX + layout.listW
                && mouseY >= layout.listViewY
                && mouseY <= layout.listViewY + layout.listViewH;
    }

    /** Turns a held grid press into a real drag once it is held long enough or moved far enough. */
    private void promotePendingDrag() {
        if (session.pendingDragItem == null || session.reorderDragItem != null) return;
        long held = System.currentTimeMillis() - session.pendingDragTime;
        int dx = session.reorderMouseX - session.pendingDragX;
        int dy = session.reorderMouseY - session.pendingDragY;
        if (held < 160L && (dx * dx + dy * dy) < 16) return;

        session.reorderDragItem = session.pendingDragItem;
        session.reorderFromGrid = true;
        session.reorderStartTime = System.currentTimeMillis();
        session.pendingDragItem = null;
    }

    /** Mouse button released: either finish a drag, or resolve the press as a plain click. */
    public void commitPointerRelease(double mouseX, double mouseY) {
        if (session.reorderDragItem != null) {
            commitReorder(mouseX, mouseY);
            return;
        }
        String clicked = session.pendingDragItem;
        session.pendingDragItem = null;
        if (clicked == null) return;

        // Never promoted, so it was a click: keep the original add-to-the-end / remove toggle.
        List<String> all = session.currentAllowedItems;
        if (all.contains(clicked)) {
            all.remove(clicked);
            editor.playClickSound(0.8f);
        } else {
            all.add(clicked);
            editor.playClickSound(1.0f);
        }
        editor.updateWhitelistSearchCache();
    }

    public void commitReorder(double mouseX, double mouseY) {
        String dragged = session.reorderDragItem;
        int releaseX = session.reorderMouseX;
        int releaseY = session.reorderMouseY;
        boolean removal = isRemovalDrop(mouseX, mouseY);
        session.reorderDragItem = null;
        session.reorderFromGrid = false;
        if (dragged == null) return;

        List<String> all = session.currentAllowedItems;

        // Remember where every row sat, so they can slide to their new places instead of jumping.
        java.util.Map<String, Integer> before = new java.util.HashMap<>();
        for (int i = 0; i < session.visibleLeftListItems.size(); i++) {
            before.put(session.visibleLeftListItems.get(i), i);
        }

        // Dragging a row out of the filter and onto the grid is the same as removing it: the grid is where
        // items that are not in the filter live, so dropping one back there returns it.
        if (removal) {
            if (!all.remove(dragged)) return;
            editor.updateWhitelistSearchCache();
            session.dropAnimFrom = before;
            session.dropAnimStart = System.currentTimeMillis();

            int rowW = layout.listW - 20;
            session.deleteAnimItem = dragged;
            session.deleteAnimX = releaseX - (int) (rowW * 0.9f) / 2;
            session.deleteAnimY = releaseY - (int) (18 * 0.9f) / 2;
            session.deleteAnimStart = System.currentTimeMillis();
            // "Item removed from the filter" — the vanilla item-frame remove sound reads as taking an item out.
            editor.playUiSound(net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0f);
            return;
        }

        // Dropping anywhere else does nothing at all — same rule the caret follows, so what you see is
        // what happens.
        if (!isOverList(mouseX, mouseY)) return;

        List<String> visible = session.visibleLeftListItems;
        int row = reorderTargetRow(mouseY);

        int insertBefore;
        if (row >= visible.size()) {
            insertBefore = all.size(); // dropped past the last row: send it to the end
        } else {
            String anchor = visible.get(row);
            if (anchor.equals(dragged)) return; // dropped on itself: nothing to do
            insertBefore = all.indexOf(anchor);
            if (insertBefore < 0) return;
        }

        int from = all.indexOf(dragged);
        if (from >= 0) {
            all.remove(from);
            if (insertBefore > from) insertBefore--; // removing the item shifted everything after it
        }
        all.add(Math.max(0, Math.min(insertBefore, all.size())), dragged);

        editor.updateWhitelistSearchCache();
        session.dropAnimFrom = before;
        session.dropAnimStart = System.currentTimeMillis();

        // Reverse of the pick-up: the held row flies from the cursor down into the slot it just took,
        // fading out as it lands, instead of vanishing the instant the button comes up.
        int landed = session.visibleLeftListItems.indexOf(dragged);
        if (landed >= 0) {
            int rowW = layout.listW - 20;
            session.dropGhostItem = dragged;
            session.dropGhostFromX = releaseX - (int) (rowW * 0.9f) / 2;
            session.dropGhostFromY = releaseY - (int) (18 * 0.9f) / 2;
            session.dropGhostToX = layout.listX + 6;
            session.dropGhostToY = layout.listViewY + (landed * 18) - (int) session.listScrollY;
            session.dropGhostStart = System.currentTimeMillis();
        }
        // A grid item newly dropped into the filter (from < 0) gets the item-frame "add" sound; a pure
        // reorder of a row already in the filter keeps the neutral UI click.
        if (from < 0) editor.playUiSound(net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0f);
        else editor.playClickSound(1.1f);
    }

    /** Scrolls the list while an item is held against its top or bottom edge. */
    private void updateReorderAutoScroll() {
        if (session.reorderDragItem == null) return;
        float maxScroll = layout.maxListScroll(session.visibleLeftListItems.size());
        if (maxScroll <= 0) return;

        int edge = 14;
        int top = layout.listViewY;
        int bottom = layout.listViewY + layout.listViewH;
        float step = 2.5f;
        if (session.reorderMouseY < top + edge) {
            session.listScrollY = net.minecraft.util.Mth.clamp(session.listScrollY - step, 0, maxScroll);
        } else if (session.reorderMouseY > bottom - edge) {
            session.listScrollY = net.minecraft.util.Mth.clamp(session.listScrollY + step, 0, maxScroll);
        }
    }

    /**
     * True when the filter under edit targets player-inventory slots — so its third rule is "Pick Up"
     * rather than "Hopper Insert". Decided by the filter's own slots (their keys are offset into the
     * inventory namespace), NOT by the screen it was opened from: an inventory filter edited from a chest
     * must still show Pick Up.
     */
    boolean isInventoryFilter() {
        for (int key : session.selectedSlots) {
            if (io.github.marcsanzdev.chestseparators.data.ChestConfigManager.isInventoryKey(key)) return true;
        }
        return false;
    }

    boolean hasUnsavedChanges() {
        if (session.pendingRegionColor != null) return true;
        if (session.originalRuleManual != session.ruleManual) return true;
        if (session.originalRuleShift != session.ruleShift) return true;
        if (session.originalRuleHopper != session.ruleHopper) return true;

        // Order is part of the filter now (it can be rearranged by dragging and is saved), so compare the
        // lists as they are: reordering alone counts as an unsaved change.
        return !session.originalItemsSnapshot.equals(session.currentAllowedItems);
    }

    @Override
    protected void buildWidgets() {
        int btnW = layout.btnW;
        int bH = layout.bH;
        int btnX = layout.rightX;
        int mainY = layout.mainY;

        // Ender Chests have no Hopper Insert rule (hoppers cannot reach them), so that button is dropped
        // entirely. With only two rule buttons left, they are re-centred in the middle section so the three
        // groups stay balanced between the panel's top and bottom edges instead of leaving a gap. An
        // inventory filter always keeps its third rule (Pick Up) even inside an Ender Chest screen.
        boolean showThirdRule = !session.isEnderChest || isInventoryFilter();
        int ruleManualY = showThirdRule ? mainY + 83 : mainY + 95;
        int ruleShiftY = showThirdRule ? mainY + 107 : mainY + 119;

        // Group 1
        WideButtonWidget btnImport = new WideButtonWidget(
                btnX,
                mainY,
                btnW,
                bH,
                Component.translatable("button.chestseparators.import_extracted").getString(),
                ModTextures.ICON_SM_IMPORT,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    boolean isSearching = editor.searchBox != null
                            && !editor.searchBox.getValue().isEmpty();

                    if (isSearching) {
                        int addedCount = 0;
                        for (Item item : session.filteredItems) {
                            String id = BuiltInRegistries.ITEM.getKey(item).toString();
                            if (!session.currentAllowedItems.contains(id)) {
                                session.currentAllowedItems.add(id);
                                addedCount++;
                            }
                        }
                        if (addedCount > 0) editor.playClickSound(1.2f);
                        else {
                            editor.showStatus(
                                    Component.translatable("message.chestseparators.items_already_in_filter"),
                                    ChatFormatting.RED);
                            editor.playClickSound(0.8f);
                        }
                    } else {
                        List<String> extracted = editor.extractItemsFromSelection();
                        extracted.removeIf(id -> {
                            Item it = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(id));
                            return it != null && !editor.isItemAllowedForFilter(it);
                        });
                        if (!extracted.isEmpty()) {
                            session.currentAllowedItems.addAll(extracted);
                            editor.playClickSound(1.2f);
                        } else {
                            editor.showStatus(
                                    Component.translatable("message.chestseparators.no_new_items_found"), ChatFormatting.RED);
                            editor.playClickSound(0.8f);
                        }
                    }
                    session.forceStopPreview = true;
                    session.isPreviewing = false;
                    session.previewItems.clear();
                    editor.updateWhitelistSearchCache();
                });
        btnImport.tooltipText = Component.translatable("tooltip.chestseparators.desc.import_extracted")
                .getString();
        btnImport.texSize = 128;
        widgets.add(btnImport);

        WideButtonWidget btnAllowAll = new WideButtonWidget(
                btnX,
                mainY + 24,
                btnW,
                bH,
                Component.translatable("button.chestseparators.allow_all").getString(),
                ModTextures.ICON_SM_ALLOW_ALL,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    session.currentAllowedItems.clear();
                    for (Item item : session.allGameItems)
                        if (editor.isItemAllowedForFilter(item))
                            session.currentAllowedItems.add(
                                    BuiltInRegistries.ITEM.getKey(item).toString());
                    editor.playClickSound(1.0f);
                    session.forceStopPreview = true;
                    session.isPreviewing = false;
                    session.previewItems.clear();
                    editor.updateWhitelistSearchCache();
                });
        btnAllowAll.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.allow_all").getString();
        btnAllowAll.texSize = 128;
        widgets.add(btnAllowAll);

        WideButtonWidget btnClearAll = new WideButtonWidget(
                btnX,
                mainY + 48,
                btnW,
                bH,
                Component.translatable("button.chestseparators.clear_all").getString(),
                ModTextures.ICON_SM_CLEAR,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    session.currentAllowedItems.clear();
                    editor.playClickSound(1.0f);
                    session.forceStopPreview = true;
                    session.isPreviewing = false;
                    session.previewItems.clear();
                    editor.updateWhitelistSearchCache();
                });
        btnClearAll.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.clear_all").getString();
        btnClearAll.texSize = 128;
        widgets.add(btnClearAll);

        // Group 2
        WideButtonWidget btnManual = new WideButtonWidget(
                btnX,
                ruleManualY,
                btnW,
                bH,
                Component.translatable("button.chestseparators.manual_insert").getString(),
                ModTextures.ICON_SM_MANUAL,
                () -> {
                    session.ruleManual = !session.ruleManual;
                    editor.playClickSound(1.0f);
                });
        btnManual.tooltipText = session.ruleManual
                ? Component.translatable("tooltip.chestseparators.rule.manual_active")
                        .getString()
                : Component.translatable("tooltip.chestseparators.rule.manual_inactive")
                        .getString();
        btnManual.texSize = 128;
        widgets.add(btnManual);

        WideButtonWidget btnShift = new WideButtonWidget(
                btnX,
                ruleShiftY,
                btnW,
                bH,
                Component.translatable("button.chestseparators.shift_insert").getString(),
                ModTextures.ICON_SM_SHIFT,
                () -> {
                    session.ruleShift = !session.ruleShift;
                    editor.playClickSound(1.0f);
                });
        btnShift.tooltipText = session.ruleShift
                ? Component.translatable("tooltip.chestseparators.rule.shift_active").getString()
                : Component.translatable("tooltip.chestseparators.rule.shift_inactive")
                        .getString();
        btnShift.texSize = 128;
        widgets.add(btnShift);

        // For an inventory filter this third rule is "Pick Up" (filter items picked up from the ground)
        // instead of "Hopper Insert", which does not apply to your own inventory. It reuses the same
        // ruleHopper flag for storage. Whether it is Pick Up or Hopper depends on the FILTER'S slots, not
        // the screen: an inventory filter edited from a chest must still show Pick Up. Ender Chests drop
        // the Hopper rule (hoppers cannot reach them), but an inventory filter always keeps Pick Up.
        boolean invFilter = isInventoryFilter();
        if (showThirdRule) {
            WideButtonWidget btnHopper = new WideButtonWidget(
                    btnX,
                    mainY + 131,
                    btnW,
                    bH,
                    Component.translatable(
                                    invFilter
                                            ? "button.chestseparators.pickup_insert"
                                            : "button.chestseparators.hopper_insert")
                            .getString(),
                    invFilter ? ModTextures.ICON_SM_PICKUP : ModTextures.ICON_SM_HOPPER,
                    () -> {
                        session.ruleHopper = !session.ruleHopper;
                        editor.playClickSound(1.0f);
                    });
            if (invFilter) {
                btnHopper.tooltipText = session.ruleHopper
                        ? Component.translatable("tooltip.chestseparators.rule.pickup_active")
                                .getString()
                        : Component.translatable("tooltip.chestseparators.rule.pickup_inactive")
                                .getString();
            } else if (!session.isEntityChest || session.isMinecartChest) {
                btnHopper.tooltipText = session.ruleHopper
                        ? Component.translatable("tooltip.chestseparators.rule.hopper_active")
                                .getString()
                        : Component.translatable("tooltip.chestseparators.rule.hopper_inactive")
                                .getString();
            }
            btnHopper.texSize = 128;
            widgets.add(btnHopper);
        }

        // Group 3 (Actions) — stacked full-width in visual order: Save, Delete, Cancel.
        ActionIconButtonWidget btnDelete = new ActionIconButtonWidget(
                btnX,
                mainY + 190,
                btnW,
                bH,
                Component.translatable("button.chestseparators.delete").getString(),
                ModTextures.ICON_SM_TRASH,
                UiColors.SURFACE_DARK,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    editor.deleteCurrentGroup();
                    session.selectedSlots.clear();
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(0.8f);
                });
        btnDelete.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.delete_group").getString();
        btnDelete.texSize = 128;
        btnDelete.deferAction = true; // flash before this button closes the filter screen
        widgets.add(btnDelete);

        ActionIconButtonWidget btnSave = new ActionIconButtonWidget(
                btnX,
                mainY + 166,
                btnW,
                bH,
                "Elegir color",
                ModTextures.ICON_SM_SAVE,
                UiColors.SURFACE_DARK,
                () -> {
                    // Check whether saving will cause existing items to be expelled.
                    boolean willExpel = false;

                    // Expulsion can only occur when all three insertion rules are blocked.
                    if (io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.expelInvalidItems
                            && session.ruleManual
                            && session.ruleShift
                            && session.ruleHopper) {

                        for (int slotIdx : session.selectedSlots) {
                            if (slotIdx >= 0
                                    && slotIdx
                                            < editor.accessor.getHandler().slots.size()) {
                                net.minecraft.world.item.ItemStack stack = editor.accessor
                                        .getHandler()
                                        .getSlot(slotIdx)
                                        .getItem();
                                if (!stack.isEmpty()) {
                                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                            .getKey(stack.getItem())
                                            .toString();
                                    if (!session.currentAllowedItems.contains(id)) {
                                        willExpel = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (willExpel) {
                        session.isExpelPopupOpen = true;
                        editor.playClickSound(1.0f);
                    } else {
                        requestFinish();
                    }
                });
        btnSave.tooltipText =
                "Elige y confirma un color para guardar la zona";
        btnSave.texSize = 128;
        btnSave.deferAction = true; // flash before saving closes the filter screen (or opens the expel popup)
        widgets.add(btnSave);

        ActionIconButtonWidget btnCancel = new ActionIconButtonWidget(
                btnX,
                mainY + 214,
                btnW,
                bH,
                Component.translatable("button.chestseparators.cancel").getString(),
                ModTextures.ICON_SM_CANCEL,
                UiColors.SURFACE_DARK,
                () -> {
                    session.selectedSlots.clear();
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(1.0f);
                });
        btnCancel.texSize = 128;
        btnCancel.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.cancel").getString();
        btnCancel.deferAction = true; // flash before this button closes the filter screen
        widgets.add(btnCancel);

    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {

        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0x77000000);
        if (session.pendingRegionColor != null) for (var slot : editor.accessor.getHandler().slots) {
            if (ChestSeparatorsEditor.isEditableSlot(slot) && session.selectedSlots.contains(ChestSeparatorsEditor.slotKey(slot)))
                context.fill(layout.guiX + slot.x, layout.guiY + slot.y, layout.guiX + slot.x + 16, layout.guiY + slot.y + 16, (session.pendingRegionColor & 0xFFFFFF) | 0x77000000);
        }

        // When any popup is active — or an item is being carried on the cursor — pass (-1, -1) as mouse
        // coordinates so background methods do not react to hover or draw tooltips. While dragging, a
        // tooltip or a button preview firing under the held row is just noise on top of it.
        boolean isAnyPopupOpen = session.isUnsavedPopupOpen || session.isExpelPopupOpen;
        boolean suppressHover = isAnyPopupOpen || session.reorderDragItem != null;
        int bgMouseX = suppressHover ? -1 : mouseX;
        int bgMouseY = suppressHover ? -1 : mouseY;

        if (session.pendingDragItem != null) {
            session.reorderMouseX = mouseX;
            session.reorderMouseY = mouseY;
            promotePendingDrag();
        }

        updateWidgetStates(bgMouseX, bgMouseY);
        handlePreviewLogic(bgMouseX, bgMouseY);
        updateReorderAutoScroll();

        panelRenderer.drawLeftPanel(context, bgMouseX, bgMouseY);
        panelRenderer.drawMainPanel(context, bgMouseX, bgMouseY);

        super.render(context, bgMouseX, bgMouseY, delta);

        // Static hint at the very bottom, matching the magnifier one in the layout editor: middle-clicking
        // an item is not discoverable on its own, and it is the entry point to the whole tag mechanic.
        if (!isAnyPopupOpen) {
            Minecraft client = Minecraft.getInstance();
            Component hint = Component.translatable("message.chestseparators.tag_hint");
            context.drawString(
                    client.font,
                    hint,
                    (layout.screenWidth - client.font.width(hint)) / 2,
                    layout.screenHeight - 12,
                    0x70FFFFFF,
                    false);
        }

        // The dragged row must sit on top of the item grid. Its bar/name are fills (drawn before items in the
        // batch order) and the grid items also write depth, so without committing + clearing depth here the
        // grid icons punch through the dragged row. Flush + clear depth, then the ghost renders cleanly on top.
        io.github.marcsanzdev.chestseparators.client.ui.EditorRenderer.flushAndClearDepth(context);
        drawReorderGhost(context);

        panelRenderer.drawTagDropdown(context, bgMouseX, bgMouseY);

        // Popup rendering.
        if (isAnyPopupOpen) {
            // Modal: flush + clear depth so the item-grid icons and their labels (drawn earlier, and flushed
            // last in the fixed batch order) cannot punch through the confirmation popup.
            io.github.marcsanzdev.chestseparators.client.ui.EditorRenderer.flushAndClearDepth(context);
            context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0xAA000000);

            int currentPopupH = layout.conflictPopupH;
            boolean isDark = GlobalChestConfig.instance.darkMode;

            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(
                    context, layout.conflictPopupX, layout.conflictPopupY, layout.conflictPopupW, currentPopupH);

            Minecraft client = Minecraft.getInstance();
            int maxContentWidth = layout.conflictPopupW - 20; // Margen interno de 10px por lado

            Component title = session.isExpelPopupOpen
                    ? Component.translatable("gui.chestseparators.expel_items_title")
                    : Component.translatable("gui.chestseparators.unsaved_changes_title");
            int titleColor = session.isExpelPopupOpen ? 0xFFFF5555 : 0xFFFFAA00;

            int titleWidth = client.font.width(title);
            float titleScale = 1.0f;
        title = Component.literal(client.font.plainSubstrByWidth(title.getString(), maxContentWidth));

            context.pose().pushPose();
            context.pose()
                    .translate(
                            layout.conflictPopupX + layout.conflictPopupW / 2.0f,
                            layout.conflictPopupY + 12 + (4 * (1 - titleScale)), 0.0F);
            context.pose().scale(titleScale, titleScale, 1.0F);
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, client.font, title, 0, 0, titleColor);
            context.pose().popPose();

            Component desc = session.isExpelPopupOpen
                    ? Component.translatable("gui.chestseparators.expel_items_desc")
                    : Component.translatable("gui.chestseparators.unsaved_changes_desc");

            if (session.isExpelPopupOpen) {
                int descY = layout.conflictPopupY + 26;
                for (net.minecraft.util.FormattedCharSequence line : client.font.split(desc, maxContentWidth)) {
                    int lineWidth = client.font.width(line);
                    context.drawString(
                            client.font,
                            line,
                            layout.conflictPopupX + (layout.conflictPopupW - lineWidth) / 2,
                            descY,
                            (isDark ? 0xFFDDDDDD : 0xFF333333),
                            false);
                    descY += client.font.lineHeight + 1;
                }
            } else {
                int descWidth = client.font.width(desc);
                float descScale = 1.0f;
        desc = Component.literal(client.font.plainSubstrByWidth(desc.getString(), maxContentWidth)); descWidth = client.font.width(desc);
                int scaledDescWidth = (int) (descWidth * descScale);
                int descX = layout.conflictPopupX + (layout.conflictPopupW - scaledDescWidth) / 2;

                context.pose().pushPose();
                context.pose().translate(descX, layout.conflictPopupY + 30 + (4 * (1 - descScale)), 0.0F);
                context.pose().scale(descScale, descScale, 1.0F);
                context.drawString(client.font, desc, 0, 0, (isDark ? 0xFFDDDDDD : 0xFF333333), false);
                context.pose().popPose();
            }

            // Renderizamos los botones correspondientes
            List<CustomWidget> activeWidgets = session.isExpelPopupOpen ? expelPopupWidgets : unsavedPopupWidgets;
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget w : activeWidgets) {
                w.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void updateWidgetStates(int mouseX, int mouseY) {
        if (widgets.isEmpty()) return;

        boolean isSearching =
                editor.searchBox != null && !editor.searchBox.getValue().isEmpty();
        WideButtonWidget importBtn = (WideButtonWidget) widgets.get(0);
        importBtn.icon = isSearching ? ModTextures.ICON_SM_SEARCH : ModTextures.ICON_SM_IMPORT;
        importBtn.tooltipText = isSearching
                ? Component.translatable("tooltip.chestseparators.desc.import_search")
                        .getString()
                : Component.translatable("tooltip.chestseparators.desc.import_extracted")
                        .getString();

        String newText = isSearching
                ? Component.translatable("button.chestseparators.import_search").getString()
                : Component.translatable("button.chestseparators.import_extracted").getString();
        importBtn.label = newText;

        widgets.get(1).isActive = (session.currentAllowedItems.size() == session.allGameItems.size());
        widgets.get(2).isActive = session.currentAllowedItems.isEmpty();

        widgets.get(3).isActive = session.ruleManual;
        widgets.get(3).tooltipText = session.ruleManual
                ? Component.translatable("tooltip.chestseparators.rule.manual_active")
                        .getString()
                : Component.translatable("tooltip.chestseparators.rule.manual_inactive")
                        .getString();

        widgets.get(4).isActive = session.ruleShift;
        widgets.get(4).tooltipText = session.ruleShift
                ? Component.translatable("tooltip.chestseparators.rule.shift_active").getString()
                : Component.translatable("tooltip.chestseparators.rule.shift_inactive")
                        .getString();

        // The third rule button (index 5) exists unless it is an Ender Chest chest-filter (there it is
        // dropped and index 5 would be a different widget). An inventory filter always has it (Pick Up).
        boolean invFilter = isInventoryFilter();
        if (!session.isEnderChest || invFilter) {
            widgets.get(5).isActive = session.ruleHopper;
            // Chest minecarts CAN be filled by hoppers and now sync their filter to the server, so the rule
            // is enabled for them. Other entity containers (chest boats, animals) still cannot, so stay
            // disabled. Inventory filters (Pick Up) are always enabled.
            widgets.get(5).isDisabled = !invFilter && session.isEntityChest && !session.isMinecartChest;

            if (invFilter) {
                widgets.get(5).tooltipText = session.ruleHopper
                        ? Component.translatable("tooltip.chestseparators.rule.pickup_active")
                                .getString()
                        : Component.translatable("tooltip.chestseparators.rule.pickup_inactive")
                                .getString();
            } else if (!session.isEntityChest || session.isMinecartChest) {
                widgets.get(5).tooltipText = session.ruleHopper
                        ? Component.translatable("tooltip.chestseparators.rule.hopper_active")
                                .getString()
                        : Component.translatable("tooltip.chestseparators.rule.hopper_inactive")
                                .getString();
            } else {
                widgets.get(5).tooltipText = null;
            }
        }
    }

    private void handlePreviewLogic(int mouseX, int mouseY) {
        if (widgets.isEmpty()) return;
        boolean hoverImport = widgets.get(0).isHovering(mouseX, mouseY);
        boolean hoverAllow = widgets.get(1).isHovering(mouseX, mouseY);
        boolean hoverClear = widgets.get(2).isHovering(mouseX, mouseY);

        if (hoverImport) session.previewType = 1;
        else if (hoverAllow) session.previewType = 2;
        else if (hoverClear) session.previewType = 3;
        else session.previewType = 0;

        if (session.previewType == 0) session.forceStopPreview = false;

        if (session.previewType != 0 && !session.forceStopPreview) {
            if (!session.isPreviewing) {
                List<String> itemsToPreview = new ArrayList<>();
                boolean isSearching =
                        editor.searchBox != null && !editor.searchBox.getValue().isEmpty();

                if (session.previewType == 1) {
                    if (isSearching) {
                        for (Item item : session.filteredItems)
                            itemsToPreview.add(BuiltInRegistries.ITEM.getKey(item).toString());
                    } else {
                        itemsToPreview = editor.extractItemsFromSelection();
                    }
                } else if (session.previewType == 2) {
                    for (Item item : session.allGameItems)
                        itemsToPreview.add(BuiltInRegistries.ITEM.getKey(item).toString());
                } else if (session.previewType == 3) {
                    itemsToPreview.addAll(session.currentAllowedItems);
                }

                if (!itemsToPreview.isEmpty() || session.previewType == 3) {
                    session.isPreviewing = true;
                    session.previewItems = itemsToPreview;
                    session.previewScrollY = 0f;
                    session.previewScrollDirectionDown = true;
                    session.userOverrodePreviewScroll = false;
                    long now = System.currentTimeMillis();
                    session.lastPreviewTime = now;
                    session.previewStartTime = now;
                } else session.forceStopPreview = true;
            } else {
                long now = System.currentTimeMillis();
                float dt = (now - session.lastPreviewTime) / 1000f;
                session.lastPreviewTime = now;
                if (!session.userOverrodePreviewScroll && (now - session.previewStartTime > 1000)) {
                    float maxScroll = Math.max(0, session.previewItems.size() * 18 - (layout.listH - 48));
                    if (maxScroll > 0) {
                        float speed = (session.previewType == 2) ? 80f : 25f;
                        // Restarting the clock at each end re-triggers the one second wait above, so the
                        // list settles for a moment at the bottom and at the top instead of turning around
                        // the instant it arrives — otherwise the ends are the hardest part to read.
                        if (session.previewScrollDirectionDown) {
                            session.previewScrollY += speed * dt;
                            if (session.previewScrollY >= maxScroll) {
                                session.previewScrollY = maxScroll;
                                session.previewScrollDirectionDown = false;
                                session.previewStartTime = now;
                            }
                        } else {
                            session.previewScrollY -= speed * dt;
                            if (session.previewScrollY <= 0) {
                                session.previewScrollY = 0;
                                session.previewScrollDirectionDown = true;
                                session.previewStartTime = now;
                            }
                        }
                    }
                }
            }
        } else session.isPreviewing = false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float scrollSpeed = 18f;

        // While previewing, the scroll wheel always controls the preview panel.
        if (session.isPreviewing) {
            float maxScroll = Math.max(0, session.previewItems.size() * layout.itemSize - layout.listViewH);
            session.previewScrollY = net.minecraft.util.Mth.clamp(
                    session.previewScrollY - (float) (verticalAmount * scrollSpeed), 0, maxScroll);
            session.userOverrodePreviewScroll = true;
            return true;
        }

        if (mouseX >= layout.mainX) {
            session.lastInteractedWasList = false;
            float maxGridScroll = Math.max(
                    0,
                    (int) Math.ceil((double) session.filteredItems.size() / layout.cols) * layout.gridCell
                            - layout.gridViewH);
            session.gridScrollY = net.minecraft.util.Mth.clamp(
                    session.gridScrollY - (float) (verticalAmount * scrollSpeed), 0, maxGridScroll);
        } else {
            session.lastInteractedWasList = true;
            float maxListScroll = layout.maxListScroll(session.visibleLeftListItems.size());
            session.listScrollY = net.minecraft.util.Mth.clamp(
                    session.listScrollY - (float) (verticalAmount * scrollSpeed), 0, maxListScroll);
        }
        return true;
    }

    public boolean keyPressed(io.github.marcsanzdev.chestseparators.client.input.KeyEvent input) {
        boolean isSearchFocused = (editor.searchBox != null && editor.searchBox.isFocused());
        boolean isWhitelistSearchFocused = (editor.whitelistSearchBox != null && editor.whitelistSearchBox.isFocused());

        if (isSearchFocused) {
            if (editor.searchBox.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
            if (input.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        }
        if (isWhitelistSearchFocused) {
            if (editor.whitelistSearchBox.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
            if (input.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        }

        // Typing anywhere in the filter editor jumps to the item search, so the inventory key ("e" by
        // default) is a character being typed into the search field — not a request to close the container.
        // This now applies on ANY tab, not only the search tab: switch to the search tab first, focus the
        // box and consume the key; charTyped then inserts the character.
        if (!isSearchFocused && !isWhitelistSearchFocused && editor.searchBox != null) {
            if (input.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN
                    && Minecraft.getInstance().options.keyInventory.matches(input.key(), input.scancode())) {
                if (!isOnSearchTab()) switchToSearchTab();
                editor.searchBox.setFocused(true);
                if (editor.whitelistSearchBox != null) editor.whitelistSearchBox.setFocused(false);
                return true;
            }
        }

        if (!isSearchFocused && !isWhitelistSearchFocused) {
            if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                float scrollAmount = 18f * 2;
                int direction = (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) ? -1 : 1;

                if (session.lastInteractedWasList) {
                    float maxListScroll = layout.maxListScroll(session.visibleLeftListItems.size());
                    session.listScrollY = net.minecraft.util.Mth.clamp(
                            session.listScrollY + (direction * scrollAmount), 0, maxListScroll);
                } else {
                    float maxGridScroll =
                            Math.max(0, (int) Math.ceil(session.filteredItems.size() / 9.0) * 18 - (9 * 18));
                    session.gridScrollY = net.minecraft.util.Mth.clamp(
                            session.gridScrollY + (direction * scrollAmount), 0, maxGridScroll);
                }
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(io.github.marcsanzdev.chestseparators.client.input.CharacterEvent input) {
        if (editor.searchBox != null && editor.searchBox.isFocused()) return editor.searchBox.charTyped((char) input.codepoint(), input.modifiers());
        if (editor.whitelistSearchBox != null && editor.whitelistSearchBox.isFocused())
            return editor.whitelistSearchBox.charTyped((char) input.codepoint(), input.modifiers());
        // Typing anywhere else jumps straight to the item search: switch to the search tab if needed, focus
        // the box and insert the character — no need to find and click the search tab first.
        if (editor.searchBox != null) {
            if (!isOnSearchTab()) switchToSearchTab();
            if (isOnSearchTab()) {
                editor.searchBox.setFocused(true);
                if (editor.whitelistSearchBox != null) editor.whitelistSearchBox.setFocused(false);
                return editor.searchBox.charTyped((char) input.codepoint(), input.modifiers());
            }
        }
        return false;
    }

    /** Switches the item grid to the creative search tab, if one is present. */
    private void switchToSearchTab() {
        for (int i = 0; i < session.availableTabs.size(); i++) {
            var tab = session.availableTabs.get(i);
            if (tab != null && tab.isSearchTab) {
                editor.switchCreativeTab(i);
                return;
            }
        }
    }

    private boolean isOnSearchTab() {
        int idx = session.currentCreativeTabIndex;
        return idx >= 0
                && idx < session.availableTabs.size()
                && session.availableTabs.get(idx) != null
                && session.availableTabs.get(idx).isSearchTab;
    }
}







