package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Renders the always-on left-side whitelist preview panel shown in normal mode, including the
 * per-rule indicator tabs. Pure rendering, extracted verbatim from ScreenViewGroups.
 */
final class WhitelistPreviewPanelRenderer {

    private final ScreenViewGroups screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;

    WhitelistPreviewPanelRenderer(ScreenViewGroups screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    void renderWhitelistPreviewPanel(GuiGraphics context, int mouseX, int mouseY) {
        int listW = layout.listW;
        int listH = layout.listH;
        int listX = layout.listX;
        int listY = layout.listY;

        int guiX = layout.guiX;
        int guiY = layout.guiY;

        boolean isDark = GlobalChestConfig.instance.darkMode;

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(context, listX, listY, listW, listH);

        Slot hoveredSlot = null;
        for (Slot slot : editor.accessor.getHandler().slots) {
            if (ChestSeparatorsEditor.isEditableSlot(slot)
                    && editor.isHovering(guiX + slot.x - 1, guiY + slot.y - 1, 18, 18, mouseX, mouseY)) {
                hoveredSlot = slot;
                break;
            }
        }

        if (hoveredSlot != null) {
            var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            int hoveredKey = ChestSeparatorsEditor.slotKey(hoveredSlot);
            if (whitelists != null && whitelists.containsKey(hoveredKey)) {
                SlotWhitelist wl = whitelists.get(hoveredKey);
                UUID currentGroupId = wl.groupId();

                if (!session.isPreviewing || !currentGroupId.equals(session.lastHoveredGroupId)) {
                    session.previewItems = new ArrayList<>(wl.allowedItems());
                    session.listScrollY = 0f;
                    session.isPreviewing = true;
                    session.lastHoveredGroupId = currentGroupId;

                    long now = System.currentTimeMillis();
                    session.previewStartTime = now;
                    session.lastPreviewTime = now;
                    session.previewScrollDirectionDown = true;
                    session.userOverrodePreviewScroll = false;
                }
            } else {
                session.isPreviewing = false;
                session.previewItems.clear();
                session.lastHoveredGroupId = null;
            }
        } else {
            if (!editor.isHovering(listX, listY, listW, listH, mouseX, mouseY)) {
                session.isPreviewing = false;
                session.previewItems.clear();
                session.lastHoveredGroupId = null;
            }
        }

        Component title = session.isPreviewing
                ? Component.translatable("gui.chestseparators.group_filter")
                : Component.translatable("gui.chestseparators.no_filter");
        int colorTitle = session.isPreviewing ? 0xFF55FF55 : 0xFFAAAAAA;
        int itemCount = session.isPreviewing ? session.previewItems.size() : 0;

        context.pose().pushPose();
        float scaleC = 1.0f;
        context.pose().scale(scaleC, scaleC, 1.0F);
        context.drawString(
                Minecraft.getInstance().font,
                title,
                (int) ((listX + 8) / scaleC),
                (int) ((listY + 8) / scaleC),
                colorTitle,
                false);
        context.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.chestseparators.items_count", itemCount),
                (int) ((listX + 8) / scaleC),
                (int) ((listY + 18) / scaleC),
                0xFFFFFFFF,
                false);
        context.pose().popPose();

        int listViewY = listY + 32;
        int listViewH = listH - 38;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.inset(
                context, listX + 6, listViewY, listW - 12, listViewH);

        if (!session.isPreviewing) {
            Component hoverText = Component.translatable("gui.chestseparators.hover_group");
            int textX = listX + 12;
            int textY = listViewY + 10;
            int maxTextW = listW - 24;

            for (net.minecraft.util.FormattedCharSequence line :
                    Minecraft.getInstance().font.split(hoverText, maxTextW)) {
                context.drawString(Minecraft.getInstance().font, line, textX, textY, 0xFF666666, false);
                textY += Minecraft.getInstance().font.lineHeight + 2;
            }
            return;
        }

        int totalListItems = session.previewItems.size();
        int totalListHeight = totalListItems * 18;
        float maxListScroll = Math.max(0, totalListHeight - listViewH);

        long now = System.currentTimeMillis();
        float dt = (now - session.lastPreviewTime) / 1000f;
        session.lastPreviewTime = now;

        if (!session.userOverrodePreviewScroll && maxListScroll > 0 && (now - session.previewStartTime > 1000)) {
            if (GlobalChestConfig.instance.enablePreviewAnimation) {
                float speed = 25f;
                if (session.previewScrollDirectionDown) {
                    session.listScrollY += speed * dt;
                    if (session.listScrollY >= maxListScroll) {
                        session.listScrollY = maxListScroll;
                        session.previewScrollDirectionDown = false;
                        session.previewStartTime = now;
                    }
                } else {
                    session.listScrollY -= speed * dt;
                    if (session.listScrollY <= 0) {
                        session.listScrollY = 0;
                        session.previewScrollDirectionDown = true;
                        session.previewStartTime = now;
                    }
                }
            }
        }

        session.listScrollY = Mth.clamp(session.listScrollY, 0, maxListScroll);

        context.enableScissor(listX + 6, listViewY + 1, listX + listW - 14, listViewY + listViewH - 1);
        int startIndex = (int) (session.listScrollY / 18);
        int visibleCount = (listViewH / 18) + 2;

        if (session.previewItems.isEmpty()) {
            Component emptyText = Component.translatable("gui.chestseparators.empty_whitelist");
            int textX = listX + 12;
            int textY = listViewY + 10;
            int maxTextW = listW - 24;

            for (net.minecraft.util.FormattedCharSequence line :
                    Minecraft.getInstance().font.split(emptyText, maxTextW)) {
                context.drawString(Minecraft.getInstance().font, line, textX, textY, 0xFF888888, false);
                textY += Minecraft.getInstance().font.lineHeight + 2;
            }
        }

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;

            String itemIdStr = session.previewItems.get(idx);
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(itemIdStr));

            int itemY = listViewY + (i * 18) - (int) (session.listScrollY % 18) + 2;
            context.renderItem(item.getDefaultInstance(), listX + 8, itemY);

            String name = item.getDescription().getString();
            context.pose().pushPose();
            float scale = 1.0f;
            context.pose().scale(scale, scale, 1.0F);
            int maxNameW = (int) ((listW - 35) / scale);
            if (Minecraft.getInstance().font.width(name) > maxNameW) {
                name = Minecraft.getInstance().font.plainSubstrByWidth(name, maxNameW - 6) + "...";
            }
            int textColor = isDark ? 0xFFDDDDDD : 0xFF222222;
            context.drawString(
                    Minecraft.getInstance().font,
                    name,
                    (int) ((listX + 26) / scale),
                    (int) ((itemY + 6) / scale),
                    textColor,
                    false);
            context.pose().popPose();
        }
        context.disableScissor();

        // Draw the rule indicator tabs to the left of the preview panel.
        if (session.isPreviewing && session.lastHoveredGroupId != null) {
            SlotWhitelist activeWl = null;
            var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            if (whitelists != null) {
                for (SlotWhitelist wl : whitelists.values()) {
                    if (wl.groupId().equals(session.lastHoveredGroupId)) {
                        activeWl = wl;
                        break;
                    }
                }
            }

            if (activeWl != null) {
                // Tabs sit on the OUTER side of the panel: to its left on the normal left dock, to its right
                // when the panel is flipped right (recipe book open), so they never point back into the GUI.
                int tabX = layout.listOnRight ? (listX + listW) : (listX - 20);
                int startY = listY + 12;

                drawRuleTab(context, tabX, startY, ModTextures.ICON_SM_MANUAL, activeWl.allowManual());
                drawRuleTab(context, tabX, startY + 24, ModTextures.ICON_SM_SHIFT, activeWl.allowShift());
                // The Hopper rule can never be toggled on an Ender Chest (hoppers cannot reach it), so its
                // indicator tab would always be meaningless — omit it entirely there.
                if (!session.isEnderChest) {
                    drawRuleTab(
                            context,
                            tabX,
                            startY + 48,
                            session.isPlayerInventory ? ModTextures.ICON_SM_PICKUP : ModTextures.ICON_SM_HOPPER,
                            activeWl.allowHopper());
                }
            }
        }

        int lsbX = listX + listW - 13;
        int lsbH = listViewH - 1;
        int listThumbH = maxListScroll > 0
                ? Math.max(10, (int) ((listViewH / (float) Math.max(1, totalListHeight)) * lsbH))
                : lsbH;
        int listThumbY = maxListScroll > 0
                ? listViewY + (int) ((session.listScrollY / maxListScroll) * (lsbH - listThumbH))
                : listViewY;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.scrollbar(
                context, lsbX, listViewY, 6, lsbH, listThumbY, listThumbH);

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.tryParse(session.previewItems.get(idx)));
            int itemY = listViewY + (i * 18) - (int) (session.listScrollY % 18) + 2;

            if (editor.isHovering(listX + 8, itemY, 16, 16, mouseX, mouseY)
                    && mouseY >= listViewY
                    && mouseY <= listViewY + listViewH) {
                context.renderOutline(listX + 7, itemY - 1, 18, 18, 0xFFFFFFFF);
                context.renderTooltip(Minecraft.getInstance().font, item.getDescription(), mouseX, mouseY);
            }
        }
    }

    private void drawRuleTab(GuiGraphics context, int x, int y, net.minecraft.resources.ResourceLocation icon, boolean isActive) {
        int w = 20;
        int h = 20;
        // Cristal tab embedded flush into the preview panel: it attaches on the edge that faces the panel —
        // its right edge on the normal left dock, its left edge when the panel is flipped to the right.
        int attach = layout.listOnRight
                ? io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ATTACH_LEFT
                : io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ATTACH_RIGHT;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.tab(context, x, y, w, h, false, isActive, attach);

        int color = isActive
                ? io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ICON_ACTIVE
                : io.github.marcsanzdev.chestseparators.client.ui.UiTheme.TEXT_MUTED;
        // An active tab draws 1px smaller; shrink its icon by the same proportion.
        if (isActive) {
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.pushActiveContent(context, x, y, w, h);
        }
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context, icon, x + 2, y + 2, 0.0F, 0.0F, 16, 16, 128, 128, 128, 128, color);
        if (isActive) {
            context.pose().popPose();
        }
    }
}

