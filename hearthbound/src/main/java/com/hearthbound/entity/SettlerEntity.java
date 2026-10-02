package com.hearthbound.entity;

import com.hearthbound.config.HBConfig;
import com.hearthbound.data.Culture;
import com.hearthbound.data.HBData;
import com.hearthbound.village.Role;
import com.hearthbound.village.VillageService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * A villager of a Hearthbound culture. Its appearance (skin set, ears, size) and job are
 * synced to clients; everything else (home, workplace, village) lives on the server.
 * Hired settlers become companions that follow and defend their owner.
 */
public class SettlerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FEMALE = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EARS = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> COMPANION = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WARBAND = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> TEXTURE = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> CULTURE_COLOR = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.INT);

    /** Set by the client from its config: show the job next to the name. */
    public static boolean showRoleInName = true;

    private String culture = "";
    private UUID village;
    private BlockPos villageCenter;
    private int villageRadius = 32;
    private BlockPos home;
    private BlockPos work;
    private UUID owner;
    private long hireUntil;
    /** A villager who follows a friend by choice (keeps home, job and village). */
    private boolean follower;
    private boolean waiting;
    // caravan travellers
    private BlockPos travelTarget;
    private long travelUntil;
    private UUID travelMount;
    // warbands
    private UUID warTarget;
    private long warUntil;
    /** Game time until which this civilian stays indoors because danger was seen nearby (not saved). */
    private long alarmUntil;
    /** Head of the bed assigned by the village (null = none, looks for a free one at night). */
    private BlockPos bed;
    private int bedSearchCooldown;

    public SettlerEntity(EntityType<? extends SettlerEntity> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ROLE, 0);
        b.define(SKIN, "valdoran");
        b.define(VARIANT, 0);
        b.define(FEMALE, false);
        b.define(EARS, false);
        b.define(COMPANION, false);
        b.define(CULTURE_COLOR, 0xFFE0B25A);
        b.define(TEXTURE, "");
        b.define(WARBAND, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FighterMelee());
        goalSelector.addGoal(1, new CivilianPanic());
        goalSelector.addGoal(1, new CivilianAvoid());
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, SettlerEntity.class, 12f, 0.6, 0.85,
                e -> e instanceof SettlerEntity o && o.isWarband() && village != null && village.equals(o.warTarget)) {
            @Override
            public boolean canUse() {
                return !isFighter() && super.canUse();
            }
        });
        goalSelector.addGoal(2, new Travel());
        goalSelector.addGoal(2, new RaidMarch());
        goalSelector.addGoal(2, new FollowOwner());
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new SeekShelter());
        goalSelector.addGoal(3, new GoToWork());
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return !isCompanion() && travelTarget == null && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this, SettlerEntity.class, Player.class) {
            @Override
            public boolean canUse() {
                return isFighter() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return isWarband() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new DefendOwner());
        // wars: raiders hunt the people of the village they attack, its fighters hunt them back
        targetSelector.addGoal(2, new WarTarget());
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, e -> !(e instanceof Creeper)) {
            @Override
            public boolean canUse() {
                return isFighter() && super.canUse();
            }
        });
    }

    // ================================================================== appearance / identity

    public void setup(Culture c, Role role, String name, boolean female, int variant) {
        this.culture = c == null ? "" : c.id.toString();
        entityData.set(SKIN, c == null ? "valdoran" : c.skin);
        entityData.set(EARS, c != null && c.ears);
        entityData.set(CULTURE_COLOR, c == null ? 0xFFE0B25A : c.color);
        if (c != null) getAttribute(Attributes.SCALE).setBaseValue(c.modelScale);
        entityData.set(FEMALE, female);
        entityData.set(VARIANT, variant);
        setCustomName(Component.literal(name));
        setRole(role);
        entityData.set(TEXTURE, c == null ? "" : c.randomSkin(random, role));
    }

    public void setRole(Role role) {
        entityData.set(ROLE, role.ordinal());
        setItemSlot(EquipmentSlot.MAINHAND, role.tool == Items.AIR ? ItemStack.EMPTY : new ItemStack(role.tool));
        setDropChance(EquipmentSlot.MAINHAND, 0f);
        boolean fighter = role.fights() || isCompanion();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(fighter ? HBConfig.GUARD_HEALTH.get() : HBConfig.SETTLER_HEALTH.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(fighter ? HBConfig.GUARD_DAMAGE.get() : 2.0);
        getAttribute(Attributes.ARMOR).setBaseValue(fighter ? 8.0 : 2.0);
        setHealth(getMaxHealth());
    }

    /** Gives a village settler a new job (and a matching look from its culture). */
    public void promote(Culture c, Role role) {
        setRole(role);
        if (c != null) entityData.set(TEXTURE, c.randomSkin(random, role));
    }

    public Role role() {
        return Role.byOrdinal(entityData.get(ROLE));
    }

    public String skin() {
        return entityData.get(SKIN);
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    public boolean female() {
        return entityData.get(FEMALE);
    }

    public boolean ears() {
        return entityData.get(EARS);
    }

    /** Texture chosen from the culture's skin pool ("" = generated default). */
    public String texture() {
        return entityData.get(TEXTURE);
    }

    public int cultureColor() {
        return entityData.get(CULTURE_COLOR);
    }

    public boolean isCompanion() {
        return entityData.get(COMPANION);
    }

    public boolean isFighter() {
        return role().fights() || isCompanion() || isWarband();
    }

    public boolean isWarband() {
        return entityData.get(WARBAND);
    }

    public UUID warTarget() {
        return warTarget;
    }

    public boolean isTraveler() {
        return travelTarget != null;
    }

    /** Joins a raid: {@code home} attacks {@code target}, marching on its center until {@code until}. */
    public void joinWarband(UUID home, UUID target, BlockPos targetCenter, int radius, long until) {
        this.village = home;
        this.warTarget = target;
        this.warUntil = until;
        this.villageCenter = targetCenter;
        this.villageRadius = radius + 8;
        this.work = targetCenter;
        this.home = targetCenter;
        entityData.set(WARBAND, true);
        setRole(Role.GUARD);
        applyRaiderStats();
        restrictTo(targetCenter, radius + 24);
    }

    private void applyRaiderStats() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(HBConfig.RAIDER_HEALTH.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(HBConfig.RAIDER_DAMAGE.get());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48.0);
        getAttribute(Attributes.ARMOR).setBaseValue(6.0);
        setHealth(getMaxHealth());
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextInt(3) == 0 ? Items.IRON_AXE : Items.IRON_SWORD));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        setDropChance(EquipmentSlot.MAINHAND, 0f);
        setDropChance(EquipmentSlot.HEAD, 0f);
    }

    /** Walks to {@code target} (a caravan on the road) and leaves the world when it gets there or at {@code until}. */
    public void travel(BlockPos target, long until, UUID mount) {
        this.travelTarget = target;
        this.travelUntil = until;
        this.travelMount = mount;
        clearRestriction();
    }

    public String cultureId() {
        return culture;
    }

    public UUID villageId() {
        return village;
    }

    public UUID owner() {
        return owner;
    }

    public long hireUntil() {
        return hireUntil;
    }

    public boolean waiting() {
        return waiting;
    }

    public void setWaiting(boolean w) {
        this.waiting = w;
    }

    public void bindVillage(UUID id, BlockPos center, int radius, BlockPos home, BlockPos work) {
        this.village = id;
        this.villageCenter = center;
        this.villageRadius = radius;
        this.home = home;
        this.work = work;
        if (!isCompanion() && center != null) restrictTo(center, radius);
    }

    public BlockPos bed() {
        return bed;
    }

    public void setBed(@Nullable BlockPos bed) {
        this.bed = bed == null ? null : bed.immutable();
    }

    public boolean isFollower() {
        return follower && isCompanion();
    }

    /** A friend decides to travel with the player. Keeps job, looks and village. */
    public void makeFollower(ServerPlayer player) {
        Role r = role();
        this.owner = player.getUUID();
        this.hireUntil = Long.MAX_VALUE;
        this.follower = true;
        this.waiting = false;
        entityData.set(COMPANION, true);
        clearRestriction();
        setRole(r); // companions fight: refresh health and damage
    }

    /** Stops following and goes back home (far away friends are brought back directly). */
    public void stopFollowing() {
        Role r = role();
        this.owner = null;
        this.follower = false;
        this.waiting = false;
        entityData.set(COMPANION, false);
        setTarget(null);
        setRole(r);
        if (villageCenter != null) {
            if (villageCenter.distSqr(blockPosition()) > 96 * 96 && level() instanceof ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                BlockPos to = home != null ? home : villageCenter;
                int y = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, to.getX(), to.getZ());
                moveTo(to.getX() + 0.5, y, to.getZ() + 0.5, getYRot(), 0);
                getNavigation().stop();
            }
            restrictTo(villageCenter, villageRadius);
        }
    }

    public void makeCompanion(ServerPlayer player, long until) {
        this.owner = player.getUUID();
        this.hireUntil = until;
        this.village = null;
        entityData.set(COMPANION, true);
        clearRestriction();
        setRole(Role.GUARD);
        entityData.set(TEXTURE, "");
    }

    @Override
    public Component getDisplayName() {
        Component base = super.getDisplayName();
        if (!showRoleInName) return base;
        Component job = isCompanion() && !isFollower() ? Component.translatable("hearthbound.role.companion")
                : isWarband() ? Component.translatable("hearthbound.role.raider").withColor(0xE06A5A)
                : isTraveler() ? Component.translatable("hearthbound.role.caravan")
                : Component.translatable(role().key());
        return Component.empty().append(base).append(Component.literal(" · ").withColor(0x8A8A8A)).append(job.copy().withColor(role().color));
    }

    // ================================================================== ticking

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 40 != 0) return;
        if (travelTarget != null) {
            if (level().getGameTime() > travelUntil || travelTarget.distSqr(blockPosition()) < 36) leaveWorld();
            return;
        }
        if (isWarband()) {
            if (level().getGameTime() > warUntil || (level() instanceof ServerLevel sl && !com.hearthbound.village.Diplomacy.raidActive(sl.getServer(), this))) {
                if (level() instanceof ServerLevel sl2) sl2.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                discard();
            }
            return;
        }
        if (isCompanion()) {
            if (level().getGameTime() > hireUntil) {
                Player p = owner == null ? null : level().getPlayerByUUID(owner);
                if (p != null) p.displayClientMessage(Component.translatable("hearthbound.companion.leaves", getCustomName()), false);
                discard();
            }
            return;
        }
        if (villageCenter == null) return;
        if (!role().fights() && HBConfig.SEEK_SHELTER.get() && dangerNearby()) alarmUntil = level().getGameTime() + 400;
        if (level().isNight() && !role().fights()) keepBed();
        boolean shelter = shouldShelter(); // guards keep watch at night
        BlockPos anchor = shelter ? shelterTarget() : villageCenter;
        int r = shelter ? 3 : villageRadius;
        if (!anchor.equals(getRestrictCenter()) || getRestrictRadius() != r) restrictTo(anchor, r);
    }

    private void leaveWorld() {
        if (travelMount != null && level() instanceof ServerLevel sl) {
            var m = sl.getEntity(travelMount);
            if (m != null) m.discard();
        }
        discard();
    }

    /** Where to take shelter: the bed, or the middle of the house. */
    private BlockPos shelterTarget() {
        return bed != null ? bed : home;
    }

    /** Drops a bed that is gone and, without one, claims the nearest free bed (like vanilla villagers). */
    private void keepBed() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (bed != null && sl.hasChunkAt(bed) && !com.hearthbound.village.VillageManager.isBedHead(sl.getBlockState(bed))) bed = null;
        if (bed != null || village == null || --bedSearchCooldown > 0) return;
        bedSearchCooldown = 10; // every 400 ticks at most
        BlockPos from = home != null ? home : blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-24, -6, -24), from.offset(24, 6, 24))) {
            if (!sl.hasChunkAt(p)) continue;
            net.minecraft.world.level.block.state.BlockState st = sl.getBlockState(p);
            if (com.hearthbound.village.VillageManager.isBedHead(st) && !st.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) found.add(p.immutable());
        }
        found.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(from)));
        for (BlockPos p : found) {
            if (com.hearthbound.village.VillageManager.claimBed(sl, this, p)) return;
        }
    }

    /** Sleeps at night in its bed when it is next to it. Guards stay awake on watch. */
    private boolean wantsSleep() {
        return bed != null && !isFighter() && level().isNight() && shouldShelter();
    }

    private void trySleep() {
        if (isSleeping() || !wantsSleep() || getTarget() != null || !bed.closerToCenterThan(position(), 2.0)) return;
        net.minecraft.world.level.block.state.BlockState st = level().getBlockState(bed);
        if (!com.hearthbound.village.VillageManager.isBedHead(st) || st.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) return;
        getNavigation().stop();
        startSleeping(bed);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isSleeping() || tickCount % 20 != 0) return;
        boolean inOwnBed = getSleepingPos().map(p -> p.equals(bed)).orElse(false);
        if (!inOwnBed || !wantsSleep()) stopSleeping(); // morning, a job change or a lost bed
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isSleeping();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && isSleeping() && !level().isClientSide) stopSleeping(); // free the bed
        super.remove(reason);
    }

    /** Civilians with a home go indoors at night and while their village is in danger. */
    public boolean shouldShelter() {
        if ((home == null && bed == null) || role().fights() || isCompanion() || isWarband() || isTraveler() || villageCenter == null) return false;
        if (level().isNight()) return true;
        return HBConfig.SEEK_SHELTER.get() && level().getGameTime() < alarmUntil;
    }

    /** Close enough to the bed (or the middle of the house) to be indoors. */
    private boolean atHome() {
        BlockPos t = shelterTarget();
        if (t == null) return false;
        double reach = t == bed ? 2.0 : 3.0;
        return t.closerToCenterThan(position(), reach) || (t.distSqr(blockPosition()) <= reach * reach && Math.abs(t.getY() - getY()) < 2);
    }

    /** A settler hunter, a warband or a raid on this village is close. */
    private boolean dangerNearby() {
        if (village == null) return false;
        for (net.minecraft.world.entity.Mob m : level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(24, 10, 24),
                m -> m.isAlive() && m != this)) {
            if (m instanceof SettlerEntity o) {
                if (o.isWarband() && village.equals(o.warTarget)) return true;
            } else if (m instanceof Monster && huntsSettlers(m)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mobs that hunt settlers: those that attack vanilla villagers (zombies, illagers, ravagers),
     * raid mobs, and anything listed in the config. Skeletons, spiders, endermen and the like leave them alone.
     */
    public static boolean huntsSettlers(net.minecraft.world.entity.Mob m) {
        if (m instanceof SettlerEntity) return false;
        if (m.getTags().contains(com.hearthbound.village.VillageManager.RAIDER_TAG)) return true;
        if (!(m instanceof Monster) || m instanceof Creeper) return false;
        if (HBConfig.ALL_MONSTERS_HUNT_SETTLERS.get()) return true;
        if (m instanceof net.minecraft.world.entity.monster.Zombie && !(m instanceof net.minecraft.world.entity.monster.ZombifiedPiglin)) return true;
        if (m instanceof net.minecraft.world.entity.monster.AbstractIllager || m instanceof net.minecraft.world.entity.monster.Ravager) return true;
        List<? extends String> extra = HBConfig.SETTLER_HUNTERS.get();
        if (extra.isEmpty()) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString();
        return extra.contains(id);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            if (isFollower()) {
                getNavigation().stop();
                getLookControl().setLookAt(player);
                com.hearthbound.village.Persons.open(sp, this);
            } else if (isCompanion()) {
                if (sp.getUUID().equals(owner)) {
                    waiting = !waiting;
                    getNavigation().stop();
                    sp.displayClientMessage(Component.translatable(waiting ? "hearthbound.companion.wait" : "hearthbound.companion.follow", getCustomName()), true);
                }
            } else if (isTraveler() || isWarband()) {
                return InteractionResult.PASS;
            } else {
                getNavigation().stop();
                getLookControl().setLookAt(player);
                if (sp.isShiftKeyDown() || !HBConfig.BONDS.get()) VillageService.openFromSettler(sp, this);
                else com.hearthbound.village.Persons.open(sp, this);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isCompanion() && source.getEntity() != null && source.getEntity().getUUID().equals(owner)) return false;
        if (!isCompanion() && source.getEntity() instanceof SettlerEntity other && isWarband() == other.isWarband()) return false;
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && !isFighter() && source.getEntity() instanceof LivingEntity && !(source.getEntity() instanceof Player)) {
            alarmUntil = level().getGameTime() + 600; // run home
        }
        if (hit && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != this
                && !(attacker instanceof Player pl && (pl.isCreative() || pl.isSpectator() || !isWarband()))) {
            // the band (or the village's fighters) rally against whoever strikes one of them
            for (SettlerEntity o : level().getEntitiesOfClass(SettlerEntity.class, getBoundingBox().inflate(20, 8, 20),
                    o -> o != this && o.isAlive() && o.getTarget() == null && sameSide(o))) {
                if (!(attacker instanceof SettlerEntity sa && o.sameSide(sa)) && o.reachable(attacker)) o.setTarget(attacker);
            }
        }
        return hit;
    }

    /** Inside the area this settler may leave to fight (raiders and companions roam freely). */
    boolean reachable(LivingEntity t) {
        return isWarband() || isCompanion() || !hasRestriction() || isWithinRestriction(t.blockPosition());
    }

    /** Same warband, or fighters of the same village. */
    private boolean sameSide(SettlerEntity o) {
        if (isWarband()) return o.isWarband() && warTarget != null && warTarget.equals(o.warTarget);
        return !o.isWarband() && !o.isCompanion() && o.isFighter() && village != null && village.equals(o.village);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        swing(InteractionHand.MAIN_HAND);
        boolean hit = super.doHurtTarget(target);
        if (hit && isWarband() && target instanceof SettlerEntity victim && !victim.isAlive() && level() instanceof ServerLevel sl) {
            com.hearthbound.village.Diplomacy.raiderKill(sl.getServer(), this);
        }
        return hit;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, type, data);
        if (culture.isEmpty() && (type == MobSpawnType.SPAWN_EGG || type == MobSpawnType.COMMAND)) {
            List<Culture> all = new ArrayList<>(HBData.cultures());
            if (!all.isEmpty()) {
                Culture c = all.get(random.nextInt(all.size()));
                boolean female = random.nextBoolean();
                Role[] roles = Role.values();
                setup(c, roles[random.nextInt(roles.length)], c.randomName(random, female), female, random.nextInt(2));
            }
        }
        return out;
    }

    // ================================================================== sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return isCompanion() ? null : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    public float getVoicePitch() {
        float base = female() ? 1.15f : 0.95f;
        return base + (random.nextFloat() - 0.5f) * 0.1f;
    }

    // ================================================================== saving

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("HBCulture", culture);
        t.putString("HBSkin", skin());
        t.putInt("HBRole", entityData.get(ROLE));
        t.putInt("HBVariant", variant());
        t.putBoolean("HBFemale", female());
        t.putBoolean("HBEars", ears());
        t.putInt("HBColor", cultureColor());
        t.putString("HBTexture", texture());
        t.putBoolean("HBCompanion", isCompanion());
        if (village != null) t.putUUID("HBVillage", village);
        if (villageCenter != null) t.put("HBCenter", NbtUtils.writeBlockPos(villageCenter));
        t.putInt("HBRadius", villageRadius);
        if (home != null) t.put("HBHome", NbtUtils.writeBlockPos(home));
        if (work != null) t.put("HBWork", NbtUtils.writeBlockPos(work));
        if (bed != null) t.put("HBBed", NbtUtils.writeBlockPos(bed));
        if (owner != null) t.putUUID("HBOwner", owner);
        t.putLong("HBHireUntil", hireUntil);
        t.putBoolean("HBWaiting", waiting);
        t.putBoolean("HBFollower", follower);
        if (travelTarget != null) {
            t.put("HBTravel", NbtUtils.writeBlockPos(travelTarget));
            t.putLong("HBTravelUntil", travelUntil);
            if (travelMount != null) t.putUUID("HBMount", travelMount);
        }
        if (warTarget != null) {
            t.putUUID("HBWarTarget", warTarget);
            t.putLong("HBWarUntil", warUntil);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        culture = t.getString("HBCulture");
        if (t.contains("HBSkin")) entityData.set(SKIN, t.getString("HBSkin"));
        entityData.set(ROLE, t.getInt("HBRole"));
        entityData.set(VARIANT, t.getInt("HBVariant"));
        entityData.set(FEMALE, t.getBoolean("HBFemale"));
        entityData.set(EARS, t.getBoolean("HBEars"));
        if (t.contains("HBColor")) entityData.set(CULTURE_COLOR, t.getInt("HBColor"));
        entityData.set(TEXTURE, t.getString("HBTexture"));
        entityData.set(COMPANION, t.getBoolean("HBCompanion"));
        village = t.hasUUID("HBVillage") ? t.getUUID("HBVillage") : null;
        villageCenter = NbtUtils.readBlockPos(t, "HBCenter").orElse(null);
        villageRadius = t.getInt("HBRadius");
        home = NbtUtils.readBlockPos(t, "HBHome").orElse(null);
        work = NbtUtils.readBlockPos(t, "HBWork").orElse(null);
        bed = NbtUtils.readBlockPos(t, "HBBed").orElse(null);
        owner = t.hasUUID("HBOwner") ? t.getUUID("HBOwner") : null;
        hireUntil = t.getLong("HBHireUntil");
        waiting = t.getBoolean("HBWaiting");
        follower = t.getBoolean("HBFollower");
        travelTarget = NbtUtils.readBlockPos(t, "HBTravel").orElse(null);
        travelUntil = t.getLong("HBTravelUntil");
        travelMount = t.hasUUID("HBMount") ? t.getUUID("HBMount") : null;
        warTarget = t.hasUUID("HBWarTarget") ? t.getUUID("HBWarTarget") : null;
        warUntil = t.getLong("HBWarUntil");
        entityData.set(WARBAND, warTarget != null);
        if (villageCenter != null && !isCompanion() && travelTarget == null) restrictTo(villageCenter, Math.max(8, villageRadius));
        if (isCompanion()) clearRestriction();
        if (warTarget != null) {
            getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48.0);
            if (villageCenter != null) restrictTo(villageCenter, villageRadius + 16);
        }
    }

    // ================================================================== goals

    private class FighterMelee extends MeleeAttackGoal {
        FighterMelee() {
            super(SettlerEntity.this, 1.0, true);
        }

        @Override
        public boolean canUse() {
            return isFighter() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isFighter() && super.canContinueToUse();
        }
    }

    private class CivilianPanic extends PanicGoal {
        CivilianPanic() {
            super(SettlerEntity.this, 0.8);
        }

        @Override
        public boolean canUse() {
            return !isFighter() && super.canUse();
        }
    }

    private class CivilianAvoid extends AvoidEntityGoal<Monster> {
        CivilianAvoid() {
            super(SettlerEntity.this, Monster.class, 8f, 0.6, 0.85, SettlerEntity::huntsSettlersEntity);
        }

        @Override
        public boolean canUse() {
            return !isFighter() && !atHome() && super.canUse();
        }
    }

    private static boolean huntsSettlersEntity(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.Mob m && huntsSettlers(m);
    }

    /** Civilians run to their house at night or when danger is near, and stay inside. */
    private class SeekShelter extends Goal {
        private int recalc;

        SeekShelter() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!shouldShelter()) return false;
            if (atHome()) {
                trySleep();
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return shouldShelter() && !atHome();
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos t = shelterTarget();
            if (t == null || (--recalc > 0 && !getNavigation().isDone())) return;
            recalc = 20;
            double speed = level().getGameTime() < alarmUntil ? 0.85 : 0.6;
            getNavigation().moveTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, speed);
        }
    }

    /** Walks to the workplace by day (the builder to the construction site) and home at night. */
    private class GoToWork extends Goal {
        private BlockPos target;
        private int cooldown;

        GoToWork() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (isCompanion() || isWarband() || villageCenter == null || travelTarget != null) return false;
            if (shouldShelter()) return false; // SeekShelter takes them home
            if (--cooldown > 0) return false;
            cooldown = 160 + random.nextInt(160);
            boolean night = level().isNight();
            BlockPos t = night ? home : work;
            if (!night && role() == Role.BUILDER && level() instanceof ServerLevel serverLevel) {
                var village = com.hearthbound.village.VillageData.get(serverLevel.getServer()).get(villageId());
                if (village != null && !village.repairs.isEmpty()) t = village.repairs.keySet().iterator().next();
            }
            if (t == null) t = villageCenter;
            if (t.distSqr(blockPosition()) < 25) return false;
            target = t;
            return true;
        }

        @Override
        public void start() {
            getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone() && target != null && target.distSqr(blockPosition()) > 9;
        }
    }

    /**
     * Raiders march on the attacked village and go from house to house looting it.
     * Paths are planned in hops so the march works from far outside the follow range.
     */
    private class RaidMarch extends Goal {
        private BlockPos waypoint;
        private int recalc;
        private int stuck;
        private int lingering;
        private int budget;

        RaidMarch() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return isWarband() && getTarget() == null && level() instanceof ServerLevel;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            recalc = 0;
            if (waypoint == null) pick();
        }

        private void pick() {
            if (level() instanceof ServerLevel sl && warTarget != null) {
                waypoint = com.hearthbound.village.Diplomacy.raidWaypoint(sl.getServer(), warTarget, random);
            }
            if (waypoint == null) waypoint = villageCenter;
            stuck = 0;
            lingering = 0;
            budget = 0;
        }

        @Override
        public void tick() {
            if (waypoint == null) return;
            double hx = waypoint.getX() + 0.5 - getX(), hz = waypoint.getZ() + 0.5 - getZ();
            boolean arrived = hx * hx + hz * hz < 9 && Math.abs(waypoint.getY() - getY()) < 6;
            if (!arrived && ++budget > 600) {
                pick();
                return;
            }
            if (arrived) {
                getNavigation().stop();
                getLookControl().setLookAt(waypoint.getX() + 0.5, waypoint.getY() + 1, waypoint.getZ() + 0.5);
                if (++lingering == 30) {
                    swing(InteractionHand.MAIN_HAND);
                    if (level() instanceof ServerLevel sl) com.hearthbound.village.Diplomacy.pillage(sl, SettlerEntity.this);
                }
                if (lingering > 50) pick();
                return;
            }
            if (--recalc > 0 && !getNavigation().isDone()) return;
            recalc = 30;
            double dx = waypoint.getX() + 0.5 - getX(), dz = waypoint.getZ() + 0.5 - getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            boolean moved;
            if (len > 20) {
                double tx = getX() + dx / len * 20, tz = getZ() + dz / len * 20;
                int ty = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(tx), (int) Math.floor(tz));
                moved = getNavigation().moveTo(tx, ty, tz, 0.85);
            } else {
                moved = getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, 0.85);
            }
            if (!moved && ++stuck > 4) pick();
        }
    }

    /**
     * Target selection for wars. Unlike the vanilla goal it needs no line of sight and searches
     * a wide area, so a warband always finds somebody to fight and defenders always respond.
     */
    private class WarTarget extends TargetGoal {
        private LivingEntity candidate;
        private int cooldown;

        WarTarget() {
            super(SettlerEntity.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (isCompanion() || isTraveler()) return false;
            if (!isWarband() && !role().fights()) return false;
            if (getTarget() != null && getTarget().isAlive()) return false;
            if (--cooldown > 0) return false;
            cooldown = 10 + random.nextInt(10);
            candidate = find();
            return candidate != null;
        }

        private LivingEntity find() {
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            if (isWarband()) {
                if (warTarget == null) return null;
                for (SettlerEntity o : level().getEntitiesOfClass(SettlerEntity.class, getBoundingBox().inflate(32, 12, 32),
                        o -> o.isAlive() && !o.isWarband() && !o.isCompanion() && !o.isTraveler() && warTarget.equals(o.village))) {
                    double d = distanceToSqr(o) * (o.isFighter() ? 0.6 : 1.0); // go for the guards first
                    if (d < bestD) {
                        best = o;
                        bestD = d;
                    }
                }
                if (HBConfig.RAIDERS_ATTACK_PLAYERS.get()) {
                    for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(10, 6, 10),
                            p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
                        if (p instanceof ServerPlayer sp && com.hearthbound.village.Diplomacy.spareFromRaid(sp, SettlerEntity.this)) continue;
                        double d = distanceToSqr(p) * 0.5;
                        if (d < bestD) {
                            best = p;
                            bestD = d;
                        }
                    }
                }
            } else {
                if (village == null) return null;
                for (SettlerEntity o : level().getEntitiesOfClass(SettlerEntity.class, getBoundingBox().inflate(40, 12, 40),
                        o -> o.isAlive() && o.isWarband() && village.equals(o.warTarget) && reachable(o))) {
                    double d = distanceToSqr(o);
                    if (d < bestD) {
                        best = o;
                        bestD = d;
                    }
                }
                // monsters chasing one of our people, seen or not
                for (net.minecraft.world.entity.Mob m : level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(32, 10, 32),
                        m -> m.isAlive() && !(m instanceof SettlerEntity) && reachable(m)
                                && m.getTarget() instanceof SettlerEntity victim && village.equals(victim.village))) {
                    double d = distanceToSqr(m) * 0.8;
                    if (d < bestD) {
                        best = m;
                        bestD = d;
                    }
                }
                // pillagers and monsters of a raid on this village
                for (net.minecraft.world.entity.Mob m : level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(40, 12, 40),
                        m -> m.isAlive() && reachable(m) && m.getTags().contains(com.hearthbound.village.VillageManager.RAIDER_TAG)
                                && (!m.getPersistentData().hasUUID(com.hearthbound.village.VillageManager.RAID_VILLAGE_KEY)
                                || village.equals(m.getPersistentData().getUUID(com.hearthbound.village.VillageManager.RAID_VILLAGE_KEY))))) {
                    double d = distanceToSqr(m);
                    if (d < bestD) {
                        best = m;
                        bestD = d;
                    }
                }
            }
            return best;
        }

        @Override
        public void start() {
            mob.setTarget(candidate);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && distanceToSqr(t) < 64 * 64 && reachable(t) && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
        }
    }

    /** Caravan travellers walk towards their destination in long hops. */
    private class Travel extends Goal {
        private int recalc;

        Travel() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return travelTarget != null;
        }

        @Override
        public void tick() {
            if (--recalc > 0 && !getNavigation().isDone()) return;
            recalc = 40;
            double dx = travelTarget.getX() - getX(), dz = travelTarget.getZ() - getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 2) return;
            double step = Math.min(24, len);
            double tx = getX() + dx / len * step, tz = getZ() + dz / len * step;
            int ty = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) tx, (int) tz);
            getNavigation().moveTo(tx, ty, tz, 0.55);
        }
    }

    private class FollowOwner extends Goal {
        private int recalc;

        FollowOwner() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private Player ownerEntity() {
            return owner == null ? null : level().getPlayerByUUID(owner);
        }

        @Override
        public boolean canUse() {
            if (!isCompanion() || waiting) return false;
            Player p = ownerEntity();
            return p != null && !p.isSpectator() && distanceToSqr(p) > 36 && getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            Player p = ownerEntity();
            return p != null && !waiting && distanceToSqr(p) > 9 && getTarget() == null;
        }

        @Override
        public void tick() {
            Player p = ownerEntity();
            if (p == null) return;
            getLookControl().setLookAt(p, 10f, getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            if (distanceToSqr(p) > 400 && level() instanceof ServerLevel) {
                BlockPos b = p.blockPosition();
                for (int i = 0; i < 10; i++) {
                    int dx = random.nextInt(7) - 3, dz = random.nextInt(7) - 3;
                    BlockPos t = b.offset(dx, 0, dz);
                    if (level().getBlockState(t).isAir() && level().getBlockState(t.above()).isAir() && level().getBlockState(t.below()).isSolid()) {
                        moveTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, getYRot(), getXRot());
                        getNavigation().stop();
                        return;
                    }
                }
            }
            getNavigation().moveTo(p, 1.0);
        }
    }

    /** Companions attack whatever hurts their owner or whatever the owner attacks. */
    private class DefendOwner extends TargetGoal {
        private LivingEntity candidate;

        DefendOwner() {
            super(SettlerEntity.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!isCompanion() || owner == null) return false;
            Player p = level().getPlayerByUUID(owner);
            if (p == null) return false;
            LivingEntity a = p.getLastHurtByMob();
            if (a == null || a == SettlerEntity.this) a = p.getLastHurtMob();
            if (a == null || a instanceof SettlerEntity || a instanceof Player) return false;
            if (!canAttack(a, TargetingConditions.DEFAULT)) return false;
            candidate = a;
            return true;
        }

        @Override
        public void start() {
            mob.setTarget(candidate);
            super.start();
        }
    }
}
