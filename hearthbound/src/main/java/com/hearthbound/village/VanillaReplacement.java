package com.hearthbound.village;

import com.hearthbound.Hearthbound;
import com.hearthbound.config.HBCommonConfig;
import com.hearthbound.data.Culture;
import com.hearthbound.data.HBData;
import com.hearthbound.entity.SettlerEntity;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.world.PlayerBuilds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Replaces vanilla villagers with settlers. A villager of an existing vanilla village turns
 * into a settler of the matching culture; the first one found "adopts" the vanilla village:
 * a Hearthbound village is registered at its meeting point and a hearth is lit next to the bell,
 * so it keeps growing with new buildings around the old houses.
 */
public final class VanillaReplacement {
    private VanillaReplacement() {}

    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getEntity() instanceof Villager vil) || !(e.getLevel() instanceof ServerLevel level)) return;
        try {
            if (!HBCommonConfig.REPLACE_VILLAGERS.get()) return;
        } catch (Exception ex) {
            return;
        }
        if (HBData.cultures().isEmpty()) return; // datapacks not loaded yet
        e.setCanceled(true);
        convert(level, vil);
    }

    static Role roleFor(Villager v) {
        if (v.isBaby()) return Role.VILLAGER;
        VillagerProfession p = v.getVillagerData().getProfession();
        if (p == VillagerProfession.FARMER || p == VillagerProfession.FISHERMAN || p == VillagerProfession.SHEPHERD) return Role.FARMER;
        if (p == VillagerProfession.ARMORER || p == VillagerProfession.WEAPONSMITH || p == VillagerProfession.TOOLSMITH) return Role.SMITH;
        if (p == VillagerProfession.LIBRARIAN) return Role.MAGE;
        if (p == VillagerProfession.CLERIC) return Role.PRIEST;
        if (p == VillagerProfession.BUTCHER) return Role.INNKEEPER;
        if (p == VillagerProfession.MASON) return Role.BUILDER;
        if (p == VillagerProfession.CARTOGRAPHER || p == VillagerProfession.FLETCHER || p == VillagerProfession.LEATHERWORKER) return Role.MERCHANT;
        return Role.VILLAGER;
    }

    private static void convert(ServerLevel level, Villager vil) {
        BlockPos pos = vil.blockPosition();
        BlockPos meeting = null;
        try {
            Optional<GlobalPos> mp = vil.getBrain().getMemoryInternal(MemoryModuleType.MEETING_POINT);
            if (mp != null && mp.isPresent() && mp.get().dimension().equals(level.dimension())) meeting = mp.get().pos();
        } catch (Exception ignored) {
        }
        VillageData data = VillageData.get(level.getServer());
        Village v = data.at(level, pos);
        if (v == null) v = data.nearest(level, meeting != null ? meeting : pos, 96);
        if (v == null && HBCommonConfig.ADOPT_VANILLA_VILLAGES.get()) {
            BlockPos anchor = meeting != null ? meeting : pos;
            Culture c = VillageManager.pickCulture(level, anchor, level.random);
            if (c == null) {
                List<Culture> all = new ArrayList<>(HBData.cultures());
                c = all.get(level.random.nextInt(all.size()));
            }
            v = VillageManager.adopt(level, c, anchor);
        }
        Culture c = v != null ? v.culture() : VillageManager.pickCulture(level, pos, level.random);
        if (c == null) c = HBData.cultures().iterator().next();
        Role role = roleFor(vil);
        boolean female = level.random.nextBoolean();
        String name = vil.hasCustomName() && vil.getCustomName() != null ? vil.getCustomName().getString() : c.randomName(level.random, female);
        final Village village = v;
        final Culture culture = c;
        double x = vil.getX(), y = vil.getY(), z = vil.getZ();
        float yaw = vil.getYRot();
        level.getServer().execute(() -> {
            SettlerEntity s = ModRegistry.SETTLER.get().create(level);
            if (s == null) return;
            s.moveTo(x, y, z, yaw, 0);
            s.setup(culture, role, name, female, level.random.nextInt(2));
            if (village != null) {
                Resident r = new Resident();
                r.role = role;
                r.name = name;
                r.female = female;
                r.variant = 0;
                r.entity = s.getUUID();
                village.residents.add(r);
                s.bindVillage(village.id, village.center, village.radius(), null, null);
                VillageData.get(level.getServer()).setDirty();
            }
            level.addFreshEntity(s);
        });
    }

    /** Finds a free, solid spot near the bell for the hearth. */
    static BlockPos hearthSpot(ServerLevel level, BlockPos anchor) {
        for (int r = 1; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos p = anchor.offset(dx, dy, dz);
                        BlockState at = level.getBlockState(p);
                        BlockState below = level.getBlockState(p.below());
                        if ((at.isAir() || at.canBeReplaced() && at.getFluidState().isEmpty())
                                && level.getBlockState(p.above()).isAir()
                                && below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                                && !PlayerBuilds.isPlayerBlock(level, p)) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    static void lightHearth(ServerLevel level, Village v) {
        level.getServer().execute(() -> {
            BlockPos spot = hearthSpot(level, v.center);
            if (spot == null) {
                Hearthbound.LOGGER.info("Adopted {} without a hearth (no free spot near the bell)", v.name);
                return;
            }
            level.setBlock(spot, ModRegistry.VILLAGE_HEARTH.get().defaultBlockState(), Block.UPDATE_ALL);
        });
    }
}
