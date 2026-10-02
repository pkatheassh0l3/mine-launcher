package es.ascension.servers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.LoggerFactory;

@Mod(value = "ascension_server_defaults", dist = Dist.CLIENT)
public final class AscensionServerDefaults {
    public AscensionServerDefaults(IEventBus bus) {
        bus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            try {
                ServerDefaults.ensure(FMLPaths.GAMEDIR.get().resolve("servers.dat"));
            } catch (Exception e) {
                LoggerFactory.getLogger("AscensionServerDefaults").error("Could not add Ascension to the server list", e);
            }
        }));
    }
}
