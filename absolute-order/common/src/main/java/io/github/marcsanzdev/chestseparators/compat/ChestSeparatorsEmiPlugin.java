package io.github.marcsanzdev.chestseparators.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;

// @EmiEntrypoint is discovered by EMI's own class scan on both Fabric and NeoForge, so this plugin lives in
// common and needs no per-loader entrypoint. EMI is a compile-only API here; the plugin only runs if EMI is
// actually installed at runtime.
@EmiEntrypoint
public class ChestSeparatorsEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea((screen, consumer) -> {
            ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();

            // While the editor is active, claim the entire screen to prevent EMI from rendering over it.
            if (editor != null
                    && editor.getSession() != null
                    && editor.getSession().currentState != EditorState.HIDDEN) {
                consumer.accept(new Bounds(0, 0, 10000, 10000));
            }
        });
    }
}
