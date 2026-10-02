package com.hearthbound.client;

import com.hearthbound.client.gui.Ui;
import com.hearthbound.config.HBClientConfig;
import com.hearthbound.data.ContractTemplate;
import com.hearthbound.data.Matcher;
import com.hearthbound.rpg.Contract;
import com.hearthbound.rpg.Rank;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** On-screen overlays: banners, small notifications, the tracked contract and the village compass. */
public final class Hud {
    private record Banner(Component title, Component sub, int color, long start, long duration) {}

    private record Note(Component text, int color, long start) {}

    private static final Deque<Banner> BANNERS = new ArrayDeque<>();
    private static final List<Note> NOTES = new ArrayList<>();
    private static final Ui UI = new Ui();
    private static String currentVillage;

    private Hud() {}

    static long now() {
        return net.minecraft.Util.getMillis();
    }

    public static void banner(Component title, Component sub, int color) {
        long dur = HBClientConfig.BANNER_SECONDS.get() * 1000L;
        if (BANNERS.size() > 4) BANNERS.pollFirst();
        BANNERS.addLast(new Banner(title, sub, color, 0, dur));
    }

    public static void notify(Component text, int color) {
        if (!HBClientConfig.NOTIFICATIONS.get()) return;
        NOTES.add(new Note(text, color, now()));
        while (NOTES.size() > 6) NOTES.remove(0);
    }

    public static void enter(String name, Component culture, int color, Rank rank, int tier, String lord) {
        if (name.equals(currentVillage)) return;
        currentVillage = name;
        if (!HBClientConfig.VILLAGE_BANNER.get()) return;
        Component sub = Component.empty().append(culture).append(Component.literal("  ·  "))
                .append(Component.translatable("hearthbound.tier." + tier)).append(Component.literal("  ·  "))
                .append(rank.title().copy().withColor(rank.color));
        if (!lord.isEmpty()) sub = sub.copy().append(Component.literal("  ·  ")).append(Component.translatable("hearthbound.ui.lord_of", lord));
        banner(Component.literal(name), sub, color);
    }

    public static void leftVillage() {
        currentVillage = null;
    }

    // ================================================================== render

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        UI.begin(-1, -1);
        renderBanner(g, mc);
        renderNotes(g, mc);
        if (mc.screen == null) {
            if (HBClientConfig.TRACKER.get() && trackerShown()) renderTracker(g, mc);
            if (HBClientConfig.COMPASS.get()) renderCompass(g, mc);
        }
    }

    private static void renderBanner(GuiGraphics g, Minecraft mc) {
        Banner b = BANNERS.peekFirst();
        if (b == null) return;
        long t = now();
        if (b.start == 0) {
            BANNERS.pollFirst();
            b = new Banner(b.title, b.sub, b.color, t, b.duration);
            BANNERS.addFirst(b);
        }
        long age = t - b.start;
        if (age > b.duration) {
            BANNERS.pollFirst();
            return;
        }
        float in = Mth.clamp(age / 350f, 0f, 1f);
        float out = Mth.clamp((b.duration - age) / 500f, 0f, 1f);
        float a = Math.min(in, out);
        if (a <= 0.02f) return;
        Font f = mc.font;
        int sw = g.guiWidth();
        int y = 34;
        float scale = 2.0f;
        int tw = (int) (f.width(b.title) * scale);
        int sw2 = f.width(b.sub);
        int w = Math.max(tw, sw2) + 60;
        int x = (sw - w) / 2;
        int h = 44;
        float slide = HBClientConfig.ANIMATIONS.get() ? (1 - in) * -10 : 0;
        g.pose().pushPose();
        g.pose().translate(0, slide, 0);
        // soft horizontal fade: solid center, strips fading out towards both sides
        int fade = 40;
        g.fill(x + fade, y, x + w - fade, y + h, Ui.alpha(0xC8101218, a));
        for (int i = 0; i < fade; i += 2) {
            float k = (i + 1) / (float) fade;
            int col = Ui.alpha(0xC8101218, a * k);
            g.fill(x + i, y, x + i + 2, y + h, col);
            g.fill(x + w - i - 2, y, x + w - i, y + h, col);
        }
        int lineW = (int) ((w - 40) * (HBClientConfig.ANIMATIONS.get() ? in : 1f));
        g.fill(sw / 2 - lineW / 2, y + h - 12, sw / 2 + lineW / 2, y + h - 11, Ui.alpha(b.color, a));
        g.fill(sw / 2 - 3, y + h - 13, sw / 2 + 3, y + h - 10, Ui.alpha(b.color, a));
        g.pose().pushPose();
        g.pose().translate(sw / 2f - tw / 2f, y + 4, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(f, b.title, 0, 0, Ui.alpha(0xFFFFFFFF, a), true);
        g.pose().popPose();
        g.drawString(f, b.sub, sw / 2 - sw2 / 2, y + h - 8, Ui.alpha(Ui.lighten(b.color, 0.35f), a), true);
        g.pose().popPose();
    }

    private static void renderNotes(GuiGraphics g, Minecraft mc) {
        long t = now();
        Font f = mc.font;
        int y = g.guiHeight() - 60;
        int x = g.guiWidth() - 6;
        for (Iterator<Note> it = NOTES.iterator(); it.hasNext(); ) {
            if (t - it.next().start > 4500) it.remove();
        }
        for (int i = NOTES.size() - 1; i >= 0; i--) {
            Note n = NOTES.get(i);
            long age = t - n.start;
            float a = Mth.clamp(Math.min(age / 200f, (4500 - age) / 600f), 0f, 1f);
            int w = f.width(n.text) + 12;
            int nx = x - w + (int) ((1 - Mth.clamp(age / 200f, 0, 1)) * 30);
            g.fill(nx, y, nx + w, y + 14, Ui.alpha(0xD0101218, a));
            g.fill(nx, y, nx + 2, y + 14, Ui.alpha(n.color, a));
            g.drawString(f, n.text, nx + 7, y + 3, Ui.alpha(n.color | 0xFF000000, a), false);
            y -= 16;
        }
    }

    // ------------------------------------------------------------------ tracker

    private static long trackerUntil;

    /** In ON_KEY mode the tracker only appears for a while after pressing the character key. */
    public static boolean trackerShown() {
        return HBClientConfig.TRACKER_MODE.get() == HBClientConfig.TrackerMode.ALWAYS || now() < trackerUntil;
    }

    public static boolean hasTracked() {
        return ClientData.DATA.trackedContract() != null;
    }

    public static void showTracker() {
        trackerUntil = now() + HBClientConfig.TRACKER_SECONDS.get() * 1000L;
    }

    public static void hideTracker() {
        trackerUntil = 0;
    }

    private static float trackerFade() {
        if (HBClientConfig.TRACKER_MODE.get() == HBClientConfig.TrackerMode.ALWAYS) return 1f;
        long left = trackerUntil - now();
        long total = HBClientConfig.TRACKER_SECONDS.get() * 1000L;
        long shown = total - left;
        return Mth.clamp(Math.min(shown / 200f, left / 400f), 0f, 1f);
    }

    static int deliverProgress(LocalPlayer p, Contract c) {
        Matcher<Item> m = Matcher.of(c.target, Registries.ITEM);
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && m.test(BuiltInRegistries.ITEM.wrapAsHolder(s.getItem()))) n += s.getCount();
        }
        return Math.min(n, c.count);
    }

    private static void renderTracker(GuiGraphics g, Minecraft mc) {
        Contract c = ClientData.DATA.trackedContract();
        if (c == null) return;
        Font f = mc.font;
        float scale = (float) (double) HBClientConfig.TRACKER_SCALE.get();
        float fade = trackerFade();
        if (fade <= 0.02f) return;
        float op = (float) (double) HBClientConfig.TRACKER_OPACITY.get() * fade;
        int progress = c.type == ContractTemplate.Type.DELIVER ? deliverProgress(mc.player, c) : c.progress;
        boolean ready = progress >= c.count;
        Component title = c.title();
        Component obj = c.objective();
        Component where = Component.literal(c.personal() ? c.personName + " · " + c.villageName : c.villageName);
        int w = Math.max(120, Math.max(f.width(title), f.width(obj)) + 30);
        int h = 44;
        int sw = (int) (g.guiWidth() / scale), sh = (int) (g.guiHeight() / scale);
        int x, y;
        switch (HBClientConfig.TRACKER_CORNER.get()) {
            case TOP_LEFT -> { x = 4; y = 4; }
            case BOTTOM_LEFT -> { x = 4; y = sh - h - 50; }
            case BOTTOM_RIGHT -> { x = sw - w - 4; y = sh - h - 50; }
            default -> { x = sw - w - 4; y = 4; }
        }
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.pose().translate((1 - fade) * (x > sw / 2 ? 20 : -20), 0, 0);
        g.fill(x, y, x + w, y + h, Ui.alpha(0xFF101218, op));
        int accent = ready ? Ui.GOOD : 0xFFE0B25A;
        g.fill(x, y, x + 2, y + h, Ui.alpha(accent, Math.max(op, 0.6f)));
        g.renderItem(new ItemStack(c.icon), x + 6, y + 5);
        g.drawString(f, title, x + 26, y + 5, 0xFFFFFFFF, false);
        g.drawString(f, obj, x + 26, y + 15, Ui.MUTED, false);
        UI.bar(g, x + 6, y + 28, w - 12, 5, c.count <= 0 ? 0 : progress / (float) c.count, accent);
        Component status = ready ? Component.translatable("hearthbound.tracker.ready", where) : Component.literal(progress + " / " + c.count + "  ·  ").append(where);
        g.drawString(f, status, x + 6, y + 35, ready ? Ui.GOOD : Ui.DIM, false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ compass

    private static void renderCompass(GuiGraphics g, Minecraft mc) {
        if (currentVillage != null || ClientData.VILLAGES.isEmpty()) return;
        LocalPlayer p = mc.player;
        String dim = p.level().dimension().location().toString();
        ClientData.KnownVillage best = null;
        double bestD = (double) ClientData.radar * ClientData.radar;
        for (ClientData.KnownVillage v : ClientData.VILLAGES) {
            if (!v.dim().equals(dim)) continue;
            double dx = v.x() - p.getX(), dz = v.z() - p.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = v;
            }
        }
        if (best == null) return;
        double dx = best.x() + 0.5 - p.getX(), dz = best.z() + 0.5 - p.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz)); // minecraft yaw of the target
        double rel = Mth.wrapDegrees(angle - p.getYRot());
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int idx = (int) Math.floorMod(Math.round(rel / 45.0), 8);
        int dist = (int) Math.sqrt(bestD);
        Component line = Component.literal(arrows[idx] + " ").withColor(best.color() & 0xFFFFFF)
                .append(Component.literal(best.name()).withColor(0xE8E6E1))
                .append(Component.literal("  " + dist + "m").withColor(0x9AA0AC));
        Font f = mc.font;
        int w = f.width(line) + 12;
        int x = g.guiWidth() / 2 - w / 2;
        int y = 3;
        if (!BANNERS.isEmpty()) return;
        g.fill(x, y, x + w, y + 13, 0x90101218);
        g.drawString(f, line, x + 6, y + 3, 0xFFFFFFFF, false);
    }
}
