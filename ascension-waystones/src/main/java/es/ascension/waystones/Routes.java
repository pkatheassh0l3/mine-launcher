package es.ascension.waystones;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.Minecraft;
import net.blay09.mods.waystones.client.gui.screen.WaystoneSelectionScreen;

@Mod(value="ascension_waystones", dist=Dist.CLIENT)
public final class Routes {
    static boolean classicOnce;
    public Routes() {
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> {
            if (event.getNewScreen() instanceof WaystoneSelectionScreen screen) {
                if (classicOnce) { classicOnce = false; return; }
                if (Minecraft.getInstance().player != null)
                    event.setNewScreen(new RoutesScreen(screen.getMenu()));
            }
        });
    }
}
