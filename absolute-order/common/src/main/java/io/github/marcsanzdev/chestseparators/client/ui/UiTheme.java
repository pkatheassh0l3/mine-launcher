package io.github.marcsanzdev.chestseparators.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Shared rendering matched to Ascension 1.0.0 Theme and Draw. */
public final class UiTheme {

    private UiTheme() {}

    /**
     * E4 equivalent of the E5 tinted {@code GuiGraphics.blit(RenderPipeline, ...)}. 1.20.1 blit has no
     * pipeline/color parameters, so the ARGB tint is applied via {@link GuiGraphics#setColor} around the
     * scaled blit and reset afterwards. Argument order after {@code atlas} mirrors the old E5 blit (minus
     * the pipeline), so call sites port with a mechanical rename.
     */
    public static void blitTex(
            GuiGraphics c,
            net.minecraft.resources.ResourceLocation atlas,
            int x,
            int y,
            float u,
            float v,
            int w,
            int h,
            int regionW,
            int regionH,
            int texW,
            int texH,
            int argb) {
        if (UiIcons.draw(c, atlas, x, y, w, h, argb)) return;
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        c.setColor(r, g, b, a);
        c.blit(atlas, x, y, w, h, u, v, regionW, regionH, texW, texH);
        c.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // Exact Ascension Theme tokens (installed 1.0.0-votaciones).
    public static final int PANEL_BG = 0xD0121826;
    public static final int PANEL_BORDER = 0x28FFFFFF;
    public static final int PANEL_HILITE = 0x50FFFFFF;
    public static final int BTN_BG = 0xD81A2233;
    public static final int BTN_BG_HOVER = 0xE0222C40;
    public static final int BTN_BORDER = PANEL_BORDER;
    public static final int ACCENT = 0xFFF5C451;
    public static final int ACCENT_BG = 0x37F5C451;
    public static final int ACCENT_BORDER = 0xBEF5C451;
    public static final int ICON = 0xFFEDF1F7;
    public static final int ICON_HOVER = ICON;
    public static final int ICON_ACTIVE = ICON;
    public static final int ON_ACCENT = ICON;
    public static final int TEXT = ICON;
    public static final int TEXT_MUTED = 0xFFA3ADC2;
    public static final int TEXT_DISABLED = 0xFF65708A;
    public static final int ATTACH_TOP = 0, ATTACH_BOTTOM = 1, ATTACH_LEFT = 2, ATTACH_RIGHT = 3;

    private static int inset(int radius, int row) {
        double dy = radius - row - 0.5;
        return (int) Math.round(radius - Math.sqrt(Math.max(0, radius * radius - dy * dy)));
    }

    /** Same scanline geometry and alpha compositing as Ascension Draw.round. */
    private static void rounded(GuiGraphics c, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            int i = inset(r, row);
            c.fill(x + i, y + row, x + w - i, y + row + 1, color);
            c.fill(x + i, y + h - row - 1, x + w - i, y + h - row, color);
        }
    }

    /** Same outline geometry as Ascension Draw.roundOutline, including corner coverage. */
    private static void outline(GuiGraphics c, int x, int y, int w, int h, int radius, int color) {
        if (w <= 1 || h <= 1) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        c.fill(x + r, y, x + w - r, y + 1, color);
        c.fill(x + r, y + h - 1, x + w - r, y + h, color);
        c.fill(x, y + r, x + 1, y + h - r, color);
        c.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            int i = inset(r, row);
            int next = row + 1 < r ? inset(r, row + 1) : 0;
            int edge = Math.max(1, i - next);
            c.fill(x + i - edge + 1, y + row, x + i + 1, y + row + 1, color);
            c.fill(x + w - i - 1, y + row, x + w - i + edge - 1, y + row + 1, color);
            c.fill(x + i - edge + 1, y + h - row - 1, x + i + 1, y + h - row, color);
            c.fill(x + w - i - 1, y + h - row - 1, x + w - i + edge - 1, y + h - row, color);
        }
    }

    public static void roundRect(GuiGraphics c, int x, int y, int w, int h, int color) {
        rounded(c, x, y, w, h, 5, color);
    }
    public static void roundBorder(GuiGraphics c, int x, int y, int w, int h, int color) {
        outline(c, x, y, w, h, 5, color);
    }
    public static void panel(GuiGraphics c, int x, int y, int w, int h) {
        rounded(c, x - 1, y + 1, w + 2, h + 2, 6, 0x33000000);
        roundRect(c, x, y, w, h, PANEL_BG);
        roundBorder(c, x, y, w, h, PANEL_BORDER);
    }
    /** Ascension header tabs: translucent accent pill, no underline or pressed scaling. */
    public static void segmentedTab(GuiGraphics c, int x, int y, int w, int h, boolean hover, boolean active) {
        rounded(c, x, y, w, h, 8, active ? 0x46F5C451 : hover ? 0x0CFFFFFF : 0x00FFFFFF);
        if (active) outline(c, x, y, w, h, 8, 0xAAF5C451);
    }
    public static void tab(GuiGraphics c, int x, int y, int w, int h, boolean hover, boolean active, int attach) {
        segmentedTab(c, x, y, w, h, hover, active);
    }
    /** Ascension cards: six-pixel corners, white outline and left accent marker. */
    public static void card(GuiGraphics c, int x, int y, int w, int h, boolean hover) {
        rounded(c, x, y, w, h, 6, hover ? 0xDF212B3E : BTN_BG);
        outline(c, x, y, w, h, 6, hover ? 0x40FFFFFF : 0x18FFFFFF);
        rounded(c, x + 2, y + 6, 2, h - 12, 1, ACCENT);
    }
    public static void inset(GuiGraphics c, int x, int y, int w, int h) {
        roundRect(c, x, y, w, h, 0x40000000);
        roundBorder(c, x, y, w, h, PANEL_BORDER);
    }
    public static void scrollbar(GuiGraphics c, int x, int y, int w, int h, int thumbY, int thumbH) {
        rounded(c, x, y, w, h, w / 2, 0x40000000);
        rounded(c, x, thumbY, w, thumbH, w / 2, 0x50FFFFFF);
    }
    /** Kept balanced for existing callers; Ascension does not shrink selected controls. */
    public static void pushActiveContent(GuiGraphics c, int x, int y, int w, int h) {
        c.pose().pushPose();
    }
    /** Ascension filter/action chips. */
    public static void button(GuiGraphics c, int x, int y, int w, int h, boolean hover, boolean active) {
        int radius = Math.min(9, h / 2);
        rounded(c, x, y, w, h, radius, active ? (hover ? 0x50F5C451 : ACCENT_BG) : hover ? BTN_BG_HOVER : PANEL_BG);
        outline(c, x, y, w, h, radius, active ? ACCENT_BORDER : PANEL_BORDER);
    }
    public static void centered(GuiGraphics c, net.minecraft.client.gui.Font font,
                                net.minecraft.network.chat.Component text, int x, int y, int color) {
        c.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }
    public static void centered(GuiGraphics c, net.minecraft.client.gui.Font font,
                                String text, int x, int y, int color) {
        c.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }
    public static void centered(GuiGraphics c, net.minecraft.client.gui.Font font,
                                net.minecraft.util.FormattedCharSequence text, int x, int y, int color) {
        c.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }
}


