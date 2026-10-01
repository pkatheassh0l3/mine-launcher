package io.github.marcsanzdev.chestseparators.network;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.FilterPriority;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-agnostic server-side networking, ported from the old Fabric {@code ChestSeparatorsMain}. All
 * packet channels are registered through Architectury's {@link NetworkManager}; the enforcement/auto
 * logic (auto-deposit, fill, whitelist persistence, editor locks) is the same as the single-loader mod.
 */
public final class ModNetworking {

    private ModNetworking() {}

    private static final Random EXPEL_RANDOM = new Random();

    /** Hard cap on the auto-deposit radius, regardless of what the client requests. */
    private static final int MAX_AUTO_DEPOSIT_RADIUS = 32;

    /**
     * Registers every C2S receiver (runs on both physical sides so the client's integrated server also
     * handles them) plus the S2C payload types on the dedicated server. Client-side S2C receivers are
     * registered separately in the client module.
     */
    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, CommunityRequest.TYPE, CommunityRequest.CODEC, (payload, context) -> context.queue(() -> { if(context.getPlayer() instanceof ServerPlayer p) CommunityServer.handle(p,payload); }));
        // Release all locks held by a player who disconnects abruptly, and drop their cached filters.
        PlayerEvent.PLAYER_QUIT.register(player -> {
            ChestSeparatorsState.LOCKED_CHESTS.values().removeIf(uuid -> uuid.equals(player.getUUID()));
            ChestSeparatorsState.INVENTORY_FILTERS.remove(player.getUUID());
        });

        // Fills the player's inventory from the container they currently have open.
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S, FillFromChestPayload.TYPE, FillFromChestPayload.CODEC, (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            performFillFromOpenContainer(
                                    player, payload.animPos(), payload.includeEmpty(), payload.lockHotbar());
                        }
                    });
                });

        // After a "push into the open chest" deposit, re-sort that container's filtered groups by priority
        // order (the deposit itself is done client-side via slot clicks; this makes higher-priority arrivals
        // move ahead of lower-priority items already stored).
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                SortOpenFiltersPayload.TYPE,
                SortOpenFiltersPayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (!(context.getPlayer() instanceof ServerPlayer p) || p.containerMenu == null) return;
                        for (net.minecraft.world.inventory.Slot s : p.containerMenu.slots) {
                            if (s.container instanceof Inventory) continue;
                            if (s.container instanceof IWhitelistProvider provider) {
                                reorderFilteredGroups(s.container, provider.getWhitelists());
                            }
                            break; // the container is a single inventory shared by all its slots
                        }
                        p.containerMenu.broadcastChanges();
                    });
                });

        // Caches the player's inventory filters (synced from the client) for the Pick Up rule.
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                InventoryFiltersPayload.TYPE,
                InventoryFiltersPayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            ChestSeparatorsState.INVENTORY_FILTERS.put(player.getUUID(), payload.filters());
                        }
                    });
                });

        // Persists incoming whitelist updates for ENTITY containers (chest/hopper minecarts). Applying it
        // to the live server-side entity is what makes the Hopper Insert rule actually enforce on minecarts,
        // and the entity's own NBT persistence keeps it across reload.
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                EntityWhitelistPayload.TYPE,
                EntityWhitelistPayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                        Level world = player.level();
                        if (!(world instanceof ServerLevel serverWorld)) return;
                        Entity entity = serverWorld.getEntity(payload.entityUuid());
                        if (entity instanceof IWhitelistProvider provider) {
                            provider.setWhitelists(payload.whitelists());
                        }
                    });
                });

        // Handles editor lock requests, with support for double chests (two associated positions).
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                EditorLockRequestPayload.TYPE,
                EditorLockRequestPayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                        UUID playerUuid = player.getUUID();
                        BlockPos targetPos = payload.pos();
                        Level world = player.level();

                        // Resolve all block positions occupied by this container (1 for single, 2 for double).
                        List<BlockPos> associatedPositions = getAssociatedPositions(world, targetPos);

                        if (payload.isLocking()) {
                            boolean canLock = true;

                            // Deny the lock if any part of the container is held by a different player.
                            for (BlockPos p : associatedPositions) {
                                if (ChestSeparatorsState.LOCKED_CHESTS.containsKey(p)
                                        && !ChestSeparatorsState.LOCKED_CHESTS.get(p).equals(playerUuid)) {
                                    canLock = false;
                                    break;
                                }
                            }

                            if (canLock) {
                                // Acquire all positions atomically so no partial lock can slip through.
                                for (BlockPos p : associatedPositions) {
                                    ChestSeparatorsState.LOCKED_CHESTS.put(p, playerUuid);
                                }
                            }
                            if (NetworkManager.canPlayerReceive(player, EditorLockResponsePayload.TYPE)) {
                                NetworkManager.sendToPlayer(player, new EditorLockResponsePayload(targetPos, canLock));
                            }
                        } else {
                            // Release all associated positions when the player exits the editor.
                            for (BlockPos p : associatedPositions) {
                                if (ChestSeparatorsState.LOCKED_CHESTS.containsKey(p)
                                        && ChestSeparatorsState.LOCKED_CHESTS.get(p).equals(playerUuid)) {
                                    ChestSeparatorsState.LOCKED_CHESTS.remove(p);
                                }
                            }
                        }
                    });
                });

        // Handles whitelist fetch requests sent by the client when a container is first opened.
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                WhitelistRequestPayload.TYPE,
                WhitelistRequestPayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                        Level world = player.level();
                        var custom = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(player.containerMenu);
                        if (custom != null) {
                            if (NetworkManager.canPlayerReceive(player, WhitelistS2CPayload.TYPE))
                                NetworkManager.sendToPlayer(player, new WhitelistS2CPayload(BlockPos.ZERO, custom.filters()));
                            return;
                        }
                        Container targetInventory = getChestInventorySafe(world, payload.pos());

                        if (targetInventory instanceof IWhitelistProvider provider) {
                            if (NetworkManager.canPlayerReceive(player, WhitelistS2CPayload.TYPE)) {
                                NetworkManager.sendToPlayer(
                                        player, new WhitelistS2CPayload(payload.pos(), provider.getWhitelists()));
                            }
                        }
                    });
                });

        // Persists incoming whitelist updates from the client and broadcasts them to co-viewers.
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S, WhitelistPayload.TYPE, WhitelistPayload.CODEC, (payload, context) -> {
                    context.queue(() -> {
                        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                        var storage = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(player.containerMenu);
                        if (storage != null) {
                            if (!player.containerMenu.stillValid(player)) return;
                            storage.filters(payload.whitelists());
                            for (ServerPlayer viewer : player.server.getPlayerList().getPlayers()) {
                                var other = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(viewer.containerMenu);
                                if (other != null && other.identity().equals(storage.identity()) && NetworkManager.canPlayerReceive(viewer, WhitelistS2CPayload.TYPE))
                                    NetworkManager.sendToPlayer(viewer, new WhitelistS2CPayload(BlockPos.ZERO, storage.filters()));
                            }
                            return;
                        }
                        Level world = player.level();
                        BlockState state = world.getBlockState(payload.pos());
                        boolean isEnderChest = state.getBlock() == Blocks.ENDER_CHEST;

                        // Ender Chest filters are local-only (hoppers cannot access them); nothing to do here.
                        if (isEnderChest) return;

                        var custom = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(player.containerMenu);
                        Container targetInventory = getChestInventorySafe(world, payload.pos());

                        if (targetInventory instanceof IWhitelistProvider provider) {
                            provider.setWhitelists(payload.whitelists());
                            targetInventory.setChanged();

                            // Broadcast the updated whitelist to all other players viewing this container.
                            if (world instanceof ServerLevel serverWorld) {
                                for (ServerPlayer trackingPlayer : serverWorld
                                        .getChunkSource()
                                        .chunkMap
                                        .getPlayers(new ChunkPos(payload.pos()), false)) {
                                    if (trackingPlayer != player
                                            && NetworkManager.canPlayerReceive(
                                                    trackingPlayer, WhitelistS2CPayload.TYPE)) {
                                        NetworkManager.sendToPlayer(
                                                trackingPlayer,
                                                new WhitelistS2CPayload(payload.pos(), payload.whitelists()));
                                    }
                                }
                            }

                            // Eject items that violate the new whitelist when all three rules are enabled.
                            if (GlobalChestConfig.instance.expelInvalidItems) {
                                for (int i = 0; i < targetInventory.getContainerSize(); i++) {
                                    ItemStack stack = targetInventory.getItem(i);
                                    if (!stack.isEmpty() && payload.whitelists().containsKey(i)) {
                                        SlotWhitelist wl = payload.whitelists().get(i);

                                        if (wl.allowManual() && wl.allowShift() && wl.allowHopper()) {
                                            String itemId = BuiltInRegistries.ITEM
                                                    .getKey(stack.getItem())
                                                    .toString();

                                            if (!wl.allowedItems().contains(itemId)) {
                                                ItemStack extracted = targetInventory.removeItemNoUpdate(i);

                                                double dropX = payload.pos().getX() + 0.5D;
                                                double dropY = payload.pos().getY() + 0.5D;
                                                double dropZ = payload.pos().getZ() + 0.5D;

                                                ItemEntity itemEntity =
                                                        new ItemEntity(world, dropX, dropY, dropZ, extracted);

                                                double dirX = player.getX() - dropX;
                                                double dirY = player.getEyeY() - dropY;
                                                double dirZ = player.getZ() - dropZ;

                                                double distance =
                                                        Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
                                                dirX /= distance;
                                                dirY /= distance;
                                                dirZ /= distance;

                                                double force = 0.3D;
                                                itemEntity.setDeltaMovement(
                                                        dirX * force + EXPEL_RANDOM.nextGaussian() * 0.05D,
                                                        dirY * force + EXPEL_RANDOM.nextGaussian() * 0.05D + 0.1D,
                                                        dirZ * force + EXPEL_RANDOM.nextGaussian() * 0.05D);

                                                itemEntity.setDefaultPickUpDelay();
                                                world.addFreshEntity(itemEntity);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    });
                });

        // Handles the radius auto-deposit trigger: sort the player's inventory into nearby filtered
        // containers and report back what moved so the client can animate it.
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                AutoDepositRequestPayload.TYPE,
                AutoDepositRequestPayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (!(context.getPlayer() instanceof ServerPlayer p)) return;
                        performAutoDeposit(
                                p,
                                payload.radius(),
                                payload.throughWalls(),
                                payload.enderWhitelists(),
                                payload.entityWhitelists());
                    });
                });

        // S2C send types must be registered on the dedicated server so it can send them. On the physical
        // client the same types are registered by the client-side receiver registration.
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(CommunityReply.TYPE, CommunityReply.CODEC);
            NetworkManager.registerS2CPayloadType(ShulkerUUIDPayload.TYPE, ShulkerUUIDPayload.CODEC);
            NetworkManager.registerS2CPayloadType(EditorLockResponsePayload.TYPE, EditorLockResponsePayload.CODEC);
            NetworkManager.registerS2CPayloadType(AutoDepositResultPayload.TYPE, AutoDepositResultPayload.CODEC);
            NetworkManager.registerS2CPayloadType(WhitelistS2CPayload.TYPE, WhitelistS2CPayload.CODEC);
            NetworkManager.registerS2CPayloadType(EntityWhitelistS2CPayload.TYPE, EntityWhitelistS2CPayload.CODEC);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Server-side logic (ported verbatim from ChestSeparatorsMain, yarn -> Mojmap).
    // ------------------------------------------------------------------------------------------------

    /** A filtered container eligible to receive items during an auto-deposit. */
    private record Candidate(BlockPos pos, Container inv, Map<Integer, SlotWhitelist> whitelists, double distSq) {}

    /**
     * Gathers every filtered container in range (block entities, chest minecarts/boats, and the
     * player's ender inventory at the nearest reachable ender chest), sorted nearest first. Containers
     * blocked from every lateral side are skipped unless {@code throughWalls} is set.
     */
    private static List<Candidate> collectCandidates(
            ServerPlayer player,
            int radius,
            boolean throughWalls,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        Level world = player.level();
        List<Candidate> candidates = new ArrayList<>();
        if (!(world instanceof ServerLevel)) return candidates;

        radius = Math.max(1, Math.min(radius, MAX_AUTO_DEPOSIT_RADIUS));
        Vec3 eye = player.getEyePosition();
        BlockPos origin = player.blockPosition();
        long radiusSq = (long) radius * radius;

        int chunkRadius = (radius >> 4) + 1;
        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;

        // Nearest reachable ender chest block, used as the target for the player's ender inventory
        // (ender filters are client-side, so they arrive in the request payload).
        BlockPos nearestEnder = null;
        double nearestEnderDistSq = Double.MAX_VALUE;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = world.getChunk(centerChunkX + dx, centerChunkZ + dz);
                for (Map.Entry<BlockPos, BlockEntity> entry :
                        chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    BlockEntity be = entry.getValue();

                    double cdx = pos.getX() + 0.5D - eye.x;
                    double cdy = pos.getY() + 0.5D - eye.y;
                    double cdz = pos.getZ() + 0.5D - eye.z;
                    double distSq = cdx * cdx + cdy * cdy + cdz * cdz;
                    if (distSq > radiusSq) continue;

                    if (be instanceof EnderChestBlockEntity) {
                        if (distSq < nearestEnderDistSq
                                && (throughWalls || !isBlockObstructed(world, eye, pos, player))) {
                            nearestEnder = pos;
                            nearestEnderDistSq = distSq;
                        }
                        continue;
                    }

                    if (!(be instanceof Container inv) || !(be instanceof IWhitelistProvider provider)) continue;
                    Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();
                    if (whitelists == null || whitelists.isEmpty()) continue;

                    if (!throughWalls && isBlockObstructed(world, eye, pos, player)) continue;

                    candidates.add(new Candidate(pos, inv, whitelists, distSq));
                }
            }
        }

        // Mobile filtered containers: chest minecarts and chest boats are entities, not block entities,
        // and their filters live client-side only, so they arrive in entityWhitelists keyed by UUID.
        AABB box = player.getBoundingBox().inflate(radius);
        for (Entity entity : world.getEntities(player, box, e -> e instanceof Container)) {
            Map<Integer, SlotWhitelist> whitelists = entityWhitelists.get(entity.getUUID());
            if (whitelists == null || whitelists.isEmpty()) continue;

            Vec3 center = entity.getBoundingBox().getCenter();
            double distSq = center.distanceToSqr(eye);
            if (distSq > radiusSq) continue;
            if (!throughWalls && blockedToPoint(world, eye, center, player, null)) continue;

            candidates.add(new Candidate(entity.blockPosition(), (Container) entity, whitelists, distSq));
        }

        // Ender chest: the player's ender inventory, animated at the nearest reachable ender chest
        // block, using the client-supplied ender filter.
        if (nearestEnder != null && enderWhitelists != null && !enderWhitelists.isEmpty()) {
            candidates.add(
                    new Candidate(nearestEnder, player.getEnderChestInventory(), enderWhitelists, nearestEnderDistSq));
        }

        candidates.sort(java.util.Comparator.comparingDouble(c -> c.distSq));
        return candidates;
    }

    /**
     * Sorts every stack in the player's main inventory into nearby containers whose filters list the
     * item, nearest containers first. Sends an {@link AutoDepositResultPayload} describing every transfer.
     */
    private static void performAutoDeposit(
            ServerPlayer player,
            int radius,
            boolean throughWalls,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        Level world = player.level();
        if (!(world instanceof ServerLevel)) return;

        List<Candidate> candidates = collectCandidates(player, radius, throughWalls, enderWhitelists, entityWhitelists);

        // 2. Deposit each stack into matching candidate slots, recording moved amounts per container.
        Inventory inventory = player.getInventory();
        // Preserve discovery order so the animation roughly mirrors inventory layout.
        Map<BlockPos, Map<Item, Integer>> moved = new java.util.LinkedHashMap<>();
        // Containers that actually received items, so only those get their groups re-sorted afterwards.
        java.util.Set<Candidate> touched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

            for (Candidate candidate : candidates) {
                if (stack.isEmpty()) break;
                if (!containerListsItem(candidate.whitelists, itemId)) continue;

                int before = stack.getCount();
                insertRespectingFilter(candidate.inv, candidate.whitelists, stack, itemId);
                int delta = before - stack.getCount();
                if (delta > 0) {
                    candidate.inv.setChanged();
                    touched.add(candidate);
                    moved.computeIfAbsent(candidate.pos, k -> new java.util.LinkedHashMap<>())
                            .merge(item, delta, Integer::sum);
                }
            }
            inventory.setItem(i, stack);
        }

        // Auto-organize each container that received items: sort every filtered group by priority order.
        for (Candidate candidate : touched) {
            reorderFilteredGroups(candidate.inv, candidate.whitelists);
        }

        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();

        // 3. Build the animation report.
        if (NetworkManager.canPlayerReceive(player, AutoDepositResultPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, new AutoDepositResultPayload(buildFlights(moved)));
        }
    }

    /**
     * Fills the player's inventory from the container they currently have open (the editor's "fill
     * inventory" button): pulls every item any inventory filter lists, or — with Shift ({@code includeEmpty})
     * — everything into whatever free space is left. {@code animPos} is only the origin of the animation.
     */
    private static void performFillFromOpenContainer(
            ServerPlayer player, BlockPos animPos, boolean includeEmpty, boolean lockHotbar) {
        if (!(player.level() instanceof ServerLevel)) return;

        // The open container's inventory: the first menu slot not backed by the player.
        Container container = null;
        for (net.minecraft.world.inventory.Slot s : player.containerMenu.slots) {
            if (!(s.container instanceof Inventory)) {
                container = s.container;
                break;
            }
        }
        if (container == null) return;

        Map<Integer, SlotWhitelist> invFilters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUUID());
        Inventory inventory = player.getInventory();
        Map<Item, Integer> movedItems = new java.util.LinkedHashMap<>();

        int size = container.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

            // Pull items the inventory filters list; with Shift, pull everything (into free space).
            if (!includeEmpty && !inventoryListsItem(invFilters, itemId)) continue;

            int take = stack.getCount();
            ItemStack portion = stack.copyWithCount(take);
            if (includeEmpty) {
                // Shift: pull everything into whatever free space is left (vanilla spill).
                inventory.add(portion);
            } else {
                // No Shift: only top up / fill the slots whose filter lists this item — never overflow into
                // unfiltered slots. Any surplus the filtered slots can't hold stays in the container.
                insertRespectingFilter(inventory, invFilters, portion, itemId);
            }
            int delta = take - portion.getCount();
            if (delta > 0) {
                stack.shrink(delta);
                container.setItem(slot, stack);
                movedItems.merge(item, delta, Integer::sum);
            }
        }

        // Auto-organize the inventory's filtered groups after a pull, so items land in priority order.
        // Restricted to the MAIN inventory (0..INVENTORY_SIZE): armor and offhand slots are type-specific.
        if (!movedItems.isEmpty() && invFilters != null) {
            // Hotbar is slots 0-8; keep it out of the sort when the option is on so a held item never moves.
            int firstSortable = lockHotbar ? 9 : 0;
            Map<Integer, SlotWhitelist> mainFilters = new java.util.HashMap<>();
            for (Map.Entry<Integer, SlotWhitelist> e : invFilters.entrySet()) {
                if (e.getKey() >= firstSortable && e.getKey() < Inventory.INVENTORY_SIZE) {
                    mainFilters.put(e.getKey(), e.getValue());
                }
            }
            reorderFilteredGroups(inventory, mainFilters);
        }

        container.setChanged();
        inventory.setChanged();
        player.containerMenu.broadcastChanges();

        Map<BlockPos, Map<Item, Integer>> moved = new java.util.LinkedHashMap<>();
        if (!movedItems.isEmpty() && animPos != null) moved.put(animPos, movedItems);
        if (NetworkManager.canPlayerReceive(player, AutoDepositResultPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, new AutoDepositResultPayload(buildFlights(moved), true));
        }
    }

    /** Builds one animation flight per (item, container) pair from a moved-amount report. */
    private static List<AutoDepositResultPayload.Flight> buildFlights(Map<BlockPos, Map<Item, Integer>> moved) {
        List<AutoDepositResultPayload.Flight> flights = new ArrayList<>();
        for (Map.Entry<BlockPos, Map<Item, Integer>> chestEntry : moved.entrySet()) {
            for (Map.Entry<Item, Integer> itemEntry : chestEntry.getValue().entrySet()) {
                ItemStack representative = new ItemStack(itemEntry.getKey(), itemEntry.getValue());
                flights.add(new AutoDepositResultPayload.Flight(representative, chestEntry.getKey()));
            }
        }
        return flights;
    }

    /** True when any of the player's inventory filters lists the item (so a Fill should pull it in). */
    private static boolean inventoryListsItem(Map<Integer, SlotWhitelist> invFilters, String itemId) {
        if (invFilters == null) return false;
        for (SlotWhitelist wl : invFilters.values()) {
            if (wl.allowedItems().contains(itemId)) return true;
        }
        return false;
    }

    /** True if any slot whitelist of the container lists the given item id. */
    private static boolean containerListsItem(Map<Integer, SlotWhitelist> whitelists, String itemId) {
        for (SlotWhitelist wl : whitelists.values()) {
            if (wl.allowedItems().contains(itemId)) return true;
        }
        return false;
    }

    /**
     * Inserts as much of {@code stack} as possible into the container, but only into slots whose
     * filter explicitly lists the item: first merging onto matching stacks, then filling empty
     * filtered slots. Mutates {@code stack}'s count in place.
     */
    private static void insertRespectingFilter(
            Container inv, Map<Integer, SlotWhitelist> whitelists, ItemStack stack, String itemId) {
        int size = inv.getContainerSize();

        // Phase 1: top up existing identical stacks in filtered slots.
        for (int slot = 0; slot < size && !stack.isEmpty(); slot++) {
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || !wl.allowedItems().contains(itemId)) continue;
            ItemStack dest = inv.getItem(slot);
            if (dest.isEmpty() || !ItemStack.isSameItemSameComponents(dest, stack)) continue;
            int max = Math.min(inv.getMaxStackSize(dest), dest.getMaxStackSize());
            int space = max - dest.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, stack.getCount());
            dest.grow(move);
            stack.shrink(move);
        }

        // Phase 2: drop the remainder into empty filtered slots, in the filter's priority order.
        List<Integer> emptySlots = new java.util.ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || !wl.allowedItems().contains(itemId)) continue;
            if (!inv.getItem(slot).isEmpty() || !inv.canPlaceItem(slot, stack)) continue;
            emptySlots.add(slot);
        }
        emptySlots.sort(java.util.Comparator.comparingInt(
                        (Integer slot) -> FilterPriority.slotPreference(whitelists, slot, itemId))
                .thenComparingInt(slot -> slot));
        for (int slot : emptySlots) {
            if (stack.isEmpty()) break;
            int max = Math.min(inv.getMaxStackSize(stack), stack.getMaxStackSize());
            int move = Math.min(max, stack.getCount());
            inv.setItem(slot, stack.copyWithCount(move));
            stack.shrink(move);
        }
    }

    /**
     * Re-lays every filtered group of {@code inv} so its stacks sit in the filter's priority order, packed
     * from the group's first slot. Server-authoritative (direct {@link Container#setItem}) so nothing can
     * desync or leave ghosts. Items the filter does not list keep their relative place at the end.
     */
    private static void reorderFilteredGroups(Container inv, Map<Integer, SlotWhitelist> whitelists) {
        if (inv == null || whitelists == null || whitelists.isEmpty()) return;

        // Collect each group's slot indices in ascending (normal Minecraft) slot order.
        Map<UUID, java.util.List<Integer>> groups = new java.util.LinkedHashMap<>();
        java.util.List<Integer> keys = new ArrayList<>(whitelists.keySet());
        java.util.Collections.sort(keys);
        for (int slot : keys) {
            if (slot < 0 || slot >= inv.getContainerSize()) continue;
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || wl.groupId() == null) continue;
            groups.computeIfAbsent(wl.groupId(), g -> new ArrayList<>()).add(slot);
        }

        for (java.util.List<Integer> slots : groups.values()) {
            if (slots.size() < 2) continue;
            java.util.List<String> order = whitelists.get(slots.get(0)).allowedItems();

            // Snapshot the group's non-empty stacks (references), then sort by the filter's priority order.
            java.util.List<ItemStack> stacks = new ArrayList<>();
            for (int slot : slots) {
                ItemStack st = inv.getItem(slot);
                if (!st.isEmpty()) stacks.add(st);
            }
            if (stacks.isEmpty()) continue;
            stacks.sort(java.util.Comparator.<ItemStack>comparingInt(st -> {
                        int rank = order.indexOf(
                                BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
                        return rank < 0 ? Integer.MAX_VALUE : rank;
                    })
                    // Tie-break equal-ranked stacks (the same item overflowing several of the group's slots)
                    // by count, fuller first — so a 65-item deposit ends as [64, 1] and not [1, 64].
                    .thenComparing(java.util.Comparator.comparingInt(ItemStack::getCount).reversed()));

            // Re-lay packed from the first slot; any trailing slots become empty.
            for (int i = 0; i < slots.size(); i++) {
                inv.setItem(slots.get(i), i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
            }
        }
        inv.setChanged();
    }

    /**
     * True if no unobstructed path exists from the player's eye to a free lateral side of the
     * container. Access must come through one of the four horizontal sides (not the top or bottom).
     */
    private static boolean isBlockObstructed(Level world, Vec3 eye, BlockPos chestPos, Entity player) {
        List<BlockPos> parts = getAssociatedPositions(world, chestPos);
        for (BlockPos part : parts) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = part.relative(dir);
                if (parts.contains(neighbor)) continue;
                // A clear line to this open side cell (ending in air) means the chest is reachable.
                if (!blockedToPoint(world, eye, Vec3.atCenterOf(neighbor), player, null)) return false;
            }
        }
        return true;
    }

    /**
     * True if a solid block lies between {@code eye} and {@code target}. Block positions in
     * {@code allowed} (e.g. the target container's own blocks) are not treated as obstructions; pass
     * {@code null} when the target is an empty point such as an entity's center.
     */
    private static boolean blockedToPoint(
            Level world, Vec3 eye, Vec3 target, Entity player, java.util.Collection<BlockPos> allowed) {
        BlockHitResult hit = world.clip(
                new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return false;
        return allowed == null || !allowed.contains(hit.getBlockPos());
    }

    /**
     * Returns the direction toward the other half of a double chest, or null when the block is not
     * part of a double chest (single chest, shulker box, barrel, or any non-chest container).
     */
    private static Direction getDoubleChestNeighborDirection(BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            return null;
        }
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) {
            return null;
        }
        Direction facing = state.getValue(ChestBlock.FACING);
        return type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    /**
     * Returns the effective inventory for the given position, merging both halves for double chests.
     */
    private static Container getChestInventorySafe(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockEntity be = world.getBlockEntity(pos);

        Direction neighborDir = getDoubleChestNeighborDirection(state);
        if (neighborDir != null) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            BlockEntity neighborBe = world.getBlockEntity(pos.relative(neighborDir));

            if (be instanceof Container && neighborBe instanceof Container) {
                if (type == ChestType.RIGHT) {
                    return new CompoundContainer((Container) be, (Container) neighborBe);
                } else {
                    return new CompoundContainer((Container) neighborBe, (Container) be);
                }
            }
        }
        // Single chest, Shulker Box, or Barrel — return directly.
        return be instanceof Container inv ? inv : null;
    }

    /**
     * Resolves all block positions occupied by a container (2 for double chests, 1 for all others).
     */
    private static List<BlockPos> getAssociatedPositions(Level world, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>();
        list.add(pos);

        Direction neighborDir = getDoubleChestNeighborDirection(world.getBlockState(pos));
        if (neighborDir != null) {
            list.add(pos.relative(neighborDir));
        }
        return list;
    }
}
