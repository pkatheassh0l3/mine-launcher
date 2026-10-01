package io.github.marcsanzdev.chestseparators.client.ui;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/**
 * Cursor zoom loupe. It samples a small block of the real framebuffer under the cursor and draws it
 * magnified, so the loupe shows exactly what is on screen — item counts, previews, separators,
 * everything. The sampled square is kept strictly smaller than the cursor ring, so the ring and the
 * connector lines are never inside the captured area (and therefore never appear magnified inside the
 * loupe). The loupe edge, the cursor ring and the connector lines are all drawn with per-pixel
 * coverage antialiasing for clean, non-jagged shapes.
 */
public final class MagnifierRenderer {

    private MagnifierRenderer() {}

    // Captured half-size: 14 logical px → a 28px window, enough to show a whole 16px slot with margin
    // even inside the circle (where only the inscribed disc is visible). Loupe radius sets on-screen
    // size; both are kept small enough that reads/draws stay cheap.
    private static final int BASE_SRC_HALF = 14;
    private static final int BASE_LOUPE_R = 66;
    private static final int BASE_GAP = 14;
    private static final int BASE_RING = 4;
    private static final int FRAME_COLOR = 0xFF202020;

    public static void render(GuiGraphics context, int cursorX, int cursorY, boolean circle) {
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null) return;

        int srcHalf = BASE_SRC_HALF;
        int loupeR = BASE_LOUPE_R;
        int gap = BASE_GAP;
        int ring = BASE_RING;

        double scale = client.getWindow().getGuiScale();
        int fbW = client.getWindow().getWidth();
        int fbH = client.getWindow().getHeight();

        int blockFb = Math.max(1, (int) Math.round(srcHalf * 2 * scale));
        int fbX = clamp((int) Math.round((cursorX - srcHalf) * scale), 0, fbW - blockFb);
        int fbYBottom = clamp(fbH - (int) Math.round((cursorY + srcHalf) * scale), 0, fbH - blockFb);

        int[] pixels = new int[blockFb * blockFb];
        // Heap-allocated native buffer: a stack buffer (MemoryStack) overflows for large loupe sizes.
        ByteBuffer buf = MemoryUtil.memAlloc(blockFb * blockFb * 4);
        try {
            GL11.glReadPixels(fbX, fbYBottom, blockFb, blockFb, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            for (int i = 0; i < pixels.length; i++) {
                int r = buf.get(i * 4) & 0xFF;
                int g = buf.get(i * 4 + 1) & 0xFF;
                int b = buf.get(i * 4 + 2) & 0xFF;
                pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        } finally {
            MemoryUtil.memFree(buf);
        }

        // Place the loupe up-right of the cursor, flipping to stay on screen.
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        int loupeCx = cursorX + gap + loupeR;
        int loupeCy = cursorY - gap - loupeR;
        if (loupeCx + loupeR + 2 > sw) loupeCx = cursorX - gap - loupeR;
        if (loupeCy - loupeR - 2 < 0) loupeCy = cursorY + gap + loupeR;
        loupeCy = clamp(loupeCy, loupeR + 2, sh - loupeR - 2);

        float cell = (loupeR * 2f) / blockFb;
        // Content extent: a hard-clipped disc/square, with the jagged edge hidden under the thick frame.
        int contentR = loupeR - ring;
        long contentR2 = (long) contentR * contentR;

        for (int py = 0; py < blockFb; py++) {
            for (int px = 0; px < blockFb; px++) {
                int x0 = Math.round(loupeCx - loupeR + px * cell);
                int x1 = Math.round(loupeCx - loupeR + (px + 1) * cell);
                // py = 0 is the bottom row in GL space → bottom of the region on screen.
                int y0 = Math.round(loupeCy + loupeR - (py + 1) * cell);
                int y1 = Math.round(loupeCy + loupeR - py * cell);
                double dx = (x0 + x1) / 2.0 - loupeCx;
                double dy = (y0 + y1) / 2.0 - loupeCy;
                if (circle) {
                    if (dx * dx + dy * dy > contentR2) continue; // hard clip → no bleed outside the disc
                } else if (Math.abs(dx) > contentR || Math.abs(dy) > contentR) {
                    continue;
                }
                context.fill(x0, y0, x1, y1, pixels[py * blockFb + px]);
            }
        }

        // Opaque frame of thickness `ring`, hiding the content edge (consistent for circle and square).
        if (circle) drawRingFrame(context, loupeCx, loupeCy, contentR, loupeR, FRAME_COLOR);
        else drawSquareFrame(context, loupeCx, loupeCy, contentR, loupeR, FRAME_COLOR);

        // Reticle on the captured (center) pixel.
        int half = Math.max(1, Math.round(cell / 2f));
        context.renderOutline(loupeCx - half, loupeCy - half, half * 2, half * 2, 0xFFFFFFFF);

        // No cursor marker or connector lines: under the deferred GUI rendering they would be sampled
        // back into the loupe (ghosted/smeared), so the loupe shows only real chest content.
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    /** Antialiased 1px circle outline using per-pixel distance coverage, iterating only near the edge. */
    private static void drawCircleOutlineAA(GuiGraphics context, int cx, int cy, int r, int color) {
        int rgb = color & 0x00FFFFFF;
        int baseA = (color >>> 24) & 0xFF;
        for (int y = -r - 1; y <= r + 1; y++) {
            double inside = (double) r * r - (double) y * y;
            int edge = inside <= 0 ? 0 : (int) Math.round(Math.sqrt(inside));
            for (int side = -1; side <= 1; side += 2) {
                for (int x = edge * side - 2; x <= edge * side + 2; x++) {
                    double cov = 1.0 - Math.abs(Math.sqrt((double) x * x + (double) y * y) - r);
                    if (cov <= 0) continue;
                    int a = (int) (baseA * Math.min(1.0, cov));
                    if (a <= 0) continue;
                    context.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, (a << 24) | rgb);
                }
                if (edge == 0) break;
            }
        }
    }

    /** Opaque ring frame: filled annulus with antialiased inner and outer edges. */
    private static void drawRingFrame(GuiGraphics context, int cx, int cy, int rInner, int rOuter, int color) {
        int rgb = color & 0x00FFFFFF;
        for (int y = -rOuter; y <= rOuter; y++) {
            double outer = (double) rOuter * rOuter - (double) y * y;
            if (outer < 0) continue;
            int xo = (int) Math.floor(Math.sqrt(outer));
            double innerSq = (double) rInner * rInner - (double) y * y;
            int xi = innerSq <= 0 ? -1 : (int) Math.ceil(Math.sqrt(innerSq));
            if (xi < 0 || xi > xo) {
                context.fill(cx - xo, cy + y, cx + xo + 1, cy + y + 1, color);
            } else {
                context.fill(cx - xo, cy + y, cx - xi + 1, cy + y + 1, color);
                context.fill(cx + xi, cy + y, cx + xo + 1, cy + y + 1, color);
            }
        }
        // Antialias the inner and outer edges so the frame reads as a clean circle.
        drawCircleOutlineAA(context, cx, cy, rOuter, (0xCC << 24) | rgb);
        drawCircleOutlineAA(context, cx, cy, rInner, (0xCC << 24) | rgb);
    }

    /** Opaque square frame: a thick border between the inner and outer half-sizes. */
    private static void drawSquareFrame(GuiGraphics context, int cx, int cy, int rInner, int rOuter, int color) {
        // top, bottom, left, right bars of thickness (rOuter - rInner)
        context.fill(cx - rOuter, cy - rOuter, cx + rOuter, cy - rInner, color);
        context.fill(cx - rOuter, cy + rInner, cx + rOuter, cy + rOuter, color);
        context.fill(cx - rOuter, cy - rInner, cx - rInner, cy + rInner, color);
        context.fill(cx + rInner, cy - rInner, cx + rOuter, cy + rInner, color);
    }
}
