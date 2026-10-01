package io.github.marcsanzdev.chestseparators.registry;

import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.marcsanzdev.chestseparators.AbsoluteOrder;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

/**
 * Custom Data Components, registered through Architectury's {@link DeferredRegister} so the same code
 * works on every loader. Registering the whitelist map as a persistent component is what makes the game
 * preserve a container's filters when it is broken and re-placed (the component rides on the ItemStack).
 */
public final class ChestSeparatorsComponents {

    private ChestSeparatorsComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(AbsoluteOrder.MOD_ID, Registries.DATA_COMPONENT_TYPE);

    public static final RegistrySupplier<DataComponentType<String>> SHULKER_UUID = COMPONENTS.register(
            "shulker_uuid",
            () -> DataComponentType.<String>builder().persistent(Codec.STRING).build());

    // Component mapping slot indices to their whitelist configurations. Persisted natively so the game
    // preserves the filters when containers are broken or moved.
    public static final RegistrySupplier<DataComponentType<Map<Integer, SlotWhitelist>>> SLOT_WHITELISTS =
            COMPONENTS.register("slot_whitelists", () -> DataComponentType.<Map<Integer, SlotWhitelist>>builder()
                    .persistent(SlotWhitelist.MAP_CODEC)
                    .build());

    /** Flushes the deferred registrations. Called once from the common bootstrap. */
    public static void initialize() {
        COMPONENTS.register();
    }
}
