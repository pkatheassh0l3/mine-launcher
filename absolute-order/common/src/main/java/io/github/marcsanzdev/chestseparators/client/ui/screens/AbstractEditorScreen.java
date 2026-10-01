package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorGeometry;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

public abstract class AbstractEditorScreen implements IEditorSubScreen {

    protected final ChestSeparatorsEditor editor;
    protected final EditorSessionData session;
    protected final EditorGeometry geometry;
    protected final EditorLayout layout;

    protected final List<CustomWidget> widgets = new ArrayList<>();

    public AbstractEditorScreen(ChestSeparatorsEditor editor) {
        this.editor = editor;
        this.session = editor.session;
        this.geometry = editor.geometry;
        this.layout = editor.layout;
    }

    @Override
    public void init() {
        widgets.clear();
        buildWidgets();
    }

    protected abstract void buildWidgets();

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        for (CustomWidget widget : widgets) {
            widget.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickWidgets(mouseX, mouseY, button);
    }

    /** Dispatches a click to this screen's widgets; returns true once one consumes it. */
    boolean clickWidgets(double mouseX, double mouseY, int button) {
        for (CustomWidget widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    // Default no-op implementations so sub-screens only override what they need.
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    protected void drawCheckerboard(GuiGraphics context, int x, int y, int width, int height, int squareSize) {
        for (int i = 0; i < width; i += squareSize) {
            for (int j = 0; j < height; j += squareSize) {
                int drawW = Math.min(squareSize, width - i);
                int drawH = Math.min(squareSize, height - j);
                int color = (((i / squareSize) + (j / squareSize)) % 2 == 0) ? 0xFF888888 : 0xFF555555;
                context.fill(x + i, y + j, x + i + drawW, y + j + drawH, color);
            }
        }
    }

    /** Draws a raised or sunken bevel border using explicit light/dark/shadow colors. */
    protected void drawBaseBevel(
            GuiGraphics context, int x, int y, int width, int height, int light, int dark, int shadow, boolean sunken) {
        if (sunken) {
            context.fill(x, y, x + width - 1, y + 1, dark);
            context.fill(x, y, x + 1, y + height - 1, dark);
            context.fill(x + width - 1, y, x + width, y + height, light);
            context.fill(x, y + height - 1, x + width, y + height, light);
        } else {
            context.fill(x, y, x + width - 1, y + 1, light);
            context.fill(x, y, x + 1, y + height - 1, light);
            context.fill(x + width - 1, y, x + width, y + height, dark);
            context.fill(x, y + height - 1, x + width, y + height, dark);
            context.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, shadow);
            context.fill(x + 1, y + height - 2, x + width - 2, y + height - 1, shadow);
        }
    }

    /** Draws a bevel whose light/dark/shadow tones are derived from a base color. */
    protected void drawColorBevel(
            GuiGraphics context, int x, int y, int width, int height, int baseColor, boolean sunken) {
        int light = editor.shiftColor(baseColor, 80) | 0xFF000000;
        int dark = editor.shiftColor(baseColor, -80) | 0xFF000000;
        int shadow = editor.shiftColor(baseColor, -40) | 0xFF000000;
        drawBaseBevel(context, x, y, width, height, light, dark, shadow, sunken);
    }

    /** Draws a bevel with the fixed vanilla-inventory tones used for standard palette swatches. */
    protected void drawStandardBevel(GuiGraphics context, int x, int y, int width, int height, boolean sunken) {
        drawBaseBevel(context, x, y, width, height, 0xFFFFFFFF, 0xFF373737, 0xFF8B8B8B, sunken);
    }
}
