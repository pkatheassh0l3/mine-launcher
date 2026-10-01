package io.github.marcsanzdev.chestseparators.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// Represents the whitelist configuration for a single inventory slot.
// Includes a groupId to visually and logically link multiple slots together.
// targetCount is the desired amount the player wants to keep of this filter in their inventory
// (0 = no target / unlimited); it drives the grab and deposit-junk hotkeys.
public record SlotWhitelist(
        UUID groupId,
        List<String> allowedItems,
        boolean allowManual,
        boolean allowShift,
        boolean allowHopper,
        int targetCount) {

    /** Backwards-compatible constructor for the pre-targetCount call sites (defaults to no target). */
    public SlotWhitelist(
            UUID groupId, List<String> allowedItems, boolean allowManual, boolean allowShift, boolean allowHopper) {
        this(groupId, allowedItems, allowManual, allowShift, allowHopper, 0);
    }

    // Codec used for saving this data into the world's NBT files (.mca) and Item Data Components.
    // target_count is optional so worlds saved before this field still load (defaulting to 0).
    public static final Codec<SlotWhitelist> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("group_id").forGetter(SlotWhitelist::groupId),
                    Codec.STRING.listOf().fieldOf("allowed_items").forGetter(SlotWhitelist::allowedItems),
                    Codec.BOOL.fieldOf("allow_manual").forGetter(SlotWhitelist::allowManual),
                    Codec.BOOL.fieldOf("allow_shift").forGetter(SlotWhitelist::allowShift),
                    Codec.BOOL.fieldOf("allow_hopper").forGetter(SlotWhitelist::allowHopper),
                    Codec.INT.optionalFieldOf("target_count", 0).forGetter(SlotWhitelist::targetCount))
            .apply(instance, SlotWhitelist::new));

    // Stream codec for transmitting this data over the network between client and server.
    public static final StreamCodec<FriendlyByteBuf, SlotWhitelist> PACKET_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            SlotWhitelist::groupId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
            SlotWhitelist::allowedItems,
            ByteBufCodecs.BOOL,
            SlotWhitelist::allowManual,
            ByteBufCodecs.BOOL,
            SlotWhitelist::allowShift,
            ByteBufCodecs.BOOL,
            SlotWhitelist::allowHopper,
            ByteBufCodecs.VAR_INT,
            SlotWhitelist::targetCount,
            SlotWhitelist::new);

    // Codec for a whole slot->filter map. The map is keyed by int slot index, but NBT/JSON map keys must
    // be strings, so this stores string keys and converts back on load. Reused by the data component and
    // by the block-entity NBT persistence so both share one on-disk shape.
    public static final Codec<Map<Integer, SlotWhitelist>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC)
            .xmap(
                    stringMap -> {
                        Map<Integer, SlotWhitelist> intMap = new HashMap<>();
                        stringMap.forEach((k, v) -> {
                            try {
                                intMap.put(Integer.parseInt(k), v);
                            } catch (NumberFormatException ignored) {
                            }
                        });
                        return intMap;
                    },
                    intMap -> {
                        Map<String, SlotWhitelist> stringMap = new HashMap<>();
                        intMap.forEach((k, v) -> stringMap.put(String.valueOf(k), v));
                        return stringMap;
                    });
}
