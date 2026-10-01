package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.access.LidAnimatorAccess;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.mixin.client.ShulkerAnimationAccessor;
import io.github.marcsanzdev.chestseparators.network.AutoDepositResultPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/**
 * Renders the auto-deposit feedback: each deposited item flies from the player toward its destination
 * chest as a real 3D item model (the same "dropped item" look as pressing Q), rendered in the world
 * via {@link #renderFlights} (called from a LevelRenderer mixin with the frame's pose stack and render
 * collector). Items follow a gentle arc, spin as they travel, and optionally leave a particle trail.
 *
 * <p>The whole animation and the trail are independently toggleable in the config.
 */
public final class AutoDepositAnimator {

    private AutoDepositAnimator() {}

    /** One item in flight: a snapshot stack, fixed world endpoints, start time, and trail bookkeeping. */
    private static final class FlyingItem {
        final ItemStack stack;
        final Vec3 start;
        final Vec3 end;
        final long startTime;
        long lastParticleMs;

        FlyingItem(ItemStack stack, Vec3 start, Vec3 end, long startTime) {
            this.stack = stack;
            this.start = start;
            this.end = end;
            this.startTime = startTime;
            this.lastParticleMs = 0L;
        }
    }

    private static final List<FlyingItem> FLIGHTS = new ArrayList<>();

    private static final long DURATION_MS = 1900L;
    private static final long STAGGER_MS = 130L;
    private static final double ARC_HEIGHT = 1.2;
    private static final long TRAIL_INTERVAL_MS = 110L;
    private static final int FULL_BRIGHT = 0xF000F0;

    // How long a destination chest stays open after its last item lands, before the lid closes.
    private static final long CHEST_DWELL_MS = 250L;

    /** A chest whose lid we forced open: when to close it, and whether it should play open/close sounds. */
    private static final class ChestOpen {
        long closeAt;
        boolean sound;
    }

    /** Chests (and double-chest neighbours) currently held open by the animation, client-side only. */
    private static final Map<BlockPos, ChestOpen> OPEN_CHESTS = new HashMap<>();

    public static void register() {
        // The flying-item render is driven by a LevelRenderer mixin that calls renderFlights(...) during
        // the world's after-entities pass (Architectury has no world-render event, so it is a common mixin
        // hook rather than a Fabric event). Only the chest-lid tick is a plain event.
        ClientTickEvent.CLIENT_POST.register(client -> tickChests());
    }

    /** Queues animations for a completed auto-deposit (player -> chest). */
    public static void addFlights(List<AutoDepositResultPayload.Flight> flights) {
        addFlights(flights, false);
    }

    /**
     * Queues animations for a completed transfer. When {@code reverse} is true the items fly from each
     * container toward the player (grab); otherwise from the player toward each container (deposit).
     * Called on the client thread from networking.
     */
    public static void addFlights(List<AutoDepositResultPayload.Flight> flights, boolean reverse) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // When a container GUI is open (the Pull button), feedback goes ABOVE the interface via the
        // editor's status overlay — exactly like the Push button — instead of the action bar behind it.
        boolean guiOpen = client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
        io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor editor =
                io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.getInstance();

        if (flights.isEmpty()) {
            // Reverse = pull from the open chest. Be specific about WHY nothing moved: no inventory filters
            // at all vs. filters exist but nothing in the chest matched. Non-reverse = radius auto-deposit.
            String key;
            if (reverse) {
                boolean invHasFilters = !io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                        .getPlayerInventoryFilters()
                        .isEmpty();
                key = invHasFilters
                        ? "message.chestseparators.pull_no_match"
                        : "message.chestseparators.pull_no_filters";
            } else {
                key = "message.chestseparators.auto_deposit_none";
            }
            Component noneMsg = Component.translatable(key).withStyle(ChatFormatting.GRAY);
            if (guiOpen && editor != null) editor.showStatus(noneMsg, ChatFormatting.GRAY);
            else client.player.displayClientMessage(noneMsg, true);
            return;
        }

        int total = 0;
        for (AutoDepositResultPayload.Flight flight : flights)
            total += flight.stack().getCount();

        client.player.playSound(SoundEvents.ITEM_PICKUP, 0.5f, reverse ? 1.0f : 1.4f);
        Component doneMsg = Component.translatable(
                        reverse
                                ? "message.chestseparators.auto_grab_done"
                                : "message.chestseparators.auto_deposit_done",
                        total)
                .withStyle(ChatFormatting.GREEN);

        // GUI open: show it above the interface and finish instantly — no world fly-over you can't see.
        if (guiOpen) {
            if (editor != null) editor.showStatus(doneMsg, ChatFormatting.GREEN);
            return;
        }
        client.player.displayClientMessage(doneMsg, true);

        // The transfer already happened server-side; the flying items are pure cosmetics.
        if (!GlobalChestConfig.instance.autoDepositAnimation) return;

        // Items appear to leave/arrive at the player around body height.
        Vec3 playerPos = new Vec3(client.player.getX(), client.player.getY() + 1.0, client.player.getZ());
        long now = System.currentTimeMillis();

        // Track, per container, when its last item leaves/arrives so we know when to close the lid.
        Map<BlockPos, Long> lastArrival = new HashMap<>();
        int index = 0;
        for (AutoDepositResultPayload.Flight flight : flights) {
            long startTime = now + (long) index * STAGGER_MS;
            Vec3 chest = Vec3.atCenterOf(flight.target());
            Vec3 start = reverse ? chest : playerPos;
            Vec3 end = reverse ? playerPos : chest;
            FLIGHTS.add(new FlyingItem(flight.stack(), start, end, startTime));
            lastArrival.merge(flight.target(), startTime + DURATION_MS, Math::max);
            index++;
        }

        // Open every destination container so the player sees where items are headed.
        ClientLevel world = client.level;
        if (world != null) {
            for (Map.Entry<BlockPos, Long> entry : lastArrival.entrySet()) {
                openContainer(world, entry.getKey(), entry.getValue() + CHEST_DWELL_MS);
            }
        }
    }

    // Plays the opening animation of whatever container sits at the position: chests and ender chests
    // raise their lid, shulker boxes run their open animation. Containers without an animation (e.g.
    // barrels, or a minecart whose target maps to an empty block) are skipped silently.
    private static void openContainer(ClientLevel world, BlockPos pos, long closeAt) {
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof LidAnimatorAccess lid) {
            boolean firstOpen = !OPEN_CHESTS.containsKey(pos);
            lid.getLidAnimator().shouldBeOpen(true);
            markOpen(pos, closeAt, true);
            if (firstOpen) playContainerSound(world, pos, openSoundFor(be));

            // Open the other half of a double chest in sync (silently, so the sound plays once).
            BlockPos neighbor = doubleNeighbor(world, pos);
            if (neighbor != null && world.getBlockEntity(neighbor) instanceof LidAnimatorAccess neighborLid) {
                neighborLid.getLidAnimator().shouldBeOpen(true);
                markOpen(neighbor, closeAt, false);
            }
        } else if (be instanceof ShulkerAnimationAccessor shulker) {
            boolean firstOpen = !OPEN_CHESTS.containsKey(pos);
            shulker.setAnimationStage(ShulkerBoxBlockEntity.AnimationStatus.OPENING);
            markOpen(pos, closeAt, true);
            if (firstOpen) playContainerSound(world, pos, SoundEvents.SHULKER_BOX_OPEN);
        }
    }

    private static void markOpen(BlockPos pos, long closeAt, boolean sound) {
        ChestOpen state = OPEN_CHESTS.computeIfAbsent(pos, k -> new ChestOpen());
        state.closeAt = Math.max(state.closeAt, closeAt);
        state.sound = state.sound || sound;
    }

    private static void tickChests() {
        if (OPEN_CHESTS.isEmpty()) return;
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            OPEN_CHESTS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, ChestOpen>> it = OPEN_CHESTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ChestOpen> entry = it.next();
            if (now < entry.getValue().closeAt) continue;
            closeContainer(world, entry.getKey(), entry.getValue().sound);
            it.remove();
        }
    }

    private static void closeContainer(ClientLevel world, BlockPos pos, boolean playSound) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof LidAnimatorAccess lid) {
            lid.getLidAnimator().shouldBeOpen(false);
            if (playSound) playContainerSound(world, pos, closeSoundFor(be));
        } else if (be instanceof ShulkerAnimationAccessor shulker) {
            shulker.setAnimationStage(ShulkerBoxBlockEntity.AnimationStatus.CLOSING);
            if (playSound) playContainerSound(world, pos, SoundEvents.SHULKER_BOX_CLOSE);
        }
    }

    private static SoundEvent openSoundFor(BlockEntity be) {
        return be instanceof EnderChestBlockEntity ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.CHEST_OPEN;
    }

    private static SoundEvent closeSoundFor(BlockEntity be) {
        return be instanceof EnderChestBlockEntity
                ? SoundEvents.ENDER_CHEST_CLOSE
                : SoundEvents.CHEST_CLOSE;
    }

    private static BlockPos doubleNeighbor(ClientLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return null;
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) return null;
        Direction facing = state.getValue(ChestBlock.FACING);
        Direction neighborDir = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
        return pos.relative(neighborDir);
    }

    private static void playContainerSound(ClientLevel world, BlockPos pos, SoundEvent sound) {
        world.playLocalSound(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                sound,
                SoundSource.BLOCKS,
                0.5f,
                world.getRandom().nextFloat() * 0.1f + 0.9f,
                false);
    }

    /**
     * Draws every in-flight item for the current frame. Called from the world-render mixin at the tail of
     * {@code renderLevel}, which supplies the frame's camera-relative {@link PoseStack} (Architectury has no
     * world-render event, so this is a common mixin hook). Renders each stack as a GROUND item model via the
     * E4 {@code ItemRenderer.renderStatic} path, flushing its own buffer source at the end.
     */
    public static void renderFlights(PoseStack matrices) {
        if (FLIGHTS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null || client.player == null || matrices == null) {
            FLIGHTS.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
        boolean trail = GlobalChestConfig.instance.autoDepositTrail;

        Iterator<FlyingItem> it = FLIGHTS.iterator();
        while (it.hasNext()) {
            FlyingItem flight = it.next();
            long age = now - flight.startTime;
            if (age < 0) continue; // staggered start not reached yet
            double t = age / (double) DURATION_MS;
            if (t >= 1.0) {
                it.remove();
                continue;
            }

            double ease = t * t * (3.0 - 2.0 * t); // smoothstep
            Vec3 pos = lerp(flight.start, flight.end, ease).add(0.0, Math.sin(Math.PI * t) * ARC_HEIGHT, 0.0);

            if (trail && now - flight.lastParticleMs >= TRAIL_INTERVAL_MS) {
                world.addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
                flight.lastParticleMs = now;
            }

            float spin = (age * 0.18f) % 360.0f;

            // Light the flying item with the world's block/sky light at its position (like a real dropped
            // item) so it blends with the ambient lighting of the moment, instead of always full-bright.
            int packedLight = net.minecraft.client.renderer.LevelRenderer.getLightColor(
                    world, net.minecraft.core.BlockPos.containing(pos));

            matrices.pushPose();
            matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            matrices.mulPose(Axis.YP.rotationDegrees(spin));
            matrices.scale(1.25f, 1.25f, 1.25f);
            client.getItemRenderer()
                    .renderStatic(
                            flight.stack,
                            ItemDisplayContext.GROUND,
                            packedLight,
                            OverlayTexture.NO_OVERLAY,
                            matrices,
                            bufferSource,
                            world,
                            0);
            matrices.popPose();
        }
        bufferSource.endBatch();
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
