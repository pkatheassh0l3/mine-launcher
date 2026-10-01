package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;

/**
 * Handles mouse-click routing for the filter editor: popups, the action buttons, the search/list
 * panels, tag tabs, and the item grid. Extracted verbatim from ScreenEditFilter.
 */
final class FilterClickHandler {

    private final ScreenEditFilter screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;

    FilterClickHandler(ScreenEditFilter screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    boolean onMouseClicked(double mouseX, double mouseY, int button) {
        // Forward clicks to the active popup and block all background interaction.
        if (session.isUnsavedPopupOpen) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget :
                    screen.unsavedPopupWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true;
        }

        if (session.isExpelPopupOpen) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget :
                    screen.expelPopupWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true;
        }

        // Track which panel received the last click to route keyboard scroll correctly.
        if (mouseX >= layout.mainX
                && mouseX <= layout.mainX + layout.mainW
                && mouseY >= layout.mainY
                && mouseY <= layout.mainY + layout.mainH) {
            session.lastInteractedWasList = false;
        } else if (mouseX >= layout.listX
                && mouseX <= layout.listX + layout.listW
                && mouseY >= layout.listY
                && mouseY <= layout.listY + layout.listH) {
            session.lastInteractedWasList = true;
        }

        // 1. Tag Dropdown logic
        if (!session.activeDropdownTags.isEmpty()) {
            if (button == 0) {
                int dropW = 120;
                int dropH = session.activeDropdownTags.size() * 14 + 4;
                if (mouseX >= session.dropdownX
                        && mouseX <= session.dropdownX + dropW
                        && mouseY >= session.dropdownY
                        && mouseY <= session.dropdownY + dropH) {
                    int clickedIndex = (int) (mouseY - session.dropdownY - 2) / 14;
                    if (clickedIndex >= 0 && clickedIndex < session.activeDropdownTags.size()) {
                        if (editor.searchBox != null) {
                            editor.searchBox.setValue("#" + session.activeDropdownTags.get(clickedIndex));
                            editor.playClickSound(1.2f);
                        }
                    }
                }
                session.activeDropdownTags.clear();
                return true;
            } else {
                session.activeDropdownTags.clear();
                return true;
            }
        }

        if (button == 0 && screen.clickWidgets(mouseX, mouseY, button)) return true;
        if (button != 0 && button != 2) return false;

        // Remove from List (The 'X')
        if (button == 0
                && mouseX >= layout.listX + 6
                && mouseX <= layout.listX + layout.listW - 14
                && mouseY >= layout.listViewY
                && mouseY <= layout.listViewY + layout.listViewH) {
            int clickedIdx = (int) (mouseY - layout.listViewY + session.listScrollY) / 18;

            // We use the cache directly
            List<String> visibleAllowed = session.visibleLeftListItems;

            if (clickedIdx >= 0 && clickedIdx < visibleAllowed.size()) {
                int itemY = layout.listViewY + (clickedIdx * 18) - (int) session.listScrollY;
                if (editor.isHovering(layout.listX + layout.listW - 24, itemY + 2, 12, 12, mouseX, mouseY)) {
                    session.currentAllowedItems.remove(visibleAllowed.get(clickedIdx));
                    editor.updateWhitelistSearchCache(); // Update cache after removing
                    editor.playClickSound(1.2f);
                    return true;
                }

                // Anywhere else on the row picks it up to reorder the filter's list. Not while a preview
                // is running: the list is then showing the preview's contents, not the filter's own.
                if (button == 0 && !session.isPreviewing) {
                    session.reorderDragItem = visibleAllowed.get(clickedIdx);
                    session.reorderFromGrid = false;
                    session.reorderMouseX = (int) mouseX;
                    session.reorderMouseY = (int) mouseY;
                    // The held row flies out of the row it was picked from.
                    session.reorderOriginX = layout.listX + 6;
                    session.reorderOriginY = itemY - 2;
                    session.reorderStartTime = System.currentTimeMillis();
                    return true;
                }
            }
        }

        // Scrollbars
        if (button == 0) {
            if (editor.isHovering(layout.msbX, layout.msbY, 6, layout.msbH, mouseX, mouseY)) {
                session.isDraggingMainScroll = true;
                return true;
            }
            if (editor.isHovering(layout.lsbX, layout.lsbY, 6, layout.lsbH, mouseX, mouseY)) {
                session.isDraggingListScroll = true;
                return true;
            }
        }

        // Search Boxes Focus
        EditorSessionData.CreativeTabInfo currentTabInfo = session.availableTabs.get(session.currentCreativeTabIndex);
        if (button == 0
                && editor.whitelistSearchBox != null
                && editor.isHovering(layout.listX + 6, layout.listY + 20, layout.listW - 12, 16, mouseX, mouseY)) {
            editor.whitelistSearchBox.setFocused(true);
            if (editor.searchBox != null) editor.searchBox.setFocused(false);
            editor.playClickSound(1.0f);
            return true;
        } else if (button == 0
                && editor.searchBox != null
                && currentTabInfo != null
                && currentTabInfo.isSearchTab
                && editor.searchBox.isMouseOver(mouseX, mouseY)) {
            editor.searchBox.setFocused(true);
            if (editor.whitelistSearchBox != null) editor.whitelistSearchBox.setFocused(false);
            editor.playClickSound(1.0f);
            return true;
        } else if (button == 0) {
            if (editor.searchBox != null) editor.searchBox.setFocused(false);
            if (editor.whitelistSearchBox != null) editor.whitelistSearchBox.setFocused(false);
        }

        // Tabs and Pagination
        if (button == 0) {
            EditorSessionData.CreativeTabInfo searchTab = null;
            int searchTabIndexGlobal = -1;
            List<Integer> normalIndices = new ArrayList<>();

            for (int i = 0; i < session.availableTabs.size(); i++) {
                EditorSessionData.CreativeTabInfo tab = session.availableTabs.get(i);
                if (tab == null) continue;
                if (tab.isSearchTab) {
                    searchTab = tab;
                    searchTabIndexGlobal = i;
                } else {
                    normalIndices.add(i);
                }
            }

            int totalNormalTabs = normalIndices.size();
            int remainingTabs = Math.max(0, totalNormalTabs - 10);
            int normalTabsPerPage = 15;

            int maxPages = 1;
            if (remainingTabs > 0) {
                maxPages = 1 + (int) Math.ceil((double) remainingTabs / normalTabsPerPage);
            }

            for (int slot = 0; slot < 16; slot++) {
                int actualGlobalIndex = -1;

                if (slot == 7 && searchTab != null) {
                    actualGlobalIndex = searchTabIndexGlobal;
                } else {
                    int normalListIndex =
                            FilterPanelRenderer.normalListIndexForSlot(slot, session.currentTabPage, normalTabsPerPage);

                    if (normalListIndex >= 0 && normalListIndex < normalIndices.size()) {
                        actualGlobalIndex = normalIndices.get(normalListIndex);
                    }
                }

                if (actualGlobalIndex == -1) continue;

                int tabX = layout.tabX(slot % 8);
                int tabY = (slot < 8) ? layout.mainY - 20 : layout.mainY + layout.mainH - 2;

                if (editor.isHovering(tabX, tabY, layout.tabW, layout.tabW, mouseX, mouseY)) {
                    editor.switchCreativeTab(actualGlobalIndex);
                    editor.playClickSound(1.0f);
                    return true;
                }
            }

            // Click en el Carrusel
            EditorSessionData.CreativeTabInfo activeTabInfo =
                    session.availableTabs.get(session.currentCreativeTabIndex);
            if (activeTabInfo == null || !activeTabInfo.isSearchTab) {
                if (maxPages > 1) {
                    int visibleDots = Math.min(maxPages, 3);
                    int startPage = Math.max(0, Math.min(session.currentTabPage - 1, maxPages - visibleDots));

                    int dotSpacing = 8;
                    int arrowWidth = 12;
                    int totalWidth = (arrowWidth * 2) + (visibleDots * dotSpacing);
                    int startX = (layout.mainX + layout.mainW - 12) - totalWidth;
                    int currentX = startX;
                    int y = layout.mainY + 10;

                    if (editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY)) {
                        session.currentTabPage = (session.currentTabPage - 1 + maxPages) % maxPages;
                        int newIndex = (session.currentTabPage == 0) ? 0 : 10 + ((session.currentTabPage - 1) * 15);
                        if (newIndex < normalIndices.size()) editor.switchCreativeTab(normalIndices.get(newIndex));
                        editor.playClickSound(0.8f);
                        return true;
                    }
                    currentX += arrowWidth;

                    for (int i = 0; i < visibleDots; i++) {
                        if (editor.isHovering(currentX, y - 2, dotSpacing, 12, mouseX, mouseY)) {
                            session.currentTabPage = startPage + i;
                            int newIndex = (session.currentTabPage == 0) ? 0 : 10 + ((session.currentTabPage - 1) * 15);
                            if (newIndex < normalIndices.size()) editor.switchCreativeTab(normalIndices.get(newIndex));
                            editor.playClickSound(1.0f);
                            return true;
                        }
                        currentX += dotSpacing;
                    }

                    if (editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY)) {
                        session.currentTabPage = (session.currentTabPage + 1) % maxPages;
                        int newIndex = (session.currentTabPage == 0) ? 0 : 10 + ((session.currentTabPage - 1) * 15);
                        if (newIndex < normalIndices.size()) editor.switchCreativeTab(normalIndices.get(newIndex));
                        editor.playClickSound(0.8f);
                        return true;
                    }
                }
            }
        }

        // Grid Click
        if (mouseX >= layout.gridX
                && mouseX < layout.gridX + (layout.cols * layout.gridCell)
                && mouseY >= layout.gridViewY
                && mouseY < layout.gridViewY + layout.gridViewH) {
            int c = (int) (mouseX - layout.gridX) / layout.gridCell;
            int r = (int) (mouseY - layout.gridViewY + session.gridScrollY) / layout.gridCell;
            int itemIndex = (r * layout.cols) + c;
            if (itemIndex >= 0 && itemIndex < session.filteredItems.size()) {
                Item item = session.filteredItems.get(itemIndex);
                if (button == 2) {
                    List<net.minecraft.tags.TagKey<Item>> candidates = new ArrayList<>();
                    item.builtInRegistryHolder().tags().forEach(tagKey -> {
                        String t = tagKey.location().getPath();
                        if (!t.startsWith("mineable/")
                                && !t.startsWith("needs_")
                                && !t.contains("tools")
                                && !t.startsWith("beacon_")
                                && !t.equals("completes_find_tree_tutorial")
                                && hasMoreThanOneItem(tagKey)) candidates.add(tagKey);
                    });
                    List<String> tags = dropEquivalentTags(candidates);
                    if (!tags.isEmpty()) {
                        if (editor.searchBox != null) editor.searchBox.setValue("");
                        for (int i = 0; i < session.availableTabs.size(); i++) {
                            if (session.availableTabs.get(i) != null && session.availableTabs.get(i).isSearchTab) {
                                editor.switchCreativeTab(i);
                                int newIndex = session.filteredItems.indexOf(item);
                                if (newIndex != -1) {
                                    // Centre the clicked item's row in the viewport. This has to use the
                                    // grid's real metrics: the column count and cell size are laid out by
                                    // EditorLayout and no longer match the 9x18 the old maths assumed, so
                                    // the view landed at the wrong height.
                                    int cols = Math.max(1, layout.cols);
                                    int cell = layout.gridCell;
                                    float centred = (newIndex / cols) * (float) cell - (layout.gridViewH - cell) / 2f;
                                    float maxScroll = Math.max(
                                            0,
                                            (int) Math.ceil(session.filteredItems.size() / (double) cols) * cell
                                                    - layout.gridViewH);
                                    session.gridScrollY = Mth.clamp(centred, 0, maxScroll);
                                }
                                break;
                            }
                        }
                        session.activeDropdownTags = tags;
                        session.dropdownX = layout.mainX + layout.mainW - 132;
                        session.dropdownY = layout.mainY + 24;
                        editor.playClickSound(1.0f);
                    } else
                        editor.showStatus(
                                Component.translatable("message.chestseparators.no_valid_groups"), ChatFormatting.GRAY);
                } else if (button == 0) {
                    // Only remember the press for now. A quick click still just toggles the item (see
                    // ScreenEditFilter#commitPointerRelease); it only becomes a drag once the button is
                    // held or the cursor moves, so a simple click never flashes a row on the cursor.
                    session.pendingDragItem = BuiltInRegistries.ITEM.getKey(item).toString();
                    session.pendingDragX = (int) mouseX;
                    session.pendingDragY = (int) mouseY;
                    session.pendingDragTime = System.currentTimeMillis();
                    session.reorderOriginX = layout.gridX + (c * layout.gridCell);
                    session.reorderOriginY = layout.gridViewY + (r * layout.gridCell) - (int) session.gridScrollY;
                }
                return true;
            }
        }

        // --- CLICK OUTSIDE TO CLOSE ---
        if (button == 0 && GlobalChestConfig.instance.closeOnClickOutside) {
            boolean isInsideMain = mouseX >= layout.mainX
                    && mouseX <= layout.mainX + layout.mainW
                    && mouseY >= layout.mainY
                    && mouseY <= layout.mainY + layout.mainH;
            boolean isInsideLeft = mouseX >= layout.listX
                    && mouseX <= layout.listX + layout.listW
                    && mouseY >= layout.listY
                    && mouseY <= layout.listY + layout.listH;
            boolean isInsideRight = mouseX >= layout.rightX
                    && mouseX <= layout.rightX + layout.btnW
                    && mouseY >= layout.mainY
                    && mouseY <= layout.mainY + (layout.bH * 8);

            if (!isInsideMain && !isInsideLeft && !isInsideRight && session.activeDropdownTags.isEmpty()) {
                if (screen.hasUnsavedChanges()) {
                    session.isUnsavedPopupOpen = true;
                } else {
                    session.selectedSlots.clear();
                    editor.toggleState(EditorState.VIEW_GROUPS);
                }
                editor.playCloseSound();
                return true;
            }
        }

        return false;
    }

    /**
     * True when a tag covers more than one item. Single-item tags (say {@code #ore_bearing_ground/deepslate})
     * are useless as a filter shortcut: picking one gives exactly the item you already middle-clicked, so
     * they only pad the list.
     */
    private static boolean hasMoreThanOneItem(net.minecraft.tags.TagKey<Item> tagKey) {
        int found = 0;
        for (var entry : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
            if (++found > 1) return true; // no need to walk the whole tag
        }
        return false;
    }

    /**
     * Collapses tags that select exactly the same items down to one entry. Vanilla ships several such
     * pairs — {@code #flowers/small} and {@code #small_flowers} resolve to an identical list — and showing
     * both only asks the player to choose between two filters that behave the same.
     *
     * <p>The flat name wins over the {@code group/variant} form: it is what the search box will display,
     * and it reads as a name rather than a path. Ties fall back to the shorter, then alphabetical, name so
     * the choice is stable between openings.
     */
    private static List<String> dropEquivalentTags(List<net.minecraft.tags.TagKey<Item>> candidates) {
        java.util.Map<List<String>, String> byContents = new java.util.LinkedHashMap<>();
        for (net.minecraft.tags.TagKey<Item> tagKey : candidates) {
            List<String> contents = new ArrayList<>();
            for (var entry : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                contents.add(BuiltInRegistries.ITEM.getKey(entry.value()).toString());
            }
            java.util.Collections.sort(contents);

            String path = tagKey.location().getPath();
            String kept = byContents.get(contents);
            if (kept == null || isBetterTagName(path, kept)) byContents.put(contents, path);
        }
        return new ArrayList<>(byContents.values());
    }

    private static boolean isBetterTagName(String candidate, String current) {
        boolean candidateFlat = !candidate.contains("/");
        boolean currentFlat = !current.contains("/");
        if (candidateFlat != currentFlat) return candidateFlat;
        if (candidate.length() != current.length()) return candidate.length() < current.length();
        return candidate.compareTo(current) < 0;
    }
}
