package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorGeometry;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;

/**
 * Handles mouse-click routing for the line-drawing screen: the tab strip, the color palette
 * (standard and custom swatches), slot painting/drag start, and click-outside-to-close. Extracted
 * from ScreenDrawLines.
 */
final class DrawLinesClickHandler {

    private final ScreenDrawLines screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;
    private final EditorGeometry geometry;

    DrawLinesClickHandler(ScreenDrawLines screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
        this.geometry = screen.geometry;
    }

    boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (screen.clickWidgets(mouseX, mouseY, button)) return true;

        if (handleTabsClick(mouseX, mouseY, button)) return true;
        if (handlePaletteClick(mouseX, mouseY, button)) return true;

        if (session.isColorPickerOpen) return false;

        if (button == 0) {
            Slot slot = editor.accessor.getFocusedSlot();
            if (slot != null && ChestSeparatorsEditor.isEditableSlot(slot)) {
                int action = geometry.calculateAction(slot, mouseX, mouseY);

                if (session.currentTab == 1 || session.currentTab == 2) {
                    ChestConfigManager.getInstance().saveSnapshot();
                    session.isDraggingLine = true;
                    session.currentDragAction = ChestConfigManager.ACTION_BG;
                    session.dragStartSlot = slot;
                    session.dragCurrentSlot = slot;
                    session.tracePath.clear();
                    session.lockedTraceAxis = 0;
                    session.tracePath.add(ChestSeparatorsEditor.slotKey(slot) + "_" + ChestConfigManager.ACTION_BG);

                    int tIndex = (session.currentTab == 1) ? session.bgColorIndex : session.comboColorIndex;
                    int colorVal = (session.currentTab == 1)
                            ? screen.getCurrentSelectedBgColorValue()
                            : screen.getCurrentSelectedComboColorValue();

                    if (tIndex == ChestSeparatorsEditor.TOOL_ERASER_ID) {
                        session.isDragModeErasing = true;
                    } else {
                        if (colorVal == 0) return false;
                        int existingColor = ChestConfigManager.getInstance()
                                .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_BG);
                        session.isDragModeErasing = (existingColor == (colorVal | 0xFF000000));
                    }
                    return true;
                }

                if (session.currentTab == 0) {
                    if (action != 0 && action != ChestConfigManager.ACTION_BG) {
                        ChestConfigManager.getInstance().saveSnapshot();
                        session.isDraggingLine = true;
                        session.currentDragAction = action;
                        session.dragStartSlot = slot;
                        session.dragCurrentSlot = slot;
                        session.tracePath.clear();
                        session.tracePath.add(ChestSeparatorsEditor.slotKey(slot) + "_" + action);
                        session.lockedTraceAction = action;
                        if (action == ChestConfigManager.ACTION_TOP || action == ChestConfigManager.ACTION_BOTTOM) {
                            session.lockedTraceAxis = 1;
                            session.lockedTraceRowCol = slot.getContainerSlot() / io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                            session.lockedLineCoord = (action == ChestConfigManager.ACTION_TOP)
                                    ? (layout.guiY + slot.y)
                                    : (layout.guiY + slot.y + 16);
                        } else {
                            session.lockedTraceAxis = 2;
                            session.lockedTraceRowCol = slot.getContainerSlot() % io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.storageColumns();
                            session.lockedLineCoord = (action == ChestConfigManager.ACTION_LEFT)
                                    ? (layout.guiX + slot.x)
                                    : (layout.guiX + slot.x + 16);
                        }

                        int tIndex = session.lineColorIndex;
                        int colorVal = screen.getCurrentSelectedLineColorValue();

                        if (tIndex == ChestSeparatorsEditor.TOOL_ERASER_ID) {
                            session.isDragModeErasing = true;
                        } else {
                            if (colorVal == 0) return false;
                            int existingColor = ChestConfigManager.getInstance()
                                    .getColor(ChestSeparatorsEditor.slotKey(slot), action);
                            session.isDragModeErasing = (existingColor == (colorVal | 0xFF000000));
                        }
                        return true;
                    }
                }
            }
        }

        // --- CLICK OUTSIDE / ON DEAD SPACE TO CLOSE ---
        // Close not only when clicking fully outside, but also when clicking a spot that changes nothing:
        // anything that is not an editable slot (or its paintable border) and not the tool sidebar or the
        // right-hand buttons. A no-op click inside the container should dismiss the panel like any other.
        boolean overSlot = editor.accessor.getFocusedSlot() != null
                && ChestSeparatorsEditor.isEditableSlot(editor.accessor.getFocusedSlot());
        boolean isInsideLeft = mouseX >= layout.sidebarX
                && mouseX <= layout.sidebarX + layout.sidebarWidth
                && mouseY >= layout.guiY + editor.getSidebarYOffset() - 4
                && mouseY <= layout.guiY + editor.getSidebarYOffset() + layout.sidebarHeight;
        boolean isInsideRight = mouseX >= layout.rightX
                && mouseX <= layout.rightX + layout.btnW
                && mouseY >= layout.mainY
                && mouseY <= layout.mainY + (layout.bH * 4) + (4 * 3);

        if (!overSlot && !isInsideLeft && !isInsideRight && !session.isEyedropperActive) {
            if (GlobalChestConfig.instance.closeOnClickOutside) {
                editor.toggleState(EditorState.HIDDEN);
                editor.playCloseSound();
                return true;
            }
        }

        return false;
    }

    boolean handleTabsClick(double mx, double my, int button) {
        int tabX = layout.sidebarX - 16;
        int startY = layout.guiY + editor.getSidebarYOffset() + 10;

        if (mx >= tabX && mx < tabX + 20) {
            if (button == 0) {
                for (int i = 0; i < 3; i++) {
                    int tabY = startY + (i * 24);
                    if (my >= tabY && my < tabY + 20) {
                        session.currentTab = i;
                        editor.playClickSound(1.0f);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean handlePaletteClick(double mx, double my, int button) {
        int pY = layout.paletteY;
        int col1X = layout.paletteCol1X;
        int col2X = layout.paletteCol2X;
        int col3X = layout.paletteCol3X;

        int tabMode = session.currentTab;

        for (int i = 0; i < 16; i++) {
            int x = (i / 8 == 0) ? col1X : col2X;
            int y = pY + ((i % 8) * (layout.swatchSize + 4));
            if (editor.isHovering(x, y, layout.swatchSize, layout.swatchSize, mx, my)) {
                if (button == 2) {
                    session.copiedColorRGB = ScreenDrawLines.STANDARD_PALETTE[i];
                    editor.showStatus(
                            Component.translatable("message.chestseparators.color_copied"), session.copiedColorRGB);
                    editor.playClickSound(0.8f);
                    return true;
                } else if (button == 0) {
                    if (tabMode == 0) {
                        session.lineColorIndex = i;
                        session.editingLineCustomIndex = -1;
                    } else if (tabMode == 1) {
                        session.bgColorIndex = i;
                        session.editingBgCustomIndex = -1;
                    } else {
                        session.comboColorIndex = i;
                        session.editingComboCustomIndex = -1;
                    }
                    editor.playClickSound(1.0f);
                    return true;
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            int y = pY + (i * (layout.swatchSize + 4));
            if (editor.isHovering(col3X, y, layout.swatchSize, layout.swatchSize, mx, my)) {
                int currentColor = ChestConfigManager.getInstance().getCustomColors(tabMode)[i];

                if (button == 2) {
                    if (currentColor != 0) {
                        session.copiedColorRGB = currentColor;
                        editor.showStatus(
                                Component.translatable("message.chestseparators.color_copied"), session.copiedColorRGB);
                        editor.playClickSound(0.8f);
                    }
                    return true;
                } else if (button == 0) {
                    if (session.copiedColorRGB != 0) {
                        int pastedColor = session.copiedColorRGB;
                        ChestConfigManager.getInstance().setCustomColor(i, session.copiedColorRGB, tabMode);
                        ChestConfigManager.getInstance().saveWorldPalette();
                        session.copiedColorRGB = 0;
                        editor.showStatus(Component.translatable("message.chestseparators.color_pasted"), pastedColor);
                        editor.playClickSound(1.2f);
                    } else {
                        // Save the current color index so it can be restored if the picker is dismissed without saving.
                        editor.restoreColorIndex = (tabMode == 0)
                                ? session.lineColorIndex
                                : (tabMode == 1 ? session.bgColorIndex : session.comboColorIndex);

                        if (currentColor == 0) {
                            // Empty slot: one click opens the color picker immediately.
                            if (tabMode == 0) {
                                session.lineColorIndex = 16 + i;
                                session.editingLineCustomIndex = i;
                            } else if (tabMode == 1) {
                                session.bgColorIndex = 16 + i;
                                session.editingBgCustomIndex = i;
                            } else {
                                session.comboColorIndex = 16 + i;
                                session.editingComboCustomIndex = i;
                            }
                            editor.openColorPicker(0, tabMode);
                            editor.playClickSound(1.0f);
                        } else {
                            // Occupied slot: single click selects for painting, double click opens the picker.
                            long now = System.currentTimeMillis();
                            boolean isDoubleClick = (i == screen.lastCustomColorClickIndex)
                                    && (now - screen.lastCustomColorClickTime < 300);
                            screen.lastCustomColorClickIndex = i;
                            screen.lastCustomColorClickTime = now;

                            if (isDoubleClick) {
                                if (tabMode == 0) {
                                    session.lineColorIndex = 16 + i;
                                    session.editingLineCustomIndex = i;
                                } else if (tabMode == 1) {
                                    session.bgColorIndex = 16 + i;
                                    session.editingBgCustomIndex = i;
                                } else {
                                    session.comboColorIndex = 16 + i;
                                    session.editingComboCustomIndex = i;
                                }
                                editor.openColorPicker(currentColor, tabMode);
                                editor.playClickSound(1.0f);
                            } else {
                                if (tabMode == 0) {
                                    session.lineColorIndex = 16 + i;
                                    session.editingLineCustomIndex = i;
                                } else if (tabMode == 1) {
                                    session.bgColorIndex = 16 + i;
                                    session.editingBgCustomIndex = i;
                                } else {
                                    session.comboColorIndex = 16 + i;
                                    session.editingComboCustomIndex = i;
                                }
                                editor.playClickSound(1.0f);
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
