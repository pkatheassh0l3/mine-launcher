package io.github.marcsanzdev.chestseparators.fabric;

import io.github.marcsanzdev.chestseparators.AbsoluteOrderClient;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point — delegates to the common client bootstrap. */
public final class AbsoluteOrderClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AbsoluteOrderClient.init();
    }
}
