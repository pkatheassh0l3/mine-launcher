package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ActionIconButtonWidget extends CustomWidget {

    public String label;
    public ResourceLocation icon;
    public int baseColor;
    /** Source texture size to sample. 32 = legacy pixel icons; 128 = smooth vector icons (with blur mcmeta). */
    public int texSize = 32;

    public ActionIconButtonWidget(
            int x, int y, int width, int height, String label, ResourceLocation icon, int baseColor, Runnable onClickAction) {
        super(x, y, width, height, onClickAction);
        this.label = label;
        this.icon = icon;
        this.baseColor = baseColor;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (this.isDisabled) return;

        boolean hover = isHovering(mouseX, mouseY);
        boolean active = this.isActive || PressAnim.active(x, y);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.button(context, x, y, width, height, hover, active);

        // The active/pressed button draws 1px smaller; shrink its icon+label by the same proportion.
        if (active) {
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.pushActiveContent(context, x, y, width, height);
        }

        float scale = 1.0f;
        int textWidth = Minecraft.getInstance().font.width(label);
        int iconSpace = (icon != null ? 20 : 0);
        int availableTextWidth = width - 8 - iconSpace;

        String displayText = label;

        if (textWidth > availableTextWidth) {
            displayText = Minecraft.getInstance().font.plainSubstrByWidth(label, Math.max(0, availableTextWidth - Minecraft.getInstance().font.width("…"))) + "…";
        }

        int scaledTextWidth = (int) (Minecraft.getInstance().font.width(displayText) * scale);
        int contentWidth = iconSpace + scaledTextWidth;
        int startX = x + (width - contentWidth) / 2;

        if (icon != null) {
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context,
                    icon,
                    startX,
                    y + (height - 16) / 2,
                    0.0F,
                    0.0F,
                    16,
                    16,
                    texSize,
                    texSize,
                    texSize,
                    texSize,
                    -1);
            startX += 20;
        }

        int textColor = active
                ? io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ON_ACCENT
                : io.github.marcsanzdev.chestseparators.client.ui.UiTheme.TEXT;

        context.pose().pushPose();
        context.pose().translate((float) startX, (float) (y + (height - 9 * scale) / 2), 0.0F);
        context.pose().scale(scale, scale, 1.0F);
        context.drawString(Minecraft.getInstance().font, displayText, 0, 0, textColor, false);
        context.pose().popPose();

        if (active) {
            context.pose().popPose();
        }

        if (hover && (!displayText.equals(label) || this.tooltipText != null) && !this.isDisabled) {
            context.renderTooltip(
                    Minecraft.getInstance().font, Component.literal(!displayText.equals(label) ? label : this.tooltipText), mouseX, mouseY);
        }
    }
}


