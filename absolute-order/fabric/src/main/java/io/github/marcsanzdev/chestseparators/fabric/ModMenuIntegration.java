package io.github.marcsanzdev.chestseparators.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.marcsanzdev.chestseparators.client.config.AbsoluteOrderConfigScreen;

/** Fabric ModMenu integration — hosts the common Cloth Config screen. */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AbsoluteOrderConfigScreen::buildScreen;
    }
}
