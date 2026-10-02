package com.hearthbound.village;

import com.hearthbound.compat.Compat;
import com.hearthbound.config.HBConfig;
import com.hearthbound.rpg.Wallet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Revival is a server-side transaction; the client supplies only a stable resident ID. */
public final class ResidentRevival {
    private ResidentRevival() {}

    public enum Result { SUCCESS, UNAVAILABLE, ALREADY_ALIVE, INSUFFICIENT, SPAWN_FAILED }

    public static int cost() {
        return HBConfig.REVIVAL_COPPER.get() + 10 * HBConfig.REVIVAL_SILVER.get();
    }

    public static boolean allowed(ServerPlayer player, Village village) {
        return HBConfig.PAID_REVIVAL.get() && VillageService.near(player, village)
                && Compat.ageReached(player, HBConfig.ASC_AGE_TRADE.get());
    }

    public static Result revive(ServerPlayer player, Village village, UUID id) {
        if (!allowed(player, village) || VillageData.get(player.server).get(village.id) != village)
            return Result.UNAVAILABLE;
        Resident resident = village.residents.stream().filter(r -> r.id.equals(id)).findFirst().orElse(null);
        if (resident == null) return Result.UNAVAILABLE;
        if (resident.alive()) return Result.ALREADY_ALIVE;
        var level = player.server.getLevel(village.dimension);
        var culture = village.culture();
        if (level == null || culture == null || !level.hasChunkAt(village.center)) return Result.SPAWN_FAILED;
        // A stale death record must never create a second living copy.
        if (resident.entity != null && level.getEntity(resident.entity) instanceof net.minecraft.world.entity.LivingEntity old
                && old.isAlive()) return Result.ALREADY_ALIVE;
        int price = cost();
        if (Wallet.balance(player) < price) return Result.INSUFFICIENT;
        UUID oldEntity = resident.entity;
        if (!VillageManager.spawnResident(level, village, resident, culture)) return Result.SPAWN_FAILED;
        boolean paid = false;
        try {
            paid = Wallet.pay(player, price);
            if (!paid) return Result.INSUFFICIENT;
            resident.deadSince = -1;
            VillageData.get(player.server).setDirty();
            return Result.SUCCESS;
        } finally {
            if (!paid) {
                var spawned = level.getEntity(resident.entity);
                if (spawned != null) spawned.discard();
                resident.entity = oldEntity;
            }
        }
    }

    public static void handle(ServerPlayer player, Village village, UUID id) {
        Result result = revive(player, village, id);
        player.displayClientMessage(Component.translatable("hearthbound.revival." + result.name().toLowerCase(java.util.Locale.ROOT)), false);
        if (result != Result.SUCCESS) VillageService.deny(player);
        VillageService.refresh(player, village);
    }
}
