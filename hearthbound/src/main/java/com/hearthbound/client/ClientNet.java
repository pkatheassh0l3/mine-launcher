package com.hearthbound.client;

import com.hearthbound.client.gui.CharacterScreen;
import com.hearthbound.client.gui.ClassScreen;
import com.hearthbound.client.gui.VillageScreen;
import com.hearthbound.config.HBClientConfig;
import com.hearthbound.rpg.Rank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Handles messages from the server. */
public final class ClientNet {
    private ClientNet() {}

    public static void handle(String kind, CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case "sync" -> {
                ClientData.load(t);
                if (mc.screen instanceof CharacterScreen cs) cs.onSync();
            }
            case "village" -> {
                if (mc.screen instanceof VillageScreen vs && vs.sameVillage(t)) {
                    vs.update(t);
                    return;
                }
                if (t.getBoolean("refresh")) return; // refresh for a closed screen
                VillageScreen screen = new VillageScreen(t);
                if (ClientData.DATA.clazz == null && !ClientData.DATA.introSeen) {
                    mc.setScreen(new ClassScreen(screen));
                } else {
                    mc.setScreen(screen);
                }
                if (HBClientConfig.SOUNDS.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
            }
            case "person" -> {
                if (mc.screen instanceof com.hearthbound.client.gui.PersonScreen ps && ps.samePerson(t)) {
                    ps.update(t);
                    return;
                }
                if (t.getBoolean("refresh")) return;
                mc.setScreen(new com.hearthbound.client.gui.PersonScreen(t));
                if (HBClientConfig.SOUNDS.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
            }
            case "project" -> {
                if (mc.screen instanceof com.hearthbound.client.gui.ProjectScreen ps && ps.samePos(t)) {
                    ps.update(t);
                    return;
                }
                if (mc.screen != null && !(mc.screen instanceof com.hearthbound.client.gui.ProjectScreen)) return;
                mc.setScreen(new com.hearthbound.client.gui.ProjectScreen(t));
                if (HBClientConfig.SOUNDS.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
            }
            case "project_close" -> {
                if (mc.screen instanceof com.hearthbound.client.gui.ProjectScreen) mc.setScreen(null);
            }
            case "banner" -> Hud.banner(ClientData.json(t.getString("title")), ClientData.json(t.getString("sub")), t.getInt("color"));
            case "notify" -> Hud.notify(ClientData.json(t.getString("text")), t.getInt("color"));
            case "enter" -> Hud.enter(t.getString("name"), Component.translatable(t.getString("culture")), t.getInt("color"),
                    Rank.byId(t.getString("rank")), t.getInt("tier"), t.getString("lord"));
            case "leave" -> Hud.leftVillage();
            default -> {
            }
        }
    }
}
