package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import net.minecraft.client.gui.GuiGraphics;

public abstract class CustomWidget {
    public int x;
    public int y;
    public int width;
    public int height;

    public boolean isActive = false;
    public boolean isDisabled = false;
    public String tooltipText = null;
    /** When true, the click plays the press flash first and runs the action a moment later (see PressAnim).
     *  Use for buttons whose action closes their own screen, so the flash is actually seen. */
    public boolean deferAction = false;

    protected Runnable onClickAction;

    public CustomWidget(int x, int y, int width, int height, Runnable onClickAction) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onClickAction = onClickAction;
    }

    // Every specific widget must implement its own drawing logic
    public abstract void render(GuiGraphics context, int mouseX, int mouseY, float delta);

    public boolean isHovering(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !isDisabled && isHovering(mouseX, mouseY)) {
            // Flash the button "pressed" (accent + shrunk) briefly, so stateless actions (Save, Load,
            // Copy, Undo…) visibly react instead of staying static. Close-screen buttons defer the action
            // until the flash has played, so it is still visible.
            if (deferAction) {
                PressAnim.pressAndDefer(x, y, onClickAction);
            } else {
                PressAnim.press(x, y);
                if (onClickAction != null) {
                    onClickAction.run();
                }
            }
            return true; // Click consumed
        }
        return false;
    }
}
