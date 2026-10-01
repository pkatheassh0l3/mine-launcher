package io.github.marcsanzdev.chestseparators.client.config;

import java.util.Optional;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** An integer slider that draws a 16×16 mod icon at its left, with the row shifted right to make room. */
public class IconIntegerSliderEntry extends IntegerSliderEntry {

    private static final int ICON_SIZE = 16;
    private static final int SHIFT = 22;
    private static final int TEX = 128;

    private final ResourceLocation icon;

    public IconIntegerSliderEntry(
            Component fieldName,
            int min,
            int max,
            int value,
            int defaultValue,
            Consumer<Integer> saveConsumer,
            Component tooltip,
            ResourceLocation icon) {
        super(
                fieldName,
                min,
                max,
                value,
                Component.translatable("text.cloth-config.reset_value"),
                () -> defaultValue,
                saveConsumer,
                () -> Optional.of(new Component[] {tooltip}),
                false);
        this.icon = icon;
        setTextGetter(v -> Component.literal(v + "%"));
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int index,
            int y,
            int x,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean isHovered,
            float delta) {
        super.render(graphics, index, y, x + SHIFT, entryWidth - SHIFT, entryHeight, mouseX, mouseY, isHovered, delta);
        int iconY = y + (entryHeight - ICON_SIZE) / 2;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(graphics, icon, x, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, TEX, TEX, TEX, TEX, -1);
    }
}
