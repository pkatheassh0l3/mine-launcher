package com.hearthbound.client.gui;

import com.hearthbound.config.HBClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Immediate-mode drawing kit shared by every Hearthbound screen: dark glass panels, accent
 * gradients, buttons that register click regions while drawing, bars, chips and tooltips.
 * Everything is drawn with fills, no textures, so it scales cleanly with any GUI scale.
 */
public final class Ui {
    // palette
    public static final int BG = 0xE0101218;
    public static final int PANEL = 0xF0171A22;
    public static final int CARD = 0xFF1F232D;
    public static final int CARD_HOVER = 0xFF282D3A;
    public static final int LINE = 0xFF2E3442;
    public static final int TEXT = 0xFFE8E6E1;
    public static final int MUTED = 0xFF9AA0AC;
    public static final int DIM = 0xFF6B7180;
    public static final int GOOD = 0xFF6CD68A;
    public static final int BAD = 0xFFE06A5A;
    public static final int GOLD = 0xFFFFD24A;

    /** Click regions registered during the last frame. */
    public record Hit(int x, int y, int w, int h, Runnable action, List<Component> tooltip) {
        boolean inside(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> tips = new ArrayList<>();
    public int mouseX, mouseY;
    public int accent = 0xFFE0B25A;

    public Font font() {
        return Minecraft.getInstance().font;
    }

    public void begin(int mx, int my) {
        hits.clear();
        tips.clear();
        mouseX = mx;
        mouseY = my;
    }

    public boolean click(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.action != null && h.inside(mx, my)) {
                h.action.run();
                return true;
            }
        }
        return false;
    }

    public boolean hover(int x, int y, int w, int h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    public void tooltip(int x, int y, int w, int h, List<Component> lines) {
        if (lines != null && !lines.isEmpty()) tips.add(new Hit(x, y, w, h, null, lines));
    }

    public void tooltip(int x, int y, int w, int h, Component line) {
        if (line != null) tooltip(x, y, w, h, List.of(line));
    }

    public void region(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action, null));
    }

    /** Draws the tooltip of the hovered region; call last. */
    public void drawTooltips(GuiGraphics g) {
        for (int i = tips.size() - 1; i >= 0; i--) {
            Hit h = tips.get(i);
            if (h.inside(mouseX, mouseY)) {
                List<FormattedCharSequence> seq = new ArrayList<>();
                for (Component c : h.tooltip) seq.addAll(font().split(c, 220));
                g.renderTooltip(font(), seq, mouseX, mouseY);
                return;
            }
        }
    }

    // ================================================================== colors

    public static int alpha(int color, float a) {
        int al = Mth.clamp((int) (((color >>> 24) & 0xFF) * a), 0, 255);
        return (al << 24) | (color & 0xFFFFFF);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24);
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24);
        int r = (int) (ar + (br - ar) * t), gg = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t), al = (int) (aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (gg << 8) | bl;
    }

    public static int darken(int c, float f) {
        return mix(c, 0xFF000000, f);
    }

    public static int lighten(int c, float f) {
        return mix(c, 0xFFFFFFFF, f);
    }

    // ================================================================== shapes

    /** Rounded-looking rectangle (corners cut by one pixel). */
    public void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public void panel(GuiGraphics g, int x, int y, int w, int h) {
        float op = (float) (double) HBClientConfig.PANEL_OPACITY.get();
        box(g, x - 1, y - 1, w + 2, h + 2, alpha(0xFF000000, 0.5f * op));
        box(g, x, y, w, h, alpha(PANEL, op));
        outline(g, x, y, w, h, alpha(lighten(accent, 0.1f), 0.35f));
    }

    public void card(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        box(g, x, y, w, h, hovered ? CARD_HOVER : CARD);
        g.fill(x + 1, y, x + w - 1, y + 1, alpha(0xFFFFFFFF, 0.05f));
    }

    public void header(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fillGradient(x + 1, y, x + w - 1, y + h, alpha(darken(color, 0.25f), 0.95f), alpha(darken(color, 0.75f), 0.95f));
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, alpha(color, 0.9f));
    }

    public void bar(GuiGraphics g, int x, int y, int w, int h, float progress, int color) {
        progress = Mth.clamp(progress, 0f, 1f);
        box(g, x, y, w, h, 0xFF0B0D12);
        int fw = Math.round((w - 2) * progress);
        if (fw > 0) {
            g.fillGradient(x + 1, y + 1, x + 1 + fw, y + h - 1, lighten(color, 0.2f), darken(color, 0.15f));
        }
    }

    // ================================================================== text

    public void text(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font(), c, x, y, color, false);
    }

    public void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font(), s, x, y, color, false);
    }

    public void shadow(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font(), c, x, y, color, true);
    }

    public void centered(GuiGraphics g, Component c, int cx, int y, int color) {
        g.drawString(font(), c, cx - font().width(c) / 2, y, color, false);
    }

    public void scaled(GuiGraphics g, Component c, int x, int y, float scale, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font(), c, 0, 0, color, shadow);
        g.pose().popPose();
    }

    /** Text clipped with an ellipsis to {@code maxW}. */
    public void clipped(GuiGraphics g, Component c, int x, int y, int maxW, int color) {
        if (font().width(c) <= maxW) {
            text(g, c, x, y, color);
            return;
        }
        FormattedText cut = font().substrByWidth(c, maxW - font().width("…"));
        g.drawString(font(), net.minecraft.locale.Language.getInstance().getVisualOrder(FormattedText.composite(cut, Component.literal("…"))), x, y, color, false);
    }

    /** Word-wrapped paragraph; returns the height used. */
    public int wrapped(GuiGraphics g, Component c, int x, int y, int w, int color, int maxLines) {
        List<FormattedCharSequence> lines = font().split(c, w);
        int n = Math.min(lines.size(), maxLines);
        for (int i = 0; i < n; i++) g.drawString(font(), lines.get(i), x, y + i * 10, color, false);
        return n * 10;
    }

    // ================================================================== widgets

    /** A flat button. Returns true if hovered. */
    public boolean button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, Runnable action) {
        boolean hov = enabled && hover(x, y, w, h);
        int base = enabled ? (hov ? lighten(accent, 0.15f) : accent) : 0xFF3A3F4B;
        g.fillGradient(x + 1, y, x + w - 1, y + h, base, darken(base, 0.25f));
        g.fill(x, y + 1, x + 1, y + h - 1, darken(base, 0.2f));
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, darken(base, 0.2f));
        g.fill(x + 1, y, x + w - 1, y + 1, alpha(0xFFFFFFFF, enabled ? 0.25f : 0.08f));
        int tc = enabled ? 0xFF15120C : 0xFF8A8F99;
        int tw = font().width(label);
        if (tw > w - 6) {
            clipped(g, label, x + 3, y + (h - 8) / 2, w - 6, tc);
        } else {
            g.drawString(font(), label, x + (w - tw) / 2, y + (h - 8) / 2, tc, false);
        }
        if (enabled && action != null) region(x, y, w, h, action);
        return hov;
    }

    /** A secondary (outlined) button. */
    public boolean ghost(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, Runnable action) {
        boolean hov = enabled && hover(x, y, w, h);
        box(g, x, y, w, h, hov ? alpha(accent, 0.25f) : 0x40FFFFFF & 0x20FFFFFF);
        outline(g, x, y, w, h, enabled ? alpha(accent, hov ? 1f : 0.6f) : 0xFF3A3F4B);
        int tw = font().width(label);
        g.drawString(font(), label, x + (w - tw) / 2, y + (h - 8) / 2, enabled ? TEXT : DIM, false);
        if (enabled && action != null) region(x, y, w, h, action);
        return hov;
    }

    /** Tab strip item. */
    public void tab(GuiGraphics g, int x, int y, int w, int h, Component label, boolean active, Runnable action) {
        boolean hov = hover(x, y, w, h);
        if (active) {
            g.fillGradient(x, y, x + w, y + h, alpha(accent, 0.35f), alpha(accent, 0.08f));
            g.fill(x, y + h - 2, x + w, y + h, accent);
        } else if (hov) {
            g.fill(x, y, x + w, y + h, 0x18FFFFFF);
        }
        int tw = font().width(label);
        g.drawString(font(), label, x + (w - tw) / 2, y + (h - 8) / 2, active ? TEXT : (hov ? TEXT : MUTED), false);
        region(x, y, w, h, action);
    }

    /** Small colored label with a background. Returns its width. */
    public int chip(GuiGraphics g, Component label, int x, int y, int color) {
        int w = font().width(label) + 8;
        box(g, x, y, w, 12, alpha(color, 0.22f));
        outline(g, x, y, w, 12, alpha(color, 0.7f));
        g.drawString(font(), label, x + 4, y + 2, lighten(color, 0.3f), false);
        return w;
    }

    public void item(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font(), stack, x, y);
    }

    public void itemTip(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
        if (stack.getCount() > 1) g.renderItemDecorations(font(), stack, x, y);
        if (hover(x, y, 16, 16)) {
            List<Component> lines = new ArrayList<>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            tooltip(x, y, 16, 16, lines);
        }
    }

    /** Coin amount "1g 2s 5c" with colored parts. Returns the width. */
    public int coins(GuiGraphics g, int copper, int x, int y) {
        int gold = copper / 100, silver = (copper % 100) / 10, cop = copper % 10;
        int cx = x;
        if (gold > 0) {
            String s = gold + "g";
            text(g, s, cx, y, 0xFFF2C94C);
            cx += font().width(s) + 3;
        }
        if (silver > 0 || gold > 0) {
            String s = silver + "s";
            text(g, s, cx, y, 0xFFC9D1D9);
            cx += font().width(s) + 3;
        }
        String s = cop + "c";
        text(g, s, cx, y, 0xFFD08A5A);
        cx += font().width(s);
        return cx - x;
    }

    public int coinsWidth(int copper) {
        int gold = copper / 100, silver = (copper % 100) / 10, cop = copper % 10;
        int w = 0;
        if (gold > 0) w += font().width(gold + "g") + 3;
        if (silver > 0 || gold > 0) w += font().width(silver + "s") + 3;
        w += font().width(cop + "c");
        return w;
    }

    /** Scroll helper: clamps a scroll offset. */
    public static int clampScroll(int scroll, int content, int view) {
        return Mth.clamp(scroll, 0, Math.max(0, content - view));
    }

    /** Draws a thin scrollbar. */
    public void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int content) {
        if (content <= h) return;
        g.fill(x, y, x + 2, y + h, 0x30FFFFFF);
        int th = Math.max(12, h * h / content);
        int ty = y + (h - th) * scroll / Math.max(1, content - h);
        g.fill(x, ty, x + 2, ty + th, alpha(accent, 0.8f));
    }
}
