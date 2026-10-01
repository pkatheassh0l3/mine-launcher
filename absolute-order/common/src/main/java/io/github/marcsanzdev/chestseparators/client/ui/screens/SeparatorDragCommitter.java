package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.world.inventory.Slot;

/**
 * Applies a completed separator drag (area or trace, across the line/background/combo tabs) to
 * ChestConfigManager. Extracted verbatim from ScreenDrawLines.
 */
final class SeparatorDragCommitter {

    private final ScreenDrawLines screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;

    SeparatorDragCommitter(ScreenDrawLines screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
    }

    void commit(double mouseX, double mouseY) {
        if (session.dragStartSlot == null || session.dragCurrentSlot == null) return;
        ChestConfigManager manager = ChestConfigManager.getInstance();
        boolean changeMade = false;

        // The drag is confined to the namespace of its start slot (chest vs inventory); grid-computed
        // slot indices are offset into that namespace, and slot-iteration loops skip the other side.
        boolean playerNs = ChestSeparatorsEditor.isPlayerSlot(session.dragStartSlot);
        int off = playerNs ? ChestConfigManager.PLAYER_KEY_OFFSET : 0;
        int nsCount = editor.geometry.getNamespaceSlotCount(playerNs);

        if (session.currentTab == EditorSessionData.TAB_COMBO) {
            int colorToPaint = screen.getCurrentSelectedComboColorValue();
            boolean explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            if (colorToPaint == 0 && !explicitEraser) return;

            int allLinesAction = ChestConfigManager.ACTION_TOP
                    | ChestConfigManager.ACTION_BOTTOM
                    | ChestConfigManager.ACTION_LEFT
                    | ChestConfigManager.ACTION_RIGHT;

            if (session.comboToolMode == 0) { // Combo Area
                // Membership + border by VISUAL box, so crossing hotbar↔inventory paints only the swept
                // cells; the border is painted where the selected region has no neighbour (outer outline).
                java.util.List<Slot> sel = editor.slotsInDragBox(session.dragStartSlot, session.dragCurrentSlot);
                java.util.Set<Long> selPos = new java.util.HashSet<>();
                for (Slot s : sel) selPos.add(SeparatorPreviewRenderer.boxKey(s.x, s.y));
                for (Slot slot : sel) {
                    int key = ChestSeparatorsEditor.slotKey(slot);
                    if (explicitEraser || session.isDragModeErasing) {
                        manager.removeAction(key, ChestConfigManager.ACTION_BG);
                        manager.removeAction(key, allLinesAction);
                    } else {
                        manager.paintAction(key, ChestConfigManager.ACTION_BG, colorToPaint);

                        // Armor/offhand are isolated cells: always give them their full box; grid slots
                        // paint an edge only where the selection has no neighbour on that side.
                        boolean nonGrid = ChestConfigManager.isNonGridInventoryKey(key);
                        if (nonGrid || !selPos.contains(SeparatorPreviewRenderer.boxKey(slot.x, slot.y - 18)))
                            manager.paintAction(key, ChestConfigManager.ACTION_TOP, colorToPaint);
                        if (nonGrid || !selPos.contains(SeparatorPreviewRenderer.boxKey(slot.x, slot.y + 18)))
                            manager.paintAction(key, ChestConfigManager.ACTION_BOTTOM, colorToPaint);
                        if (nonGrid || !selPos.contains(SeparatorPreviewRenderer.boxKey(slot.x - 18, slot.y)))
                            manager.paintAction(key, ChestConfigManager.ACTION_LEFT, colorToPaint);
                        if (nonGrid || !selPos.contains(SeparatorPreviewRenderer.boxKey(slot.x + 18, slot.y)))
                            manager.paintAction(key, ChestConfigManager.ACTION_RIGHT, colorToPaint);
                    }
                    changeMade = true;
                }
            } else { // Combo Trace
                java.util.Set<Integer> traceSlots = new java.util.HashSet<>();
                for (String step : session.tracePath) {
                    traceSlots.add(Integer.parseInt(step.split("_")[0]));
                }

                for (int slotIdx : traceSlots) {
                    if (explicitEraser || session.isDragModeErasing) {
                        manager.removeAction(slotIdx, ChestConfigManager.ACTION_BG);
                        manager.removeAction(slotIdx, allLinesAction);
                    } else {
                        manager.paintAction(slotIdx, ChestConfigManager.ACTION_BG, colorToPaint);

                        // Armor and offhand are isolated (non-grid) cells: their keys are contiguous but
                        // they are not visual neighbours, so they never share/suppress an edge. A
                        // non-grid cell always gets its full box; grid cells ignore non-grid neighbours.
                        boolean nonGrid = ChestConfigManager.isNonGridInventoryKey(slotIdx);
                        boolean hasTop = !nonGrid
                                && traceSlots.contains(slotIdx - io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns())
                                && !ChestConfigManager.isNonGridInventoryKey(slotIdx - io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns());
                        boolean hasBottom = !nonGrid
                                && traceSlots.contains(slotIdx + io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns())
                                && !ChestConfigManager.isNonGridInventoryKey(slotIdx + io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns());
                        boolean hasLeft = !nonGrid
                                && (slotIdx % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() != 0)
                                && traceSlots.contains(slotIdx - 1)
                                && !ChestConfigManager.isNonGridInventoryKey(slotIdx - 1);
                        boolean hasRight = !nonGrid
                                && (slotIdx % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() != (io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() - 1))
                                && traceSlots.contains(slotIdx + 1)
                                && !ChestConfigManager.isNonGridInventoryKey(slotIdx + 1);

                        if (!hasTop) manager.paintAction(slotIdx, ChestConfigManager.ACTION_TOP, colorToPaint);
                        if (!hasBottom) manager.paintAction(slotIdx, ChestConfigManager.ACTION_BOTTOM, colorToPaint);
                        if (!hasLeft) manager.paintAction(slotIdx, ChestConfigManager.ACTION_LEFT, colorToPaint);
                        if (!hasRight) manager.paintAction(slotIdx, ChestConfigManager.ACTION_RIGHT, colorToPaint);
                    }
                    changeMade = true;
                }
            }
            if (changeMade) {
                editor.saveSmart();
                editor.playClickSound(1.0f);
            }
            return;
        }

        if (session.currentDragAction == ChestConfigManager.ACTION_BG) {
            int colorToPaint = screen.getCurrentSelectedBgColorValue();
            boolean explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            if (colorToPaint == 0 && !explicitEraser) return;

            if (session.bgToolMode == 0) {
                // Membership by VISUAL box so crossing hotbar↔inventory fills only the swept cells.
                boolean erase = explicitEraser || session.isDragModeErasing;
                for (Slot slot : editor.slotsInDragBox(session.dragStartSlot, session.dragCurrentSlot)) {
                    int key = ChestSeparatorsEditor.slotKey(slot);
                    if (erase) manager.removeAction(key, ChestConfigManager.ACTION_BG);
                    else manager.paintAction(key, ChestConfigManager.ACTION_BG, colorToPaint);
                    changeMade = true;
                }
            } else {
                for (String step : session.tracePath) {
                    int slotIdx = Integer.parseInt(step.split("_")[0]);
                    if (explicitEraser || session.isDragModeErasing)
                        manager.removeAction(slotIdx, ChestConfigManager.ACTION_BG);
                    else manager.paintAction(slotIdx, ChestConfigManager.ACTION_BG, colorToPaint);
                    changeMade = true;
                }
            }
        } else { // PENCIL MODE
            int colorToPaint = screen.getCurrentSelectedLineColorValue();
            boolean explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            if (colorToPaint == 0 && !explicitEraser) return;

            if (session.lineToolMode == 0) { // Pencil Area
                boolean erase = explicitEraser || session.isDragModeErasing;

                int sRow = session.dragStartSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int sCol = session.dragStartSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int cRow = session.dragCurrentSlot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                int cCol = session.dragCurrentSlot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();

                int minRow = Math.min(sRow, cRow);
                int maxRow = Math.max(sRow, cRow);
                int minCol = Math.min(sCol, cCol);
                int maxCol = Math.max(sCol, cCol);

                // Armor/offhand are isolated (non-grid) cells outside the 9-wide grid: the grid
                // rederivation below can't address them (their partial row is clamped back into the
                // main inventory), which is why dragging lines over armor drew nothing on the armor
                // and painted phantom lines mid-inventory. Give each covered non-grid slot its full
                // box directly, exactly like the combo/trace tools do.
                int allLines = ChestConfigManager.ACTION_TOP
                        | ChestConfigManager.ACTION_BOTTOM
                        | ChestConfigManager.ACTION_LEFT
                        | ChestConfigManager.ACTION_RIGHT;
                // A single click on a non-grid cell paints only the clicked edge, exactly like the hover
                // preview shows. The full box is drawn only when the gesture forms a rectangle — either a
                // real multi-cell area sweep, or a corner-to-corner drag that crosses the cell's midpoint
                // (isDraggingRectangle), matching how a single grid slot fills its whole box.
                boolean isRect = editor.geometry.isDraggingRectangle(mouseX, mouseY);
                int startKey = ChestSeparatorsEditor.slotKey(session.dragStartSlot);
                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
                    if (ChestSeparatorsEditor.isPlayerSlot(slot) != playerNs) continue;
                    int key = ChestSeparatorsEditor.slotKey(slot);
                    if (!ChestConfigManager.isNonGridInventoryKey(key)) continue;
                    int r = slot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                    int c = slot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        if (!isRect && key == startKey) {
                            if (erase) manager.removeAction(key, session.currentDragAction);
                            else manager.paintAction(key, session.currentDragAction, colorToPaint);
                        } else if (erase) {
                            manager.removeAction(key, allLines);
                        } else {
                            manager.paintAction(key, ChestConfigManager.ACTION_TOP, colorToPaint);
                            manager.paintAction(key, ChestConfigManager.ACTION_BOTTOM, colorToPaint);
                            manager.paintAction(key, ChestConfigManager.ACTION_LEFT, colorToPaint);
                            manager.paintAction(key, ChestConfigManager.ACTION_RIGHT, colorToPaint);
                        }
                        changeMade = true;
                    }
                }

                // Grid rederivation only covers the true 9-wide grid: the player namespace's grid is
                // its first 36 slots (everything past that is the non-grid row handled above), while
                // the chest namespace is fully grid. Skip entirely when the drag lies wholly in the
                // non-grid row.
                int gridSlotCount = playerNs ? 36 : nsCount;
                int gridRows = gridSlotCount / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                if (minRow < gridRows) {
                    int yTopRaw = minRow * 2;
                    int yBotRaw = maxRow * 2 + 1;
                    if (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow)
                        yTopRaw = sRow * 2 + 1;
                    else if (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow)
                        yBotRaw = sRow * 2;

                    int xLeftRaw = minCol * 2;
                    int xRightRaw = maxCol * 2 + 1;
                    if (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol)
                        xLeftRaw = sCol * 2 + 1;
                    else if (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol)
                        xRightRaw = sCol * 2;

                    boolean isOuterIntent =
                            (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow)
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

                    boolean isRectangle = editor.geometry.isDraggingRectangle(mouseX, mouseY);

                    if (isRectangle) {
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

                                    if (hInside && topY >= yTopExp && topY <= yBotExp)
                                        manager.removeAction(key, ChestConfigManager.ACTION_TOP);
                                    if (hInside && botY >= yTopExp && botY <= yBotExp)
                                        manager.removeAction(key, ChestConfigManager.ACTION_BOTTOM);
                                    if (vInside && leftX >= xLeftExp && leftX <= xRightExp)
                                        manager.removeAction(key, ChestConfigManager.ACTION_LEFT);
                                    if (vInside && rightX >= xLeftExp && rightX <= xRightExp)
                                        manager.removeAction(key, ChestConfigManager.ACTION_RIGHT);
                                }
                            }
                        } else {
                            int fillMinCol = (xLeftExp + 1) / 2;
                            int fillMaxCol = (xRightExp - 1) / 2;
                            int topAction = (yTopExp % 2 == 0)
                                    ? ChestConfigManager.ACTION_TOP
                                    : ChestConfigManager.ACTION_BOTTOM;
                            int topRow = yTopExp / 2;
                            int botAction = (yBotExp % 2 == 0)
                                    ? ChestConfigManager.ACTION_TOP
                                    : ChestConfigManager.ACTION_BOTTOM;
                            int botRow = yBotExp / 2;

                            for (int c = fillMinCol; c <= fillMaxCol; c++) {
                                if (topRow * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c < gridSlotCount)
                                    manager.paintAction(topRow * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c + off, topAction, colorToPaint);
                                if (botRow * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c < gridSlotCount)
                                    manager.paintAction(botRow * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c + off, botAction, colorToPaint);
                            }

                            int fillMinRow = (yTopExp + 1) / 2;
                            int fillMaxRow = (yBotExp - 1) / 2;
                            int leftAction = (xLeftExp % 2 == 0)
                                    ? ChestConfigManager.ACTION_LEFT
                                    : ChestConfigManager.ACTION_RIGHT;
                            int leftCol = xLeftExp / 2;
                            int rightAction = (xRightExp % 2 == 0)
                                    ? ChestConfigManager.ACTION_LEFT
                                    : ChestConfigManager.ACTION_RIGHT;
                            int rightCol = xRightExp / 2;

                            for (int r = fillMinRow; r <= fillMaxRow; r++) {
                                if (r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + leftCol < gridSlotCount)
                                    manager.paintAction(r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + leftCol + off, leftAction, colorToPaint);
                                if (r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + rightCol < gridSlotCount)
                                    manager.paintAction(r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + rightCol + off, rightAction, colorToPaint);
                            }
                        }
                        changeMade = true;
                    } else { // 1D Line
                        for (int r = minRow; r <= Math.min(maxRow, gridRows - 1); r++) {
                            for (int c = minCol; c <= maxCol; c++) {
                                if (erase) manager.removeAction(r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c + off, session.currentDragAction);
                                else manager.paintAction(r * io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns() + c + off, session.currentDragAction, colorToPaint);
                            }
                        }
                        changeMade = true;
                    }
                }
            } else { // Pencil Trace
                for (String step : session.tracePath) {
                    String[] parts = step.split("_");
                    int slotIdx = Integer.parseInt(parts[0]);
                    int act = Integer.parseInt(parts[1]);
                    if (explicitEraser || session.isDragModeErasing) manager.removeAction(slotIdx, act);
                    else manager.paintAction(slotIdx, act, colorToPaint);
                    changeMade = true;
                }
            }
        }
        if (changeMade) {
            editor.saveSmart();
            editor.playClickSound(1.0f);
        }
    }
}
