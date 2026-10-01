package io.github.marcsanzdev.chestseparators.client.ui;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import net.minecraft.client.Minecraft;

/** Temporary suppression: never modifies JEI's saved toggle or search state. */
public final class RecipeOverlayGuard {
    private RecipeOverlayGuard() {}
    public static boolean suppress(boolean sameScreen,boolean inventory,EditorState state,boolean presets,boolean naming) {
        return sameScreen && !inventory && (state!=EditorState.HIDDEN || presets || naming);
    }
    public static boolean active() {
        var editor=ChestSeparatorsEditor.getInstance();
        if(editor==null)return false;
        var session=editor.getSession();
        return session!=null && suppress(Minecraft.getInstance().screen==editor.screen,session.isInventoryScreenContext,session.currentState,session.isPresetsMenuOpen,CommunityNamePrompt.active());
    }
}
