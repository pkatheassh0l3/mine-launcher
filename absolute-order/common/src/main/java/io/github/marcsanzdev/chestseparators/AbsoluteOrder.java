package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.network.ModNetworking;
import io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents;

/** Common (loader-agnostic) entry point, run on every loader from each platform initializer. */
public final class AbsoluteOrder {

    public static final String MOD_ID = "chestseparators";

    private AbsoluteOrder() {}

    public static void init() {
        // Common bootstrap runs on every loader (both physical sides).
        GlobalChestConfig.loadConfig();
        ChestSeparatorsComponents.initialize();
        ModNetworking.init();
    }
}
