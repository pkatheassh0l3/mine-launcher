package io.github.marcsanzdev.chestseparators.fabric;

import io.github.marcsanzdev.chestseparators.AbsoluteOrder;
import net.fabricmc.api.ModInitializer;

/** Fabric loader entry point — delegates to the common bootstrap. */
public final class AbsoluteOrderFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AbsoluteOrder.init();
    }
}
