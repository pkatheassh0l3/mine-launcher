package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;

public class ScreenColorPicker extends AbstractEditorScreen {

    EditBox hexField;
    EditBox rField;
    EditBox gField;
    EditBox bField;

    private boolean isUpdatingFields = false;
    int hoveredPixelColor = 0xFFFFFF;
    // Consecutive frames the eyedropper has sampled a near-white pixel — used to reject the one-frame white
    // flash caused by the (white) dropper icon of the previous frame landing on the sample point on fast moves.
    private int whiteSampleStreak = 0;

    private final ColorPickerInputHandler inputHandler;

    public ScreenColorPicker(ChestSeparatorsEditor editor) {
        super(editor);
        this.inputHandler = new ColorPickerInputHandler(this);
    }

    @Override
    protected void buildWidgets() {

        int w = layout.popupW;
        int x = layout.popupX;
        int y = layout.popupY;
        int btnY = y + layout.popupH - 30;
        int bH = 20;

        WideButtonWidget btnEyedropper = new WideButtonWidget(
                x + 10,
                btnY,
                85,
                bH,
                Component.translatable("button.chestseparators.eyedropper").getString(),
                ModTextures.ICON_SM_EYEDROPPER,
                () -> {
                    session.isEyedropperActive = true;
                    org.lwjgl.glfw.GLFW.glfwSetInputMode(
                            Minecraft.getInstance().getWindow().getWindow(),
                            org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                            org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN);
                    editor.playClickSound(1.2f);
                });
        btnEyedropper.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.eyedropper").getString();
        btnEyedropper.texSize = 128;
        widgets.add(btnEyedropper);

        WideButtonWidget btnExit = new WideButtonWidget(
                x + 145,
                btnY,
                85,
                bH,
                Component.translatable("button.chestseparators.exit").getString(),
                ModTextures.ICON_SM_CANCEL,
                () -> {
                    if (session.finishingRegion) {
                        editor.acceptRegionColor(session.pickerCurrentRGB);
                        session.isColorPickerOpen = false;
                        editor.screenEditFilter.completeSave(); return;
                    }
                    saveCurrentCustomColor();
                    editor.playClickSound(0.8f);
                    clearSelection();
                    session.isColorPickerOpen = false;
                    closeAndRestore();
                });
        btnExit.tooltipText =
                Component.translatable("tooltip.chestseparators.desc.exit").getString();
        btnExit.deferAction = true; // flash before the Exit button closes the colour picker
        widgets.add(btnExit);

        Minecraft client = Minecraft.getInstance();

        int rightPanelX = layout.popupX + 155;
        int textX = rightPanelX + 5;
        int fieldX = textX + 12;
        int fieldY = layout.popupY + 85;

        hexField = new EditBox(
                client.font, fieldX, fieldY, 60, 12, Component.translatable("gui.chestseparators.hex"));
        hexField.setMaxLength(6);
        hexField.setBordered(false);
        boolean isDark = GlobalChestConfig.instance.darkMode;
        hexField.setTextColor(isDark ? 0xFFFFFFFF : 0xFF222222);
        hexField.setFilter(text -> text.matches("^[0-9a-fA-F]*$"));
        hexField.setResponder(text -> {
            String upper = text.toUpperCase();
            // Normalize to uppercase so the hex string is always canonical.
            if (!text.equals(upper)) {
                hexField.setValue(upper);
            } else {
                updateColorFromFields(true);
            }
        });

        rField = new EditBox(
                client.font, fieldX, fieldY + 20, 30, 12, Component.translatable("gui.chestseparators.color_r"));
        rField.setMaxLength(3);
        rField.setBordered(false);
        rField.setTextColor(0xFFFF5555);
        rField.setFilter(text -> text.matches("^[0-9]*$"));
        rField.setResponder(text -> {
            if (!text.isEmpty() && Integer.parseInt(text) > 255) {
                rField.setValue("255");
            } else {
                updateColorFromFields(false);
            }
        });

        gField = new EditBox(
                client.font, fieldX, fieldY + 36, 30, 12, Component.translatable("gui.chestseparators.color_g"));
        gField.setMaxLength(3);
        gField.setBordered(false);
        gField.setTextColor(0xFF55FF55);
        gField.setFilter(text -> text.matches("^[0-9]*$"));
        gField.setResponder(text -> {
            if (!text.isEmpty() && Integer.parseInt(text) > 255) {
                gField.setValue("255");
            } else {
                updateColorFromFields(false);
            }
        });

        bField = new EditBox(
                client.font, fieldX, fieldY + 52, 30, 12, Component.translatable("gui.chestseparators.color_b"));
        bField.setMaxLength(3);
        bField.setBordered(false);
        bField.setTextColor(0xFF5555FF);
        bField.setFilter(text -> text.matches("^[0-9]*$"));
        bField.setResponder(text -> {
            if (!text.isEmpty() && Integer.parseInt(text) > 255) {
                bField.setValue("255");
            } else {
                updateColorFromFields(false);
            }
        });

        updateFieldsFromColor();
    }

    void saveCurrentCustomColor() {
        if (session.pickerTargetMode == 3) return;
        int customIndex = (session.pickerTargetMode == 0)
                ? session.editingLineCustomIndex
                : ((session.pickerTargetMode == 1) ? session.editingBgCustomIndex : session.editingComboCustomIndex);

        if (customIndex != -1) {
            ChestConfigManager.getInstance()
                    .setCustomColor(customIndex, session.pickerCurrentRGB, session.pickerTargetMode);
            ChestConfigManager.getInstance().saveWorldPalette();
        }
    }

    void clearSelection() {
        if (session.pickerTargetMode == 3) return;
        if (session.pickerTargetMode == 0) session.editingLineCustomIndex = -1;
        else if (session.pickerTargetMode == 1) session.editingBgCustomIndex = -1;
        else session.editingComboCustomIndex = -1;
    }

    public void onOpen() {
        if (widgets.size() > 1 && widgets.get(1) instanceof WideButtonWidget exit) {
            exit.label = session.finishingRegion ? "Confirmar" : "Aplicar";
            exit.tooltipText = session.finishingRegion ? "Confirmar color y guardar la zona" : "Aplicar color";
        }
        updateFieldsFromColor();
    }

    public void updateFieldsFromColor() {
        if (hexField == null) return;
        isUpdatingFields = true;

        if (session.pickerCurrentRGB == 0 && !editor.colorPickerModified) {
            hexField.setValue("");
            rField.setValue("");
            gField.setValue("");
            bField.setValue("");
            boolean isDark = GlobalChestConfig.instance.darkMode;
            hexField.setTextColor(isDark ? 0xFFFFFFFF : 0xFF222222);
        } else {
            int r = (session.pickerCurrentRGB >> 16) & 0xFF;
            int g = (session.pickerCurrentRGB >> 8) & 0xFF;
            int b = session.pickerCurrentRGB & 0xFF;

            hexField.setValue(String.format("%02X%02X%02X", r, g, b));
            rField.setValue(String.valueOf(r));
            gField.setValue(String.valueOf(g));
            bField.setValue(String.valueOf(b));

            hexField.setTextColor(session.pickerCurrentRGB | 0xFF000000);
        }

        isUpdatingFields = false;
    }

    void updateColorFromFields(boolean fromHex) {
        if (isUpdatingFields) return;

        editor.colorPickerModified = true;

        try {
            int r, g, b;
            if (fromHex) {
                String hex = hexField.getValue().trim().replace("#", "");
                if (hex.length() == 6) {
                    int color = Integer.parseInt(hex, 16);
                    r = (color >> 16) & 0xFF;
                    g = (color >> 8) & 0xFF;
                    b = color & 0xFF;
                } else return;
            } else {
                r = Mth.clamp(Integer.parseInt(rField.getValue().isEmpty() ? "0" : rField.getValue()), 0, 255);
                g = Mth.clamp(Integer.parseInt(gField.getValue().isEmpty() ? "0" : gField.getValue()), 0, 255);
                b = Mth.clamp(Integer.parseInt(bField.getValue().isEmpty() ? "0" : bField.getValue()), 0, 255);
            }

            session.pickerCurrentRGB = (r << 16) | (g << 8) | b;
            float[] hsb = Color.RGBtoHSB(r, g, b, null);
            session.pickerHue = hsb[0];
            session.pickerSat = hsb[1];
            session.pickerVal = hsb[2];

            isUpdatingFields = true;
            if (fromHex) {
                rField.setValue(String.valueOf(r));
                gField.setValue(String.valueOf(g));
                bField.setValue(String.valueOf(b));
            } else {
                hexField.setValue(String.format("%02X%02X%02X", r, g, b));
                hexField.setTextColor(session.pickerCurrentRGB | 0xFF000000);
            }
            isUpdatingFields = false;

            saveCurrentCustomColor();

        } catch (NumberFormatException ignored) {
        }
    }

    public void closeAndRestore() {
        if (session.finishingRegion) {
            session.finishingRegion = false; session.isColorPickerOpen = false;
            editor.playCloseSound(); return;
        }
        if (session.pickerTargetMode == 3) {
            editor.acceptRegionColor(session.pickerCurrentRGB);
            session.isColorPickerOpen = false; editor.playCloseSound(); return;
        }
        boolean hasNoColor = (session.pickerCurrentRGB == 0 && !editor.colorPickerModified);

        if (editor.colorPickerModified) {
            saveCurrentCustomColor();
        } else if (hasNoColor) {
            // Revert to the previous color and deselect the custom slot.
            if (session.pickerTargetMode == 0) {
                session.lineColorIndex = editor.restoreColorIndex;
                session.editingLineCustomIndex = -1;
            } else if (session.pickerTargetMode == 1) {
                session.bgColorIndex = editor.restoreColorIndex;
                session.editingBgCustomIndex = -1;
            } else {
                session.comboColorIndex = editor.restoreColorIndex;
                session.editingComboCustomIndex = -1;
            }
        }

        editor.playCloseSound();
        session.isColorPickerOpen = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputHandler.onMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return inputHandler.onMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return inputHandler.onMouseReleased(mouseX, mouseY, button);
    }

    public boolean keyPressed(io.github.marcsanzdev.chestseparators.client.input.KeyEvent input) {
        return inputHandler.onKeyPressed(input);
    }

    public boolean charTyped(io.github.marcsanzdev.chestseparators.client.input.CharacterEvent input) {
        return inputHandler.onCharTyped(input);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!session.isColorPickerOpen) return;

        if (session.isEyedropperActive) {
            int sampled = ColorPickerGradients.readHoveredPixelColor(mouseX, mouseY);
            // Trust a near-white reading only after it has held for a second frame: a real white target
            // stays put, but the previous frame's white dropper icon flashing onto the sample point during
            // a fast move lasts a single frame. Every other colour updates live and instantly.
            boolean nearWhite =
                    ((sampled >> 16) & 0xFF) >= 0xF0 && ((sampled >> 8) & 0xFF) >= 0xF0 && (sampled & 0xFF) >= 0xF0;
            if (!nearWhite || whiteSampleStreak >= 1) {
                hoveredPixelColor = sampled;
            }
            whiteSampleStreak = nearWhite ? whiteSampleStreak + 1 : 0;
            // Whole-screen zoom loupe for precise color picking, drawn before the eyedropper cursor so
            // the cursor stays visible and is not captured into the loupe.
            if (GlobalChestConfig.instance.magnifierEnabled) {
                boolean circle = GlobalChestConfig.instance.magnifierShape == GlobalChestConfig.MagnifierShape.CIRCLE;
                io.github.marcsanzdev.chestseparators.client.ui.MagnifierRenderer.render(
                        context, mouseX, mouseY, circle);
            }
            renderEyedropperCursor(context, mouseX, mouseY);
            return;
        }

        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0x77000000);

        // Re-draw only the 8 custom swatches above the dim layer so they remain visible and clickable.
        int pCol3X = layout.paletteCol3X;
        int pY = layout.paletteY;
        int sS = layout.swatchSize;
        int gap = 4;
        int tabMode = session.pickerTargetMode;
        int[] worldColors = ChestConfigManager.getInstance().getCustomColors(tabMode);

        for (int i = 0; i < 8; i++) {
            int slotY = pY + (i * (sS + gap));
            editor.screenDrawLines.drawSwatch(
                    context, pCol3X, slotY, worldColors[i], 16 + i, mouseX, mouseY, true, tabMode);
        }

        int w = layout.popupW;
        int h = layout.popupH;
        int x = layout.popupX;
        int y = layout.popupY;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(context, x, y, w, h);

        Minecraft client = Minecraft.getInstance();

        Component titleText = Component.translatable("window.chestseparators.edit_color").withStyle(ChatFormatting.BOLD);
        int titleWidth = client.font.width(titleText);
        int rightPanelStartX = layout.popupX + 155;
        int maxTitleWidth = (rightPanelStartX - 5) - (x + 12);

        float titleScale = 1.0f;
        if (titleWidth > maxTitleWidth) titleText = Component.literal(client.font.plainSubstrByWidth(titleText.getString(), maxTitleWidth - 6) + "…");

        context.pose().pushPose();
        context.pose().translate((float) (x + 12), (float) (y + 12 + (9 * (1.0f - titleScale)) / 2), 0.0F);
        context.pose().scale(titleScale, titleScale, 1.0F);
        context.drawString(client.font, titleText, 0, 0, isDark ? 0xFFE0E0E0 : 0xFF333333, false);
        context.pose().popPose();

        Component instructionText = Component.literal("Elige un color o escribe HEX").withStyle(ChatFormatting.GRAY);
        int maxTextWidth = 135;
        float scale = 1.0f;

        java.util.List<net.minecraft.util.FormattedCharSequence> wrappedLines =
                client.font.split(instructionText, maxTextWidth);

        // Auto-shrink: if the text wraps beyond 2 lines, reduce scale by 5% and recalculate
        // (a smaller scale means a larger logical wrap width, allowing more text per line).


        context.pose().pushPose();

        float yOffset = (2 - wrappedLines.size()) * (client.font.lineHeight + 2) * scale / 2.0f;
        context.pose().translate((float) (x + 12), (float) (y + 26) + yOffset, 0.0F);
        context.pose().scale(scale, scale, 1.0F);

        int instructY = 0;
        for (net.minecraft.util.FormattedCharSequence line : wrappedLines) {
            context.drawString(client.font, line, 0, instructY, isDark ? 0xFFFFFFFF : 0xFF222222, false);
            instructY += client.font.lineHeight + 2;
        }
        context.pose().popPose();

        int contentY = layout.popupY + 45;
        int contentX = layout.popupX + 12;

        ColorPickerGradients.drawSaturationValueBox(
                context, contentX, contentY, layout.pickerBoxSize, layout.pickerBoxSize, session.pickerHue);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(
                context, contentX - 1, contentY - 1, 102, 102, 0x33FFFFFF);

        int cursorX = contentX + (int) (session.pickerSat * 100);
        int cursorY = contentY + (int) ((1.0f - session.pickerVal) * 100);
        context.renderOutline(cursorX - 2, cursorY - 2, 5, 5, 0xFF000000);
        context.renderOutline(cursorX - 1, cursorY - 1, 3, 3, 0xFFFFFFFF);

        int hueX = contentX + 115;
        ColorPickerGradients.drawHueBar(context, hueX, contentY, layout.pickerHueWidth, layout.pickerBoxSize);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(
                context, hueX - 1, contentY - 1, 22, 102, 0x33FFFFFF);

        int hueCursorY = contentY + (int) (session.pickerHue * 100);
        context.fill(hueX - 3, hueCursorY - 1, hueX + 23, hueCursorY + 2, 0xFF000000);
        context.fill(hueX - 1, hueCursorY, hueX + 21, hueCursorY + 1, 0xFFFFFFFF);

        int rightPanelX = layout.popupX + 155;
        int pSize = 64;
        int previewX = rightPanelX + 6;
        int previewY = layout.popupY + 10;

        if (session.pickerCurrentRGB == 0 && !editor.colorPickerModified) {
            drawCheckerboard(context, previewX, previewY, pSize, pSize, 8);
            drawColorBevel(context, previewX, previewY, pSize, pSize, 0xFF555555, false);
        } else {
            context.fill(previewX, previewY, previewX + pSize, previewY + pSize, 0xFF000000 | session.pickerCurrentRGB);
            drawColorBevel(context, previewX, previewY, pSize, pSize, session.pickerCurrentRGB, false);
        }

        int textX = rightPanelX + 5;
        int fieldY = layout.popupY + 84;

        int dynamicColor = isDark ? 0xFFFFFFFF : 0xFF222222;
        if (session.pickerCurrentRGB != 0 || editor.colorPickerModified) {
            dynamicColor = session.pickerCurrentRGB | 0xFF000000;
        }

        context.drawString(client.font, "#:", textX, fieldY + 1, dynamicColor, false);
        context.drawString(client.font, "R:", textX, fieldY + 21, 0xFFFF5555, false);
        context.drawString(client.font, "G:", textX, fieldY + 37, 0xFF55FF55, false);
        context.drawString(client.font, "B:", textX, fieldY + 53, 0xFF5555FF, false);

        if (hexField != null) hexField.render(context, mouseX, mouseY, delta);
        if (rField != null) rField.render(context, mouseX, mouseY, delta);
        if (gField != null) gField.render(context, mouseX, mouseY, delta);
        if (bField != null) bField.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        // Custom color slot tooltips are drawn last so they layer above the picker window.
        for (int i = 0; i < 8; i++) {
            int slotY = pY + (i * (sS + gap));
            if (editor.isHovering(pCol3X, slotY, sS, sS, mouseX, mouseY)) {
                context.renderTooltip(
                        client.font,
                        Component.translatable("color.chestseparators.custom", (i + 1)),
                        mouseX,
                        mouseY);
            }
        }
    }

    private void renderEyedropperCursor(GuiGraphics context, int mx, int my) {
        // The diagonal dropper's tip sits at (3.47, 12.53) within its 16px footprint; offset the sprite so
        // that tip lands exactly on (mx, my) — the pixel that readHoveredPixelColor(mx, my) samples.
        int iconX = mx - 3;
        int iconY = my - 13;

        int previewX = mx + 18;
        int previewY = my - 24;
        int pw = 16;
        int ph = 16;

        context.fill(previewX, previewY, previewX + pw, previewY + ph, 0xFF000000 | hoveredPixelColor);
        drawColorBevel(context, previewX, previewY, pw, ph, hoveredPixelColor, false);

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context,
                io.github.marcsanzdev.chestseparators.client.ModTextures.ICON_SM_EYEDROPPER,
                iconX,
                iconY,
                0.0F,
                0.0F,
                16,
                16,
                128,
                128,
                128,
                128,
                -1);
    }
}





