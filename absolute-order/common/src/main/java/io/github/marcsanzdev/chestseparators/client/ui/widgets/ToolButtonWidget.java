package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import io.github.marcsanzdev.chestseparators.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ToolButtonWidget extends CustomWidget {

    // Source texture size in px: 32 for the legacy pixel-art icons, larger (e.g. 128) for the smooth
    // vector-exported icons. Used as the u/v region and texture dimensions when sampling.
    public int texSize = 32;
    // When true the base icon is a single-color (white) glyph that is tinted by the button state
    // (light-gray normally, brighter on hover, accent blue when active). Used by the smooth line icons.
    public boolean tintByState = false;
    /** When true the BASE icon is drawn in {@link #dynamicColor} (the current paint colour) instead of
     *  white/state — used by the area/trace paint buttons so the whole mode icon shows the paint colour. */
    public boolean baseUsesDynamicColor = false;

    private boolean hovered = false;

    public ResourceLocation baseIcon;
    public ResourceLocation maskIcon;
    public int dynamicColor = 0xFFFFFF;

    public int baseOffsetX = 0;
    public int maskOffsetX = 0;

    public ToolButtonWidget(int x, int y, ResourceLocation baseIcon, String tooltip, Runnable onClickAction) {
        super(x, y, 20, 20, onClickAction);
        this.baseIcon = baseIcon;
        this.tooltipText = tooltip;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.hovered = isHovering(mouseX, mouseY);
        boolean active = this.isActive || PressAnim.active(x, y);

        // A button that disables itself right after acting (e.g. preset Delete → slot now empty) still
        // plays its press flash, so the click is visible; otherwise draw the normal disabled look.
        if (this.isDisabled && !PressAnim.active(x, y)) {
            UiTheme.roundRect(context, x, y, width, height, 0x0AFFFFFF);
            UiTheme.roundBorder(context, x, y, width, height, 0x14FFFFFF);
            drawIcon(context, this.baseIcon, this.maskIcon, 0xFFFFFF);
            UiTheme.roundRect(context, x, y, width, height, 0x66121218);
            return;
        }

        UiTheme.button(context, x, y, width, height, this.hovered, active);
        if (active) {
            // The active button draws 1px smaller; shrink its icon by the same proportion.
            UiTheme.pushActiveContent(context, x, y, width, height);
            drawIcon(context, this.baseIcon, this.maskIcon, this.dynamicColor);
            context.pose().popPose();
        } else {
            drawIcon(context, this.baseIcon, this.maskIcon, this.dynamicColor);
        }

        if (this.hovered && this.tooltipText != null) {
            java.util.List<Component> tooltipLines = new java.util.ArrayList<>();
            for (String line : this.tooltipText.split("\n")) {
                tooltipLines.add(Component.literal(line));
            }
            context.renderComponentTooltip(Minecraft.getInstance().font, tooltipLines, mouseX, mouseY);
        }
    }

    private void drawIcon(GuiGraphics context, ResourceLocation base, ResourceLocation mask, int color) {
        if (base == null) return;

        int baseColor = -1;
        if (this.tintByState) {
            boolean active = this.isActive || PressAnim.active(x, y);
            baseColor = active ? UiTheme.ICON_ACTIVE : (this.hovered ? UiTheme.ICON_HOVER : UiTheme.ICON);
        }
        if (this.baseUsesDynamicColor) {
            baseColor = this.dynamicColor | 0xFF000000;
        }
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context,
                base,
                x + 2 + baseOffsetX,
                y + 2,
                0.0F,
                0.0F,
                16,
                16,
                texSize,
                texSize,
                texSize,
                texSize,
                baseColor);

        if (mask != null) {
            int colorARGB = color | 0xFF000000;
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context,
                    mask,
                    x + 2 + maskOffsetX,
                    y + 2,
                    0.0F,
                    0.0F,
                    16,
                    16,
                    texSize,
                    texSize,
                    texSize,
                    texSize,
                    colorARGB);
        }
    }
}
