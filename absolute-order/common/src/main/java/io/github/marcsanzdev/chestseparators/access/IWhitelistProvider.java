package io.github.marcsanzdev.chestseparators.access;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;

// Duck interface applied to BlockEntities (and entity containers) to expose their internal whitelist
// data. Loader-agnostic — no Minecraft types, so it moves to common verbatim.
public interface IWhitelistProvider {

    // Retrieves the current map of slot indices to their whitelist configurations.
    Map<Integer, SlotWhitelist> getWhitelists();

    // Replaces the current whitelist map with a new one and marks the block entity as dirty for saving.
    void setWhitelists(Map<Integer, SlotWhitelist> whitelists);
}
