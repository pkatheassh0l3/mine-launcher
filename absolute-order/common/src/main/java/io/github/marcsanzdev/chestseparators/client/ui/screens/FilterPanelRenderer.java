package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renders the three panels of the filter editor: the left item list, the main whitelist panel,
 * and the tag dropdown. Pure rendering, extracted verbatim from ScreenEditFilter.
 */
final class FilterPanelRenderer {

    private final ScreenEditFilter screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;

    FilterPanelRenderer(ScreenEditFilter screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    void drawLeftPanel(GuiGraphics context, int mouseX, int mouseY) {
        int listW = layout.listW;
        int listH = layout.listH;
        int listX = layout.listX;
        int listY = layout.listY;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(context, listX, listY, listW, listH);

        List<String> activeListToRender;
        float activeScrollY;

        if (session.isPreviewing) {
            activeListToRender = session.previewItems;
            activeScrollY = session.previewScrollY;
        } else {
            activeListToRender = session.visibleLeftListItems;
            activeScrollY = session.listScrollY;
        }

        int allowedCount = session.currentAllowedItems.size();
        int blockedCount = session.allGameItems.size() - allowedCount;

        context.pose().pushPose();
        float scaleC = 1.0f;
        context.pose().scale(scaleC, scaleC, 1.0F);

        if (session.isPreviewing) {
            if (session.previewType == 1)
                context.drawString(
                        Minecraft.getInstance().font,
                        Component.translatable("gui.chestseparators.new_detected", activeListToRender.size()),
                        (int) ((listX + 8) / scaleC),
                        (int) ((listY + 9) / scaleC),
                        0xFF55FF55,
                        false);
            else if (session.previewType == 2)
                context.drawString(
                        Minecraft.getInstance().font,
                        Component.translatable("gui.chestseparators.allowing_all", activeListToRender.size()),
                        (int) ((listX + 8) / scaleC),
                        (int) ((listY + 9) / scaleC),
                        0xFF55FF55,
                        false);
            else if (session.previewType == 3)
                context.drawString(
                        Minecraft.getInstance().font,
                        Component.translatable("gui.chestseparators.clearing", activeListToRender.size()),
                        (int) ((listX + 8) / scaleC),
                        (int) ((listY + 9) / scaleC),
                        0xFFFF5555,
                        false);
        } else {
            context.drawString(
                    Minecraft.getInstance().font,
                    Component.translatable("gui.chestseparators.allowed", allowedCount),
                    (int) ((listX + 8) / scaleC),
                    (int) ((listY + 3) / scaleC),
                    0xFF55FF55,
                    false);
            context.drawString(
                    Minecraft.getInstance().font,
                    Component.translatable("gui.chestseparators.blocked", blockedCount),
                    (int) ((listX + 8) / scaleC),
                    (int) ((listY + 13) / scaleC),
                    0xFFFF5555,
                    false);
        }
        context.pose().popPose();

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.inset(context, listX + 6, listY + 22, listW - 12, 16);

        if (editor.whitelistSearchBox != null) {
            editor.whitelistSearchBox.setX(listX + 10);
            editor.whitelistSearchBox.setY(listY + 26);
            if (!session.isPreviewing)
                editor.whitelistSearchBox.render(
                        context,
                        mouseX,
                        mouseY,
                        Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
            if (editor.whitelistSearchBox.getValue().isEmpty()
                    && !editor.whitelistSearchBox.isFocused()
                    && !session.isPreviewing) {
                context.drawString(
                        Minecraft.getInstance().font,
                        Component.translatable("gui.chestseparators.search"),
                        listX + 12,
                        listY + 26,
                        0xFF666666,
                        false);
            }
        }

        int listViewY = layout.listViewY;
        int listViewH = layout.listViewH;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.inset(
                context, listX + 6, listViewY, listW - 12, listViewH);

        int totalListItems = activeListToRender.size();
        // Trailing padding so scrolling to the bottom fully reveals the last row. Shared formula via
        // layout.maxListScroll keeps the renderer and the scroll inputs in sync.
        int totalListHeight = totalListItems * 18 + EditorLayout.LIST_TRAILING_PADDING;
        float maxListScroll = layout.maxListScroll(totalListItems);

        if (!session.isPreviewing) session.listScrollY = Mth.clamp(session.listScrollY, 0, maxListScroll);

        int lsbX = layout.lsbX;
        int lsbY = layout.lsbY;
        int lsbH = layout.lsbH;
        int listThumbH = maxListScroll > 0
                ? Math.max(10, (int) ((listViewH / (float) Math.max(1, totalListHeight)) * lsbH))
                : lsbH;
        int listThumbY =
                maxListScroll > 0 ? lsbY + (int) ((activeScrollY / maxListScroll) * (lsbH - listThumbH)) : lsbY;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.scrollbar(
                context, lsbX, lsbY, 6, lsbH, listThumbY, listThumbH);

        context.enableScissor(listX + 6, listViewY + 1, listX + listW - 14, listViewY + listViewH - 1);
        int startIndex = (int) (activeScrollY / 18);
        int visibleCount = (listViewH / 18) + 2;

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;

            String itemIdStr = activeListToRender.get(idx);

            // The row being carried leaves an empty gap behind it: it is on the cursor, not in the list,
            // and the space stays open until the drop rebuilds the order. Drawing it here too would show
            // the same entry twice.
            if (!session.reorderFromGrid && itemIdStr.equals(session.reorderDragItem)) continue;

            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemIdStr));
            int itemY = listViewY + (i * 18) - (int) (activeScrollY % 18) + 2;

            // Settle animation: right after a drop, every row starts from where it used to be and slides
            // to its new place, so the list opens a gap for the dropped item instead of snapping.
            long sinceDrop = System.currentTimeMillis() - session.dropAnimStart;
            if (sinceDrop < ScreenEditFilter.DROP_MS && !session.dropAnimFrom.isEmpty()) {
                Integer previousIndex = session.dropAnimFrom.get(itemIdStr);
                if (previousIndex != null) {
                    float k = 1f - (float) sinceDrop / ScreenEditFilter.DROP_MS;
                    k = k * k; // ease-out: most of the travel happens immediately
                    itemY += Math.round((previousIndex - idx) * 18 * k);
                }
            }

            context.renderItem(item.getDefaultInstance(), listX + 8, itemY);

            String name = item.getDescription().getString();
            context.pose().pushPose();
            float scale = 1.0f;
            context.pose().scale(scale, scale, 1.0F);
            int maxNameW = (int) ((listW - 55) / scale);
            if (Minecraft.getInstance().font.width(name) > maxNameW)
                name = Minecraft.getInstance().font.plainSubstrByWidth(name, maxNameW - 6) + "...";
            int textColor = session.isPreviewing
                    ? ((session.previewType == 3) ? 0x66FFFFFF : 0xAAFFFFFF)
                    : (isDark ? 0xFFFFFFFF : 0xFF222222);
            context.drawString(
                    Minecraft.getInstance().font,
                    name,
                    (int) ((listX + 26) / scale),
                    (int) ((itemY + 6) / scale),
                    textColor,
                    false);
            context.pose().popPose();

            if (!session.isPreviewing || session.previewType == 3) {
                int crossX = listX + listW - 22;
                int crossY = itemY + 4;
                boolean hoverCross =
                        !session.isPreviewing && editor.isHovering(crossX - 2, crossY - 2, 10, 10, mouseX, mouseY);
                context.drawString(
                        Minecraft.getInstance().font,
                        "x",
                        crossX,
                        crossY,
                        session.isPreviewing
                                ? 0xFFFF5555
                                : (hoverCross ? 0xFFFF5555 : (isDark ? 0xFFAAAAAA : 0xFF777777)));
            }
        }
        context.disableScissor();

        // Insertion caret: where the held item will land if released now. Drawn OUTSIDE the scissor and
        // kept one pixel inside the viewport: the clip region starts just below the top edge, so a caret
        // for the very first position fell entirely outside it and never showed up.
        // Only while the cursor is actually over the list: outside it a drop changes nothing, so promising
        // a landing spot there would be a lie.
        if (session.reorderDragItem != null && screen.isOverList(session.reorderMouseX, session.reorderMouseY)) {
            int row = screen.reorderTargetRow(session.reorderMouseY);
            int caretY = listViewY + (row * 18) - (int) session.listScrollY;
            caretY = Mth.clamp(caretY, listViewY + 1, listViewY + listViewH - 1);
            context.fill(listX + 6, caretY - 1, listX + listW - 14, caretY + 1, io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ACCENT);
        }
    }

    /** Maps a 16-slot tab-grid position to an index into the normal-tabs list for {@code currentTabPage},
     *  or -1 if that slot holds no normal tab. Shared verbatim by the panel renderer and click handler. */
    static int normalListIndexForSlot(int slot, int currentTabPage, int normalTabsPerPage) {
        if (currentTabPage == 0) {
            if (slot >= 0 && slot <= 4) return slot;
            if (slot >= 8 && slot <= 12) return 5 + (slot - 8);
            return -1;
        }
        int offsetOnPage = (slot > 7) ? slot - 1 : slot;
        return 10 + ((currentTabPage - 1) * normalTabsPerPage) + offsetOnPage;
    }

    void drawMainPanel(GuiGraphics context, int mouseX, int mouseY) {
        int mainX = layout.mainX;
        int mainY = layout.mainY;
        int mainW = layout.mainW;
        int mainH = layout.mainH;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        // --- 1. TABS LOGIC SEPARATION ---
        EditorSessionData.CreativeTabInfo searchTab = null;
        int searchTabIndexGlobal = -1;
        List<EditorSessionData.CreativeTabInfo> normalTabs = new ArrayList<>();
        List<Integer> normalIndices = new ArrayList<>();

        for (int i = 0; i < session.availableTabs.size(); i++) {
            EditorSessionData.CreativeTabInfo tab = session.availableTabs.get(i);
            if (tab == null) continue;
            if (tab.isSearchTab) {
                searchTab = tab;
                searchTabIndexGlobal = i;
            } else {
                normalTabs.add(tab);
                normalIndices.add(i);
            }
        }

        // Pagination calculations
        int totalNormalTabs = normalTabs.size();
        int remainingTabs = Math.max(0, totalNormalTabs - 10);
        int normalTabsPerPage = 15;

        int maxPages = 1;
        if (remainingTabs > 0) {
            maxPages = 1 + (int) Math.ceil((double) remainingTabs / normalTabsPerPage);
        }

        if (session.currentTabPage >= maxPages) session.currentTabPage = maxPages - 1;

        // --- MAIN PANEL FIRST so the opaque tabs drawn next sit ON TOP and don't bleed through it ---
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(context, mainX, mainY, mainW, mainH);

        // --- 2. DRAW TABS ON TOP OF THE PANEL ---
        for (int slot = 0; slot < 16; slot++) {
            EditorSessionData.CreativeTabInfo info = null;
            int actualGlobalIndex = -1;

            if (slot == 7 && searchTab != null) {
                info = searchTab;
                actualGlobalIndex = searchTabIndexGlobal;
            } else {
                int normalListIndex = normalListIndexForSlot(slot, session.currentTabPage, normalTabsPerPage);

                if (normalListIndex >= 0 && normalListIndex < normalTabs.size()) {
                    info = normalTabs.get(normalListIndex);
                    actualGlobalIndex = normalIndices.get(normalListIndex);
                }
            }

            if (info == null) continue;

            boolean isTop = slot < 8;
            int col = slot % 8;
            int tabW = layout.tabW;
            int tabH = layout.tabW;
            int tabX = layout.tabX(col);
            int tabY = isTop ? mainY - tabH + 2 : mainY + mainH - 2;

            boolean isSelected = (session.currentCreativeTabIndex == actualGlobalIndex);
            boolean hover = editor.isHovering(tabX, tabY, tabW, tabH, mouseX, mouseY);

            // The tab sits flush against the panel and is embedded into it along the touching edge: top
            // tabs attach on their BOTTOM, bottom tabs on their TOP. That edge has no border/gap, and a
            // selected tab shrinks anchored to it so it never separates from the panel.
            int drawY = isTop ? tabY : tabY + 2;
            int drawH = tabH - 2;
            int attach = isTop
                    ? io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ATTACH_BOTTOM
                    : io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ATTACH_TOP;
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.tab(
                    context, tabX, drawY, tabW, drawH, hover, isSelected, attach);
            // A selected tab draws 1px smaller; shrink its item icon by the same proportion.
            if (isSelected) {
                io.github.marcsanzdev.chestseparators.client.ui.UiTheme.pushActiveContent(
                        context, tabX, drawY, tabW, drawH);
            }
            context.renderItem(info.icon, tabX + 3, tabY + (isTop ? 2 : 4));
            if (isSelected) {
                context.pose().popPose();
            }

            if (hover) context.renderTooltip(Minecraft.getInstance().font, info.name, mouseX, mouseY);
        }

        // --- 5. TITLE & CAROUSEL OR SEARCH BOX ---
        EditorSessionData.CreativeTabInfo activeTabInfo = session.availableTabs.get(session.currentCreativeTabIndex);
        String tabTitle = activeTabInfo != null
                ? activeTabInfo.name.getString()
                : Component.translatable("gui.chestseparators.edit_filter_rules").getString();

        if (activeTabInfo != null && activeTabInfo.isSearchTab) {
            int titleWidth = Minecraft.getInstance().font.width(tabTitle);
            int maxTitleWidth = 60;
            float scale = 1.0f;

            if (titleWidth > maxTitleWidth) {
                tabTitle = Minecraft.getInstance().font.plainSubstrByWidth(tabTitle, maxTitleWidth - 6) + "…"; titleWidth = Minecraft.getInstance().font.width(tabTitle);
            }

            context.pose().pushPose();
            context.pose().translate((float) (mainX + 12), (float) (mainY + 10 + (4 * (1 - scale))), 0.0F);
            context.pose().scale(scale, scale, 1.0F);
            context.drawString(
                    Minecraft.getInstance().font,
                    tabTitle,
                    0,
                    0,
                    (isDark ? 0xFF909090 : 0xFF555555),
                    false);
            context.pose().popPose();

            int boxX = mainX + 12 + (int) (titleWidth * scale) + 12;
            int boxY = mainY + 6;
            int boxW = (mainX + mainW - 12) - boxX;

            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.inset(context, boxX, boxY, boxW, 16);
            editor.searchBox.setX(boxX + 4);
            editor.searchBox.setY(boxY + 4);
            editor.searchBox.setWidth(boxW - 8);
            editor.searchBox.render(
                    context,
                    mouseX,
                    mouseY,
                    Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));

            if (editor.searchBox.getValue().isEmpty() && !editor.searchBox.isFocused()) {
                context.drawString(
                        Minecraft.getInstance().font,
                        Component.translatable("gui.chestseparators.search"),
                        boxX + 6,
                        boxY + 4,
                        0xFF666666,
                        false);
            }
        } else {
            int visibleDots = Math.min(maxPages, 3);
            int carouselWidth = (maxPages > 1) ? ((12 * 2) + (visibleDots * 8)) : 0;
            int maxTextWidth = mainW - 24 - carouselWidth - 5;

            int textWidth = Minecraft.getInstance().font.width(tabTitle);

            if (textWidth > maxTextWidth) {
                float scale = 1.0f; tabTitle = Minecraft.getInstance().font.plainSubstrByWidth(tabTitle, maxTextWidth - 6) + "…";
                context.pose().pushPose();

                context.pose().translate((float) (mainX + 12), (float) (mainY + 10 + (4 * (1 - scale))), 0.0F);
                context.pose().scale(scale, scale, 1.0F);

                context.drawString(
                        Minecraft.getInstance().font,
                        tabTitle,
                        0,
                        0,
                        (isDark ? 0xFF909090 : 0xFF555555),
                        false);
                context.pose().popPose();
            } else {
                context.drawString(
                        Minecraft.getInstance().font,
                        tabTitle,
                        mainX + 12,
                        mainY + 10,
                        (isDark ? 0xFF909090 : 0xFF555555),
                        false);
            }

            drawPaginationCarousel(
                    context, mainX + mainW - 12, mainY + 10, maxPages, session.currentTabPage, mouseX, mouseY);
        }

        // --- 6. DRAW ITEM GRID ---
        int gridX = layout.gridX;
        int gridViewY = layout.gridViewY;
        int gridViewH = layout.gridViewH;
        int cols = layout.cols;
        int itemSize = layout.gridCell;

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.inset(
                context, gridX - 2, gridViewY - 2, (cols * itemSize) + 4, gridViewH + 4);

        int totalGridItems = session.filteredItems.size();
        int totalGridHeight = (int) Math.ceil((double) totalGridItems / cols) * itemSize;
        float maxGridScroll = Math.max(0, totalGridHeight - gridViewH);
        session.gridScrollY = Mth.clamp(session.gridScrollY, 0, maxGridScroll);

        int msbX = layout.msbX;
        int msbY = layout.msbY;
        int msbH = layout.msbH;
        int gridThumbH = maxGridScroll > 0
                ? Math.max(10, (int) ((gridViewH / (float) Math.max(1, totalGridHeight)) * msbH))
                : msbH;
        int gridThumbY =
                maxGridScroll > 0 ? msbY + (int) ((session.gridScrollY / maxGridScroll) * (msbH - gridThumbH)) : msbY;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.scrollbar(
                context, msbX, msbY, 6, msbH, gridThumbY, gridThumbH);

        // The hovered item's tooltip is deferred until AFTER the grid scissor is popped: drawing it inside the
        // scissor region clips it at the grid's edge (the bug where the item name was cut off on the right).
        net.minecraft.network.chat.Component deferredHoverTooltip = null;

        context.enableScissor(gridX, gridViewY, gridX + (cols * itemSize), gridViewY + gridViewH);
        int startGridRow = (int) (session.gridScrollY / itemSize);
        int visibleGridRows = (gridViewH / itemSize) + 2;

        for (int r = 0; r < visibleGridRows; r++) {
            int absoluteRow = startGridRow + r;
            int drawY = gridViewY + (r * itemSize) - (int) (session.gridScrollY % itemSize);

            for (int c = 0; c < cols; c++) {
                int itemIndex = (absoluteRow * cols) + c;
                if (itemIndex >= totalGridItems) break;

                int drawX = gridX + (c * itemSize);
                Item item = session.filteredItems.get(itemIndex);
                String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
                boolean isAllowed = session.currentAllowedItems.contains(itemId);
                boolean isPreviewed = session.isPreviewing && session.previewItems.contains(itemId);

                // Cristal slot: a faint glass cell. Allowed items carry the blue accent — the same "on"
                // language as every active button — while the preview washes stay neutral so they still
                // read as a temporary state on top of it.
                int cell = itemSize - 2; // slot visual: the cell minus a 1px breathing gap
                io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(
                        context, drawX + 1, drawY + 1, cell, cell, 0x18FFFFFF);
                final int allowedWash = 0x662C6BAE;
                if (session.isPreviewing) {
                    if (session.previewType == 1) {
                        if (isAllowed)
                            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(
                                    context, drawX + 1, drawY + 1, cell, cell, allowedWash);
                        if (isPreviewed)
                            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(
                                    context, drawX + 1, drawY + 1, cell, cell, 0x44FFFFFF);
                    } else if (session.previewType == 2) {
                        if (isAllowed)
                            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(
                                    context, drawX + 1, drawY + 1, cell, cell, allowedWash);
                        else
                            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(
                                    context, drawX + 1, drawY + 1, cell, cell, 0x44FFFFFF);
                    } else if (session.previewType == 3) {
                        if (isAllowed)
                            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(
                                    context, drawX + 1, drawY + 1, cell, cell, 0x44000000);
                    }
                } else if (isAllowed) {
                    io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(
                            context, drawX + 1, drawY + 1, cell, cell, allowedWash);
                }

                // The item keeps its native 16px size (scaling it down resamples the sprite and looks
                // pixelated); the cell is what grew, so it now sits centred with a margin all round.
                int itemInset = (itemSize - 16) / 2;
                context.renderItem(item.getDefaultInstance(), drawX + itemInset, drawY + itemInset);

                if (session.activeDropdownTags.isEmpty()
                        && editor.isHovering(drawX, drawY, itemSize, itemSize, mouseX, mouseY)
                        && mouseY >= gridViewY
                        && mouseY <= gridViewY + gridViewH) {
                    // Exactly the cell's own geometry, so the hover ring traces the slot instead of
                    // floating around it with a different shape.
                    io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(
                            context, drawX + 1, drawY + 1, cell, cell, 0xFFFFFFFF);
                    deferredHoverTooltip = item.getDescription();
                }
            }
        }
        context.disableScissor();

        // Outside the scissor so it is never clipped by the grid's right edge (flush + clear depth so the
        // fixed batch/depth order can't hide it behind the grid or right-panel content).
        if (deferredHoverTooltip != null) {
            io.github.marcsanzdev.chestseparators.client.ui.EditorRenderer.flushAndClearDepth(context);
            context.renderTooltip(Minecraft.getInstance().font, deferredHoverTooltip, mouseX, mouseY);
        }
    }

    void drawTagDropdown(GuiGraphics context, int mouseX, int mouseY) {
        if (!session.activeDropdownTags.isEmpty()) {
            boolean isDark = GlobalChestConfig.instance.darkMode;
            int dropW = 120;
            int dropH = session.activeDropdownTags.size() * 14 + 4;
            int dX = session.dropdownX;
            int dY = session.dropdownY;
            if (dX + dropW > layout.screenWidth) dX = layout.screenWidth - dropW;
            if (dY + dropH > layout.screenHeight) dY = layout.screenHeight - dropH;
            session.dropdownX = dX;
            session.dropdownY = dY;

            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(context, dX, dY, dropW, dropH);

            for (int i = 0; i < session.activeDropdownTags.size(); i++) {
                String tag = session.activeDropdownTags.get(i);
                int itemY = dY + 2 + (i * 14);
                boolean isHoveringTag = mouseX >= dX && mouseX <= dX + dropW && mouseY >= itemY && mouseY < itemY + 14;
                if (isHoveringTag)
                    context.fill(dX + 2, itemY, dX + dropW - 2, itemY + 14, isDark ? 0x40FFFFFF : 0x40000000);

                context.pose().pushPose();
                float scale = 1.0f;
                tag = Minecraft.getInstance().font.plainSubstrByWidth(tag, dropW - 24);
                context.pose().translate((float) (dX + 6), (itemY + (14 - 8 * scale) / 2), 0.0F);
                context.pose().scale(scale, scale, 1.0F);

                int textColor =
                        isHoveringTag ? io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ACCENT : 0xFFFFFFFF;
                context.drawString(Minecraft.getInstance().font, "#" + tag, 0, 0, textColor, false);
                context.pose().popPose();
            }
        }
    }

    void drawPaginationCarousel(
            GuiGraphics context, int rightBoundX, int y, int maxPages, int currentPage, int mouseX, int mouseY) {
        if (maxPages <= 1) return;

        // Sliding window: show at most 3 page dots centered on the current page.
        int visibleDots = Math.min(maxPages, 3);
        int startPage = Math.max(0, Math.min(currentPage - 1, maxPages - visibleDots));

        int dotSpacing = 8;
        int arrowWidth = 12;
        int totalWidth = (arrowWidth * 2) + (visibleDots * dotSpacing);
        int startX = rightBoundX - totalWidth;

        int currentX = startX;

        boolean hoverLeft = editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY);
        context.drawString(
                Minecraft.getInstance().font,
                "<",
                currentX + 2,
                y,
                hoverLeft ? 0xFF55FF55 : 0xFF888888,
                false);
        currentX += arrowWidth;

        for (int i = 0; i < visibleDots; i++) {
            int pageIndex = startPage + i;
            boolean isActive = (pageIndex == currentPage);
            boolean hoverDot = editor.isHovering(currentX, y - 2, dotSpacing, 12, mouseX, mouseY);

            int color = isActive ? 0xFFFFFFFF : (hoverDot ? 0xFFAAAAAA : 0xFF555555);

            if (isActive) {
                context.fill(currentX + 2, y + 2, currentX + 6, y + 6, color);
            } else {
                context.fill(currentX + 3, y + 3, currentX + 5, y + 5, color);
            }
            currentX += dotSpacing;
        }

        boolean hoverRight = editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY);
        context.drawString(
                Minecraft.getInstance().font,
                ">",
                currentX + 2,
                y,
                hoverRight ? 0xFF55FF55 : 0xFF888888,
                false);
    }
}



