package com.hearthbound.client.gui;

import com.hearthbound.client.ClientData;
import com.hearthbound.config.HBClientConfig;
import com.hearthbound.network.Net;
import com.hearthbound.rpg.Contract;
import com.hearthbound.rpg.Rank;
import com.hearthbound.village.Resource;
import com.hearthbound.village.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The village ledger: one screen with tabs for everything a player can do with a village. */
public class VillageScreen extends Screen {
    private static final String[] ALL_TABS = {"overview", "trade", "contracts", "projects", "diplomacy", "shrine", "people", "build", "donate"};

    private final Ui ui = new Ui();
    private CompoundTag view;
    private String tab;
    private final Map<String, Integer> scroll = new HashMap<>();
    private final Map<String, Integer> contentH = new HashMap<>();
    private UUID pendingRevival;
    private Component greeting;
    private long opened;
    private int x0, y0, w, h, cx, cy, cw, ch;

    public VillageScreen(CompoundTag view) {
        super(Component.literal(view.getString("name")));
        this.view = view;
        String t = view.getString("tab");
        this.tab = t.isEmpty() ? "overview" : t;
        String role = view.getString("speakerRole");
        if (!role.isEmpty()) {
            int n = Math.abs(view.getString("speaker").hashCode() + (int) (System.currentTimeMillis() / 60000)) % 3;
            greeting = Component.translatable("hearthbound.greet." + role + "." + n);
        }
        opened = net.minecraft.Util.getMillis();
    }

    public boolean sameVillage(CompoundTag t) {
        return t.hasUUID("id") && view.hasUUID("id") && t.getUUID("id").equals(view.getUUID("id"));
    }

    public void update(CompoundTag t) {
        String keepTab = tab;
        CompoundTag speaker = new CompoundTag();
        for (String k : new String[]{"speaker", "speakerRole", "speakerVariant"}) if (view.contains(k)) speaker.put(k, view.get(k).copy());
        this.view = t;
        this.view.merge(speaker);
        String nt = t.getString("tab");
        this.tab = nt.isEmpty() ? keepTab : nt;
    }

    private UUID id() {
        return view.getUUID("id");
    }

    private void act(String action, CompoundTag args) {
        args.putUUID("village", id());
        Net.action(action, args);
        if (HBClientConfig.SOUNDS.get()) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
    }

    private void act(String action) {
        act(action, new CompoundTag());
    }

    static ItemStack stack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return new ItemStack(Items.BARRIER);
        Item i = BuiltInRegistries.ITEM.get(rl);
        return new ItemStack(i);
    }

    static Component tr(String keyOrLiteral) {
        return Component.translatable(keyOrLiteral);
    }

    private List<String> tabs() {
        List<String> out = new ArrayList<>();
        if (view.contains("shop")) {
            out.add("trade");
            return out;
        }
        for (String t : ALL_TABS) {
            // wares are bought inside their buildings (foundation stone); the ledger only lists them with centralMarket
            if (t.equals("trade") && !view.getBoolean("centralMarket")) continue;
            if (t.equals("shrine") && view.getList("blessings", Tag.TAG_COMPOUND).isEmpty()) continue;
            out.add(t);
        }
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================== layout

    private void layout() {
        w = Math.min(width - 16, 480);
        h = Math.min(height - 16, 290);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        layout();
        ui.begin(mx, my);
        int color = HBClientConfig.CULTURE_ACCENT.get() && view.contains("color") ? view.getInt("color") | 0xFF000000 : HBClientConfig.accent();
        ui.accent = color;
        float anim = HBClientConfig.ANIMATIONS.get() ? Math.min(1f, (net.minecraft.Util.getMillis() - opened) / 180f) : 1f;
        g.pose().pushPose();
        g.pose().translate(0, (1 - anim) * 8, 0);

        ui.panel(g, x0, y0, w, h);
        int headerH = 46;
        ui.header(g, x0, y0, w, headerH, color);
        drawHeader(g, color);

        int ty = y0 + headerH;
        if (greeting != null) {
            g.fill(x0 + 1, ty, x0 + w - 1, ty + 14, 0x40000000);
            Component who = Component.literal(view.getString("speaker")).withColor(Role.byId(view.getString("speakerRole")).color);
            Component line = Component.empty().append(who).append(Component.literal(": ").withColor(0x9AA0AC))
                    .append(Component.literal("«").append(greeting).append("»").withStyle(s -> s.withItalic(true)).withColor(0xE8E6E1));
            ui.clipped(g, line, x0 + 8, ty + 3, w - 16, Ui.TEXT);
            ty += 14;
        }
        List<String> tabs = tabs();
        if (!tabs.contains(tab)) tab = "overview";
        int tw = w / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            String t = tabs.get(i);
            int x = x0 + i * tw;
            int ww = i == tabs.size() - 1 ? w - i * tw : tw;
            ui.tab(g, x, ty, ww, 16, Component.translatable("hearthbound.tab." + t), t.equals(tab), () -> {
                tab = t;
                if (HBClientConfig.SOUNDS.get()) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.5f));
            });
        }
        g.fill(x0 + 1, ty + 16, x0 + w - 1, ty + 17, Ui.LINE);

        cx = x0 + 8;
        cy = ty + 22;
        cw = w - 16;
        ch = y0 + h - 6 - cy;
        int sc = scroll.getOrDefault(tab, 0);
        g.enableScissor(cx - 2, cy - 2, cx + cw + 4, cy + ch);
        int used = switch (tab) {
            case "trade" -> drawTrade(g, cy - sc);
            case "contracts" -> drawContracts(g, cy - sc);
            case "projects" -> drawProjects(g, cy - sc);
            case "shrine" -> drawShrine(g, cy - sc);
            case "diplomacy" -> drawDiplomacy(g, cy - sc);
            case "people" -> drawPeople(g, cy - sc);
            case "build" -> drawBuild(g, cy - sc);
            case "donate" -> drawDonate(g, cy - sc);
            default -> drawOverview(g, cy - sc);
        };
        g.disableScissor();
        contentH.put(tab, used);
        ui.scrollbar(g, x0 + w - 4, cy, ch, sc, used);
        g.pose().popPose();
        ui.drawTooltips(g);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x90000000, 0xB0000000);
    }

    private void drawHeader(GuiGraphics g, int color) {
        g.pose().pushPose();
        g.pose().translate(x0 + 8, y0 + 7, 0);
        g.pose().scale(2f, 2f, 1f);
        ItemStack flag = view.contains("flag") ? cached(view.getCompound("flag")) : ItemStack.EMPTY;
        g.renderItem(flag.isEmpty() ? stack(view.getString("icon")) : flag, 0, 0);
        g.pose().popPose();
        if (!flag.isEmpty()) ui.tooltip(x0 + 8, y0 + 7, 32, 32, flag.getHoverName());
        int tx = x0 + 46;
        ui.scaled(g, Component.literal(view.getString("name")), tx, y0 + 8, 1.6f, 0xFFFFFFFF, true);
        Component sub = Component.empty().append(tr(view.getString("culture")).copy().withColor(Ui.lighten(color, 0.4f) & 0xFFFFFF))
                .append(Component.literal("  ·  ").withColor(0x9AA0AC))
                .append(Component.translatable("hearthbound.tier." + view.getInt("tier")).withColor(0xE8E6E1));
        if (!view.getString("lordName").isEmpty()) {
            sub = sub.copy().append(Component.literal("  ·  ").withColor(0x9AA0AC)).append(Component.translatable("hearthbound.ui.lord_of", view.getString("lordName")).withColor(0xFFD24A));
        }
        ui.text(g, sub, tx, y0 + 30, Ui.TEXT);

        // standing
        Rank rank = Rank.byId(view.getString("rank"));
        int rx = x0 + w - 128;
        int chipW = ui.font().width(rank.title()) + 8;
        ui.chip(g, rank.title(), x0 + w - 8 - chipW, y0 + 7, 0xFF000000 | rank.color);
        int rep = view.getInt("rep"), at = view.getInt("rankAt"), next = view.getInt("nextRankAt");
        float prog = next <= at ? 1f : (rep - at) / (float) (next - at);
        ui.bar(g, rx, y0 + 23, 120, 5, prog, 0xFF000000 | rank.color);
        ui.tooltip(rx, y0 + 20, 120, 10, Component.translatable("hearthbound.ui.rep_tip", rep, next));
        int bw = ui.coinsWidth(view.getInt("balance"));
        ui.coins(g, view.getInt("balance"), x0 + w - 8 - bw, y0 + 32);
        ui.tooltip(x0 + w - 8 - bw, y0 + 30, bw, 10, Component.translatable("hearthbound.ui.balance"));
    }

    // ================================================================== overview

    private int drawOverview(GuiGraphics g, int y) {
        int start = y;
        int half = (cw - 8) / 2;
        int lx = cx, rx = cx + half + 8;
        // culture description
        int yy = y;
        ui.text(g, tr(view.getString("people")).copy().withColor(0xFFE0B25A), lx, yy, Ui.TEXT);
        yy += 12;
        yy += ui.wrapped(g, tr(view.getString("cultureDesc")), lx, yy, half, Ui.MUTED, 4) + 6;
        // stats
        int sw = (half - 6) / 2;
        statCard(g, lx, yy, sw, Component.translatable("hearthbound.ui.population"), Component.literal(view.getInt("population") + " / " + view.getInt("housing")), Items.PLAYER_HEAD);
        statCard(g, lx + sw + 6, yy, sw, Component.translatable("hearthbound.ui.buildings"), Component.literal(String.valueOf(view.getInt("buildingsDone"))), Items.BRICKS);
        yy += 30;
        statCard(g, lx, yy, sw, Component.translatable("hearthbound.ui.tier"), Component.translatable("hearthbound.tier." + view.getInt("tier")), Items.BELL);
        ui.card(g, lx + sw + 6, yy, sw, 26, false);
        ui.text(g, Component.translatable("hearthbound.ui.prosperity"), lx + sw + 12, yy + 4, Ui.MUTED);
        int pr = view.getInt("prosperity");
        ui.bar(g, lx + sw + 12, yy + 16, sw - 12, 5, pr / 100f, pr > 60 ? Ui.GOOD : pr > 30 ? 0xFFE0B25A : Ui.BAD);
        yy += 32;
        // resources
        ui.text(g, Component.translatable("hearthbound.ui.stock"), lx, yy, Ui.MUTED);
        yy += 11;
        CompoundTag stock = view.getCompound("stock");
        int rw = half / 4;
        for (int i = 0; i < Resource.values().length; i++) {
            Resource r = Resource.values()[i];
            int x = lx + i * rw;
            g.renderItem(new ItemStack(r.icon), x, yy);
            ui.text(g, String.valueOf(stock.getInt(r.id())), x + 18, yy + 4, 0xFF000000 | r.color);
            ui.tooltip(x, yy, rw, 16, Component.translatable(r.key()));
        }
        yy += 20;
        int leftEnd = yy;

        // right column: standing
        yy = y;
        Rank rank = Rank.byId(view.getString("rank"));
        ui.card(g, rx, yy, half, 58, false);
        ui.text(g, Component.translatable("hearthbound.ui.standing"), rx + 6, yy + 5, Ui.MUTED);
        ui.scaled(g, rank.title(), rx + 6, yy + 17, 1.4f, 0xFF000000 | rank.color, true);
        int rep = view.getInt("rep"), at = view.getInt("rankAt"), next = view.getInt("nextRankAt");
        ui.bar(g, rx + 6, yy + 33, half - 12, 6, next <= at ? 1f : (rep - at) / (float) (next - at), 0xFF000000 | rank.color);
        Component hint = rank == Rank.HERO ? Component.translatable("hearthbound.ui.max_rank")
                : Component.translatable("hearthbound.ui.next_rank", rank.next().title(), Math.max(0, next - rep));
        ui.clipped(g, hint, rx + 6, yy + 44, half - 12, Ui.MUTED);
        yy += 64;
        ui.card(g, rx, yy, half, 22, false);
        ui.text(g, Component.translatable("hearthbound.ui.contracts_active", view.getInt("activeContracts"), view.getInt("maxContracts")), rx + 6, yy + 7, Ui.TEXT);
        yy += 28;
        // lordship
        if (view.getBoolean("isLord")) {
            ui.card(g, rx, yy, half, 42, false);
            ui.text(g, Component.translatable("hearthbound.ui.you_rule").withColor(0xFFD24A), rx + 6, yy + 5, Ui.GOLD);
            ui.text(g, Component.translatable("hearthbound.ui.treasury"), rx + 6, yy + 18, Ui.MUTED);
            ui.coins(g, view.getInt("treasury"), rx + 6 + ui.font().width(Component.translatable("hearthbound.ui.treasury")) + 4, yy + 18);
            ui.button(g, rx + half - 76, yy + 24, 70, 14, Component.translatable("hearthbound.ui.collect"), view.getInt("treasury") > 0, () -> act("tribute"));
            yy += 48;
        } else if (view.getBoolean("canClaimLord")) {
            ui.card(g, rx, yy, half, 40, false);
            ui.wrapped(g, Component.translatable("hearthbound.ui.claim_hint"), rx + 6, yy + 5, half - 12, Ui.GOLD, 2);
            ui.button(g, rx + 6, yy + 24, half - 12, 13, Component.translatable("hearthbound.ui.claim"), true, () -> act("claim_lord"));
            yy += 46;
        } else if (view.getBoolean("lordAgeLocked")) {
            ui.card(g, rx, yy, half, 26, false);
            ui.wrapped(g, Component.translatable("hearthbound.gate.age", ClientData.json(view.getString("lordAge"))), rx + 6, yy + 5, half - 12, Ui.MUTED, 2);
            yy += 32;
        }
        yy = drawCitizenship(g, rx, yy, half);
        return Math.max(leftEnd, yy) - start;
    }

    private final java.util.Map<CompoundTag, ItemStack> parsed = new java.util.IdentityHashMap<>();

    /** Parses a synced item once per view (the view is replaced on every update). */
    private ItemStack cached(CompoundTag tag) {
        if (parsed.size() > 512) parsed.clear();
        return parsed.computeIfAbsent(tag, VillageScreen::parse);
    }

    static ItemStack parse(CompoundTag tag) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.parseOptional(mc.level.registryAccess(), tag);
    }

    /** Citizenship and banner card. */
    private int drawCitizenship(GuiGraphics g, int x, int yy, int w) {
        if (!view.getBoolean("citizenship")) return yy;
        boolean citizen = view.getBoolean("citizen");
        boolean lord = view.getBoolean("isLord");
        int h = lord ? 74 : 58;
        ui.card(g, x, yy, w, h, false);
        int fc = 0xFF000000 | view.getInt("flagColor");
        g.fill(x, yy, x + 2, yy + h, fc);
        ItemStack flag = cached(view.getCompound("flag"));
        if (!flag.isEmpty()) ui.itemTip(g, flag, x + 6, yy + 5);
        Component title = citizen ? Component.translatable("hearthbound.citizen.you_are").withColor(fc & 0xFFFFFF)
                : Component.translatable("hearthbound.citizen.title");
        ui.text(g, title, x + 26, yy + 5, Ui.TEXT);
        ui.text(g, Component.translatable("hearthbound.citizen.count", view.getInt("citizens")), x + 26, yy + 15, Ui.MUTED);
        int by = yy + 28;
        if (citizen) {
            ui.clipped(g, Component.translatable("hearthbound.citizen.perks", view.getInt("citizenDiscount")), x + 6, by, w - 12, Ui.DIM);
            by += 12;
            int bw = (w - 16) / 2;
            ui.button(g, x + 6, by, bw, 14, Component.translatable("hearthbound.flag.copy", view.getInt("flagCost")), view.getInt("balance") >= view.getInt("flagCost"), () -> act("flag_copy"));
            ui.ghost(g, x + 10 + bw, by, bw, 14, Component.translatable("hearthbound.citizen.renounce"), true, () -> act("renounce"));
        } else {
            boolean locked = view.contains("citizenLock");
            Component info = locked ? ClientData.json(view.getString("citizenLock")).copy().withColor(0xE06A5A)
                    : view.contains("citizenOf") ? Component.translatable("hearthbound.citizen.switch", view.getString("citizenOf"))
                    : Component.translatable("hearthbound.citizen.perks", view.getInt("citizenDiscount"));
            ui.clipped(g, info, x + 6, by, w - 12, locked ? Ui.BAD : Ui.DIM);
            ui.tooltip(x + 6, by, w - 12, 10, info);
            by += 12;
            ui.button(g, x + 6, by, w - 12, 14, Component.translatable("hearthbound.citizen.join"), !locked, () -> act("citizen"));
        }
        if (lord) {
            by += 16;
            int bw = (w - 16) / 2;
            ui.button(g, x + 6, by, bw, 14, Component.translatable("hearthbound.flag.set"), true, () -> act("flag_set"));
            ui.tooltip(x + 6, by, bw, 14, Component.translatable("hearthbound.flag.set_tip"));
            ui.ghost(g, x + 10 + bw, by, bw, 14, Component.translatable("hearthbound.flag.reset"), true, () -> act("flag_reset"));
        }
        return yy + h + 6;
    }

    // ================================================================== projects

    private int drawProjects(GuiGraphics g, int y) {
        int start = y;
        ui.wrapped(g, Component.translatable("hearthbound.project.intro"), cx, y, cw, Ui.MUTED, 3);
        y += 24;
        ListTag list = view.getList("projects", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            ui.text(g, Component.translatable("hearthbound.project.none"), cx, y, Ui.DIM);
            y += 14;
        }
        int cols = cw >= 380 ? 2 : 1;
        int colW = (cw - (cols - 1) * 8) / cols;
        int i = 0;
        int rowY = y, rowH = 0;
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            int x = cx + (i % cols) * (colW + 8);
            if (i % cols == 0) {
                rowY += rowH;
                rowH = 0;
            }
            int h = projectCard(g, e, x, rowY, colW);
            rowH = Math.max(rowH, h + 6);
            i++;
        }
        y = rowY + rowH + 4;
        ListTag works = view.getList("works", Tag.TAG_COMPOUND);
        if (!works.isEmpty()) {
            ui.text(g, Component.translatable("hearthbound.project.works"), cx, y, 0xFFE0B25A);
            y += 12;
            int wc = Math.max(2, cw / 150);
            int ww = (cw - (wc - 1) * 6) / wc;
            int k = 0;
            for (Tag t : works) {
                CompoundTag e = (CompoundTag) t;
                int x = cx + (k % wc) * (ww + 6);
                int yy = y + (k / wc) * 24;
                ui.card(g, x, yy, ww, 20, false);
                g.renderItem(stack(e.getString("icon")), x + 2, yy + 2);
                ui.clipped(g, Component.translatable("hearthbound.project.level_name", tr(e.getString("name")), e.getInt("level")), x + 20, yy + 2, ww - 22, Ui.TEXT);
                ui.clipped(g, Component.translatable("hearthbound.project.built_by", e.getString("builder")), x + 20, yy + 11, ww - 22, Ui.DIM);
                k++;
            }
            y += ((k + wc - 1) / wc) * 24;
        }
        return y - start;
    }

    private int projectCard(GuiGraphics g, CompoundTag e, int x, int y, int w) {
        int h = 86;
        boolean locked = e.contains("lock");
        boolean mine = e.getBoolean("mine");
        ui.card(g, x, y, w, h, ui.hover(x, y, w, h));
        if (mine) g.fill(x, y, x + 2, y + h, 0xFFE0B25A);
        g.renderItem(stack(e.getString("icon")), x + 5, y + 5);
        Component title = Component.translatable("hearthbound.project.level_name", tr(e.getString("name")), e.getInt("level"));
        ui.clipped(g, title, x + 25, y + 5, w - 30, locked && !mine ? Ui.DIM : Ui.TEXT);
        Component size = Component.translatable("hearthbound.project.size", e.getInt("size"), e.getInt("size"), e.getInt("maxSize"), e.getInt("maxSize"));
        ui.clipped(g, size, x + 25, y + 15, w - 30, Ui.DIM);
        ui.tooltip(x + 25, y + 3, w - 30, 22, List.of(tr(e.getString("name")), tr(e.getString("desc")).copy().withColor(0x9AA0AC)));
        // requirements
        int rx = x + 5, ry = y + 29;
        ui.text(g, Component.translatable("hearthbound.project.needs_blocks"), rx, ry + 4, Ui.MUTED);
        rx += ui.font().width(Component.translatable("hearthbound.project.needs_blocks")) + 4;
        for (Tag t : e.getList("reqs", Tag.TAG_COMPOUND)) {
            CompoundTag q = (CompoundTag) t;
            if (rx > x + w - 30) break;
            ItemStack st = stack(q.getString("icon"));
            g.renderItem(st, rx, ry);
            String n = "×" + q.getInt("count");
            ui.text(g, n, rx + 17, ry + 5, Ui.TEXT);
            Component name = q.getString("block").startsWith("#")
                    ? Component.translatableWithFallback("hearthbound.tag." + q.getString("block").substring(1).replace(':', '.').replace('/', '.'), q.getString("block"))
                    : st.getHoverName();
            ui.tooltip(rx, ry, 20 + ui.font().width(n), 16, Component.literal(q.getInt("count") + " × ").append(name));
            rx += 22 + ui.font().width(n);
        }
        // rewards
        int wy = y + 49;
        int wx = x + 5;
        Component rep = Component.literal("+" + e.getInt("reputation") + " ").append(Component.translatable("hearthbound.ui.rep_short")).withColor(0x6CD68A);
        ui.text(g, rep, wx, wy, Ui.GOOD);
        wx += ui.font().width(rep) + 8;
        wx += ui.coins(g, e.getInt("coins"), wx, wy) + 8;
        ui.text(g, e.getInt("xp") + " XP", wx, wy, 0xFF8FD18F);
        if (e.getInt("unlocks") > 0) {
            Component un = Component.translatable("hearthbound.project.unlocks", e.getInt("unlocks"));
            ui.clipped(g, un, x + 5, wy + 11, w - 10, 0xFFE0B25A);
        }
        int by = y + h - 18;
        if (mine) {
            Component st = Component.translatable(e.getBoolean("placed") ? "hearthbound.project.status_placed" : "hearthbound.project.status_stone");
            ui.clipped(g, st, x + 5, by + 3, w - 110, Ui.GOLD);
            String id = e.getString("id");
            if (!e.getBoolean("placed")) {
                ui.ghost(g, x + w - 100, by, 18, 14, Component.literal("⛏"), true, () -> {
                    CompoundTag a = new CompoundTag();
                    a.putString("id", id);
                    act("project_take", a);
                });
                ui.tooltip(x + w - 100, by, 18, 14, Component.translatable("hearthbound.project.new_stone"));
            }
            ui.ghost(g, x + w - 78, by, 72, 14, Component.translatable("hearthbound.project.abandon"), true, () -> {
                CompoundTag a = new CompoundTag();
                a.putString("id", id);
                act("project_abandon", a);
            });
        } else if (e.contains("owner")) {
            ui.clipped(g, Component.translatable("hearthbound.project.taken_by", e.getString("owner")), x + 5, by + 3, w - 10, Ui.DIM);
        } else {
            if (locked) ui.clipped(g, ClientData.json(e.getString("lock")), x + 5, by + 3, w - 90, Ui.BAD);
            String id = e.getString("id");
            ui.button(g, x + w - 78, by, 72, 14, Component.translatable("hearthbound.project.take"), !locked, () -> {
                CompoundTag a = new CompoundTag();
                a.putString("id", id);
                act("project_take", a);
            });
            if (locked) ui.tooltip(x, by, w - 80, 14, ClientData.json(e.getString("lock")));
        }
        return h;
    }

    private void statCard(GuiGraphics g, int x, int y, int w, Component label, Component value, Item icon) {
        ui.card(g, x, y, w, 26, false);
        g.renderItem(new ItemStack(icon), x + 4, y + 5);
        ui.text(g, label, x + 24, y + 4, Ui.MUTED);
        ui.clipped(g, value, x + 24, y + 15, w - 28, Ui.TEXT);
    }

    // ================================================================== trade

    private int drawTrade(GuiGraphics g, int y) {
        int half = (cw - 8) / 2;
        int start = y;
        if (view.contains("shop")) {
            ui.card(g, cx, y, cw, 22, false);
            g.renderItem(stack(view.getString("shopIcon")), cx + 3, y + 3);
            ui.text(g, Component.translatable("hearthbound.shop.title", tr(view.getString("shopName"))), cx + 24, y + 7, 0xFFE0B25A);
            y += 28;
        } else if (hasRemote()) {
            y += ui.wrapped(g, Component.translatable("hearthbound.shop.catalog"), cx, y, cw, Ui.MUTED, 2) + 6;
        }
        int a = tradeColumn(g, cx, y, half, true);
        int b = tradeColumn(g, cx + half + 8, y, half, false);
        ui.text(g, Component.translatable("hearthbound.ui.shift_hint"), cx, Math.max(a, b) + 4, Ui.DIM);
        return Math.max(a, b) - start + 16;
    }

    private boolean hasRemote() {
        for (Tag t : view.getList("trades", Tag.TAG_COMPOUND)) if (((CompoundTag) t).contains("where")) return true;
        return false;
    }

    private int tradeColumn(GuiGraphics g, int x, int y, int w, boolean sell) {
        ui.text(g, Component.translatable(sell ? "hearthbound.ui.for_sale" : "hearthbound.ui.wanted"), x, y, 0xFFE0B25A);
        y += 12;
        ListTag list = view.getList("trades", Tag.TAG_COMPOUND);
        boolean any = false;
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            if (e.getBoolean("sell") != sell) continue;
            any = true;
            boolean locked = e.contains("lock");
            int left = e.getInt("left");
            boolean out = left == 0;
            boolean hov = ui.hover(x, y, w, 24);
            ui.card(g, x, y, w, 24, hov && !locked);
            ItemStack st = e.contains("stack") ? cached(e.getCompound("stack")).copy() : stack(e.getString("item"));
            if (st.isEmpty()) st = stack(e.getString("item"));
            st.setCount(e.getInt("count"));
            ui.itemTip(g, st, x + 4, y + 4);
            Role role = Role.byId(e.getString("role"));
            ui.clipped(g, st.getHoverName(), x + 24, y + 3, w - 110, locked ? Ui.DIM : Ui.TEXT);
            Component sub = e.contains("source") ? ClientData.json(e.getString("source")).copy().withColor(0xE0B25A) : Component.translatable(role.key()).withColor(role.color);
            if (left > 0) sub = sub.copy().append(Component.literal("  ·  " + left).withColor(0x6B7180));
            if (!sell) sub = sub.copy().append(Component.literal("  ·  ").withColor(0x6B7180)).append(Component.translatable("hearthbound.ui.have", e.getInt("have")).withColor(e.getInt("have") >= e.getInt("count") ? 0x6CD68A : 0x9AA0AC));
            ui.clipped(g, sub, x + 24, y + 13, w - 110, Ui.MUTED);
            int price = e.getInt("price");
            int pw = ui.coinsWidth(price);
            ui.coins(g, price, x + w - 52 - pw, y + 8);
            int idx = e.getInt("index");
            boolean remote = e.contains("where");
            boolean can = !remote && !locked && !out && (!sell || view.getInt("balance") >= price) && (sell || e.getInt("have") >= e.getInt("count"));
            ui.button(g, x + w - 48, y + 5, 44, 14, Component.translatable(sell ? "hearthbound.ui.buy" : "hearthbound.ui.sell"), can, () -> {
                CompoundTag args = new CompoundTag();
                args.putInt("index", idx);
                args.putInt("times", hasShiftDown() ? 5 : 1);
                act("trade", args);
            });
            if (remote && !locked) ui.tooltip(x + w - 48, y + 5, 44, 14, ClientData.json(e.getString("where")));
            if (locked) ui.tooltip(x, y, w - 50, 24, ClientData.json(e.getString("lock")).copy().withColor(0xE06A5A));
            else if (out) ui.tooltip(x, y, w - 50, 24, Component.translatable("hearthbound.ui.sold_out"));
            y += 27;
        }
        if (!any) {
            ui.text(g, Component.translatable("hearthbound.ui.nothing"), x, y + 2, Ui.DIM);
            y += 14;
        }
        return y;
    }

    // ================================================================== contracts

    private int drawContracts(GuiGraphics g, int y) {
        int half = (cw - 8) / 2;
        int lx = cx, rx = cx + half + 8;
        int ly = y, ry = y;
        ui.text(g, Component.translatable("hearthbound.ui.board"), lx, ly, 0xFFE0B25A);
        ly += 12;
        ListTag board = view.getList("board", Tag.TAG_COMPOUND);
        if (board.isEmpty()) {
            ui.wrapped(g, Component.translatable("hearthbound.ui.board_empty"), lx, ly, half, Ui.DIM, 3);
            ly += 30;
        }
        boolean full = view.getInt("activeContracts") >= view.getInt("maxContracts");
        for (Tag t : board) {
            CompoundTag e = (CompoundTag) t;
            Contract c = Contract.load(e);
            boolean locked = e.contains("lock");
            ui.card(g, lx, ly, half, 50, ui.hover(lx, ly, half, 50));
            g.renderItem(new ItemStack(c.icon), lx + 4, ly + 4);
            ui.clipped(g, ClientData.json(e.getString("titleText")), lx + 24, ly + 4, half - 28, locked ? Ui.DIM : Ui.TEXT);
            ui.clipped(g, ClientData.json(e.getString("objectiveText")), lx + 24, ly + 14, half - 28, Ui.MUTED);
            rewards(g, c, lx + 6, ly + 26);
            UUID cid = c.id;
            if (locked) {
                ui.clipped(g, ClientData.json(e.getString("lock")), lx + 6, ly + 38, half - 12, Ui.BAD);
            } else {
                ui.button(g, lx + half - 64, ly + 35, 60, 12, Component.translatable("hearthbound.ui.accept"), !full, () -> {
                    CompoundTag a = new CompoundTag();
                    a.putUUID("id", cid);
                    act("accept", a);
                });
                if (full) ui.tooltip(lx + half - 64, ly + 35, 60, 12, Component.translatable("hearthbound.contract.full", view.getInt("maxContracts")));
            }
            ly += 54;
        }

        ui.text(g, Component.translatable("hearthbound.ui.your_contracts", view.getInt("activeContracts"), view.getInt("maxContracts")), rx, ry, 0xFFE0B25A);
        ry += 12;
        ListTag mine = view.getList("mine", Tag.TAG_COMPOUND);
        if (mine.isEmpty()) {
            ui.wrapped(g, Component.translatable("hearthbound.ui.no_contracts_here"), rx, ry, half, Ui.DIM, 3);
            ry += 30;
        }
        for (Tag t : mine) {
            CompoundTag e = (CompoundTag) t;
            Contract c = Contract.load(e);
            boolean ready = e.getBoolean("ready");
            ui.card(g, rx, ry, half, 56, false);
            if (ready) g.fill(rx, ry + 1, rx + 2, ry + 55, Ui.GOOD);
            g.renderItem(new ItemStack(c.icon), rx + 4, ry + 4);
            ui.clipped(g, ClientData.json(e.getString("titleText")), rx + 24, ry + 4, half - 28, Ui.TEXT);
            ui.clipped(g, ClientData.json(e.getString("objectiveText")), rx + 24, ry + 14, half - 28, Ui.MUTED);
            ui.bar(g, rx + 6, ry + 26, half - 12, 5, c.count <= 0 ? 0 : c.progress / (float) c.count, ready ? Ui.GOOD : 0xFFE0B25A);
            ui.text(g, c.progress + " / " + c.count, rx + 6, ry + 33, Ui.DIM);
            UUID cid = c.id;
            ui.button(g, rx + half - 62, ry + 40, 58, 12, Component.translatable("hearthbound.ui.turn_in"), ready, () -> {
                CompoundTag a = new CompoundTag();
                a.putUUID("id", cid);
                act("turnin", a);
            });
            boolean tracked = cid.equals(ClientData.DATA.tracked);
            ui.ghost(g, rx + half - 124, ry + 40, 58, 12, Component.translatable(tracked ? "hearthbound.ui.tracked" : "hearthbound.ui.track"), !tracked, () -> {
                CompoundTag a = new CompoundTag();
                a.putUUID("id", cid);
                Net.action("track", a);
            });
            ui.ghost(g, rx + 6, ry + 40, 48, 12, Component.translatable("hearthbound.ui.abandon"), true, () -> {
                CompoundTag a = new CompoundTag();
                a.putUUID("id", cid);
                act("abandon", a);
            });
            ry += 60;
        }
        return Math.max(ly, ry) - y;
    }

    private void rewards(GuiGraphics g, Contract c, int x, int y) {
        int xx = x;
        if (c.coins > 0) xx += ui.coins(g, c.coins, xx, y) + 8;
        if (c.reputation > 0) {
            Component r = Component.translatable("hearthbound.ui.plus_rep", c.reputation);
            ui.text(g, r, xx, y, Ui.GOOD);
            xx += ui.font().width(r) + 8;
        }
        if (c.xp > 0) {
            Component r = Component.translatable("hearthbound.ui.plus_xp", c.xp);
            ui.text(g, r, xx, y, 0xFF9FE06A);
            xx += ui.font().width(r) + 8;
        }
        for (String s : c.items) {
            ItemStack st = com.hearthbound.village.Contracts.parseStack(s);
            if (st.isEmpty()) continue;
            g.pose().pushPose();
            g.pose().translate(xx, y - 3, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(st, 0, 0);
            g.renderItemDecorations(ui.font(), st, 0, 0);
            g.pose().popPose();
            ui.tooltip(xx, y - 3, 12, 12, st.getHoverName());
            xx += 14;
        }
    }

    // ================================================================== diplomacy

    private int drawDiplomacy(GuiGraphics g, int y) {
        int start = y;
        y += ui.wrapped(g, Component.translatable("hearthbound.ui.diplo_intro"), cx, y, cw, Ui.MUTED, 3) + 6;
        ListTag list = view.getList("diplomacy", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            ui.wrapped(g, Component.translatable("hearthbound.ui.diplo_none"), cx, y, cw, Ui.DIM, 3);
            return y - start + 30;
        }
        int giftCost = view.getInt("giftCost"), medCost = view.getInt("mediationCost");
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            int h = 46;
            ui.card(g, cx, y, cw, h, ui.hover(cx, y, cw, h));
            g.fill(cx + 1, y + 1, cx + 3, y + h - 1, 0xFF000000 | e.getInt("color"));
            Component name = e.getBoolean("known") ? Component.literal(e.getString("name")) : Component.literal(e.getString("name")).withColor(0x9AA0AC);
            ui.text(g, name, cx + 8, y + 5, Ui.TEXT);
            Component sub = tr(e.getString("culture")).copy().withColor(Ui.lighten(0xFF000000 | e.getInt("color"), 0.3f) & 0xFFFFFF)
                    .append(Component.literal("  ·  ").withColor(0x6B7180))
                    .append(Component.translatable("hearthbound.tier." + e.getInt("tier")).withColor(0x9AA0AC))
                    .append(Component.literal("  ·  " + e.getInt("dist") + " m").withColor(0x6B7180));
            ui.clipped(g, sub, cx + 8, y + 16, cw / 2, Ui.MUTED);
            // relation bar from -100 to 100
            int bx = cx + 8, bw = cw / 2 - 16, byy = y + 30;
            ui.box(g, bx, byy, bw, 6, 0xFF0B0D12);
            g.fill(bx + bw / 2, byy - 1, bx + bw / 2 + 1, byy + 7, 0x60FFFFFF);
            int value = e.getInt("value");
            int col = e.getInt("stanceColor");
            int mid = bx + bw / 2;
            int end = mid + (int) (value / 100.0 * (bw / 2 - 1));
            g.fill(Math.min(mid, end), byy + 1, Math.max(mid, end), byy + 5, col);
            ui.tooltip(bx, byy - 2, bw, 10, Component.translatable("hearthbound.ui.relation", value));
            int rx = cx + cw / 2 + 4;
            Component stance = Component.translatable("hearthbound.stance." + e.getString("stance").toLowerCase(java.util.Locale.ROOT));
            ui.chip(g, stance, rx, y + 5, col);
            Component status = Component.empty();
            if (e.getInt("caravans") > 0) status = Component.translatable("hearthbound.ui.caravans", e.getInt("caravans")).withColor(0xE0B25A);
            else if (e.contains("truce")) status = Component.translatable("hearthbound.ui.truce", e.getInt("truce")).withColor(0x9FD0FF);
            ui.clipped(g, status, rx, y + 20, cw / 2 - 8, Ui.MUTED);
            java.util.UUID other = e.getUUID("id");
            boolean war = "WAR".equals(e.getString("stance"));
            int btnW = 76;
            ui.button(g, cx + cw - btnW - 6, y + 28, btnW, 13, Component.translatable("hearthbound.ui.gift"), view.getInt("balance") >= giftCost, () -> {
                CompoundTag a = new CompoundTag();
                a.putUUID("other", other);
                act("gift", a);
            });
            ui.tooltip(cx + cw - btnW - 6, y + 28, btnW, 13, Component.translatable("hearthbound.ui.gift_tip", giftCost));
            if (war) {
                boolean can = e.getBoolean("canMediate") && view.getInt("balance") >= medCost;
                ui.button(g, cx + cw - 2 * btnW - 10, y + 28, btnW, 13, Component.translatable("hearthbound.ui.mediate"), can, () -> {
                    CompoundTag a = new CompoundTag();
                    a.putUUID("other", other);
                    act("mediate", a);
                });
                Component tip = e.contains("mediateLock") ? ClientData.json(e.getString("mediateLock")) : Component.translatable("hearthbound.ui.mediate_tip", medCost);
                ui.tooltip(cx + cw - 2 * btnW - 10, y + 28, btnW, 13, tip);
            }
            y += h + 4;
        }
        return y - start;
    }

    // ================================================================== shrine

    private int drawShrine(GuiGraphics g, int y) {
        int start = y;
        ui.wrapped(g, Component.translatable("hearthbound.ui.shrine_intro"), cx, y, cw, Ui.MUTED, 2);
        y += 24;
        ListTag list = view.getList("blessings", Tag.TAG_COMPOUND);
        int colW = (cw - 8) / 2;
        int i = 0;
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            int x = cx + (i % 2) * (colW + 8);
            int yy = y + (i / 2) * 44;
            boolean locked = e.contains("lock");
            ui.card(g, x, yy, colW, 40, ui.hover(x, yy, colW, 40));
            int col = 0xFF000000 | e.getInt("color");
            g.fill(x + 1, yy + 1, x + 3, yy + 39, col);
            ResourceLocation eff = ResourceLocation.tryParse(e.getString("effect"));
            var holder = eff == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(eff).orElse(null);
            if (holder != null) {
                TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(holder);
                g.blit(x + 6, yy + 4, 0, 18, 18, sprite);
            }
            Component name = tr(e.getString("name"));
            int amp = e.getInt("amp");
            if (amp > 0) name = name.copy().append(" " + roman(amp + 1));
            ui.clipped(g, name, x + 28, yy + 5, colW - 34, locked ? Ui.DIM : Ui.TEXT);
            ui.text(g, Component.translatable("hearthbound.ui.minutes", e.getInt("minutes")), x + 28, yy + 16, Ui.MUTED);
            ui.coins(g, e.getInt("price"), x + 6, yy + 28);
            int idx = e.getInt("index");
            ui.button(g, x + colW - 60, yy + 24, 56, 13, Component.translatable("hearthbound.ui.receive"), !locked && view.getInt("balance") >= e.getInt("price"), () -> {
                CompoundTag a = new CompoundTag();
                a.putInt("index", idx);
                act("bless", a);
            });
            if (locked) ui.tooltip(x, yy, colW - 62, 40, ClientData.json(e.getString("lock")).copy().withColor(0xE06A5A));
            i++;
        }
        return y + ((i + 1) / 2) * 44 - start;
    }

    static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    // ================================================================== people

    private int drawPeople(GuiGraphics g, int y) {
        int start = y;
        CompoundTag hire = view.getCompound("hire");
        boolean locked = hire.contains("lock");
        ui.card(g, cx, y, cw, 40, false);
        g.renderItem(new ItemStack(Items.IRON_SWORD), cx + 6, y + 6);
        ui.text(g, Component.translatable("hearthbound.ui.hire_title"), cx + 28, y + 5, Ui.TEXT);
        ui.text(g, Component.translatable("hearthbound.ui.hire_sub", hire.getInt("days"), hire.getInt("have"), hire.getInt("max")), cx + 28, y + 16, Ui.MUTED);
        if (locked) ui.clipped(g, ClientData.json(hire.getString("lock")), cx + 28, y + 27, cw - 120, Ui.BAD);
        int price = hire.getInt("cost");
        int pw = ui.coinsWidth(price);
        ui.coins(g, price, cx + cw - 76 - pw, y + 16);
        ui.button(g, cx + cw - 70, y + 13, 64, 14, Component.translatable("hearthbound.ui.hire"), !locked && view.getInt("balance") >= price, () -> act("hire"));
        y += 46;
        ui.text(g, Component.translatable("hearthbound.ui.residents", view.getInt("population")), cx, y, 0xFFE0B25A);
        y += 12;
        ListTag list = view.getList("residents", Tag.TAG_COMPOUND);
        int cols = Math.max(1, cw / 170);
        int colW = (cw - (cols - 1) * 6) / cols;
        int i = 0;
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            int x = cx + (i % cols) * (colW + 6);
            int yy = y + (i / cols) * 48;
            boolean alive = e.getBoolean("alive");
            Role role = Role.byId(e.getString("role"));
            ui.card(g, x, yy, colW, 44, false);
            g.fill(x + 4, yy + 4, x + 7, yy + 18, alive ? 0xFF000000 | role.color : 0xFF3A3F4B);
            int bond = e.getInt("bond");
            Component hearts = Component.literal("♥" + bond).withColor(0xF08CB0);
            int hw = bond > 0 ? ui.font().width(hearts) + 4 : 0;
            ui.clipped(g, Component.literal(e.getString("name")), x + 11, yy + 3, colW - 14 - hw, alive ? Ui.TEXT : Ui.DIM);
            if (bond > 0) ui.text(g, hearts, x + colW - hw, yy + 3, 0xFFF08CB0);
            Component r = alive ? Component.translatable(role.key()).withColor(role.color) : Component.translatable("hearthbound.ui.resting");
            if (alive && e.contains("persona")) {
                r = r.copy().append(Component.literal(" · ").withColor(0x6B7180)).append(PersonScreen.title(e.getString("persona"), e.getBoolean("female")).copy().withColor(0xB8A0C8));
            }
            ui.clipped(g, r, x + 11, yy + 12, colW - 14, Ui.DIM);
            if (e.contains("persona") && ui.hover(x, yy, colW, 22)) {
                java.util.List<Component> tip = new java.util.ArrayList<>();
                tip.add(PersonScreen.title(e.getString("persona"), e.getBoolean("female")).copy().withColor(0xF4D9A6));
                if (e.contains("bioFirst")) tip.add(Component.translatable(e.getString("bioFirst")).withColor(0x9AA0AC));
                if (e.contains("source")) tip.add(Component.translatable("hearthbound.person.inspired", Component.translatable(e.getString("source"))).withColor(0xB8A0C8));
                if (e.getInt("stages") > 0) tip.add(Component.translatable("hearthbound.person.chapter", Math.min(e.getInt("stages"), e.getInt("stage")), e.getInt("stages")).withColor(0xF08CB0));
                tip.add(Component.translatable("hearthbound.person.talk_hint").withColor(0x6B7180));
                ui.tooltip(x, yy, colW, 22, tip);
            }
            if (!alive && e.hasUUID("id") && view.contains("revivalCost")) {
                UUID residentId = e.getUUID("id");
                int copper = view.getInt("revivalCopper"), silver = view.getInt("revivalSilver");
                // Show the configured denominations, rather than normalising 10 copper into silver.
                g.renderItem(stack("hearthbound:copper_coin"), x + 5, yy + 25);
                ui.text(g, Component.literal(Integer.toString(copper)), x + 22, yy + 29, Ui.TEXT);
                g.renderItem(stack("hearthbound:silver_coin"), x + 43, yy + 25);
                ui.text(g, Component.literal(Integer.toString(silver)), x + 60, yy + 29, Ui.TEXT);
                boolean confirm = residentId.equals(pendingRevival);
                ui.button(g, x + colW - 70, yy + 26, 64, 14,
                        Component.translatable(confirm ? "hearthbound.revival.confirm" : "hearthbound.revival.button"),
                        view.getBoolean("canRevive") && view.getInt("balance") >= view.getInt("revivalCost"), () -> {
                            if (!residentId.equals(pendingRevival)) { pendingRevival = residentId; return; }
                            pendingRevival = null;
                            CompoundTag args = new CompoundTag(); args.putUUID("resident", residentId);
                            act("revive", args);
                        });
                ui.tooltip(x + 4, yy + 24, colW - 78, 18, java.util.List.of(Component.translatable("hearthbound.revival.price", copper, silver)));
            }
            i++;
        }
        y += ((i + cols - 1) / cols) * 48;
        return y - start;
    }

    // ================================================================== build

    private int drawBuild(GuiGraphics g, int y) {
        int start = y;
        ListTag list = view.getList("buildings", Tag.TAG_COMPOUND);
        CompoundTag current = null;
        for (Tag t : list) if (!((CompoundTag) t).getBoolean("complete")) current = (CompoundTag) t;
        CompoundTag stock = view.getCompound("stock");
        if (current != null) {
            ui.card(g, cx, y, cw, 34, false);
            g.renderItem(stack(current.getString("icon")), cx + 6, y + 9);
            ui.text(g, Component.translatable("hearthbound.ui.building_now", tr(current.getString("name"))), cx + 28, y + 5, Ui.TEXT);
            float pr = current.getFloat("progress");
            ui.bar(g, cx + 28, y + 18, cw - 70, 6, pr, ui.accent);
            ui.text(g, Math.round(pr * 100) + "%", cx + cw - 36, y + 17, Ui.MUTED);
            y += 40;
        } else if (view.contains("next")) {
            CompoundTag n = view.getCompound("next");
            ui.card(g, cx, y, cw, 50, false);
            g.renderItem(stack(n.getString("icon")), cx + 6, y + 6);
            ui.text(g, Component.translatable("hearthbound.ui.next_building", tr(n.getString("name"))), cx + 28, y + 5, Ui.TEXT);
            ui.clipped(g, Component.translatableWithFallback(n.getString("desc"), ""), cx + 28, y + 15, cw - 34, Ui.MUTED);
            CompoundTag cost = n.getCompound("cost");
            int xx = cx + 28;
            for (Resource r : Resource.values()) {
                int need = cost.getInt(r.id());
                if (need <= 0) continue;
                g.pose().pushPose();
                g.pose().translate(xx, y + 26, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.renderItem(new ItemStack(r.icon), 0, 0);
                g.pose().popPose();
                boolean ok = stock.getInt(r.id()) >= need;
                String s = stock.getInt(r.id()) + "/" + need;
                ui.text(g, s, xx + 14, y + 29, ok ? Ui.GOOD : Ui.BAD);
                ui.tooltip(xx, y + 26, 14 + ui.font().width(s), 12, Component.translatable(r.key()));
                xx += 22 + ui.font().width(s);
            }
            if (n.contains("blocker")) ui.clipped(g, ClientData.json(n.getString("blocker")), cx + 28, y + 40, cw - 34, 0xFFE0B25A);
            y += 56;
        } else if (view.getBoolean("planDone")) {
            ui.card(g, cx, y, cw, 22, false);
            ui.text(g, Component.translatable("hearthbound.ui.plan_done"), cx + 8, y + 7, Ui.GOLD);
            y += 28;
        }
        if (view.contains("planChoices")) {
            ui.text(g, Component.translatable("hearthbound.ui.lord_plan"), cx, y, 0xFFFFD24A);
            y += 12;
            int xx = cx;
            for (Tag t : view.getList("planChoices", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) t;
                boolean chosen = e.getBoolean("chosen");
                boolean hov = ui.hover(xx, y, 22, 22);
                ui.box(g, xx, y, 22, 22, chosen ? Ui.alpha(0xFFFFD24A, 0.4f) : (hov ? Ui.CARD_HOVER : Ui.CARD));
                if (chosen) ui.outline(g, xx, y, 22, 22, 0xFFFFD24A);
                g.renderItem(stack(e.getString("icon")), xx + 3, y + 3);
                String bid = e.getString("id");
                ui.tooltip(xx, y, 22, 22, List.of(tr(e.getString("name")), Component.translatable(chosen ? "hearthbound.ui.plan_clear" : "hearthbound.ui.plan_pick").withColor(0x9AA0AC)));
                ui.region(xx, y, 22, 22, () -> {
                    CompoundTag a = new CompoundTag();
                    a.putString("id", chosen ? "" : bid);
                    act("plan", a);
                });
                xx += 25;
                if (xx + 22 > cx + cw) {
                    xx = cx;
                    y += 25;
                }
            }
            y += 30;
        }
        ui.text(g, Component.translatable("hearthbound.ui.buildings_list", view.getInt("buildingsDone")), cx, y, 0xFFE0B25A);
        y += 12;
        int cols = Math.max(2, cw / 140);
        int colW = (cw - (cols - 1) * 6) / cols;
        int i = 0;
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            int x = cx + (i % cols) * (colW + 6);
            int yy = y + (i / cols) * 24;
            ui.card(g, x, yy, colW, 20, false);
            g.pose().pushPose();
            g.pose().translate(x + 3, yy + 2, 0);
            g.renderItem(stack(e.getString("icon")), 0, 0);
            g.pose().popPose();
            boolean done = e.getBoolean("complete");
            ui.clipped(g, tr(e.getString("name")), x + 22, yy + 6, colW - 40, done ? Ui.TEXT : 0xFFE0B25A);
            ui.text(g, done ? "✔" : Math.round(e.getFloat("progress") * 100) + "%", x + colW - 16 - (done ? 0 : 8), yy + 6, done ? Ui.GOOD : Ui.MUTED);
            i++;
        }
        y += ((i + cols - 1) / cols) * 24;
        return y - start;
    }

    // ================================================================== donate

    private int drawDonate(GuiGraphics g, int y) {
        int start = y;
        y += ui.wrapped(g, Component.translatable("hearthbound.ui.donate_intro"), cx, y, cw, Ui.MUTED, 3) + 6;
        CompoundTag don = view.getCompound("donate");
        CompoundTag stock = view.getCompound("stock");
        int colW = (cw - 8) / 2;
        int i = 0;
        for (Resource r : Resource.values()) {
            int x = cx + (i % 2) * (colW + 8);
            int yy = y + (i / 2) * 50;
            ui.card(g, x, yy, colW, 46, ui.hover(x, yy, colW, 46));
            g.fill(x + 1, yy + 1, x + 3, yy + 45, 0xFF000000 | r.color);
            g.pose().pushPose();
            g.pose().translate(x + 8, yy + 6, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(new ItemStack(r.icon), 0, 0);
            g.pose().popPose();
            ui.text(g, Component.translatable(r.key()), x + 36, yy + 6, 0xFF000000 | r.color);
            ui.text(g, Component.translatable("hearthbound.ui.village_has", stock.getInt(r.id())), x + 36, yy + 17, Ui.MUTED);
            int pts = don.getInt(r.id());
            ui.text(g, Component.translatable("hearthbound.ui.you_can_give", pts), x + 36, yy + 28, pts > 0 ? Ui.GOOD : Ui.DIM);
            String rid = r.id();
            ui.button(g, x + colW - 58, yy + 26, 52, 14, Component.translatable("hearthbound.ui.donate"), pts > 0, () -> {
                CompoundTag a = new CompoundTag();
                a.putString("resource", rid);
                act("donate", a);
            });
            ui.tooltip(x, yy, colW - 60, 46, Component.translatable("hearthbound.ui.donate_tip." + rid));
            i++;
        }
        y += ((i + 1) / 2) * 50;
        return y - start;
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && ui.click(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int s = scroll.getOrDefault(tab, 0) - (int) (sy * 18);
        scroll.put(tab, Ui.clampScroll(s, contentH.getOrDefault(tab, 0), ch));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Minecraft.getInstance().options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        List<String> tabs = tabs();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            int i = tabs.indexOf(tab);
            tab = tabs.get(Math.floorMod(i + (hasShiftDown() ? -1 : 1), tabs.size()));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}

