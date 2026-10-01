package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Handles all mouse and keyboard input for the color-picker overlay: eyedropper, saturation/value
 * box, hue bar, hex/RGB text fields, drag-select, and key navigation. Extracted from
 * ScreenColorPicker.
 */
final class ColorPickerInputHandler {

    private final ScreenColorPicker screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;

    ColorPickerInputHandler(ScreenColorPicker screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    /** Focuses {@code field} and places the caret at the click if the click lands inside it; otherwise blurs
     *  it. Returns whether the click hit this field. No-op (returns false) for a null field. */
    private boolean focusFieldIfHovered(
            net.minecraft.client.gui.components.EditBox field,
            double mouseX,
            double mouseY,
            Minecraft client) {
        if (field == null) return false;
        if (editor.isHovering(field.getX(), field.getY(), field.getWidth(), field.getHeight(), mouseX, mouseY)) {
            field.setFocused(true);
            int localX = (int) (mouseX - field.getX());
            field.moveCursorTo(client.font
                    .plainSubstrByWidth(field.getValue(), Math.max(0, localX))
                    .length(), false);
            return true;
        }
        field.setFocused(false);
        return false;
    }

    boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!session.isColorPickerOpen) return false;

        if (session.isEyedropperActive) {
            if (button == 1) {
                session.isEyedropperActive = false;
                org.lwjgl.glfw.GLFW.glfwSetInputMode(
                        Minecraft.getInstance().getWindow().getWindow(),
                        org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                        org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
                editor.playClickSound(0.8f);
                return true;
            }
            if (button == 0) {
                editor.colorPickerModified = true;
                session.pickerCurrentRGB = screen.hoveredPixelColor;
                float[] hsb = Color.RGBtoHSB(
                        (screen.hoveredPixelColor >> 16) & 0xFF,
                        (screen.hoveredPixelColor >> 8) & 0xFF,
                        screen.hoveredPixelColor & 0xFF,
                        null);
                session.pickerHue = hsb[0];
                session.pickerSat = hsb[1];
                session.pickerVal = hsb[2];
                screen.updateFieldsFromColor();
                screen.saveCurrentCustomColor();

                session.isEyedropperActive = false;
                org.lwjgl.glfw.GLFW.glfwSetInputMode(
                        Minecraft.getInstance().getWindow().getWindow(),
                        org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                        org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
                editor.playClickSound(1.2f);
                return true;
            }
            return false;
        }

        if (button == 0) {
            int pCol3X = layout.paletteCol3X;
            int pY = layout.paletteY;
            int sS = layout.swatchSize;
            int gap = 4;
            int tabMode = session.pickerTargetMode;

            for (int i = 0; i < 8; i++) {
                int slotY = pY + (i * (sS + gap));
                if (editor.isHovering(pCol3X, slotY, sS, sS, mouseX, mouseY)) {
                    editor.playClickSound(1.0f);
                    if (editor.colorPickerModified) screen.saveCurrentCustomColor();

                    if (tabMode == 0) {
                        session.editingLineCustomIndex = i;
                        session.lineColorIndex = 16 + i;
                    } else if (tabMode == 1) {
                        session.editingBgCustomIndex = i;
                        session.bgColorIndex = 16 + i;
                    } else if (tabMode == 2) {
                        session.editingComboCustomIndex = i;
                        session.comboColorIndex = 16 + i;
                    }

                    int newColor = ChestConfigManager.getInstance().getCustomColors(tabMode)[i];
                    editor.colorPickerModified = false;

                    if (newColor == 0) {
                        session.pickerCurrentRGB = 0;
                    } else {
                        newColor = newColor & 0xFFFFFF;
                        session.pickerCurrentRGB = newColor;
                        float[] hsb =
                                Color.RGBtoHSB((newColor >> 16) & 0xFF, (newColor >> 8) & 0xFF, newColor & 0xFF, null);
                        session.pickerHue = hsb[0];
                        session.pickerSat = hsb[1];
                        session.pickerVal = hsb[2];
                    }
                    screen.updateFieldsFromColor();
                    return true;
                }
            }
        }

        boolean inPopup = mouseX >= layout.popupX
                && mouseX <= layout.popupX + layout.popupW
                && mouseY >= layout.popupY
                && mouseY <= layout.popupY + layout.popupH;

        if (inPopup) {
            if (button == 0) {
                Minecraft client = Minecraft.getInstance();
                boolean clickedText = focusFieldIfHovered(screen.hexField, mouseX, mouseY, client);
                clickedText |= focusFieldIfHovered(screen.rField, mouseX, mouseY, client);
                clickedText |= focusFieldIfHovered(screen.gField, mouseX, mouseY, client);
                clickedText |= focusFieldIfHovered(screen.bField, mouseX, mouseY, client);

                if (clickedText) {
                    editor.playClickSound(1.0f);
                    return true;
                }

                int contentX = layout.popupX + 12;
                int contentY = layout.popupY + 45;

                if (mouseX >= contentX && mouseX <= contentX + 100 && mouseY >= contentY && mouseY <= contentY + 100) {
                    session.isDraggingSatVal = true;
                    session.lastClickedHue = false;
                    updateColorFromMouse(mouseX, mouseY, true);
                    return true;
                } else if (mouseX >= contentX + 115
                        && mouseX <= contentX + 135
                        && mouseY >= contentY
                        && mouseY <= contentY + 100) {
                    session.isDraggingHue = true;
                    session.lastClickedHue = true;
                    updateColorFromMouse(mouseX, mouseY, false);
                    return true;
                }
            }
            return screen.clickWidgets(mouseX, mouseY, button);
        } else {
            if (button == 0 || button == 1) {
                if (GlobalChestConfig.instance.closeOnClickOutside) {
                    screen.closeAndRestore();
                    return true;
                }
            }
        }
        return false;
    }

    boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!session.isColorPickerOpen || session.isEyedropperActive) return false;

        if (button == 0) {
            if (session.isDraggingSatVal) {
                updateColorFromMouse(mouseX, mouseY, true);
                return true;
            } else if (session.isDraggingHue) {
                updateColorFromMouse(mouseX, mouseY, false);
                return true;
            }
        }
        return true;
    }

    boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            session.isDraggingSatVal = false;
            session.isDraggingHue = false;
        }
        return false;
    }

    private void updateColorFromMouse(double mx, double my, boolean isSatValBox) {
        editor.colorPickerModified = true;
        int contentX = layout.popupX + 12;
        int contentY = layout.popupY + 45;

        if (isSatValBox) {
            float relX = Mth.clamp((float) (mx - contentX), 0.0f, 100.0f);
            float relY = Mth.clamp((float) (my - contentY), 0.0f, 100.0f);

            session.pickerSat = relX / 100.0f;
            session.pickerVal = 1.0f - (relY / 100.0f);
        } else {
            float relY = Mth.clamp((float) (my - contentY), 0.0f, 100.0f);
            session.pickerHue = relY / 100.0f;
        }

        session.pickerCurrentRGB = Color.HSBtoRGB(session.pickerHue, session.pickerSat, session.pickerVal);
        screen.updateFieldsFromColor();

        screen.saveCurrentCustomColor();
    }

    boolean onKeyPressed(io.github.marcsanzdev.chestseparators.client.input.KeyEvent input) {
        if (!session.isColorPickerOpen) return false;

        if (session.isEyedropperActive && input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            session.isEyedropperActive = false;
            org.lwjgl.glfw.GLFW.glfwSetInputMode(
                    Minecraft.getInstance().getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                    org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
            return true;
        }

        int key = input.key();
        boolean isAnyFieldFocused = (screen.hexField != null && screen.hexField.isFocused())
                || (screen.rField != null && screen.rField.isFocused())
                || (screen.gField != null && screen.gField.isFocused())
                || (screen.bField != null && screen.bField.isFocused());

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (isAnyFieldFocused) {
                if (screen.hexField != null) screen.hexField.setFocused(false);
                if (screen.rField != null) screen.rField.setFocused(false);
                if (screen.gField != null) screen.gField.setFocused(false);
                if (screen.bField != null) screen.bField.setFocused(false);
            } else {
                screen.closeAndRestore();
            }
            return true;
        }

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            if (screen.hexField != null && screen.hexField.isFocused()) {
                screen.hexField.setFocused(false);
                screen.rField.setFocused(true);
            } else if (screen.rField != null && screen.rField.isFocused()) {
                screen.rField.setFocused(false);
                screen.gField.setFocused(true);
            } else if (screen.gField != null && screen.gField.isFocused()) {
                screen.gField.setFocused(false);
                screen.bField.setFocused(true);
            } else if (screen.bField != null && screen.bField.isFocused()) {
                screen.bField.setFocused(false);
                screen.hexField.setFocused(true);
            } else if (screen.hexField != null) {
                screen.hexField.setFocused(true);
            }
            return true;
        }

        if (isAnyFieldFocused) {
            boolean isUp = key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL;
            boolean isDown = key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT;

            if (isUp || isDown) {
                editor.colorPickerModified = true;
                int delta = isUp ? 1 : -1;
                try {
                    if (screen.hexField.isFocused()) {
                        String hex = screen.hexField.getValue().trim().replace("#", "");
                        int val = hex.isEmpty() ? 0 : Integer.parseInt(hex, 16);
                        val = Mth.clamp(val + delta, 0, 0xFFFFFF);
                        screen.hexField.setValue(
                                String.format("%02X%02X%02X", (val >> 16) & 0xFF, (val >> 8) & 0xFF, val & 0xFF));
                        screen.updateColorFromFields(true);
                    } else if (screen.rField.isFocused()) {
                        int val = Integer.parseInt(screen.rField.getValue().isEmpty() ? "0" : screen.rField.getValue());
                        screen.rField.setValue(String.valueOf(Mth.clamp(val + delta, 0, 255)));
                        screen.updateColorFromFields(false);
                    } else if (screen.gField.isFocused()) {
                        int val = Integer.parseInt(screen.gField.getValue().isEmpty() ? "0" : screen.gField.getValue());
                        screen.gField.setValue(String.valueOf(Mth.clamp(val + delta, 0, 255)));
                        screen.updateColorFromFields(false);
                    } else if (screen.bField.isFocused()) {
                        int val = Integer.parseInt(screen.bField.getValue().isEmpty() ? "0" : screen.bField.getValue());
                        screen.bField.setValue(String.valueOf(Mth.clamp(val + delta, 0, 255)));
                        screen.updateColorFromFields(false);
                    }
                    screen.saveCurrentCustomColor();
                } catch (NumberFormatException ignored) {
                }
                return true;
            }

            // Forward delete/copy/paste keystrokes to the focused field.
            if (screen.hexField != null && screen.hexField.isFocused()) screen.hexField.keyPressed(input.key(), input.scancode(), input.modifiers());
            else if (screen.rField != null && screen.rField.isFocused()) screen.rField.keyPressed(input.key(), input.scancode(), input.modifiers());
            else if (screen.gField != null && screen.gField.isFocused()) screen.gField.keyPressed(input.key(), input.scancode(), input.modifiers());
            else if (screen.bField != null && screen.bField.isFocused()) screen.bField.keyPressed(input.key(), input.scancode(), input.modifiers());

            // Always consume the keystroke so Minecraft's own bindings do not fire.
            return true;
        } else {
            float step = 0.01f;
            boolean changed = false;

            if (session.lastClickedHue) {
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                    session.pickerHue = Mth.clamp(session.pickerHue - step, 0.0f, 1.0f);
                    changed = true;
                } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                    session.pickerHue = Mth.clamp(session.pickerHue + step, 0.0f, 1.0f);
                    changed = true;
                }
            } else {
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
                    session.pickerSat = Mth.clamp(session.pickerSat - step, 0.0f, 1.0f);
                    changed = true;
                } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
                    session.pickerSat = Mth.clamp(session.pickerSat + step, 0.0f, 1.0f);
                    changed = true;
                } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                    session.pickerVal = Mth.clamp(session.pickerVal + step, 0.0f, 1.0f);
                    changed = true;
                } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                    session.pickerVal = Mth.clamp(session.pickerVal - step, 0.0f, 1.0f);
                    changed = true;
                }
            }

            if (changed) {
                editor.colorPickerModified = true;
                session.pickerCurrentRGB = Color.HSBtoRGB(session.pickerHue, session.pickerSat, session.pickerVal);
                screen.updateFieldsFromColor();
                screen.saveCurrentCustomColor();
                return true;
            }
        }
        return false;
    }

    boolean onCharTyped(io.github.marcsanzdev.chestseparators.client.input.CharacterEvent input) {
        if (!session.isColorPickerOpen) return false;
        if (screen.hexField != null && screen.hexField.isFocused()) return screen.hexField.charTyped((char) input.codepoint(), input.modifiers());
        if (screen.rField != null && screen.rField.isFocused()) return screen.rField.charTyped((char) input.codepoint(), input.modifiers());
        if (screen.gField != null && screen.gField.isFocused()) return screen.gField.charTyped((char) input.codepoint(), input.modifiers());
        if (screen.bField != null && screen.bField.isFocused()) return screen.bField.charTyped((char) input.codepoint(), input.modifiers());
        return false;
    }
}


