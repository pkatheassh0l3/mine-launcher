package io.github.marcsanzdev.chestseparators.compat;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import java.util.Collections;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class ChestSeparatorsReiPlugin implements REIClientPlugin {

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(AbstractContainerScreen.class, screen -> {
            ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();

            // While the editor is active, claim the entire screen to prevent REI from rendering over it.
            if (editor != null
                    && editor.getSession() != null
                    && editor.getSession().currentState != EditorState.HIDDEN) {
                return Collections.singletonList(new Rectangle(0, 0, 10000, 10000));
            }

            return Collections.emptyList();
        });
    }
}
