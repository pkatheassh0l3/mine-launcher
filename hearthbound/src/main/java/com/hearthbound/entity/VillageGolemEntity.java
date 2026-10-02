package com.hearthbound.entity;

import com.hearthbound.config.HBConfig;
import com.hearthbound.village.Village;
import com.hearthbound.village.VillageData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** A nocturnal village defender, separate from vanilla iron golems and their iron loot. */
public final class VillageGolemEntity extends IronGolem {
    public static final int VARIANT_COUNT = 5;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> VARIANT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(VillageGolemEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);
    private UUID villageId;
    private long night = -1;

    public VillageGolemEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
        xpReward = 10;
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, 0);
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    public void bind(Village village, long night) {
        entityData.set(VARIANT, random.nextInt(VARIANT_COUNT));
        villageId = village.id;
        this.night = night;
        restrictTo(village.center, village.radius());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, this::validTarget));
    }

    private boolean validTarget(LivingEntity target) {
        return level().isNight() && target instanceof Enemy && !(target instanceof Creeper)
                && isWithinRestriction(target.blockPosition());
    }

    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(target == null || validTarget(target) ? target : null);
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != EntityType.PLAYER && super.canAttackType(type);
    }

    @Override
    public void aiStep() {
        if (level() instanceof ServerLevel server) {
            Village village = VillageData.get(server.getServer()).get(villageId);
            if (!HBConfig.NIGHT_GOLEMS.get() || !server.isNight() || night != server.getDayTime() / 24000L
                    || village == null || !village.dimension.equals(server.dimension())) {
                discard(); // Dawn/unloaded old nights never grant loot or XP.
                return;
            }
            restrictTo(village.center, village.radius());
            if (getTarget() != null && !validTarget(getTarget())) setTarget(null);
        }
        super.aiStep();
    }

    @Override
    public boolean isAlwaysExperienceDropper() {
        return level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (villageId != null) tag.putUUID("HearthboundVillage", villageId);
        tag.putLong("HearthboundNight", night);
        tag.putInt("HearthboundVariant", variant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, Math.floorMod(tag.getInt("HearthboundVariant"), VARIANT_COUNT));
        villageId = tag.hasUUID("HearthboundVillage") ? tag.getUUID("HearthboundVillage") : null;
        night = tag.contains("HearthboundNight") ? tag.getLong("HearthboundNight") : -1;
    }
}
