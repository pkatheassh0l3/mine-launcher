package com.hearthbound.client.gui;

import com.hearthbound.client.ClientData;
import com.hearthbound.config.HBClientConfig;
import com.hearthbound.data.ContractTemplate;
import com.hearthbound.network.Net;
import com.hearthbound.rpg.Contract;
import com.hearthbound.rpg.PlayerClass;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rank;
import com.hearthbound.rpg.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** Character sheet (default key J): level, class, skills, contracts and known villages. */
public class CharacterScreen extends Screen {
    private final Ui ui = new Ui();
    private String tab = "character";
    private int scroll;
    private int contentH;
    private int x0, y0, w, h, cy, ch;

    public CharacterScreen() {
        super(Component.translatable("hearthbound.character.title"));
        Net.action("sync", new CompoundTag());
    }

    public void onSync() {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x90000000, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        ui.begin(mx, my);
        PlayerData d = ClientData.DATA;
        ui.accent = d.clazz == null ? HBClientConfig.accent() : 0xFF000000 | d.clazz.color;
        w = Math.min(width - 16, 440);
        h = Math.min(height - 16, 280);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        ui.panel(g, x0, y0, w, h);
        ui.header(g, x0, y0, w, 46, ui.accent);
        header(g, d);
        int ty = y0 + 46;
        String[] tabs = {"character", "contracts", "villages"};
        int tw = w / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            String t = tabs[i];
            ui.tab(g, x0 + i * tw, ty, i == tabs.length - 1 ? w - i * tw : tw, 16,
                    Component.translatable("hearthbound.ctab." + t), t.equals(tab), () -> {
                        tab = t;
                        scroll = 0;
                    });
        }
        g.fill(x0 + 1, ty + 16, x0 + w - 1, ty + 17, Ui.LINE);
        cy = ty + 22;
        ch = y0 + h - 6 - cy;
        g.enableScissor(x0 + 4, cy - 2, x0 + w - 4, cy + ch);
        int y = cy - scroll;
        contentH = switch (tab) {
            case "contracts" -> contracts(g, d, y);
            case "villages" -> villages(g, d, y);
            default -> character(g, d, y);
        };
        g.disableScissor();
        ui.scrollbar(g, x0 + w - 4, cy, ch, scroll, contentH);
        ui.drawTooltips(g);
    }

    private void header(GuiGraphics g, PlayerData d) {
        Minecraft mc = Minecraft.getInstance();
        String name = mc.player == null ? "" : mc.player.getGameProfile().getName();
        int lvlW = 40;
        ui.box(g, x0 + 8, y0 + 7, lvlW, 32, 0x60000000);
        ui.centered(g, Component.translatable("hearthbound.character.level"), x0 + 8 + lvlW / 2, y0 + 10, Ui.MUTED);
        g.pose().pushPose();
        String lv = String.valueOf(d.level);
        float sc = 1.6f;
        g.pose().translate(x0 + 8 + lvlW / 2f - mc.font.width(lv) * sc / 2f, y0 + 22, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(mc.font, lv, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        int tx = x0 + 56;
        ui.scaled(g, Component.literal(name), tx, y0 + 7, 1.3f, 0xFFFFFFFF, true);
        Component cls = d.clazz == null ? Component.translatable("hearthbound.character.no_class") : d.clazz.title();
        int cw = ui.chip(g, cls, tx, y0 + 21, d.clazz == null ? 0xFF9AA0AC : 0xFF000000 | d.clazz.color);
        if (d.clazz == null || ClientData.classChange) {
            ui.ghost(g, tx + cw + 4, y0 + 21, 70, 12, Component.translatable(d.clazz == null ? "hearthbound.character.pick_class" : "hearthbound.character.change_class"), true,
                    () -> Minecraft.getInstance().setScreen(new ClassScreen(this)));
        }
        boolean capped = d.level >= ClientData.cap;
        int barW = w - 64 - 130;
        ui.bar(g, tx, y0 + 37, barW, 5, capped ? 1f : d.xp / (float) ClientData.next, capped ? Ui.GOLD : 0xFF9FE06A);
        ui.tooltip(tx, y0 + 34, barW, 10, capped ? Component.translatable("hearthbound.character.capped", ClientData.cap)
                : Component.translatable("hearthbound.character.xp", d.xp, ClientData.next));
        // right: points and coins earned
        int rx = x0 + w - 124;
        ui.text(g, Component.translatable("hearthbound.character.points", d.skillPoints), rx, y0 + 9, d.skillPoints > 0 ? Ui.GOLD : Ui.MUTED);
        ui.text(g, Component.translatable("hearthbound.character.cap", ClientData.cap), rx, y0 + 21, Ui.MUTED);
        if (capped && ClientData.nextAgeName != null) {
            ui.clipped(g, Component.translatable("hearthbound.character.next_age", ClientData.nextAgeName), rx, y0 + 33, 118, 0xFFE0B25A);
        }
    }

    // ================================================================== character

    private int character(GuiGraphics g, PlayerData d, int y) {
        int start = y;
        int lw = (w - 24) * 3 / 5;
        int lx = x0 + 8, rx = lx + lw + 8, rw = w - 24 - lw;
        ui.text(g, Component.translatable("hearthbound.character.skills"), lx, y, 0xFFE0B25A);
        if (ClientData.skillsLocked && ClientData.skillsAge != null) {
            Component l = Component.translatable("hearthbound.gate.age", ClientData.skillsAge);
            ui.text(g, l, lx + lw - ui.font().width(l), y, Ui.BAD);
        }
        int yy = y + 12;
        for (Skill s : Skill.values()) {
            int rank = d.skill(s);
            boolean hov = ui.hover(lx, yy, lw, 30);
            ui.card(g, lx, yy, lw, 30, hov);
            g.fill(lx + 1, yy + 1, lx + 3, yy + 29, 0xFF000000 | s.color);
            g.renderItem(new ItemStack(s.icon), lx + 6, yy + 7);
            ui.text(g, s.title(), lx + 26, yy + 4, Ui.TEXT);
            // pips
            int px = lx + 26;
            for (int i = 0; i < ClientData.maxRank; i++) {
                int col = i < rank ? 0xFF000000 | s.color : 0xFF2E3442;
                g.fill(px, yy + 16, px + 8, yy + 20, col);
                px += 10;
            }
            ui.clipped(g, s.description(), px + 4, yy + 15, lx + lw - px - 32, Ui.DIM);
            boolean can = d.skillPoints > 0 && rank < ClientData.maxRank && !ClientData.skillsLocked;
            ui.button(g, lx + lw - 22, yy + 8, 16, 14, Component.literal("+"), can, () -> {
                CompoundTag t = new CompoundTag();
                t.putString("id", s.id());
                Net.action("skill", t);
            });
            ui.tooltip(lx, yy, lw - 24, 30, List.of(s.title().copy().withColor(s.color), s.description(),
                    Component.translatable("hearthbound.character.rank", rank, ClientData.maxRank).withColor(0x9AA0AC)));
            yy += 33;
        }
        int leftEnd = yy;
        // right column: class + stats
        yy = y;
        if (d.clazz != null) {
            PlayerClass c = d.clazz;
            ui.card(g, rx, yy, rw, 60, false);
            g.renderItem(new ItemStack(c.icon), rx + 5, yy + 5);
            ui.text(g, c.title(), rx + 25, yy + 9, 0xFF000000 | c.color);
            ui.wrapped(g, c.perk(), rx + 5, yy + 25, rw - 10, Ui.MUTED, 3);
            yy += 66;
        }
        ui.text(g, Component.translatable("hearthbound.character.stats"), rx, yy, 0xFFE0B25A);
        yy += 12;
        yy = stat(g, rx, yy, rw, "contracts_done", d.contractsDone, Items.WRITABLE_BOOK);
        yy = stat(g, rx, yy, rw, "villages_known", d.discovered.size(), Items.FILLED_MAP);
        yy = stat(g, rx, yy, rw, "coins_earned", d.coinsEarned, Items.GOLD_NUGGET);
        yy = stat(g, rx, yy, rw, "coins_spent", d.coinsSpent, Items.EMERALD);
        yy = stat(g, rx, yy, rw, "donated", d.donated, Items.CHEST);
        yy = stat(g, rx, yy, rw, "defended", d.defended, Items.SHIELD);
        yy = stat(g, rx, yy, rw, "hires", d.hires, Items.IRON_SWORD);
        yy = stat(g, rx, yy, rw, "built", d.blocksBuilt, Items.BRICKS);
        return Math.max(leftEnd, yy) - start;
    }

    private int stat(GuiGraphics g, int x, int y, int w, String key, int value, net.minecraft.world.item.Item icon) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.renderItem(new ItemStack(icon), 0, 0);
        g.pose().popPose();
        ui.text(g, Component.translatable("hearthbound.character.stat." + key), x + 16, y + 3, Ui.MUTED);
        String v = String.valueOf(value);
        ui.text(g, v, x + w - ui.font().width(v), y + 3, Ui.TEXT);
        return y + 14;
    }

    // ================================================================== contracts

    private int contracts(GuiGraphics g, PlayerData d, int y) {
        int start = y;
        int x = x0 + 8, cw = w - 16;
        ui.text(g, Component.translatable("hearthbound.ui.your_contracts", d.boardContracts(), ClientData.maxContracts), x, y, 0xFFE0B25A);
        y += 12;
        if (d.contracts.isEmpty()) {
            ui.wrapped(g, Component.translatable("hearthbound.character.no_contracts"), x, y, cw, Ui.DIM, 3);
            return 40;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        for (Contract c : d.contracts) {
            boolean tracked = c.id.equals(d.tracked) || (d.tracked == null && c == d.contracts.get(0));
            ui.card(g, x, y, cw, 44, false);
            if (tracked) g.fill(x, y + 1, x + 2, y + 43, ui.accent);
            g.renderItem(new ItemStack(c.icon), x + 5, y + 5);
            ui.text(g, c.title(), x + 26, y + 4, Ui.TEXT);
            ui.clipped(g, c.objective().copy().append(Component.literal("  ·  " + c.villageName).withColor(0x9AA0AC)), x + 26, y + 14, cw - 150, Ui.MUTED);
            int prog = c.type == ContractTemplate.Type.DELIVER ? deliver(c) : c.progress;
            ui.bar(g, x + 26, y + 27, cw - 160, 5, c.count <= 0 ? 0 : prog / (float) c.count, prog >= c.count ? Ui.GOOD : 0xFFE0B25A);
            ui.text(g, prog + "/" + c.count, x + cw - 128, y + 25, Ui.DIM);
            if (c.expires > 0) {
                long left = Math.max(0, c.expires - now);
                ui.text(g, Component.translatable("hearthbound.character.expires", String.format("%.1f", left / 24000f)), x + 26, y + 34, Ui.DIM);
            }
            UUID id = c.id;
            ui.ghost(g, x + cw - 96, y + 6, 44, 12, Component.translatable(tracked ? "hearthbound.ui.tracked" : "hearthbound.ui.track"), !tracked, () -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("id", id);
                Net.action("track", t);
            });
            ui.ghost(g, x + cw - 48, y + 6, 44, 12, Component.translatable("hearthbound.ui.abandon"), true, () -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("id", id);
                Net.action("abandon", t);
            });
            y += 48;
        }
        return y - start;
    }

    private static int deliver(Contract c) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        var m = com.hearthbound.data.Matcher.of(c.target, net.minecraft.core.registries.Registries.ITEM);
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (!s.isEmpty() && m.test(net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(s.getItem()))) n += s.getCount();
        }
        return Math.min(n, c.count);
    }

    // ================================================================== villages

    private int villages(GuiGraphics g, PlayerData d, int y) {
        int start = y;
        int x = x0 + 8, cw = w - 16;
        if (ClientData.VILLAGES.isEmpty()) {
            ui.wrapped(g, Component.translatable("hearthbound.character.no_villages"), x, y, cw, Ui.DIM, 3);
            return 40;
        }
        Minecraft mc = Minecraft.getInstance();
        for (ClientData.KnownVillage v : ClientData.VILLAGES) {
            int rep = d.rep(v.id());
            Rank r = Rank.of(rep);
            ui.card(g, x, y, cw, 30, ui.hover(x, y, cw, 30));
            g.fill(x + 1, y + 1, x + 3, y + 29, 0xFF000000 | v.color());
            ui.text(g, Component.literal(v.name()), x + 8, y + 5, Ui.TEXT);
            Component sub = Component.translatable(v.culture()).withColor(Ui.lighten(0xFF000000 | v.color(), 0.3f) & 0xFFFFFF)
                    .append(Component.literal("  ·  ").withColor(0x6B7180))
                    .append(Component.translatable("hearthbound.tier." + v.tier()).withColor(0x9AA0AC));
            if (v.lord()) sub = sub.copy().append(Component.literal("  ·  ").withColor(0x6B7180)).append(Component.translatable("hearthbound.ui.you_rule").withColor(0xFFD24A));
            ui.clipped(g, sub, x + 8, y + 16, cw - 170, Ui.MUTED);
            int chipX = x + cw - 160;
            ui.chip(g, r.title(), chipX, y + 4, 0xFF000000 | r.color);
            ui.text(g, rep + " " + Component.translatable("hearthbound.ui.rep_short").getString(), chipX, y + 18, Ui.DIM);
            if (mc.player != null && mc.player.level().dimension().location().toString().equals(v.dim())) {
                int dist = (int) Math.sqrt(mc.player.distanceToSqr(v.x() + 0.5, v.y(), v.z() + 0.5));
                String s = dist + " m";
                ui.text(g, s, x + cw - 8 - ui.font().width(s), y + 5, Ui.MUTED);
            }
            String coords = v.x() + ", " + v.z();
            ui.text(g, coords, x + cw - 8 - ui.font().width(coords), y + 18, Ui.DIM);
            y += 34;
        }
        return y - start;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0 && ui.click(mx, my)) return true;
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Ui.clampScroll(scroll - (int) (sy * 18), contentH, ch);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (com.hearthbound.client.HBClient.CHARACTER.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
