package io.github.marcsanzdev.chestseparators.util;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Slot-ordering rule shared by every automatic insertion path (shift-click quick-move and hopper
 * transfers): an item that has a dedicated filter slot goes into that slot first, and when a group owns
 * several slots the filter's item order decides which one — the list the player arranges by dragging is
 * a priority order, not just a display order.
 *
 * <p>The rule is expressed as a <em>refusal</em>: a slot with no matching filter declines the item
 * while some dedicated slot can still take it. Vanilla then keeps scanning and lands the item in the
 * dedicated slot on its own. Doing it this way — instead of moving stacks around ourselves — matters:
 * vanilla performs the actual transfer, so the client's predicted result and the server's authoritative
 * result are produced by the very same code and agree. Manually relocating stacks desynced them and
 * left ghost items behind.
 *
 * <p>The refusal is always conditional on a dedicated slot having room, so once those are full the
 * item falls back to normal slots and can never get stuck.
 */
public final class FilterPriority {

    private FilterPriority() {}

    /** Every filter of this inventory, keyed by slot index, or null when it carries none. */
    private static Map<Integer, SlotWhitelist> whitelistsOf(Container inventory) {
        if (inventory instanceof IWhitelistProvider provider) {
            return provider.getWhitelists();
        }
        if (inventory instanceof Inventory playerInventory) {
            return ChestSeparatorsState.INVENTORY_FILTERS.get(playerInventory.player.getUUID());
        }
        return null;
    }

    /** The filter attached to a slot of this inventory, or null when it has none. */
    private static SlotWhitelist whitelistFor(Container inventory, int slotIndex) {
        Map<Integer, SlotWhitelist> whitelists = whitelistsOf(inventory);
        return whitelists == null ? null : whitelists.get(slotIndex);
    }

    /** True when this slot's filter explicitly lists the item — i.e. the slot is dedicated to it. */
    private static boolean isDedicatedTo(Container inventory, int slotIndex, String itemId) {
        SlotWhitelist whitelist = whitelistFor(inventory, slotIndex);
        return whitelist != null && whitelist.allowedItems().contains(itemId);
    }

    /** How many slots of the same group come before this one, i.e. its rank inside the group. */
    private static int positionInGroup(Map<Integer, SlotWhitelist> whitelists, int slotIndex, SlotWhitelist whitelist) {
        if (whitelists == null || whitelist.groupId() == null) return 0;
        int position = 0;
        for (Map.Entry<Integer, SlotWhitelist> entry : whitelists.entrySet()) {
            if (entry.getKey() < slotIndex
                    && whitelist.groupId().equals(entry.getValue().groupId())) position++;
        }
        return position;
    }

    /**
     * How well a dedicated slot suits this item, lower being better, or {@link Integer#MAX_VALUE} when the
     * slot's filter does not list the item at all. The filter's item list is an ordered priority — the
     * player arranges it by dragging — so the first item of the list wants the group's first slot, the
     * second item the second slot, and so on. A slot is scored by how far its position in the group is from
     * the item's position in the list; ties keep the earlier slot.
     *
     * <p>Groups smaller than their item list simply run out of exact matches and fall back to the nearest
     * slot, which is the same order read from a shorter row. Shared by every placement path so the shift,
     * hopper, grab and deposit routes all lay items out in the same order.
     */
    public static int slotPreference(Map<Integer, SlotWhitelist> whitelists, int slotIndex, String itemId) {
        if (whitelists == null) return Integer.MAX_VALUE;
        SlotWhitelist whitelist = whitelists.get(slotIndex);
        if (whitelist == null) return Integer.MAX_VALUE;
        int itemRank = whitelist.allowedItems().indexOf(itemId);
        if (itemRank < 0) return Integer.MAX_VALUE;
        return Math.abs(positionInGroup(whitelists, slotIndex, whitelist) - itemRank);
    }

    private static int preference(Container inventory, int slotIndex, String itemId) {
        return slotPreference(whitelistsOf(inventory), slotIndex, itemId);
    }

    /**
     * Occupancy-aware home score for the shift-click path — lower is better, {@link Integer#MAX_VALUE} when
     * the slot's filter does not list the item. Unlike {@link #slotPreference}, which permanently reserves
     * the group's first slots for higher-priority items even when they are absent, this ranks a slot by
     * where the item should sit given what the group ACTUALLY holds right now: an item sits just after every
     * higher-priority item currently present, so when none are present it packs to the group's first slot in
     * normal slot order. (Fixes emeralds landing in the second slot of a [diamond, emerald] filter while no
     * diamonds are stored.)
     */
    private static int occupancyPreference(Slot slot, String itemId) {
        Container inv = slot.container;
        Map<Integer, SlotWhitelist> whitelists = whitelistsOf(inv);
        if (whitelists == null) return Integer.MAX_VALUE;
        SlotWhitelist whitelist = whitelists.get(slot.getContainerSlot());
        if (whitelist == null) return Integer.MAX_VALUE;
        int itemRank = whitelist.allowedItems().indexOf(itemId);
        if (itemRank < 0) return Integer.MAX_VALUE;

        int position = 0; // how many group slots come before this one (its index inside the group)
        int higherPresent = 0; // group slots currently holding a strictly higher-priority item
        for (Map.Entry<Integer, SlotWhitelist> entry : whitelists.entrySet()) {
            SlotWhitelist other = entry.getValue();
            if (whitelist.groupId() == null || !whitelist.groupId().equals(other.groupId())) continue;
            int idx = entry.getKey();
            if (idx < slot.getContainerSlot()) position++;
            if (idx == slot.getContainerSlot()) continue;
            if (idx < 0 || idx >= inv.getContainerSize()) continue;
            ItemStack existing = inv.getItem(idx);
            if (existing.isEmpty()) continue;
            int occRank = other.allowedItems()
                    .indexOf(BuiltInRegistries.ITEM.getKey(existing.getItem()).toString());
            if (occRank >= 0 && occRank < itemRank) higherPresent++;
        }
        return Math.abs(position - higherPresent);
    }

    /**
     * True when the slot at {@code (otherInv, otherIndex)} is a strictly better home for the item than
     * {@code (candidateInv, candidateIndex)}. Strictness matters: it makes "better than" a total order, so
     * two slots can never both defer to each other and leave the item homeless.
     */
    private static boolean isBetterFor(
            Container otherInv,
            int otherIndex,
            Container candidateInv,
            int candidateIndex,
            String itemId,
            boolean otherComesFirst) {
        int otherPreference = preference(otherInv, otherIndex, itemId);
        int candidatePreference = preference(candidateInv, candidateIndex, itemId);
        if (otherPreference != candidatePreference) return otherPreference < candidatePreference;
        return otherComesFirst;
    }

    /** True when a slot holding {@code existing} could still accept part of {@code incoming}. */
    private static boolean hasRoom(ItemStack existing, ItemStack incoming, int maxCount) {
        if (existing.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(existing, incoming) && existing.getCount() < maxCount;
    }

    /**
     * Shift-click path. True when {@code candidate} should decline {@code stack} because another slot
     * inside the target range [{@code start}, {@code end}) is a better home for it — dedicated where the
     * candidate is not, or ranked ahead of it by the filter's order — and still has room.
     */
    public static boolean shouldDefer(NonNullList<Slot> slots, int start, int end, Slot candidate, ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        boolean candidateIsDedicated = isDedicatedTo(candidate.container, candidate.getContainerSlot(), itemId);
        int candidatePreference = candidateIsDedicated ? occupancyPreference(candidate, itemId) : Integer.MAX_VALUE;
        int candidatePosition = candidateIsDedicated ? slots.indexOf(candidate) : -1;
        int from = Math.max(0, start);
        int to = Math.min(end, slots.size());
        for (int i = from; i < to; i++) {
            Slot other = slots.get(i);
            if (other == candidate) continue;
            if (!isDedicatedTo(other.container, other.getContainerSlot(), itemId)) continue;
            // A dedicated candidate only steps aside for a slot the filter's order ranks above it given the
            // group's current contents (occupancy-aware, so absent higher-priority items never reserve a
            // slot); an undedicated candidate steps aside for any dedicated slot at all. The strict
            // preference-then-index comparison keeps this a total order, so two slots can't both defer.
            if (candidateIsDedicated) {
                int otherPreference = occupancyPreference(other, itemId);
                boolean otherIsBetter = otherPreference < candidatePreference
                        || (otherPreference == candidatePreference && i < candidatePosition);
                if (!otherIsBetter) continue;
            }
            if (hasRoom(other.getItem(), stack, other.getMaxStackSize(stack)) && other.mayPlace(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hopper path. True when slot {@code candidateSlot} of {@code inventory} should decline
     * {@code stack} because another slot of the same inventory is a better home for it and still has room.
     */
    public static boolean shouldDefer(Container inventory, int candidateSlot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        boolean candidateIsDedicated = isDedicatedTo(inventory, candidateSlot, itemId);

        int maxCount = Math.min(inventory.getMaxStackSize(stack), stack.getMaxStackSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (i == candidateSlot) continue;
            if (!isDedicatedTo(inventory, i, itemId)) continue;
            // Same rule as the shift-click path: a dedicated slot yields only to one the filter's order
            // ranks above it.
            if (candidateIsDedicated && !isBetterFor(inventory, i, inventory, candidateSlot, itemId, i < candidateSlot))
                continue;
            if (hasRoom(inventory.getItem(i), stack, maxCount) && inventory.canPlaceItem(i, stack)) {
                return true;
            }
        }
        return false;
    }
}
