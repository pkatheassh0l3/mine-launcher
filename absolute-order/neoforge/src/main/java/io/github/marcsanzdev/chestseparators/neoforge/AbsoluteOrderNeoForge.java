package io.github.marcsanzdev.chestseparators.neoforge;

import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import io.github.marcsanzdev.chestseparators.AbsoluteOrder;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge loader entry point — delegates to the common bootstrap (and the client bootstrap on the client). */
@Mod(AbsoluteOrder.MOD_ID)
public final class AbsoluteOrderNeoForge {

    public AbsoluteOrderNeoForge(ModContainer modContainer) {
        AbsoluteOrder.init();
        if (net.neoforged.fml.ModList.get().isLoaded("sophisticatedcore")) SophisticatedCompatibility.init();
        // Client-only bootstrap (common client init + the Cloth Config screen registration). The double-lambda
        // defers the class references so the dedicated server JVM never loads the client-only classes.
        EnvExecutor.runInEnv(Env.CLIENT, () -> () -> NeoForgeClientBootstrap.init(modContainer));
    }
}
