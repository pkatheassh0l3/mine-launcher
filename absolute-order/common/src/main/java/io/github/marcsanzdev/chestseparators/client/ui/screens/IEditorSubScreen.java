package io.github.marcsanzdev.chestseparators.client.ui.screens;

import net.minecraft.client.gui.GuiGraphics;

public interface IEditorSubScreen {
    /** Called when the screen opens or the window is resized. */
    void init();

    /** Called each frame to draw the screen contents. */
    void render(GuiGraphics context, int mouseX, int mouseY, float delta);

    // Mouse and keyboard event delegates.
    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);

    boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount);

    boolean keyPressed(int keyCode, int scanCode, int modifiers);

    boolean charTyped(char chr, int modifiers);
}
