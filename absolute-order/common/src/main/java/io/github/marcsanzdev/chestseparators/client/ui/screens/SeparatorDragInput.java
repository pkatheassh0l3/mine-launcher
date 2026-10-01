package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.world.inventory.Slot;

/**
 * Handles mouse-drag input for the separator editor: extends the trace path while painting and
 * runs the pencil-trace vertex rollback/advance logic. Extracted verbatim from ScreenDrawLines.
 */
final class SeparatorDragInput {

    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;

    SeparatorDragInput(ScreenDrawLines screen) {
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    boolean onDrag(double mouseX, double mouseY) {
        if (session.isColorPickerOpen) return false;

        if (session.isDraggingLine) {
            Slot slot = editor.accessor.getFocusedSlot();
            // Confine the drag to the namespace it started in (no chest<->inventory crossover).
            if (slot != null
                    && ChestSeparatorsEditor.isEditableSlot(slot)
                    && (session.dragStartSlot == null
                            || ChestSeparatorsEditor.isPlayerSlot(slot)
                                    == ChestSeparatorsEditor.isPlayerSlot(session.dragStartSlot))) {
                session.dragCurrentSlot = slot;

                int toolMode = (session.currentTab == 0)
                        ? session.lineToolMode
                        : (session.currentTab == 1 ? session.bgToolMode : session.comboToolMode);

                if (session.currentTab == 1 || session.currentTab == 2) {
                    if (toolMode == 1) {
                        editor.updateTracePath(
                                ChestSeparatorsEditor.slotKey(slot) + "_" + ChestConfigManager.ACTION_BG);
                    }
                } else {
                    if (toolMode == 1) {
                        // Armor/offhand are isolated non-grid cells with no grid neighbours. The
                        // vertex-connection logic below projects segments along rows/columns via grid
                        // math (index%9, index/9, row*9+col), which is meaningless for them. But we
                        // still honour the axis lock via calculateTraceStep (patched to paint the
                        // locked edge on the hovered cell itself, without grid projection) so a
                        // vertical drag paints only the left/right edge and a horizontal one only
                        // top/bottom — exactly like the rest of the inventory. Skip the vertex
                        // undo/advance logic since there is nothing to connect to.
                        if (ChestConfigManager.isNonGridInventoryKey(ChestSeparatorsEditor.slotKey(slot))) {
                            String rawStep = editor.geometry.calculateTraceStep(slot, mouseX, mouseY);
                            if (rawStep != null && !rawStep.isEmpty() && !session.tracePath.contains(rawStep)) {
                                editor.updateTracePath(rawStep);
                            }
                            return true;
                        }

                        int guiX = layout.guiX;
                        int guiY = layout.guiY;

                        // 1. UNDO LOGIC (Exact rollback from connection vertex)
                        while (session.tracePath.size() >= 2) {
                            String currStep = session.tracePath.get(session.tracePath.size() - 1);
                            String prevStep = session.tracePath.get(session.tracePath.size() - 2);

                            int currSlotIdx = Integer.parseInt(currStep.split("_")[0]);
                            int prevSlotIdx = Integer.parseInt(prevStep.split("_")[0]);
                            int currAction = Integer.parseInt(currStep.split("_")[1]);
                            int prevAction = Integer.parseInt(prevStep.split("_")[1]);

                            Slot currSlot = editor.slotForKey(currSlotIdx);
                            if (currSlot == null) break;
                            double cRelX = mouseX - (guiX + currSlot.x);
                            double cRelY = mouseY - (guiY + currSlot.y);

                            // Calculate vertices (P1 and P2) of the previous line
                            int c1 = prevSlotIdx % 9, r1 = prevSlotIdx / 9;
                            int v1x1 = c1, v1y1 = r1, v1x2 = c1, v1y2 = r1;
                            if (prevAction == ChestConfigManager.ACTION_TOP) {
                                v1x2 = c1 + 1;
                            } else if (prevAction == ChestConfigManager.ACTION_BOTTOM) {
                                v1y1 = r1 + 1;
                                v1x2 = c1 + 1;
                                v1y2 = r1 + 1;
                            } else if (prevAction == ChestConfigManager.ACTION_LEFT) {
                                v1y2 = r1 + 1;
                            } else if (prevAction == ChestConfigManager.ACTION_RIGHT) {
                                v1x1 = c1 + 1;
                                v1x2 = c1 + 1;
                                v1y2 = r1 + 1;
                            }

                            // Calculate vertices of the current line
                            int c2 = currSlotIdx % 9, r2 = currSlotIdx / 9;
                            int v2x1 = c2, v2y1 = r2, v2x2 = c2, v2y2 = r2;
                            if (currAction == ChestConfigManager.ACTION_TOP) {
                                v2x2 = c2 + 1;
                            } else if (currAction == ChestConfigManager.ACTION_BOTTOM) {
                                v2y1 = r2 + 1;
                                v2x2 = c2 + 1;
                                v2y2 = r2 + 1;
                            } else if (currAction == ChestConfigManager.ACTION_LEFT) {
                                v2y2 = r2 + 1;
                            } else if (currAction == ChestConfigManager.ACTION_RIGHT) {
                                v2x1 = c2 + 1;
                                v2x2 = c2 + 1;
                                v2y2 = r2 + 1;
                            }

                            // Which vertex of the current line connects to the previous line?
                            boolean sharedV1 = (v2x1 == v1x1 && v2y1 == v1y1) || (v2x1 == v1x2 && v2y1 == v1y2);
                            boolean sharedV2 = (v2x2 == v1x1 && v2y2 == v1y1) || (v2x2 == v1x2 && v2y2 == v1y2);

                            boolean undo = false;
                            if (currAction == ChestConfigManager.ACTION_LEFT
                                    || currAction == ChestConfigManager.ACTION_RIGHT) {
                                if (sharedV1 && cRelY < 8) undo = true;
                                if (sharedV2 && cRelY > 8) undo = true;
                            } else {
                                if (sharedV1 && cRelX < 8) undo = true;
                                if (sharedV2 && cRelX > 8) undo = true;
                            }

                            if (undo) {
                                session.tracePath.remove(session.tracePath.size() - 1);
                                session.lockedTraceAction = prevAction;
                                Slot prevSlot = editor.slotForKey(prevSlotIdx);
                                if (prevSlot == null) break;
                                if (prevAction == ChestConfigManager.ACTION_TOP
                                        || prevAction == ChestConfigManager.ACTION_BOTTOM) {
                                    session.lockedTraceAxis = 1;
                                    session.lockedTraceRowCol = prevSlotIdx / 9;
                                    session.lockedLineCoord = (prevAction == ChestConfigManager.ACTION_TOP)
                                            ? (guiY + prevSlot.y)
                                            : (guiY + prevSlot.y + 16);
                                } else {
                                    session.lockedTraceAxis = 2;
                                    session.lockedTraceRowCol = prevSlotIdx % 9;
                                    session.lockedLineCoord = (prevAction == ChestConfigManager.ACTION_LEFT)
                                            ? (guiX + prevSlot.x)
                                            : (guiX + prevSlot.x + 16);
                                }
                            } else {
                                break;
                            }
                        }

                        // 2. PAINT LOGIC (Advance crossing the middle)
                        String rawStep = editor.geometry.calculateTraceStep(slot, mouseX, mouseY);
                        if (rawStep != null && !rawStep.isEmpty()) {
                            if (!session.tracePath.contains(rawStep)) {
                                int rawSlotIdx = Integer.parseInt(rawStep.split("_")[0]);
                                String lastStep = session.tracePath.get(session.tracePath.size() - 1);
                                int lastSlotIdx = Integer.parseInt(lastStep.split("_")[0]);

                                int rawAction = Integer.parseInt(rawStep.split("_")[1]);
                                int lastAction = Integer.parseInt(lastStep.split("_")[1]);

                                Slot rawSlot = editor.slotForKey(rawSlotIdx);
                                if (rawSlot == null) return true;
                                double rRelX = mouseX - (guiX + rawSlot.x);
                                double rRelY = mouseY - (guiY + rawSlot.y);

                                int c1 = lastSlotIdx % 9, r1 = lastSlotIdx / 9;
                                int v1x1 = c1, v1y1 = r1, v1x2 = c1, v1y2 = r1;
                                if (lastAction == ChestConfigManager.ACTION_TOP) {
                                    v1x2 = c1 + 1;
                                } else if (lastAction == ChestConfigManager.ACTION_BOTTOM) {
                                    v1y1 = r1 + 1;
                                    v1x2 = c1 + 1;
                                    v1y2 = r1 + 1;
                                } else if (lastAction == ChestConfigManager.ACTION_LEFT) {
                                    v1y2 = r1 + 1;
                                } else if (lastAction == ChestConfigManager.ACTION_RIGHT) {
                                    v1x1 = c1 + 1;
                                    v1x2 = c1 + 1;
                                    v1y2 = r1 + 1;
                                }

                                int c2 = rawSlotIdx % 9, r2 = rawSlotIdx / 9;
                                int v2x1 = c2, v2y1 = r2, v2x2 = c2, v2y2 = r2;
                                if (rawAction == ChestConfigManager.ACTION_TOP) {
                                    v2x2 = c2 + 1;
                                } else if (rawAction == ChestConfigManager.ACTION_BOTTOM) {
                                    v2y1 = r2 + 1;
                                    v2x2 = c2 + 1;
                                    v2y2 = r2 + 1;
                                } else if (rawAction == ChestConfigManager.ACTION_LEFT) {
                                    v2y2 = r2 + 1;
                                } else if (rawAction == ChestConfigManager.ACTION_RIGHT) {
                                    v2x1 = c2 + 1;
                                    v2x2 = c2 + 1;
                                    v2y2 = r2 + 1;
                                }

                                boolean sharedV1 = (v2x1 == v1x1 && v2y1 == v1y1) || (v2x1 == v1x2 && v2y1 == v1y2);
                                boolean sharedV2 = (v2x2 == v1x1 && v2y2 == v1y1) || (v2x2 == v1x2 && v2y2 == v1y2);

                                if (sharedV1 || sharedV2) {
                                    boolean shouldAdd = false;
                                    if (rawAction == ChestConfigManager.ACTION_LEFT
                                            || rawAction == ChestConfigManager.ACTION_RIGHT) {
                                        if (sharedV1 && rRelY >= 8) shouldAdd = true;
                                        if (sharedV2 && rRelY <= 8) shouldAdd = true;
                                    } else {
                                        if (sharedV1 && rRelX >= 8) shouldAdd = true;
                                        if (sharedV2 && rRelX <= 8) shouldAdd = true;
                                    }
                                    if (shouldAdd) editor.updateTracePath(rawStep);
                                }
                            } else {
                                editor.updateTracePath(rawStep);
                            }
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }
}
