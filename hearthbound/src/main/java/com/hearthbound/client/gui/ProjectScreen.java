package com.hearthbound.client.gui;

import com.hearthbound.config.HBClientConfig;
import com.hearthbound.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/** The foundation stone: plot size, required blocks and the button to hand the building in. */
public class ProjectScreen extends Screen {
    private final Ui ui = new Ui();
    private CompoundTag view;
    private final long opened;
    private long lastRefresh;

    public ProjectScreen(CompoundTag view) {
        super(Component.translatable(view.getString("name")));
        this.view = view;
        this.opened = net.minecraft.Util.getMillis();
        this.lastRefresh = opened;
    }

    public boolean samePos(CompoundTag t) {
        return t.getLong("pos") == view.getLong("pos");
    }

    public void update(CompoundTag t) {
        this.view = t;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void act(String a) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", view.getLong("pos"));
        Net.action(a, t);
        if (HBClientConfig.SOUNDS.get()) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
    }

    @Override
    public void tick() {
        // keep the counts fresh while the player builds with the screen closed and reopened
        long now = net.minecraft.Util.getMillis();
        if (now - lastRefresh > 3000) {
            lastRefresh = now;
            act("stone_refresh");
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x90000000, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int w = Math.min(width - 16, 340), h = Math.min(height - 16, 250);
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        ui.begin(mx, my);
        ui.accent = HBClientConfig.accent();
        float anim = HBClientConfig.ANIMATIONS.get() ? Math.min(1f, (net.minecraft.Util.getMillis() - opened) / 180f) : 1f;
        g.pose().pushPose();
        g.pose().translate(0, (1 - anim) * 8, 0);
        ui.panel(g, x0, y0, w, h);
        ui.header(g, x0, y0, w, 38, ui.accent);
        g.pose().pushPose();
        g.pose().translate(x0 + 8, y0 + 5, 0);
        g.pose().scale(1.75f, 1.75f, 1f);
        g.renderItem(VillageScreen.stack(view.getString("icon")), 0, 0);
        g.pose().popPose();
        Component title = Component.translatable("hearthbound.project.level_name", Component.translatable(view.getString("name")), view.getInt("level"));
        ui.scaled(g, title, x0 + 42, y0 + 7, 1.4f, 0xFFFFFFFF, true);
        ui.clipped(g, Component.translatable("hearthbound.project.for_village", view.getString("village"), view.getString("owner")), x0 + 42, y0 + 24, w - 50, Ui.TEXT);

        int x = x0 + 10, y = y0 + 46, iw = w - 20;
        y += ui.wrapped(g, Component.translatable(view.getString("desc")), x, y, iw, Ui.MUTED, 3) + 6;

        // plot size
        boolean mine = view.getBoolean("mine");
        int side = view.getInt("side");
        ui.card(g, x, y, iw, 24, false);
        ui.text(g, Component.translatable("hearthbound.project.plot", side, side, view.getInt("height")), x + 6, y + 8, Ui.TEXT);
        ui.ghost(g, x + iw - 58, y + 5, 24, 14, Component.literal("−"), mine && side > view.getInt("minSide"), () -> act("stone_shrink"));
        ui.ghost(g, x + iw - 30, y + 5, 24, 14, Component.literal("+"), mine && side < view.getInt("maxSide"), () -> act("stone_grow"));
        ui.tooltip(x + iw - 58, y + 5, 52, 14, Component.translatable("hearthbound.project.plot_tip", view.getInt("minSide"), view.getInt("maxSide")));
        y += 30;

        // requirements
        ui.text(g, Component.translatable("hearthbound.project.required"), x, y, 0xFFE0B25A);
        ui.text(g, Component.translatable("hearthbound.project.creative"), x + iw - ui.font().width(Component.translatable("hearthbound.project.creative")), y, Ui.DIM);
        y += 12;
        ListTag reqs = view.getList("reqs", Tag.TAG_COMPOUND);
        for (Tag t : reqs) {
            CompoundTag q = (CompoundTag) t;
            int have = q.getInt("have"), need = q.getInt("need");
            boolean ok = have >= need;
            ItemStack st = VillageScreen.stack(q.getString("icon"));
            ui.card(g, x, y, iw, 20, false);
            g.renderItem(st, x + 2, y + 2);
            Component name = q.getString("block").startsWith("#")
                    ? Component.translatableWithFallback("hearthbound.tag." + q.getString("block").substring(1).replace(':', '.').replace('/', '.'), q.getString("block"))
                    : st.getHoverName();
            ui.clipped(g, name, x + 22, y + 6, iw / 2 - 24, ok ? Ui.TEXT : Ui.MUTED);
            ui.bar(g, x + iw / 2, y + 8, iw / 2 - 52, 5, Math.min(1f, have / (float) Math.max(1, need)), ok ? Ui.GOOD : 0xFFE0B25A);
            String n = Math.min(have, 999) + " / " + need;
            ui.text(g, n, x + iw - 6 - ui.font().width(n), y + 6, ok ? Ui.GOOD : Ui.TEXT);
            y += 22;
        }
        y += 4;
        // rewards and unlocks
        int wx = x;
        Component rep = Component.literal("+" + view.getInt("reputation") + " ").append(Component.translatable("hearthbound.ui.rep_short")).withColor(0x6CD68A);
        ui.text(g, rep, wx, y + 4, Ui.GOOD);
        wx += ui.font().width(rep) + 8;
        wx += ui.coins(g, view.getInt("coins"), wx, y + 4) + 8;
        Component xp = Component.literal(view.getInt("xp") + " XP");
        ui.text(g, xp, wx, y + 4, 0xFF8FD18F);
        wx += ui.font().width(xp) + 10;
        ListTag unlocks = view.getList("unlocks", Tag.TAG_COMPOUND);
        if (!unlocks.isEmpty()) {
            ui.text(g, Component.translatable("hearthbound.project.unlocks_short"), wx, y + 4, 0xFFE0B25A);
            wx += ui.font().width(Component.translatable("hearthbound.project.unlocks_short")) + 3;
            for (Tag t : unlocks) {
                if (wx > x + iw - 16) break;
                ItemStack st = VillageScreen.parse((CompoundTag) t);
                if (st.isEmpty()) continue;
                ui.itemTip(g, st, wx, y);
                wx += 17;
            }
        }
        y += 22;

        // finish
        boolean ready = view.getBoolean("ready");
        int by = y0 + h - 22;
        if (mine) {
            ui.clipped(g, Component.translatable(ready ? "hearthbound.project.ready_hint" : "hearthbound.project.keep_building"), x, by + 3, iw - 110, ready ? Ui.GOOD : Ui.MUTED);
            ui.button(g, x + iw - 104, by, 104, 16, Component.translatable("hearthbound.project.finish"), ready, () -> act("stone_finish"));
        } else {
            ui.clipped(g, Component.translatable("hearthbound.project.not_yours_view", view.getString("owner")), x, by + 3, iw, Ui.MUTED);
        }
        g.pose().popPose();
        ui.drawTooltips(g);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && ui.click(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }
}
