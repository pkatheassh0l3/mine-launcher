package io.github.marcsanzdev.chestseparators.client.ui.screens;

import java.awt.Color;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

/**
 * Stateless drawing helpers for the color picker: the saturation/value gradient box, the hue
 * strip, and the framebuffer pixel read used by the eyedropper tool.
 */
final class ColorPickerGradients {

    private ColorPickerGradients() {}

    /** Fills a saturation (x) by value (y) gradient for a fixed hue. */
    static void drawSaturationValueBox(GuiGraphics context, int x, int y, int w, int h, float hue) {
        int step = 2;
        for (int i = 0; i < w; i += step) {
            for (int j = 0; j < h; j += step) {
                float sat = (float) i / w;
                float val = 1.0f - ((float) j / h);
                int color = Color.HSBtoRGB(hue, sat, val);
                context.fill(x + i, y + j, x + i + step, y + j + step, color);
            }
        }
    }

    /** Fills a vertical hue strip at full saturation and value. */
    static void drawHueBar(GuiGraphics context, int x, int y, int w, int h) {
        for (int i = 0; i < h; i++) {
            float hue = (float) i / h;
            int color = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            context.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

    /** Reads the RGB color of the framebuffer pixel under the cursor (for the eyedropper). */
    static int readHoveredPixelColor(int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        double scale = client.getWindow().getGuiScale();
        int fbX = (int) (mouseX * scale);
        int fbY = client.getWindow().getHeight() - (int) (mouseY * scale) - 1;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buffer = stack.malloc(4);
            GL11.glReadPixels(fbX, fbY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            int r = buffer.get(0) & 0xFF;
            int g = buffer.get(1) & 0xFF;
            int b = buffer.get(2) & 0xFF;
            return (r << 16) | (g << 8) | b;
        }
    }
}
