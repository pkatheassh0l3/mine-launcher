package io.github.marcsanzdev.chestseparators.neoforge;

import io.github.marcsanzdev.chestseparators.AbsoluteOrderClient;
import io.github.marcsanzdev.chestseparators.client.config.AbsoluteOrderConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge client-only bootstrap, reached only from inside the {@code EnvExecutor.runInEnv(Env.CLIENT, …)}
 * block so the dedicated-server JVM never loads its client class references. Runs the common client init and
 * registers the Cloth Config screen as the mod's config screen (NeoForge's ModMenu equivalent).
 */
final class NeoForgeClientBootstrap {

    private NeoForgeClientBootstrap() {}

    static void init(ModContainer modContainer) {
        AbsoluteOrderClient.init();
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, (container, parent) -> AbsoluteOrderConfigScreen.buildScreen(parent));
    }
}
