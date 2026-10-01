package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Adds {@link IWhitelistProvider} to {@link SimpleContainer}, used both as the client-side dummy
 * inventory backing open container GUIs and as the cargo inventory of pack animals (donkeys, mules,
 * llamas). No persistence is performed here; data comes from the server payload or the entity's local
 * UUID-keyed config file.
 */
@Mixin(SimpleContainer.class)
public abstract class SimpleInventoryMixin implements IWhitelistProvider {
    private Map<Integer, SlotWhitelist> whitelists = null;

    @Override
    public Map<Integer, SlotWhitelist> getWhitelists() {
        return this.whitelists;
    }

    @Override
    public void setWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        this.whitelists = whitelists;
    }
}
