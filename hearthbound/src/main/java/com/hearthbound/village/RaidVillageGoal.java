package com.hearthbound.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Given to the mobs of a village raid: march on the village and go from house to house.
 * Pillagers ({@code loots}) ransack every house they reach. Paths are planned in hops so the
 * march works from outside the follow range.
 */
public class RaidVillageGoal extends Goal {
    private final PathfinderMob mob;
    private final UUID villageId;
    private final boolean loots;
    private BlockPos waypoint;
    private int recalc;
    private int stuck;
    private int lingering;
    private int budget;

    public RaidVillageGoal(PathfinderMob mob, UUID villageId, boolean loots) {
        this.mob = mob;
        this.villageId = villageId;
        this.loots = loots;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private Village village() {
        if (!(mob.level() instanceof ServerLevel sl)) return null;
        return VillageData.get(sl.getServer()).get(villageId);
    }

    private boolean raidOn() {
        Village v = village();
        return v != null && VillageManager.inCurrentRaid(mob, v);
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null && !mob.isVehicle() && mob.level() instanceof ServerLevel && raidOn();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && !mob.isVehicle() && raidOn();
    }

    @Override
    public void start() {
        recalc = 0;
        if (waypoint == null) pick();
    }

    private void pick() {
        Village v = village();
        waypoint = v == null ? null : VillageManager.raidWaypoint(v, mob.getRandom());
        stuck = 0;
        lingering = 0;
        budget = 0;
    }

    @Override
    public void tick() {
        if (waypoint == null) {
            pick();
            return;
        }
        double hx = waypoint.getX() + 0.5 - mob.getX(), hz = waypoint.getZ() + 0.5 - mob.getZ();
        boolean arrived = hx * hx + hz * hz < 9 && Math.abs(waypoint.getY() - mob.getY()) < 6;
        if (!arrived && ++budget > 600) {
            pick(); // unreachable: try another house
            return;
        }
        if (arrived) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(waypoint.getX() + 0.5, waypoint.getY() + 1, waypoint.getZ() + 0.5);
            if (++lingering == 30 && loots && mob.level() instanceof ServerLevel sl) {
                mob.swing(InteractionHand.MAIN_HAND);
                Village v = village();
                if (v != null) VillageManager.loot(sl, v, mob);
            }
            if (lingering > 50) pick();
            return;
        }
        if (--recalc > 0 && !mob.getNavigation().isDone()) return;
        recalc = 30;
        double dx = waypoint.getX() + 0.5 - mob.getX(), dz = waypoint.getZ() + 0.5 - mob.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        boolean moved;
        if (len > 20) {
            double tx = mob.getX() + dx / len * 20, tz = mob.getZ() + dz / len * 20;
            int ty = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(tx), (int) Math.floor(tz));
            moved = mob.getNavigation().moveTo(tx, ty, tz, 1.0);
        } else {
            moved = mob.getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, 1.0);
        }
        if (!moved && ++stuck > 4) pick();
    }
}
