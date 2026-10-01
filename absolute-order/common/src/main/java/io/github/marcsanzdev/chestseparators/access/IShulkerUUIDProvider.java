package io.github.marcsanzdev.chestseparators.access;

import java.util.UUID;

// Duck interface to expose the internal UUID of a Shulker Box.
// This allows other parts of the codebase to retrieve or modify the unique identifier
// without needing direct access to the modified BlockEntity class.
public interface IShulkerUUIDProvider {

    // Retrieves the UUID associated with this Shulker Box.
    // If no UUID is currently assigned, it should generate and store a new one.
    UUID getShulkerUUID();

    // Restores a previously saved UUID into this Shulker Box instance.
    // Used when the block is placed back into the world from an ItemStack.
    void setShulkerUUID(UUID uuid);
}
