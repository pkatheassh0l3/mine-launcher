package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import io.github.marcsanzdev.chestseparators.client.input.CharacterEvent;
import io.github.marcsanzdev.chestseparators.client.input.KeyEvent;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class EditorInputHandler {

    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorGeometry geometry;
    private final AbstractContainerScreen<?> screen;
    private final HandledScreenAccessor accessor;

    private final EditorLayout layout;

    public EditorInputHandler(
            ChestSeparatorsEditor editor,
            EditorSessionData session,
            EditorGeometry geometry,
            EditorLayout layout,
            AbstractContainerScreen<?> screen,
            HandledScreenAccessor accessor) {
        this.editor = editor;
        this.session = session;
        this.geometry = geometry;
        this.layout = layout;
        this.screen = screen;
        this.accessor = accessor;
    }

    // Mouse click/release/drag/scroll are routed here from the common GenericContainerScreenMixin
    // (which injects into AbstractContainerScreen's mouse handlers). Each returns the Fabric-style
    // "allow" boolean: true lets the vanilla handler run, false cancels it because the editor consumed it.

    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if(CommunityNamePrompt.active())return true;
        session.isDraggingMainScroll = false;
        session.isDraggingListScroll = false;

        if (session.isColorPickerOpen) {
            editor.screenColorPicker.mouseReleased(mouseX, mouseY, button);
            return true;
        }

        if (session.reorderDragItem != null || session.pendingDragItem != null) {
            editor.screenEditFilter.commitPointerRelease(mouseX, mouseY);
            return true;
        }

        if (button == 0 && session.isDraggingLine) {
            if (session.currentState == EditorState.VIEW_GROUPS
                    || session.currentState == EditorState.SELECT_SLOTS) {

                editor.screenViewGroups.mouseReleased(mouseX, mouseY, button);

            } else if (session.currentState == EditorState.DRAW_LINES) {
                editor.screenDrawLines.commitDrag(mouseX, mouseY);
                session.isDraggingLine = false;
                session.currentDragAction = 0;
                session.dragStartSlot = null;
                session.dragCurrentSlot = null;
                session.isDragModeErasing = false;
            }
        }
        return true;
    }

    public boolean onMouseDragged(double mouseX, double mouseY, double deltaX, double deltaY) {
        if(CommunityNamePrompt.active())return false;
        {
            if (session.currentState == EditorState.HIDDEN) return true;

            if (editor.screenColorPicker.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY)) {
                return false;
            }

            if (session.currentState == EditorState.EDIT_FILTER) {
                layout.update(screen, accessor, editor.getSidebarYOffset());

                int mainX = layout.mainX;

                // Reordering the filter list: just follow the cursor. The drop position and the edge
                // auto-scroll are resolved per frame while rendering, so they keep working even when the
                // mouse is held still against the top or bottom of the list.
                if (session.reorderDragItem != null) {
                    session.reorderMouseX = (int) mouseX;
                    session.reorderMouseY = (int) mouseY;
                    return false;
                }

                session.lastInteractedWasList = (mouseX < mainX);

                int gridViewH = layout.gridViewH;
                int msbY = layout.msbY;
                int msbH = layout.msbH;

                int listViewH = layout.listViewH;
                int lsbY = layout.lsbY;
                int lsbH = layout.lsbH;

                if (session.isDraggingMainScroll) {
                    int totalGridHeight = (int) Math.ceil(session.filteredItems.size() / 9.0) * 18;
                    float maxGridScroll = Math.max(0, totalGridHeight - gridViewH);
                    int thumbH = maxGridScroll > 0
                            ? Math.max(10, (int) ((gridViewH / (float) Math.max(1, totalGridHeight)) * msbH))
                            : msbH;
                    float percent = (float) (mouseY - msbY - (thumbH / 2.0f)) / (msbH - thumbH);
                    session.gridScrollY = Mth.clamp(percent * maxGridScroll, 0, maxGridScroll);
                    return false;
                }
                if (session.isDraggingListScroll) {
                    List<String> displayedAllowedItems = new ArrayList<>();
                    String wlSearch = editor.whitelistSearchBox != null
                            ? editor.whitelistSearchBox.getValue().toLowerCase()
                            : "";
                    for (String id : session.currentAllowedItems) {
                        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
                        if (item != null
                                && (wlSearch.isEmpty()
                                        || item.getDescription()
                                                .getString()
                                                .toLowerCase()
                                                .contains(wlSearch))) displayedAllowedItems.add(id);
                    }

                    int totalListHeight = displayedAllowedItems.size() * 18
                            + io.github.marcsanzdev.chestseparators.client.ui.EditorLayout.LIST_TRAILING_PADDING;
                    float maxListScroll = layout.maxListScroll(displayedAllowedItems.size());
                    int thumbH = maxListScroll > 0
                            ? Math.max(10, (int) ((listViewH / (float) Math.max(1, totalListHeight)) * lsbH))
                            : lsbH;
                    float percent = (float) (mouseY - lsbY - (thumbH / 2.0f)) / (lsbH - thumbH);
                    session.listScrollY = Mth.clamp(percent * maxListScroll, 0, maxListScroll);
                    return false;
                }
                return false;
            }

            if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
                if (editor.screenViewGroups.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY)) {
                    return false;
                }
            }

            if (session.currentState == EditorState.DRAW_LINES) {
                editor.screenDrawLines.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY);
                return false;
            }
            return true;
        }
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if(CommunityNamePrompt.active())return false;
        // The presets menu (when open, over a Load preview) grabs the wheel to scroll its
        // detail list, before the group view below it can react.
        if (session.isPresetsMenuOpen && editor.presetsMenu.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return false;
        }
        if (session.currentState == EditorState.VIEW_GROUPS
                || session.currentState == EditorState.SELECT_SLOTS) {
            if (editor.screenViewGroups.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount))
                return false;
        }
        if (session.currentState == EditorState.EDIT_FILTER) {
            if (editor.screenEditFilter.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount))
                return false;
        }

        return true;
    }

    public boolean keyPressed(KeyEvent input) {
        if(CommunityNamePrompt.active())return CommunityNamePrompt.key(input);
        // While a preset name is being edited inline, all keys belong to that field (typing, Enter, Esc) —
        // before any shortcut or the Esc-closes-menu handling below can steal them.
        if (session.isPresetsMenuOpen) {
            if (editor.presetsMenu.keyPressed(input)) return true;
        }

        boolean isControlDown = GLFW.glfwGetKey(
                                Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)
                        == GLFW.GLFW_PRESS;

        if (session.currentState == EditorState.EDIT_FILTER) {
            if (editor.screenEditFilter.keyPressed(input)) {
                return true;
            }
        }

        if (isControlDown && session.currentState != EditorState.HIDDEN) {
            boolean isTyping = (session.currentState == EditorState.EDIT_FILTER)
                    && ((editor.searchBox != null && editor.searchBox.isFocused())
                            || (editor.whitelistSearchBox != null && editor.whitelistSearchBox.isFocused()));

            if (!isTyping) {
                // Undo (unified: separators or filters, whichever happened last).
                if (input.key() == GLFW.GLFW_KEY_Z) {
                    editor.applyUndoRedo(ChestConfigManager.getInstance().undo(), false);
                    return true;
                }
                // Redo
                else if (input.key() == GLFW.GLFW_KEY_Y) {
                    editor.applyUndoRedo(ChestConfigManager.getInstance().redo(), true);
                    return true;
                }
                // Copy — whole-chest layout (lines + backgrounds) in DRAW_LINES, all filters in VIEW_GROUPS.
                else if (input.key() == GLFW.GLFW_KEY_C) {
                    if (session.currentState == EditorState.VIEW_GROUPS) {
                        ChestConfigManager.getInstance().copyWhitelistsToClipboard();
                        editor.showStatus(Component.translatable("message.chestseparators.filters_copied"), ChatFormatting.GRAY);
                        editor.playClickSound(1.0f);
                    } else if (session.currentState == EditorState.DRAW_LINES) {
                        ChestConfigManager.getInstance().copyToClipboard();
                        editor.showStatus(Component.translatable("message.chestseparators.layout_copied"), ChatFormatting.GRAY);
                        editor.playClickSound(1.0f);
                    }
                    return true;
                }
                // Paste
                else if (input.key() == GLFW.GLFW_KEY_V) {
                    if (session.currentState == EditorState.VIEW_GROUPS) {
                        if (ChestConfigManager.getInstance().hasWhitelistClipboardData()) {
                            ChestConfigManager.getInstance().saveWhitelistSnapshot();
                            ChestConfigManager.getInstance().pasteWhitelistsFromClipboard();
                            editor.saveSmart();
                            editor.sendWhitelistToServer();

                            if (Minecraft.getInstance().player != null
                                    && Minecraft.getInstance().player.containerMenu
                                            instanceof ChestMenu genericHandler) {
                                if (genericHandler.getContainer() instanceof IWhitelistProvider provider) {
                                    provider.setWhitelists(
                                            ChestConfigManager.getInstance().getCurrentWhitelists());
                                }
                            }
                            editor.showStatus(
                                    Component.translatable("message.chestseparators.filters_pasted"), ChatFormatting.GREEN);
                            editor.playClickSound(1.0f);
                        } else {
                            editor.playClickSound(0.5f);
                        }
                    } else if (session.currentState == EditorState.DRAW_LINES) {
                        // Paste the whole-chest layout (lines + backgrounds) regardless of the active tab.
                        if (ChestConfigManager.getInstance().hasClipboardData()) {
                            ChestConfigManager.getInstance().saveSnapshot();
                            ChestConfigManager.getInstance().pasteFromClipboard();
                            editor.saveSmart();
                            editor.showStatus(
                                    Component.translatable("message.chestseparators.layout_pasted"), ChatFormatting.GREEN);
                            editor.playClickSound(1.0f);
                        } else {
                            editor.playClickSound(0.5f);
                        }
                    }
                    return true;
                }
            }
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE && session.currentState != EditorState.HIDDEN) {
            if (session.isPresetsMenuOpen) {
                session.isPresetsMenuOpen = false;
                editor.playClickSound(0.8f);
                return true;
            }
            if (session.isColorPickerOpen) {
                session.isColorPickerOpen = false;
            } else {
                editor.toggleState(EditorState.HIDDEN);
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent input) {
        if(CommunityNamePrompt.active())return CommunityNamePrompt.character(input);
        if (session.isPresetsMenuOpen) {
            if (editor.presetsMenu.charTyped(input)) return true;
        }
        if (session.currentState == EditorState.EDIT_FILTER) {
            return editor.screenEditFilter.charTyped(input);
        }
        return false;
    }

    /**
     * The editor's click handling. Invoked from the container-screen mixin's mouseClicked inject
     * (returning false cancels the vanilla click). The creative-inventory mixin also calls it directly,
     * because that screen processes its own tab/search clicks BEFORE the generic mixin can cancel them —
     * so while editing there, the mixin routes the click here and swallows vanilla entirely.
     */
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if(CommunityNamePrompt.active()){CommunityNamePrompt.click(mouseX,mouseY,button);return false;}
        layout.update(screen, accessor, editor.getSidebarYOffset());

        // While the color picker is open, all clicks outside it must be suppressed.
        if (session.isColorPickerOpen) {
            editor.screenColorPicker.mouseClicked(mouseX, mouseY, button);
            return false;
        }

        boolean isFilterMenuOpen = (session.currentState == EditorState.EDIT_FILTER);
        boolean isEditorClosed = (session.currentState == EditorState.HIDDEN);

        // PUSH (deposit) and PULL (fill) act on the open container and stay clickable in the editor
        // screens — edit layout, groups, presets. Hidden buttons are parked off-screen, so a disabled
        // one is never hit here. They are NOT clickable while the filter item-picker is open: that panel
        // covers the container, so moving items underneath it would be an invisible, unintended action.
        if (!session.isPlayerInventory && !isFilterMenuOpen) {
            if (editor.depositButton != null && editor.depositButton.mouseClicked(mouseX, mouseY, button)) {
                return false;
            }
            if (io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.PLAYER_INVENTORY_ENABLED && editor.fillButton != null && editor.fillButton.mouseClicked(mouseX, mouseY, button)) {
                return false;
            }
        }

        // The layout/filters/presets icons stay clickable except while the filter menu is open. Clicking
        // layout or filters also closes the presets menu.
        if (!isFilterMenuOpen && GlobalChestConfig.isShowEditButton()) {
            if (editor.entryButton != null && editor.entryButton.mouseClicked(mouseX, mouseY, button)) {
                editor.presetsMenu.cancelRename();
                session.isPresetsMenuOpen = false;
                editor.ensureCreativeInventoryTab();
                return false;
            }
            if (editor.whitelistButton != null && editor.whitelistButton.mouseClicked(mouseX, mouseY, button)) {
                editor.presetsMenu.cancelRename();
                session.isPresetsMenuOpen = false;
                editor.ensureCreativeInventoryTab();
                return false;
            }
            if (io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.PLAYER_INVENTORY_ENABLED && editor.presetsButton != null && editor.presetsButton.mouseClicked(mouseX, mouseY, button)) {
                editor.ensureCreativeInventoryTab();
                return false;
            }
            if (editor.chestPresetsButton != null
                    && !session.isPlayerInventory
                    && editor.chestPresetsButton.mouseClicked(mouseX, mouseY, button)) {
                return false;
            }
        }

        // Otherwise the presets menu swallows clicks while open (its panel rows and close button).
        if (session.isPresetsMenuOpen) {
            editor.presetsMenu.onClick(mouseX, mouseY, button, screen.width, screen.height);
            return false;
        }

        if (!GlobalChestConfig.isShowEditButton() || isEditorClosed) {
            return true;
        }

        if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
            editor.screenViewGroups.mouseClicked(mouseX, mouseY, button);
            return false;
        }

        if (session.currentState == EditorState.EDIT_FILTER) {
            editor.screenEditFilter.mouseClicked(mouseX, mouseY, button);
            return false;
        }

        editor.screenDrawLines.mouseClicked(mouseX, mouseY, button);
        return false;
    }
}
