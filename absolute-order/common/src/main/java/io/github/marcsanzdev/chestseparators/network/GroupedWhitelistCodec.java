package io.github.marcsanzdev.chestseparators.network;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;

/** Avoid repeating a group's item list for every storage slot. Accepts legacy positive-count maps. */
public final class GroupedWhitelistCodec {
    private GroupedWhitelistCodec() {}
    public static void write(FriendlyByteBuf buf, Map<Integer, SlotWhitelist> map) {
        Map<SlotWhitelist, Integer> groups = new LinkedHashMap<>();
        for (var rule : map.values()) groups.computeIfAbsent(rule, r -> groups.size());
        buf.writeInt(-1);
        buf.writeVarInt(groups.size());
        for (var rule : groups.keySet()) SlotWhitelist.PACKET_CODEC.encode(buf, rule);
        buf.writeVarInt(map.size());
        map.forEach((slot, rule) -> { buf.writeVarInt(slot); buf.writeVarInt(groups.get(rule)); });
    }
    public static Map<Integer, SlotWhitelist> read(FriendlyByteBuf buf) {
        int count = buf.readInt();
        Map<Integer, SlotWhitelist> result = new HashMap<>();
        if (count >= 0) {
            check(count);
            for (int i = 0; i < count; i++) result.put(buf.readInt(), SlotWhitelist.PACKET_CODEC.decode(buf));
            return result;
        }
        if (count != -1) throw new IllegalArgumentException("Unknown whitelist format");
        int groupCount = buf.readVarInt();
        check(groupCount);
        List<SlotWhitelist> groups = new ArrayList<>();
        for (int i = 0; i < groupCount; i++) groups.add(SlotWhitelist.PACKET_CODEC.decode(buf));
        int slots = buf.readVarInt();
        check(slots);
        for (int i = 0; i < slots; i++) {
            int slot = buf.readVarInt(), group = buf.readVarInt();
            if (slot < 0 || slot >= 16384 || group < 0 || group >= groups.size()) throw new IllegalArgumentException("Invalid whitelist slot/group");
            result.put(slot, groups.get(group));
        }
        return result;
    }
    private static void check(int size) {
        if (size < 0 || size > 16384) throw new IllegalArgumentException("Invalid whitelist count");
    }
}
