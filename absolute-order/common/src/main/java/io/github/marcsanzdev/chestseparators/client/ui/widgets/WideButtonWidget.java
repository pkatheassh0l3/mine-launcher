package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class WideButtonWidget extends CustomWidget {

    public String label;
    public ResourceLocation icon;
    public ResourceLocation disabledIconFallback;
    public boolean keepNormalTextColor = false;
    /** Source texture size to sample. 32 = legacy pixel icons; 128 = smooth vector icons (with blur mcmeta). */
    public int texSize = 32;

    public WideButtonWidget(
            int x, int y, int width, int height, String label, ResourceLocation icon, Runnable onClickAction) {
        super(x, y, width, height, onClickAction);
        this.label = label;
        this.icon = icon;
        this.disabledIconFallback = icon;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (this.isDisabled) {
            renderDisabled(context);
            return;
        }

        boolean hover = isHovering(mouseX, mouseY);
        boolean sunken = this.isActive || PressAnim.active(x, y);
        boolean isDark = GlobalChestConfig.instance.darkMode;

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.button(context, x, y, width, height, hover, sunken);

        // The sunken (active) button draws 1px smaller; shrink its icon+label by the same proportion.
        if (sunken) {
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.pushActiveContent(context, x, y, width, height);
        }

        int iconColor;
        if (sunken && !this.keepNormalTextColor) {
            iconColor = io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ON_ACCENT;
        } else {
            iconColor = io.github.marcsanzdev.chestseparators.client.ui.UiTheme.TEXT;
        }

        if (this.icon != null) {
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context, this.icon, x + 2, y + 2, 0.0F, 0.0F, 16, 16, texSize, texSize, texSize, texSize, -1);
        }

        float scale = 1.0f;
        int textWidth = Minecraft.getInstance().font.width(label);
        int availableWidth = width - (icon == null ? 16 : 28);
        String displayText = label;

        if (textWidth > availableWidth) {
            displayText = Minecraft.getInstance().font.plainSubstrByWidth(label, Math.max(0, availableWidth - Minecraft.getInstance().font.width("…"))) + "…";
        }

        context.pose().pushPose();
        context.pose().translate((float) (icon == null ? x + (width - Minecraft.getInstance().font.width(displayText) * scale) / 2 : x + 22), (float) (y + (height - 9 * scale) / 2), 0.0F);
        context.pose().scale(scale, scale, 1.0F);
        // Shadow only when in dark mode
        context.drawString(Minecraft.getInstance().font, displayText, 0, 0, iconColor, false);
        context.pose().popPose();

        if (sunken) {
            context.pose().popPose();
        }

        if (hover && (!displayText.equals(label) || this.tooltipText != null) && !this.isDisabled) {
            context.renderTooltip(
                    Minecraft.getInstance().font, Component.literal(!displayText.equals(label) ? label : this.tooltipText), mouseX, mouseY);
        }
    }

    private void renderDisabled(GuiGraphics context) {
        boolean isDark = GlobalChestConfig.instance.darkMode;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(context, x, y, width, height, 0x0AFFFFFF);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(context, x, y, width, height, 0x14FFFFFF);

        ResourceLocation iconToDraw = this.disabledIconFallback != null ? this.disabledIconFallback : this.icon;
        int disabledColor = io.github.marcsanzdev.chestseparators.client.ui.UiTheme.TEXT_DISABLED;

        if (iconToDraw != null) {
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context,
                    iconToDraw,
                    x + 2,
                    y + 2,
                    0.0F,
                    0.0F,
                    16,
                    16,
                    texSize,
                    texSize,
                    texSize,
                    texSize,
                    disabledColor);
        }

        float scale = 1.0f;
        int textWidth = Minecraft.getInstance().font.width(label);
        int availableWidth = width - (icon == null ? 16 : 28);
        String displayText = label;

        if (textWidth > availableWidth) {
            displayText = Minecraft.getInstance().font.plainSubstrByWidth(label, Math.max(0, availableWidth - Minecraft.getInstance().font.width("…"))) + "…";
        }

        context.pose().pushPose();
        context.pose().translate((float) (icon == null ? x + (width - Minecraft.getInstance().font.width(displayText) * scale) / 2 : x + 22), (float) (y + (height - 9 * scale) / 2), 0.0F);
        context.pose().scale(scale, scale, 1.0F);
        context.drawString(Minecraft.getInstance().font, displayText, 0, 0, disabledColor, false);
        context.pose().popPose();
    }
}



