package com.hearthbound.client.gui;

import com.hearthbound.client.ClientData;
import com.hearthbound.config.HBClientConfig;
import com.hearthbound.network.Net;
import com.hearthbound.rpg.PlayerClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Pick a class once: a free skill rank and a passive talent. */
public class ClassScreen extends Screen {
    private final Ui ui = new Ui();
    private final Screen next;
    private PlayerClass selected;

    public ClassScreen(Screen next) {
        super(Component.translatable("hearthbound.class.choose"));
        this.next = next;
        this.selected = ClientData.DATA.clazz;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xA0000000, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        ui.begin(mx, my);
        ui.accent = selected == null ? HBClientConfig.accent() : 0xFF000000 | selected.color;
        int w = Math.min(width - 16, 420), h = Math.min(height - 16, 250);
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        ui.panel(g, x0, y0, w, h);
        ui.header(g, x0, y0, w, 30, ui.accent);
        ui.scaled(g, title, x0 + 10, y0 + 9, 1.4f, 0xFFFFFFFF, true);
        ui.clipped(g, Component.translatable("hearthbound.class.choose_sub"), x0 + 10, y0 + 36, w - 20, Ui.MUTED);

        PlayerClass[] all = PlayerClass.values();
        int cols = 3;
        int gap = 6;
        int cw = (w - 20 - gap * (cols - 1)) / cols;
        int chh = 58;
        int gy = y0 + 50;
        boolean locked = ClientData.DATA.clazz != null && !ClientData.classChange;
        for (int i = 0; i < all.length; i++) {
            PlayerClass c = all[i];
            int x = x0 + 10 + (i % cols) * (cw + gap);
            int y = gy + (i / cols) * (chh + gap);
            boolean sel = c == selected;
            boolean hov = ui.hover(x, y, cw, chh);
            ui.card(g, x, y, cw, chh, hov);
            int col = 0xFF000000 | c.color;
            if (sel) ui.outline(g, x, y, cw, chh, col);
            g.fill(x + 1, y + 1, x + cw - 1, y + 3, sel ? col : Ui.alpha(col, 0.45f));
            g.renderItem(new ItemStack(c.icon), x + 5, y + 7);
            ui.text(g, c.title(), x + 25, y + 8, sel ? col : Ui.TEXT);
            ui.clipped(g, c.bonusSkill.title().copy().append(" +1"), x + 25, y + 18, cw - 28, Ui.DIM);
            ui.wrapped(g, c.perk(), x + 5, y + 30, cw - 10, Ui.MUTED, 3);
            ui.tooltip(x, y, cw, chh, java.util.List.of(c.title().copy().withColor(c.color), c.description(), Component.empty(), c.perk().copy().withColor(0xE0B25A)));
            if (!locked) ui.region(x, y, cw, chh, () -> selected = c);
        }
        int by = y0 + h - 22;
        ui.ghost(g, x0 + 10, by, 90, 14, Component.translatable(ClientData.DATA.clazz == null ? "hearthbound.class.later" : "gui.back"), true, this::onClose);
        if (!locked) {
            Component label = selected == null ? Component.translatable("hearthbound.class.pick_one") : Component.translatable("hearthbound.class.confirm", selected.title());
            ui.button(g, x0 + w - 150, by, 140, 14, label, selected != null && selected != ClientData.DATA.clazz, () -> {
                CompoundTag t = new CompoundTag();
                t.putString("id", selected.id());
                Net.action("class", t);
                ClientData.DATA.clazz = selected;
                ClientData.DATA.introSeen = true;
                Minecraft.getInstance().setScreen(next);
            });
        }
        ui.drawTooltips(g);
    }

    @Override
    public void onClose() {
        if (ClientData.DATA.clazz == null && !ClientData.DATA.introSeen) {
            Net.action("intro_seen", new CompoundTag());
            ClientData.DATA.introSeen = true;
        }
        Minecraft.getInstance().setScreen(next);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0 && ui.click(mx, my)) return true;
        return super.mouseClicked(mx, my, b);
    }
}
