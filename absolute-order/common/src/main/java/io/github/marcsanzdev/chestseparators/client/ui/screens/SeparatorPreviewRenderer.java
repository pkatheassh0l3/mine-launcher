package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorGeometry;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * Draws the live hover and drag previews for the separator editor. Pure rendering: it reads
 * editor/session state and paints, never mutating anything. Extracted verbatim from ScreenDrawLines.
 */
final class SeparatorPreviewRenderer {

    private final ScreenDrawLines screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;
    private final EditorGeometry geometry;

    SeparatorPreviewRenderer(ScreenDrawLines screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
        this.geometry = screen.geometry;
    }

    void renderHoverPreview(GuiGraphics context, int mouseX, int mouseY) {
        Slot slot = editor.accessor.getFocusedSlot();
        if (slot != null && ChestSeparatorsEditor.isEditableSlot(slot)) {
            int tabMode = session.currentTab;
            int action = (tabMode == 2 || tabMode == 1)
                    ? ChestConfigManager.ACTION_BG
                    : geometry.calculateAction(slot, mouseX, mouseY);

            int colorVal = 0;
            boolean explicitEraser = false;
            if (tabMode == 0) {
                colorVal = screen.getCurrentSelectedLineColorValue();
                explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            } else if (tabMode == 1) {
                colorVal = screen.getCurrentSelectedBgColorValue();
                explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            } else if (tabMode == 2) {
                colorVal = screen.getCurrentSelectedComboColorValue();
                explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            }

            if (colorVal == 0 && !explicitEraser) return;

            boolean willErase = explicitEraser;
            if (!willErase && tabMode != 2) {
                int existingColor = ChestConfigManager.getInstance().getColor(slot.getContainerSlot(), action);
                if (existingColor == (colorVal | 0xFF000000)) willErase = true;
            }

            int colorBg = willErase ? 0x66FFFFFF : ((colorVal & 0x00FFFFFF) | 0x66000000);
            int colorLine = willErase ? 0x88FFFFFF : ((colorVal & 0x00FFFFFF) | 0x88000000);

            int x = layout.guiX + slot.x;
            int y = layout.guiY + slot.y;

            if (tabMode == 1) {
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_BG)
                                != 0) {
                    context.fill(x, y, x + 16, y + 16, colorBg);
                }
            } else if (tabMode == 0) {
                if (action != 0 && action != ChestConfigManager.ACTION_BG) {
                    if ((action & ChestConfigManager.ACTION_TOP) != 0
                            && (!willErase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(
                                                            ChestSeparatorsEditor.slotKey(slot),
                                                            ChestConfigManager.ACTION_TOP)
                                            != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if ((action & ChestConfigManager.ACTION_BOTTOM) != 0
                            && (!willErase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(
                                                            ChestSeparatorsEditor.slotKey(slot),
                                                            ChestConfigManager.ACTION_BOTTOM)
                                            != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if ((action & ChestConfigManager.ACTION_LEFT) != 0
                            && (!willErase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(
                                                            ChestSeparatorsEditor.slotKey(slot),
                                                            ChestConfigManager.ACTION_LEFT)
                                            != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if ((action & ChestConfigManager.ACTION_RIGHT) != 0
                            && (!willErase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(
                                                            ChestSeparatorsEditor.slotKey(slot),
                                                            ChestConfigManager.ACTION_RIGHT)
                                            != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            } else {
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_BG)
                                != 0) context.fill(x, y, x + 16, y + 16, colorBg);
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_TOP)
                                != 0) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_BOTTOM)
                                != 0) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_LEFT)
                                != 0) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_RIGHT)
                                != 0) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
            }
        }
    }

    void renderDragPreview(GuiGraphics context, int mouseX, int mouseY) {
        if (!session.isDraggingLine) return;
        // Inventory drags use offset keys and include the non-grid armor/offhand cells, so they get a
        // dedicated, namespace-correct preview instead of the raw-index chest path below.
        if (session.dragStartSlot != null && ChestSeparatorsEditor.isPlayerSlot(session.dragStartSlot)) {
            renderPlayerDragPreview(context, mouseX, mouseY);
            return;
        }
        int guiX = layout.guiX;
        int guiY = layout.guiY;
        int tabMode = session.currentTab;

        int colorVal = 0;
        boolean explicitEraser = false;
        int tMode = 0;
        if (tabMode == 0) {
            colorVal = screen.getCurrentSelectedLineColorValue();
            explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            tMode = session.lineToolMode;
        } else if (tabMode == 1) {
            colorVal = screen.getCurrentSelectedBgColorValue();
            explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            tMode = session.bgToolMode;
        } else if (tabMode == 2) {
            colorVal = screen.getCurrentSelectedComboColorValue();
            explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            tMode = session.comboToolMode;
        }

        if (colorVal == 0 && !explicitEraser) return;

        boolean erase = explicitEraser || session.isDragModeErasing;
        int colorBg = erase ? 0x66FFFFFF : ((colorVal & 0x00FFFFFF) | 0x66000000);
        int colorLine = erase ? 0x88FFFFFF : ((colorVal & 0x00FFFFFF) | 0x88000000);

        if (tabMode == 1) {
            if (tMode == 0) {
                int startRow = session.dragStartSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int startCol = session.dragStartSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int currRow = session.dragCurrentSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int currCol = session.dragCurrentSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int minRow = Math.min(startRow, currRow);
                int maxRow = Math.max(startRow, currRow);
                int minCol = Math.min(startCol, currCol);
                int maxCol = Math.max(startCol, currCol);
                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
                    // Chest namespace only: player-inventory drags are handled by renderPlayerDragPreview.
                    // Without this, raw getIndex() of player slots (0-35) collides with chest indices and the
                    // chest preview gets mirrored onto the inventory.
                    if (ChestSeparatorsEditor.isPlayerSlot(slot)) continue;
                    int r = slot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                    int c = slot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        if (erase
                                && ChestConfigManager.getInstance()
                                                .getColor(
                                                        ChestSeparatorsEditor.slotKey(slot),
                                                        ChestConfigManager.ACTION_BG)
                                        == 0) continue;
                        context.fill(guiX + slot.x, guiY + slot.y, guiX + slot.x + 16, guiY + slot.y + 16, colorBg);
                    }
                }
            } else {
                for (String step : session.tracePath) {
                    int slotIdx = Integer.parseInt(step.split("_")[0]);
                    if (erase && ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BG) == 0)
                        continue;
                    Slot slot = editor.accessor.getHandler().getSlot(slotIdx);
                    context.fill(guiX + slot.x, guiY + slot.y, guiX + slot.x + 16, guiY + slot.y + 16, colorBg);
                }
            }
        } else if (tabMode == 2) {
            if (tMode == 0) {
                int startRow = session.dragStartSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int startCol = session.dragStartSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int currRow = session.dragCurrentSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int currCol = session.dragCurrentSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int minRow = Math.min(startRow, currRow);
                int maxRow = Math.max(startRow, currRow);
                int minCol = Math.min(startCol, currCol);
                int maxCol = Math.max(startCol, currCol);
                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
                    // Chest namespace only; player drags go through renderPlayerDragPreview.
                    if (ChestSeparatorsEditor.isPlayerSlot(slot)) continue;
                    int slotIdx = slot.getContainerSlot();
                    int r = slotIdx / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                    int c = slotIdx % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        int x = guiX + slot.x;
                        int y = guiY + slot.y;
                        if (!erase
                                || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BG)
                                        != 0) context.fill(x, y, x + 16, y + 16, colorBg);
                        if (r == minRow
                                && (!erase
                                        || ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_TOP)
                                                != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                        if (r == maxRow
                                && (!erase
                                        || ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM)
                                                != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                        if (c == minCol
                                && (!erase
                                        || ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_LEFT)
                                                != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                        if (c == maxCol
                                && (!erase
                                        || ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_RIGHT)
                                                != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                    }
                }
            } else {
                java.util.Set<Integer> traceSlots = new java.util.HashSet<>();
                for (String step : session.tracePath) {
                    traceSlots.add(Integer.parseInt(step.split("_")[0]));
                }
                for (int slotIdx : traceSlots) {
                    Slot slot = editor.accessor.getHandler().getSlot(slotIdx);
                    int x = guiX + slot.x;
                    int y = guiY + slot.y;
                    boolean hasTop = traceSlots.contains(slotIdx - io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns());
                    boolean hasBottom = traceSlots.contains(slotIdx + io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns());
                    boolean hasLeft = (slotIdx % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() != 0) && traceSlots.contains(slotIdx - 1);
                    boolean hasRight = (slotIdx % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() != (io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() - 1)) && traceSlots.contains(slotIdx + 1);

                    if (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BG) != 0)
                        context.fill(x, y, x + 16, y + 16, colorBg);
                    if (!hasTop
                            && (!erase
                                    || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_TOP)
                                            != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if (!hasBottom
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM)
                                            != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if (!hasLeft
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_LEFT)
                                            != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if (!hasRight
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_RIGHT)
                                            != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            }
        } else {
            if (tMode == 0) {
                int sRow = session.dragStartSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int sCol = session.dragStartSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int cRow = session.dragCurrentSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int cCol = session.dragCurrentSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int minRow = Math.min(sRow, cRow);
                int maxRow = Math.max(sRow, cRow);
                int minCol = Math.min(sCol, cCol);
                int maxCol = Math.max(sCol, cCol);

                int yTopRaw = minRow * 2;
                int yBotRaw = maxRow * 2 + 1;
                if (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow)
                    yTopRaw = sRow * 2 + 1;
                else if (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow) yBotRaw = sRow * 2;

                int xLeftRaw = minCol * 2;
                int xRightRaw = maxCol * 2 + 1;
                if (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol)
                    xLeftRaw = sCol * 2 + 1;
                else if (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol)
                    xRightRaw = sCol * 2;

                boolean isOuterIntent = (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow)
                        || (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow)
                        || (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol)
                        || (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol);

                int yTopExp = yTopRaw;
                int yBotExp = yBotRaw;
                int xLeftExp = xLeftRaw;
                int xRightExp = xRightRaw;

                if (isOuterIntent) {
                    if (yTopRaw % 2 == 0) yTopExp--;
                    if (yBotRaw % 2 != 0) yBotExp++;
                    if (xLeftRaw % 2 == 0) xLeftExp--;
                    if (xRightRaw % 2 != 0) xRightExp++;
                }

                int maxRows = geometry.getContainerSlotCount() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                yTopExp = Math.max(0, Math.min(maxRows * 2 - 1, yTopExp));
                yBotExp = Math.max(0, Math.min(maxRows * 2 - 1, yBotExp));
                xLeftExp = Math.max(0, xLeftExp);
                xRightExp = Math.max(0, Math.min(17, xRightExp));

                if (geometry.isDraggingRectangle(mouseX, mouseY)) {
                    Slot startRowSlot = editor.accessor.getHandler().getSlot((yTopExp / 2) * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns());
                    int topY = guiY + startRowSlot.y + (yTopExp % 2 == 0 ? -1 : 16);
                    Slot endRowSlot = editor.accessor.getHandler().getSlot((yBotExp / 2) * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns());
                    int botY = guiY + endRowSlot.y + (yBotExp % 2 == 0 ? 0 : 17);
                    Slot startColSlot = editor.accessor.getHandler().getSlot(xLeftExp / 2);
                    int leftX = guiX + startColSlot.x + (xLeftExp % 2 == 0 ? -1 : 16);
                    Slot endColSlot = editor.accessor.getHandler().getSlot(xRightExp / 2);
                    int rightX = guiX + endColSlot.x + (xRightExp % 2 == 0 ? 0 : 17);

                    context.fill(leftX, topY, rightX, topY + 1, colorLine);
                    context.fill(leftX, botY - 1, rightX, botY, colorLine);
                    context.fill(leftX, topY, leftX + 1, botY, colorLine);
                    context.fill(rightX - 1, topY, rightX, botY, colorLine);

                    if (erase) {
                        for (int r = Math.max(0, minRow - 1); r <= maxRow + 1; r++) {
                            for (int c = Math.max(0, minCol - 1); c <= maxCol + 1; c++) {
                                int slotIdx = r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c;
                                if (slotIdx >= geometry.getContainerSlotCount()) continue;
                                Slot s = editor.accessor.getHandler().getSlot(slotIdx);
                                int x = guiX + s.x;
                                int y = guiY + s.y;
                                int tY = r * 2, bY = r * 2 + 1;
                                int lX = c * 2, rX = c * 2 + 1;
                                boolean hInside = (lX >= xLeftExp) && (rX <= xRightExp);
                                boolean vInside = (tY >= yTopExp) && (bY <= yBotExp);
                                if (hInside
                                        && tY >= yTopExp
                                        && tY <= yBotExp
                                        && ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_TOP)
                                                != 0) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                                if (hInside
                                        && bY >= yTopExp
                                        && bY <= yBotExp
                                        && ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM)
                                                != 0) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                                if (vInside
                                        && lX >= xLeftExp
                                        && lX <= xRightExp
                                        && ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_LEFT)
                                                != 0) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                                if (vInside
                                        && rX >= xLeftExp
                                        && rX <= xRightExp
                                        && ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_RIGHT)
                                                != 0) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                            }
                        }
                    }
                } else {
                    for (int r = minRow; r <= maxRow; r++) {
                        for (int c = minCol; c <= maxCol; c++) {
                            int slotIdx = r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c;
                            Slot s = editor.accessor.getHandler().getSlot(slotIdx);
                            int x = guiX + s.x;
                            int y = guiY + s.y;
                            if ((session.currentDragAction & ChestConfigManager.ACTION_TOP) != 0)
                                context.fill(x - 1, y - 1, x + 17, y, colorLine);
                            if ((session.currentDragAction & ChestConfigManager.ACTION_BOTTOM) != 0)
                                context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                            if ((session.currentDragAction & ChestConfigManager.ACTION_LEFT) != 0)
                                context.fill(x - 1, y - 1, x, y + 17, colorLine);
                            if ((session.currentDragAction & ChestConfigManager.ACTION_RIGHT) != 0)
                                context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                        }
                    }
                }
            } else {
                for (String step : session.tracePath) {
                    String[] parts = step.split("_");
                    int slotIdx = Integer.parseInt(parts[0]);
                    int act = Integer.parseInt(parts[1]);
                    Slot slot = editor.accessor.getHandler().getSlot(slotIdx);
                    int x = guiX + slot.x;
                    int y = guiY + slot.y;
                    if ((act & ChestConfigManager.ACTION_TOP) != 0
                            && (!erase
                                    || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_TOP)
                                            != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if ((act & ChestConfigManager.ACTION_BOTTOM) != 0
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM)
                                            != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if ((act & ChestConfigManager.ACTION_LEFT) != 0
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_LEFT)
                                            != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if ((act & ChestConfigManager.ACTION_RIGHT) != 0
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_RIGHT)
                                            != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            }
        }
    }

    /**
     * Live drag preview for the player inventory namespace. Uses offset keys (positioned via
     * {@link ChestSeparatorsEditor#slotForKey}) and treats armor/offhand as isolated non-grid cells so
     * each gets its full box. Kept separate from the chest preview, which uses raw container indices.
     */
    private void renderPlayerDragPreview(GuiGraphics context, int mouseX, int mouseY) {
        int guiX = layout.guiX;
        int guiY = layout.guiY;
        int tabMode = session.currentTab;

        int colorVal;
        boolean explicitEraser;
        int tMode;
        if (tabMode == 0) {
            colorVal = screen.getCurrentSelectedLineColorValue();
            explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            tMode = session.lineToolMode;
        } else if (tabMode == 1) {
            colorVal = screen.getCurrentSelectedBgColorValue();
            explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            tMode = session.bgToolMode;
        } else {
            colorVal = screen.getCurrentSelectedComboColorValue();
            explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            tMode = session.comboToolMode;
        }
        if (colorVal == 0 && !explicitEraser) return;

        int colorBg = (colorVal & 0x00FFFFFF) | 0x66000000;
        int colorLine = (colorVal & 0x00FFFFFF) | 0x88000000;
        if (explicitEraser || session.isDragModeErasing) {
            colorBg = 0x66FFFFFF;
            colorLine = 0x88FFFFFF;
        }

        if (tMode == 1) { // Trace
            java.util.Set<Integer> traceSlots = new java.util.HashSet<>();
            for (String s : session.tracePath) traceSlots.add(Integer.parseInt(s.split("_")[0]));
            for (String step : session.tracePath) {
                String[] p = step.split("_");
                int key = Integer.parseInt(p[0]);
                int act = Integer.parseInt(p[1]);
                Slot slot = editor.slotForKey(key);
                if (slot == null) continue;
                int x = guiX + slot.x;
                int y = guiY + slot.y;
                if (tabMode == 1) {
                    context.fill(x, y, x + 16, y + 16, colorBg);
                } else if (tabMode == 2) {
                    context.fill(x, y, x + 16, y + 16, colorBg);
                    boolean nonGrid = ChestConfigManager.isNonGridInventoryKey(key);
                    boolean hasTop = !nonGrid
                            && traceSlots.contains(key - io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns())
                            && !ChestConfigManager.isNonGridInventoryKey(key - io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns());
                    boolean hasBottom = !nonGrid
                            && traceSlots.contains(key + io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns())
                            && !ChestConfigManager.isNonGridInventoryKey(key + io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns());
                    boolean hasLeft = !nonGrid
                            && (key % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() != 0)
                            && traceSlots.contains(key - 1)
                            && !ChestConfigManager.isNonGridInventoryKey(key - 1);
                    boolean hasRight = !nonGrid
                            && (key % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() != (io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() - 1))
                            && traceSlots.contains(key + 1)
                            && !ChestConfigManager.isNonGridInventoryKey(key + 1);
                    if (!hasTop) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if (!hasBottom) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if (!hasLeft) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if (!hasRight) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                } else {
                    if ((act & ChestConfigManager.ACTION_TOP) != 0) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if ((act & ChestConfigManager.ACTION_BOTTOM) != 0)
                        context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if ((act & ChestConfigManager.ACTION_LEFT) != 0) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if ((act & ChestConfigManager.ACTION_RIGHT) != 0)
                        context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            }
            return;
        }

        // Pencil line tool keeps its intricate index-based grid geometry (mirrors the committer exactly).
        if (tabMode == 0) {
            int sRow = session.dragStartSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
            int sCol = session.dragStartSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
            int cRow = session.dragCurrentSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
            int cCol = session.dragCurrentSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
            int minRow = Math.min(sRow, cRow);
            int maxRow = Math.max(sRow, cRow);
            int minCol = Math.min(sCol, cCol);
            int maxCol = Math.max(sCol, cCol);
            boolean erase = explicitEraser || session.isDragModeErasing;
            renderPlayerLineAreaPreview(
                    context, mouseX, mouseY, minRow, maxRow, minCol, maxCol, sRow, sCol, cRow, cCol, colorLine, erase);
            return;
        }

        // Bg/combo area: membership + border by VISUAL box (slotsInDragBox), so a drag crossing the hotbar
        // and the inventory rows covers exactly the swept cells. index/9 rows get this wrong because the
        // hotbar is drawn below the inventory yet its indices (0-8) come first.
        java.util.List<Slot> sel = editor.slotsInDragBox(session.dragStartSlot, session.dragCurrentSlot);
        java.util.Set<Long> selPos = new java.util.HashSet<>();
        for (Slot s : sel) selPos.add(boxKey(s.x, s.y));
        for (Slot slot : sel) {
            int x = guiX + slot.x;
            int y = guiY + slot.y;
            if (tabMode == 1) {
                context.fill(x, y, x + 16, y + 16, colorBg);
            } else { // tabMode == 2 (combo): outline the selected region (edge where no selected neighbour)
                context.fill(x, y, x + 16, y + 16, colorBg);
                boolean nonGrid = ChestConfigManager.isNonGridInventoryKey(ChestSeparatorsEditor.slotKey(slot));
                if (nonGrid || !selPos.contains(boxKey(slot.x, slot.y - 18)))
                    context.fill(x - 1, y - 1, x + 17, y, colorLine);
                if (nonGrid || !selPos.contains(boxKey(slot.x, slot.y + 18)))
                    context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                if (nonGrid || !selPos.contains(boxKey(slot.x - 18, slot.y)))
                    context.fill(x - 1, y - 1, x, y + 17, colorLine);
                if (nonGrid || !selPos.contains(boxKey(slot.x + 18, slot.y)))
                    context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
            }
        }
    }

    /** Position key for a slot's on-screen cell, used to test same-group visual neighbours. */
    static long boxKey(int x, int y) {
        return ((long) x << 20) ^ (y & 0xFFFFF);
    }

    /**
     * Live preview for the pencil line tool in area mode over the player inventory. Mirrors
     * {@link SeparatorDragCommitter}'s pencil-area branch edge-for-edge so what the user sees before
     * releasing is exactly what gets committed: non-grid armor/offhand cells get their full box, while
     * the true 9-wide grid (first 36 player slots) uses the rectangle-border / 1D-line logic.
     */
    private void renderPlayerLineAreaPreview(
            GuiGraphics context,
            int mouseX,
            int mouseY,
            int minRow,
            int maxRow,
            int minCol,
            int maxCol,
            int sRow,
            int sCol,
            int cRow,
            int cCol,
            int colorLine,
            boolean erase) {
        ChestConfigManager m = ChestConfigManager.getInstance();
        int off = ChestConfigManager.PLAYER_KEY_OFFSET;

        // Non-grid armor/offhand cells: full box when the gesture forms a rectangle (area sweep or a
        // corner-to-corner drag crossing the midpoint), but only the clicked edge on a single click —
        // mirrors the committer so the preview always matches what lands.
        boolean isRect = editor.geometry.isDraggingRectangle(mouseX, mouseY);
        int startKey = ChestSeparatorsEditor.slotKey(session.dragStartSlot);
        for (Slot slot : editor.accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(slot) || !ChestSeparatorsEditor.isPlayerSlot(slot)) continue;
            int key = ChestSeparatorsEditor.slotKey(slot);
            if (!ChestConfigManager.isNonGridInventoryKey(key)) continue;
            int r = slot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
            int c = slot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
            if (r < minRow || r > maxRow || c < minCol || c > maxCol) continue;
            if (!isRect && key == startKey) {
                int single = session.currentDragAction;
                if (!erase || m.getColor(key, single) != 0) drawPreviewEdge(context, key, single, colorLine);
                continue;
            }
            int mask = 0;
            if (!erase || m.getColor(key, ChestConfigManager.ACTION_TOP) != 0) mask |= ChestConfigManager.ACTION_TOP;
            if (!erase || m.getColor(key, ChestConfigManager.ACTION_BOTTOM) != 0)
                mask |= ChestConfigManager.ACTION_BOTTOM;
            if (!erase || m.getColor(key, ChestConfigManager.ACTION_LEFT) != 0) mask |= ChestConfigManager.ACTION_LEFT;
            if (!erase || m.getColor(key, ChestConfigManager.ACTION_RIGHT) != 0)
                mask |= ChestConfigManager.ACTION_RIGHT;
            drawPreviewEdge(context, key, mask, colorLine);
        }

        int gridSlotCount = 36;
        int gridRows = gridSlotCount / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
        if (minRow >= gridRows) return;

        int yTopRaw = minRow * 2;
        int yBotRaw = maxRow * 2 + 1;
        if (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow) yTopRaw = sRow * 2 + 1;
        else if (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow) yBotRaw = sRow * 2;

        int xLeftRaw = minCol * 2;
        int xRightRaw = maxCol * 2 + 1;
        if (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol) xLeftRaw = sCol * 2 + 1;
        else if (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol) xRightRaw = sCol * 2;

        boolean isOuterIntent = (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow)
                || (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow)
                || (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol)
                || (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol);

        int yTopExp = yTopRaw;
        int yBotExp = yBotRaw;
        int xLeftExp = xLeftRaw;
        int xRightExp = xRightRaw;
        if (isOuterIntent) {
            if (yTopRaw % 2 == 0) yTopExp--;
            if (yBotRaw % 2 != 0) yBotExp++;
            if (xLeftRaw % 2 == 0) xLeftExp--;
            if (xRightRaw % 2 != 0) xRightExp++;
        }

        int maxRows = gridRows;
        yTopExp = Math.max(0, Math.min(maxRows * 2 - 1, yTopExp));
        yBotExp = Math.max(0, Math.min(maxRows * 2 - 1, yBotExp));
        xLeftExp = Math.max(0, Math.min(17, xLeftExp));
        xRightExp = Math.max(0, Math.min(17, xRightExp));

        if (geometry.isDraggingRectangle(mouseX, mouseY)) {
            if (erase) {
                for (int r = Math.max(0, minRow - 1); r <= maxRow + 1; r++) {
                    for (int c = Math.max(0, minCol - 1); c <= maxCol + 1; c++) {
                        int slotIdx = r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c;
                        if (slotIdx >= gridSlotCount) continue;
                        int key = slotIdx + off;
                        int topY = r * 2, botY = r * 2 + 1;
                        int leftX = c * 2, rightX = c * 2 + 1;
                        boolean hInside = (leftX >= xLeftExp) && (rightX <= xRightExp);
                        boolean vInside = (topY >= yTopExp) && (botY <= yBotExp);
                        int mask = 0;
                        if (hInside
                                && topY >= yTopExp
                                && topY <= yBotExp
                                && m.getColor(key, ChestConfigManager.ACTION_TOP) != 0)
                            mask |= ChestConfigManager.ACTION_TOP;
                        if (hInside
                                && botY >= yTopExp
                                && botY <= yBotExp
                                && m.getColor(key, ChestConfigManager.ACTION_BOTTOM) != 0)
                            mask |= ChestConfigManager.ACTION_BOTTOM;
                        if (vInside
                                && leftX >= xLeftExp
                                && leftX <= xRightExp
                                && m.getColor(key, ChestConfigManager.ACTION_LEFT) != 0)
                            mask |= ChestConfigManager.ACTION_LEFT;
                        if (vInside
                                && rightX >= xLeftExp
                                && rightX <= xRightExp
                                && m.getColor(key, ChestConfigManager.ACTION_RIGHT) != 0)
                            mask |= ChestConfigManager.ACTION_RIGHT;
                        drawPreviewEdge(context, key, mask, colorLine);
                    }
                }
            } else {
                int fillMinCol = (xLeftExp + 1) / 2;
                int fillMaxCol = (xRightExp - 1) / 2;
                int topAction = (yTopExp % 2 == 0) ? ChestConfigManager.ACTION_TOP : ChestConfigManager.ACTION_BOTTOM;
                int topRow = yTopExp / 2;
                int botAction = (yBotExp % 2 == 0) ? ChestConfigManager.ACTION_TOP : ChestConfigManager.ACTION_BOTTOM;
                int botRow = yBotExp / 2;
                for (int c = fillMinCol; c <= fillMaxCol; c++) {
                    if (topRow * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c < gridSlotCount)
                        drawPreviewEdge(context, topRow * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c + off, topAction, colorLine);
                    if (botRow * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c < gridSlotCount)
                        drawPreviewEdge(context, botRow * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c + off, botAction, colorLine);
                }

                int fillMinRow = (yTopExp + 1) / 2;
                int fillMaxRow = (yBotExp - 1) / 2;
                int leftAction = (xLeftExp % 2 == 0) ? ChestConfigManager.ACTION_LEFT : ChestConfigManager.ACTION_RIGHT;
                int leftCol = xLeftExp / 2;
                int rightAction =
                        (xRightExp % 2 == 0) ? ChestConfigManager.ACTION_LEFT : ChestConfigManager.ACTION_RIGHT;
                int rightCol = xRightExp / 2;
                for (int r = fillMinRow; r <= fillMaxRow; r++) {
                    if (r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + leftCol < gridSlotCount)
                        drawPreviewEdge(context, r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + leftCol + off, leftAction, colorLine);
                    if (r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + rightCol < gridSlotCount)
                        drawPreviewEdge(context, r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + rightCol + off, rightAction, colorLine);
                }
            }
        } else { // 1D Line
            for (int r = minRow; r <= Math.min(maxRow, gridRows - 1); r++) {
                for (int c = minCol; c <= maxCol; c++) {
                    int key = r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c + off;
                    if (erase && m.getColor(key, session.currentDragAction) == 0) continue;
                    drawPreviewEdge(context, key, session.currentDragAction, colorLine);
                }
            }
        }
    }

    /** Draws the requested edge(s) of a slot (looked up by its offset key) with the given preview color. */
    private void drawPreviewEdge(GuiGraphics context, int key, int actionMask, int color) {
        if (actionMask == 0) return;
        Slot slot = editor.slotForKey(key);
        if (slot == null) return;
        int x = layout.guiX + slot.x;
        int y = layout.guiY + slot.y;
        if ((actionMask & ChestConfigManager.ACTION_TOP) != 0) context.fill(x - 1, y - 1, x + 17, y, color);
        if ((actionMask & ChestConfigManager.ACTION_BOTTOM) != 0) context.fill(x - 1, y + 16, x + 17, y + 17, color);
        if ((actionMask & ChestConfigManager.ACTION_LEFT) != 0) context.fill(x - 1, y - 1, x, y + 17, color);
        if ((actionMask & ChestConfigManager.ACTION_RIGHT) != 0) context.fill(x + 16, y - 1, x + 17, y + 17, color);
    }
}
