package io.github.marcsanzdev.chestseparators.util;

import java.util.UUID;
import net.minecraft.core.BlockPos;

// Functions as a transient state container for context propagation across disjoint system boundaries.
// Specifically designed to bridge the gap between the initial player interaction event (ClientPlayerInteractionManager)
// and the subsequent GUI initialization (HandledScreen), where direct parameter passing is restricted by the vanilla
// architecture.
public class ChestPosStorage {

    // Buffer for the coordinate vectors of static block-entities (Chests, Barrels, Shulker Boxes).
    // Note: Shulker Boxes will ignore this value and use the UUID system instead.
    public static BlockPos lastClickedPos;

    // Stores the registry key of the dimension where the interaction occurred.
    // Necessary to distinguish between identical coordinates in different worlds (Overworld vs Nether).
    public static String lastClickedDimension;

    // --- ENTITY CONTEXT EXTENSIONS ---

    // Unique identifier for mobile inventory holders (e.g., Donkeys, Llamas, Chest Minecarts).
    // Required because entities lack fixed BlockPos coordinates for persistent data keying.
    public static UUID lastClickedEntityUUID;

    // Mode discriminator flag indicating whether the current UI context belongs to a dynamic entity
    // or a static block. Used by the ConfigManager to select the appropriate loading strategy.
    public static boolean isEntityOpened = false;

    // True when the opened entity is a chest/hopper minecart. Hoppers can insert into these, so the mod
    // enables the Hopper Insert rule for them and syncs their filter to the server for enforcement.
    public static boolean isMinecartEntity = false;

    // --- NETWORK EXTENSIONS ---

    // Transient storage for the incoming Shulker Box UUID received via the S2C network packet.
    // Populated precisely when the server confirms the container has been opened.
    public static UUID lastOpenedShulkerUUID = null;
}
