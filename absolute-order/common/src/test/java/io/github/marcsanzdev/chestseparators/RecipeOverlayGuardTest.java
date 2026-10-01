package io.github.marcsanzdev.chestseparators;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.RecipeOverlayGuard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RecipeOverlayGuardTest {
    @Test void suppressesOnlyTheActiveStorageEditor(){
        for(var state:EditorState.values())assertEquals(state!=EditorState.HIDDEN,RecipeOverlayGuard.suppress(true,false,state,false,false));
        assertTrue(RecipeOverlayGuard.suppress(true,false,EditorState.HIDDEN,true,false));
        assertTrue(RecipeOverlayGuard.suppress(true,false,EditorState.HIDDEN,false,true));
        assertFalse(RecipeOverlayGuard.suppress(false,false,EditorState.EDIT_FILTER,true,true));
        assertFalse(RecipeOverlayGuard.suppress(true,true,EditorState.EDIT_FILTER,true,true));
    }
    @Test void releasesSuppressionImmediatelyAfterClosingEditor(){
        assertTrue(RecipeOverlayGuard.suppress(true,false,EditorState.EDIT_FILTER,false,false));
        assertFalse(RecipeOverlayGuard.suppress(true,false,EditorState.HIDDEN,false,false));
        assertFalse(RecipeOverlayGuard.suppress(false,false,EditorState.EDIT_FILTER,false,false));
    }
}
