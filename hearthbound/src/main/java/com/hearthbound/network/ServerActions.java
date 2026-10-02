package com.hearthbound.network;

import com.hearthbound.rpg.PlayerClass;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.rpg.Skill;
import com.hearthbound.village.Citizenship;
import com.hearthbound.village.Contracts;
import com.hearthbound.village.Projects;
import com.hearthbound.village.Resource;
import com.hearthbound.village.Village;
import com.hearthbound.village.VillageData;
import com.hearthbound.village.VillageService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Dispatches client actions. Every village action re-checks distance and permissions server side. */
public final class ServerActions {
    private ServerActions() {}

    public static void handle(ServerPlayer p, String action, CompoundTag d) {
        switch (action) {
            case "class" -> Rpg.chooseClass(p, PlayerClass.byId(d.getString("id")));
            case "skill" -> Rpg.raise(p, Skill.byId(d.getString("id")));
            case "sync" -> Rpg.sync(p);
            case "intro_seen" -> {
                Rpg.data(p).introSeen = true;
                Rpg.sync(p);
            }
            case "track" -> {
                PlayerData pd = Rpg.data(p);
                if (d.hasUUID("id")) pd.tracked = d.getUUID("id");
                Rpg.sync(p);
            }
            case "abandon" -> {
                if (d.hasUUID("id")) Contracts.abandon(p, d.getUUID("id"));
                Village v = village(p, d);
                if (v != null) VillageService.refresh(p, v);
                else Rpg.sync(p);
            }
            default -> {
                if (action.startsWith("person_")) com.hearthbound.village.Persons.handle(p, action, d);
                else if (action.startsWith("stone_")) stoneAction(p, action, d);
                else villageAction(p, action, d);
            }
        }
    }

    /** Actions from the foundation stone screen: the player must be next to the stone. */
    private static void stoneAction(ServerPlayer p, String action, CompoundTag d) {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(d.getLong("pos"));
        if (p.blockPosition().distSqr(pos) > 12 * 12) return;
        switch (action) {
            case "stone_grow" -> Projects.resize(p, pos, 1);
            case "stone_shrink" -> Projects.resize(p, pos, -1);
            case "stone_finish" -> Projects.finish(p, pos);
            case "stone_refresh" -> Projects.refresh(p, pos);
            default -> {
            }
        }
    }

    private static Village village(ServerPlayer p, CompoundTag d) {
        if (!d.hasUUID("village")) return null;
        Village v = VillageService.byId(p, d.getUUID("village"));
        if (v == null || !VillageService.near(p, v)) return null;
        return v;
    }

    private static void villageAction(ServerPlayer p, String action, CompoundTag d) {
        Village v = village(p, d);
        if (v == null) return;
        switch (action) {
            case "open" -> VillageService.open(p, v, d.getString("tab"));
            case "trade" -> VillageService.trade(p, v, d.getInt("index"), d.getInt("times"));
            case "bless" -> VillageService.bless(p, v, d.getInt("index"));
            case "accept" -> {
                if (d.hasUUID("id") && Contracts.accept(p, v, d.getUUID("id"))) {
                    VillageData.get(p.server).setDirty();
                }
                VillageService.refresh(p, v);
            }
            case "turnin" -> {
                if (d.hasUUID("id")) Contracts.turnIn(p, v, d.getUUID("id"));
                VillageService.refresh(p, v);
            }
            case "donate" -> VillageService.donate(p, v, Resource.byId(d.getString("resource")));
            case "hire" -> VillageService.hire(p, v);
            case "revive" -> {
                if (d.hasUUID("resident")) com.hearthbound.village.ResidentRevival.handle(p, v, d.getUUID("resident"));
            }
            case "plan" -> VillageService.choosePlan(p, v, d.getString("id").isEmpty() ? null : ResourceLocation.tryParse(d.getString("id")));
            case "tribute" -> VillageService.collectTribute(p, v);
            case "claim_lord" -> VillageService.claimLordship(p, v);
            case "gift" -> VillageService.gift(p, v, d.hasUUID("other") ? VillageService.byId(p, d.getUUID("other")) : null);
            case "project_take" -> Projects.take(p, v, d.getString("id"));
            case "project_abandon" -> Projects.abandon(p, v, d.getString("id"));
            case "citizen" -> Citizenship.join(p, v);
            case "renounce" -> Citizenship.renounce(p, v);
            case "flag_set" -> VillageService.flagSet(p, v);
            case "flag_reset" -> VillageService.flagReset(p, v);
            case "flag_copy" -> VillageService.flagCopy(p, v);
            case "mediate" -> VillageService.mediate(p, v, d.hasUUID("other") ? VillageService.byId(p, d.getUUID("other")) : null);
            default -> {
            }
        }
    }
}
