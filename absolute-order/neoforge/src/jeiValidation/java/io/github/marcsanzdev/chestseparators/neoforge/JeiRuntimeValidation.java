package io.github.marcsanzdev.chestseparators.neoforge;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import io.github.marcsanzdev.chestseparators.client.ui.*;
import io.github.marcsanzdev.chestseparators.client.EditorState;

/** Isolated client smoke test; never included in the delivered jar. */
@EventBusSubscriber(modid="chestseparators",value=Dist.CLIENT)
public final class JeiRuntimeValidation {
    private static boolean ran;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        var mc=Minecraft.getInstance();if(ran || mc.screen==null)return;ran=true;var original=mc.screen;
        try {
            Class<?> type=Class.forName("mezz.jei.common.config.ClientToggleState");
            Class.forName("mezz.jei.gui.input.ClientInputHandler");Class.forName("mezz.jei.gui.events.GuiEventHandler");
            Object toggles=type.getConstructor().newInstance();var enabled=type.getMethod("isOverlayEnabled");var bookmarks=type.getMethod("isBookmarkOverlayEnabled");var toggle=type.getMethod("toggleOverlayEnabled");
            boolean before=(boolean)enabled.invoke(toggles),beforeBookmarks=(boolean)bookmarks.invoke(toggles);
            var inventory=new net.minecraft.world.entity.player.Inventory(null);
            var screen=new net.minecraft.client.gui.screens.inventory.ContainerScreen(net.minecraft.world.inventory.ChestMenu.threeRows(1,inventory),inventory,net.minecraft.network.chat.Component.literal("Validation"));
            var editor=new ChestSeparatorsEditor(screen);editor.getSession().isInventoryScreenContext=false;editor.getSession().currentState=EditorState.EDIT_FILTER;mc.screen=screen;
            if(!RecipeOverlayGuard.active() || (boolean)enabled.invoke(toggles) || (boolean)bookmarks.invoke(toggles))throw new AssertionError("JEI visible while editing");
            toggle.invoke(toggles);editor.getSession().currentState=EditorState.HIDDEN;
            if((boolean)enabled.invoke(toggles)!=before || (boolean)bookmarks.invoke(toggles)!=beforeBookmarks)throw new AssertionError("JEI preference was changed");
            editor.getSession().currentState=EditorState.EDIT_FILTER;mc.screen=original;
            if(RecipeOverlayGuard.active())throw new AssertionError("Suppression leaked to another screen");
            System.out.println("JEI_VALIDATION_OK mixins-applied hidden-during-edit restored-preferences screen-isolation");
            var zoneEditor = new ChestSeparatorsEditor(screen) {
                @Override public void saveSmart() {}
                @Override public void toggleState(EditorState target) { getSession().currentState=target; }
                @Override public void playClickSound(float pitch) {}
                @Override public void playCloseSound() {}
                @Override public void sendWhitelistToServer() {}
                @Override public void syncClientInventoryWhitelists(java.util.Map<Integer,io.github.marcsanzdev.chestseparators.data.SlotWhitelist> filters) {}
            };
            var state=zoneEditor.getSession();
            var manager=io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance();
            int bg=io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_BG;
            state.selectedSlots.clear();state.selectedSlots.add(0);state.selectedSlots.add(1);
            state.selectedGroupId=java.util.UUID.randomUUID();state.currentAllowedItems.clear();state.currentAllowedItems.add("minecraft:iron_ingot");
            state.currentState=EditorState.EDIT_FILTER;state.pendingRegionColor=null;
            int oldColor=manager.getColor(0,bg),outside=manager.getColor(2,bg);
                        zoneEditor.screenEditFilter=new io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenEditFilter(zoneEditor);
            zoneEditor.screenEditFilter.requestFinish();
            if(!state.finishingRegion || !state.isColorPickerOpen || manager.getColor(0,bg)!=oldColor)throw new AssertionError("Required color step skipped");
            zoneEditor.screenEditFilter.completeSave();
            if(state.currentState!=EditorState.EDIT_FILTER)throw new AssertionError("Saved without a color");
            new io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenColorPicker(zoneEditor).closeAndRestore();
            if(state.finishingRegion || state.isColorPickerOpen || state.selectedSlots.size()!=2 || manager.getColor(0,bg)!=oldColor)throw new AssertionError("Cancel lost draft or saved zone");
            zoneEditor.screenEditFilter.requestFinish();
            zoneEditor.acceptRegionColor(0xFF55AA77);
            if(manager.getColor(0,bg)!=oldColor || state.pendingRegionColor==null)throw new AssertionError("Color was not staged");
            zoneEditor.screenEditFilter.completeSave();
            if(manager.getColor(0,bg)!=0xFF55AA77 || manager.getColor(1,bg)!=0xFF55AA77 || manager.getColor(2,bg)!=outside)throw new AssertionError("Color escaped selected region");
            if(!manager.getCurrentWhitelists().get(0).allowedItems().contains("minecraft:iron_ingot"))throw new AssertionError("Filter lost on color save");
            manager.undo();if(manager.getColor(0,bg)!=oldColor)throw new AssertionError("Undo did not restore color");
            state.selectedSlots.clear();state.pendingRegionColor=null;
            System.out.println("ZONE_VALIDATION_OK required-color cancel-preserves-draft explicit-confirm selection-only filter-preserved atomic-undo");
        }catch(Throwable failure){System.out.println("JEI_VALIDATION_FAILED");failure.printStackTrace();}
        finally{mc.screen=original;mc.setScreen(new ThemePreviewScreen());}
    }
}

