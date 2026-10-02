package com.hearthbound.client.gui;

import com.hearthbound.client.ClientData;
import com.hearthbound.config.HBClientConfig;
import com.hearthbound.network.Net;
import com.hearthbound.village.Role;
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
import net.minecraft.world.item.Items;


/**
 * Talking to one settler: who they are, how close you are, their personal quest and what
 * a friend can ask of them (travel together, move to your village).
 */
public class PersonScreen extends Screen {
    private static final int PINK = 0xFFF08CB0;
    private final Ui ui = new Ui();
    private CompoundTag view;
    private final long opened;
    private int x0, y0, w, h;
    private int homeIndex;

    public PersonScreen(CompoundTag view) {
        super(Component.literal(view.getString("name")));
        this.view = view;
        this.opened = net.minecraft.Util.getMillis();
    }

    public boolean samePerson(CompoundTag t) {
        return t.hasUUID("entity") && view.hasUUID("entity") && t.getUUID("entity").equals(view.getUUID("entity"));
    }

    public void update(CompoundTag t) {
        this.view = t;
    }

    /** Persona title, with the female form when the language has one. */
    public static Component title(String key, boolean female) {
        if (female && net.minecraft.client.resources.language.I18n.exists(key + ".title.f")) return Component.translatable(key + ".title.f");
        return Component.translatable(key + ".title");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String key() {
        return view.getString("key");
    }

    private void act(String action, CompoundTag args) {
        args.putUUID("entity", view.getUUID("entity"));
        Net.action(action, args);
        if (HBClientConfig.SOUNDS.get()) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
    }

    private void act(String action) {
        act(action, new CompoundTag());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x90000000, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        w = Math.min(width - 16, 440);
        h = Math.min(height - 16, 262);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        ui.begin(mx, my);
        int color = 0xFF000000 | view.getInt("color");
        ui.accent = HBClientConfig.CULTURE_ACCENT.get() ? color : HBClientConfig.accent();
        float anim = HBClientConfig.ANIMATIONS.get() ? Math.min(1f, (net.minecraft.Util.getMillis() - opened) / 180f) : 1f;
        g.pose().pushPose();
        g.pose().translate(0, (1 - anim) * 8, 0);

        ui.panel(g, x0, y0, w, h);
        ui.header(g, x0, y0, w, 44, ui.accent);
        drawHeader(g);
        int y = y0 + 44;
        // what they say today
        g.fill(x0 + 1, y, x0 + w - 1, y + 16, 0x40000000);
        Role role = Role.byId(view.getString("role"));
        Component line = Component.empty().append(Component.literal(view.getString("name")).withColor(role.color))
                .append(Component.literal(": ").withColor(0x9AA0AC))
                .append(Component.literal("«").append(Component.translatable(view.getString("line"))).append("»").withStyle(s -> s.withItalic(true)).withColor(0xE8E6E1));
        ui.clipped(g, line, x0 + 8, y + 4, w - 16, Ui.TEXT);
        y += 22;

        int half = (w - 24) / 2;
        int lx = x0 + 8, rx = x0 + 16 + half;
        int bottom = y0 + h - 8;
        drawStory(g, lx, y, half, bottom - 60 - y);
        drawActions(g, lx, bottom - 42, half);
        drawQuest(g, rx, y, half, bottom - y);

        g.pose().popPose();
        ui.drawTooltips(g);
    }

    private void drawHeader(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(x0 + 8, y0 + 6, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(VillageScreen.stack(view.getString("icon")), 0, 0);
        g.pose().popPose();
        int tx = x0 + 46;
        ui.scaled(g, Component.literal(view.getString("name")), tx, y0 + 7, 1.6f, 0xFFFFFFFF, true);
        Role role = Role.byId(view.getString("role"));
        Component sub = Component.empty()
                .append(title(key(), view.getBoolean("female")).copy().withColor(0xF4D9A6))
                .append(Component.literal("  ·  ").withColor(0x9AA0AC))
                .append(Component.translatable(role.key()).withColor(role.color))
                .append(Component.literal("  ·  ").withColor(0x9AA0AC))
                .append(Component.literal(view.getString("villageName")).withColor(0xE8E6E1));
        ui.clipped(g, sub, tx, y0 + 28, w - 46 - 132, Ui.TEXT);

        int level = view.getInt("level");
        Component lvl = Component.translatable("hearthbound.bond.level." + level);
        int cw = ui.font().width(lvl) + 8;
        ui.chip(g, lvl, x0 + w - 8 - cw, y0 + 7, PINK);
        int bond = view.getInt("bond"), at = view.getInt("levelAt"), next = view.getInt("nextAt");
        int bx = x0 + w - 128;
        ui.bar(g, bx, y0 + 23, 120, 5, next <= at ? 1f : (bond - at) / (float) (next - at), PINK);
        Component hearts = Component.literal("♥ " + bond + " / 100").withColor(PINK);
        ui.text(g, hearts, x0 + w - 8 - ui.font().width(hearts), y0 + 31, PINK);
        ui.tooltip(bx, y0 + 20, 120, 20, java.util.List.of(
                Component.translatable("hearthbound.bond.tip", bond),
                Component.translatable("hearthbound.bond.tip_follow", view.getInt("followAt")).withColor(0x9AA0AC),
                Component.translatable("hearthbound.bond.tip_move", view.getInt("moveAt")).withColor(0x9AA0AC)));
    }

    private void drawStory(GuiGraphics g, int x, int y, int cw, int ch) {
        ui.text(g, Component.translatable("hearthbound.person.story"), x, y, 0xFFE0B25A);
        y += 12;
        int end = y + ch - 20;
        ListTag bios = view.getList("bioKeys", Tag.TAG_STRING);
        if (view.contains("source")) end -= 10;
        if (bios.isEmpty()) {
            y += ui.wrapped(g, Component.translatable("hearthbound.persona.none.bio.0"), x, y, cw, Ui.MUTED, 3) + 4;
        }
        for (int i = 0; i < bios.size() && y < end; i++) {
            int lines = Math.max(1, (end - y) / 10);
            y += ui.wrapped(g, Component.translatable(bios.getString(i)), x, y, cw, i == 0 ? Ui.TEXT : Ui.MUTED, Math.min(5, lines)) + 4;
        }
        if (view.getBoolean("moreBio") && y < end) {
            ui.text(g, Component.translatable("hearthbound.person.more_story").withStyle(s -> s.withItalic(true)), x, y, Ui.DIM);
        }
        if (view.contains("source")) {
            Component src = Component.translatable("hearthbound.person.inspired", Component.translatable(view.getString("source")));
            ui.clipped(g, src.copy().withStyle(s -> s.withItalic(true)), x, end + 2, cw, 0xFFB8A0C8);
            ui.tooltip(x, end, cw, 10, src);
            end += 10;
        }
        // likes
        int ly = end + 4;
        ui.text(g, Component.translatable("hearthbound.person.likes"), x, ly + 4, Ui.MUTED);
        int ix = x + ui.font().width(Component.translatable("hearthbound.person.likes")) + 6;
        ListTag likes = view.getList("likes", Tag.TAG_STRING);
        if (likes.isEmpty()) {
            ui.text(g, Component.translatable(view.getBoolean("likesHidden") ? "hearthbound.person.likes_hidden" : "hearthbound.person.likes_none"), ix, ly + 4, Ui.DIM);
        }
        for (Tag t : likes) {
            String s = t.getAsString();
            if (s.startsWith("#")) {
                Component tag = Component.translatableWithFallback("hearthbound.tag." + s.substring(1).replace(':', '.').replace('/', '.'), s);
                int tw = ui.chip(g, tag, ix, ly + 2, 0xFFB8A0C8);
                ix += tw + 3;
            } else {
                ui.itemTip(g, VillageScreen.stack(s), ix, ly);
                ix += 18;
            }
        }
    }

    private void drawActions(GuiGraphics g, int x, int y, int cw) {
        int bw = (cw - 4) / 2;
        boolean talked = view.getBoolean("talked");
        boolean dlgNew = view.getBoolean("dlgNew");
        Component talkLabel = dlgNew ? Component.translatable("hearthbound.person.talk_new")
                : Component.translatable(talked ? "hearthbound.person.talk_again" : "hearthbound.person.talk");
        ui.button(g, x, y, bw, 14, talkLabel, true, () -> act("person_talk"));
        if (view.getInt("dlgTotal") > 0) {
            ui.tooltip(x, y, bw, 14, Component.translatable("hearthbound.person.dialogues", view.getInt("dlgHeard"), view.getInt("dlgTotal")));
        }
        ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        boolean gifted = view.getBoolean("gifted");
        ui.button(g, x + bw + 4, y, bw, 14, gifted ? Component.translatable("hearthbound.person.gifted")
                : held.isEmpty() ? Component.translatable("hearthbound.person.gift_none") : Component.translatable("hearthbound.person.gift", held.getHoverName()),
                !gifted && !held.isEmpty(), () -> act("person_gift"));
        if (!gifted && held.isEmpty()) ui.tooltip(x + bw + 4, y, bw, 14, Component.translatable("hearthbound.person.gift_tip"));
        y += 16;
        int bond = view.getInt("bond");
        if (view.getBoolean("following")) {
            ui.button(g, x, y, bw, 14, Component.translatable(view.getBoolean("waiting") ? "hearthbound.person.resume" : "hearthbound.person.wait"), true, () -> act("person_wait"));
            ui.ghost(g, x + bw + 4, y, bw, 14, Component.translatable("hearthbound.person.go_home"), true, () -> act("person_home"));
        } else {
            boolean can = bond >= view.getInt("followAt") && !view.getBoolean("otherFollow") && view.getInt("followers") < view.getInt("maxFollowers");
            ui.button(g, x, y, bw, 14, Component.translatable("hearthbound.person.follow"), can, () -> act("person_follow"));
            Component why = bond < view.getInt("followAt") ? Component.translatable("hearthbound.person.need_bond", view.getInt("followAt"))
                    : view.getBoolean("otherFollow") ? Component.translatable("hearthbound.person.busy")
                    : Component.translatable("hearthbound.bond.too_many", view.getInt("maxFollowers"));
            if (!can) ui.tooltip(x, y, bw, 14, why);
            ui.ghost(g, x + bw + 4, y, bw, 14, Component.translatable("hearthbound.person.village"), true, () -> act("person_village"));
        }
        y += 16;
        ListTag homes = view.getList("homes", Tag.TAG_COMPOUND);
        if (!homes.isEmpty()) {
            boolean can = bond >= view.getInt("moveAt");
            if (homeIndex >= homes.size()) homeIndex = 0;
            CompoundTag dest = homes.getCompound(homeIndex);
            int mw = homes.size() > 1 ? cw - 18 : cw;
            ui.button(g, x, y, mw, 14, Component.translatable("hearthbound.person.move", dest.getString("name")), can, () -> {
                CompoundTag a = new CompoundTag();
                a.putUUID("to", dest.getUUID("id"));
                act("person_move", a);
            });
            if (!can) ui.tooltip(x, y, mw, 14, Component.translatable("hearthbound.person.need_bond", view.getInt("moveAt")));
            if (homes.size() > 1) {
                ui.ghost(g, x + cw - 14, y, 14, 14, Component.literal("⟳"), true, () -> homeIndex = (homeIndex + 1) % homes.size());
                ui.tooltip(x + cw - 14, y, 14, 14, Component.translatable("hearthbound.person.move_more"));
            }
        } else if (view.getBoolean("following")) {
            ui.ghost(g, x, y, cw, 14, Component.translatable("hearthbound.person.village"), true, () -> act("person_village"));
        } else {
            ui.text(g, Component.translatable("hearthbound.person.move_hint").withStyle(s -> s.withItalic(true)), x, y + 3, Ui.DIM);
            ui.tooltip(x, y, cw, 14, Component.translatable("hearthbound.person.move_hint_tip", view.getInt("moveAt")));
        }
    }

    private void drawQuest(GuiGraphics g, int x, int y, int cw, int ch) {
        CompoundTag q = view.getCompound("quest");
        String state = q.getString("state");
        ui.card(g, x, y, cw, ch, false);
        g.fill(x, y, x + 2, y + ch, Ui.alpha(PINK, 0.8f));
        int ix = x + 8, iw = cw - 14;
        int yy = y + 6;
        int stages = view.getInt("stages");
        if (stages > 0) {
            Component chap = state.equals("done") ? Component.translatable("hearthbound.person.story_done")
                    : Component.translatable("hearthbound.person.chapter", Math.min(stages, view.getInt("stage") + 1), stages);
            ui.text(g, chap, ix, yy, PINK);
            // chapter pips
            for (int i = 0; i < stages; i++) {
                int px = x + cw - 8 - (stages - i) * 9;
                g.fill(px, yy + 1, px + 6, yy + 7, i < view.getInt("stage") ? PINK : 0xFF3A3F4B);
            }
            yy += 13;
        }
        switch (state) {
            case "none" -> ui.wrapped(g, Component.translatable("hearthbound.person.no_quest"), ix, yy, iw, Ui.MUTED, 6);
            case "done" -> {
                yy += ui.wrapped(g, Component.translatable(q.getString("text")), ix, yy, iw, Ui.TEXT, 12) + 6;
                ui.text(g, Component.translatable("hearthbound.person.thanks").withStyle(s -> s.withItalic(true)), ix, yy, Ui.DIM);
            }
            default -> {
                g.renderItem(VillageScreen.stack(q.getString("icon")), ix, yy);
                ui.clipped(g, Component.translatable(q.getString("title")), ix + 20, yy + 4, iw - 20, 0xFFFFFFFF);
                yy += 20;
                int bottomArea = y + ch - 44;
                int lines = Math.max(2, (bottomArea - yy) / 10);
                yy += ui.wrapped(g, Component.translatable(q.getString("text")), ix, yy, iw, Ui.MUTED, lines) + 4;
                int by = y + ch - 40;
                if (state.equals("active") || state.equals("ready")) {
                    Component obj = ClientData.json(q.getString("objective"));
                    ui.clipped(g, obj, ix, by - 12, iw, Ui.TEXT);
                    int count = Math.max(1, q.getInt("count"));
                    ui.bar(g, ix, by - 2, iw, 5, q.getInt("progress") / (float) count, state.equals("ready") ? Ui.GOOD : PINK);
                }
                // rewards
                int rx = ix;
                int ry = by + 6;
                Component heart = Component.literal("♥ +" + q.getInt("bondReward")).withColor(PINK);
                ui.text(g, heart, rx, ry + 3, PINK);
                rx += ui.font().width(heart) + 8;
                if (q.getInt("coins") > 0) rx += ui.coins(g, q.getInt("coins"), rx, ry + 3) + 8;
                Component xp = Component.literal(q.getInt("xp") + " XP").withColor(0x8FD18F);
                ui.text(g, xp, rx, ry + 3, 0xFF8FD18F);
                rx += ui.font().width(xp) + 6;
                if (q.getBoolean("keepsake")) {
                    g.renderItem(new ItemStack(Items.NETHER_STAR), rx, ry - 1);
                    ui.tooltip(rx, ry - 1, 16, 16, Component.translatable("hearthbound.person.keepsake"));
                }
                int bw = 70;
                int bx = x + cw - bw - 6;
                int bby = y + ch - 18;
                switch (state) {
                    case "available" -> ui.button(g, bx, bby, bw, 14, Component.translatable("hearthbound.ui.accept"), true, () -> act("person_accept"));
                    case "ready" -> ui.button(g, bx, bby, bw, 14, Component.translatable("hearthbound.ui.turn_in"), true, () -> act("person_turnin"));
                    case "active" -> {
                        boolean tracked = q.hasUUID("contract") && q.getUUID("contract").equals(ClientData.DATA.tracked);
                        ui.ghost(g, bx, bby, bw, 14, Component.translatable(tracked ? "hearthbound.ui.tracked" : "hearthbound.ui.track"), !tracked, () -> act("person_track"));
                    }
                    case "locked" -> {
                        Component need = Component.translatable("hearthbound.person.need_bond", q.getInt("need"));
                        ui.clipped(g, need, ix, bby + 3, cw - 16, Ui.BAD);
                    }
                    default -> {
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && ui.click(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

}
