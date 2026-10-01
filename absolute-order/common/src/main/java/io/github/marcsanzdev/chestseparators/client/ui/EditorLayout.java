package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import net.minecraft.client.gui.screens.Screen;

public class EditorLayout {
    // Base Screen Info
    public int screenWidth;
    public int screenHeight;
    public int guiX;
    public int guiY;
    public int bgWidth;
    public int bgHeight;

    // Approximate on-screen width of the vanilla recipe book PANEL PLUS its left category tabs, used to
    // park the preview clear to the left of the whole book (tabs included) when the book and a potion-effect
    // panel are both open. Generous so the preview never overlaps the book's protruding tabs.
    private static final int RECIPE_BOOK_WIDTH = 200;

    // Universal Symmetrical Layout metrics
    public int gap = 6;
    public int mainW = 226;
    public int mainH = 236;
    public int listW = 120;
    public int btnW = 108;
    public int bH = 22;
    public int swatchSize = 12;

    // Calculated X/Y positions for main panels
    public int mainX;
    public int mainY;
    public int listX;
    public int listY;
    public int listH;
    public int rightX;
    /** True when the preview/list panel is docked to the RIGHT of the container (recipe book open). */
    public boolean listOnRight;

    // Edit Filter Grid
    public int cols = 9;
    public int rows = 9;
    /** Row height of the LEFT allowed-items list. */
    public int itemSize = 18;
    /** Cell size of the item GRID: a 16px item plus a 3px margin per side so it never touches its slot. */
    public int gridCell = 22;

    public int gridX;
    public int gridViewY;
    public int gridViewH;

    // Main Grid Scrollbar
    public int msbX;
    public int msbY;
    public int msbH;

    // List Panel Scrollbar
    public int listViewY;
    public int listViewH;
    public int lsbX;
    public int lsbY;
    public int lsbH;

    /** Trailing pixels added below the last list row so it can scroll fully clear of the scissor. */
    public static final int LIST_TRAILING_PADDING = 4;

    /** Size of a category tab (the 8-per-row strips above and below the item grid). */
    public int tabW = 22;

    /**
     * X of category tab {@code col} (0..7). Single source of truth shared by the renderer and the click
     * handler so they can never drift: the 8 tabs spread evenly across the panel, flush to both margins.
     */
    public int tabX(int col) {
        int margin = 4;
        int stride = (this.mainW - (2 * margin) - this.tabW) / 7;
        return this.mainX + margin + (col * stride);
    }

    /**
     * Maximum vertical scroll for the left (allowed-items) list. Single source of truth shared by the
     * renderer and every scroll input (wheel, scrollbar drag, arrow keys) so the bar always reaches
     * the bottom that the renderer draws.
     */
    public int maxListScroll(int itemCount) {
        return Math.max(0, itemCount * itemSize + LIST_TRAILING_PADDING - listViewH);
    }

    // View Groups & Tools
    public int sidebarWidth = 76;
    public int sidebarHeight;
    public int sidebarX;
    public int paletteBoxY;
    public int paletteY;
    public int paletteCol1X;
    public int paletteCol2X;
    public int paletteCol3X;

    // Color Picker
    public int pickerBoxSize = 100;
    public int pickerHueWidth = 20;
    public int popupW = 240;
    public int popupH = 185;
    public int popupX;
    public int popupY;

    // Conflict Popup
    public int conflictPopupW = 260;
    public int conflictPopupH = 110;
    public int conflictPopupX;
    public int conflictPopupY;

    public void update(Screen screen, HandledScreenAccessor accessor, int customSidebarYOffset) {
        this.screenWidth = screen.width;
        this.screenHeight = screen.height;
        this.guiX = accessor.getX();
        this.guiY = accessor.getY();
        this.bgWidth = accessor.getBackgroundWidth();
        this.bgHeight = accessor.getBackgroundHeight();

        // 1. Center panels (Edit Filter / View Groups)
        this.mainY = this.guiY + (this.bgHeight - this.mainH) / 2;
        this.mainX = this.guiX + (this.bgWidth - this.mainW) / 2;

        // 2. Side panels. The preview/list normally docks to the LEFT of the container. When the vanilla
        // recipe book is open it covers that dock, so the panel flips to the RIGHT of the container. But if
        // a potion effect is ALSO shown, the effect panel pushes the whole GUI far to the right and leaves
        // no room there — so in that combined case the panel goes to the FAR LEFT, to the left of the book.
        // The book state is read from its own widget (not from the GUI's shift) so it is never confused with
        // the effect panel's shift.
        boolean bookOpen = false;
        if (screen instanceof net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener rul) {
            net.minecraft.client.gui.screens.recipebook.RecipeBookComponent book = rul.getRecipeBookComponent();
            bookOpen = book != null && book.isVisible();
        }
        boolean hasPotion = net.minecraft.client.Minecraft.getInstance().player != null
                && !net.minecraft.client.Minecraft.getInstance()
                        .player
                        .getActiveEffects()
                        .isEmpty();

        // Mirror the exact separation the left dock leaves, so the right dock is not visually tighter.
        int dockGap = this.guiX - this.mainX + this.gap;
        if (!bookOpen) {
            this.listOnRight = false;
            this.listX = this.mainX - this.listW - this.gap;
        } else if (hasPotion) {
            this.listOnRight = false;
            this.listX = Math.max(22, this.guiX - RECIPE_BOOK_WIDTH - this.listW - this.gap);
        } else {
            this.listOnRight = true;
            this.listX = this.guiX + this.bgWidth + dockGap;
        }
        this.listY = this.mainY;
        this.listH = this.mainH;
        this.rightX = this.mainX + this.mainW + this.gap;

        // 3. Grid area
        this.gridX = this.mainX + 10;
        this.gridViewY = this.mainY + 25;
        this.gridViewH = this.rows * this.gridCell;

        // 4. Main Grid Scrollbar
        this.msbX = this.gridX + (this.cols * this.gridCell) + 3;
        this.msbY = this.gridViewY - 2;
        this.msbH = this.gridViewH + 4;

        // 5. Left List area & Scrollbar
        this.listViewY = this.listY + 42;
        this.listViewH = this.listH - 48;
        this.lsbX = this.listX + this.listW - 13;
        this.lsbY = this.listViewY;
        this.lsbH = this.listViewH - 1;

        // 6. Draw Lines Sidebar
        this.sidebarX = this.guiX - this.sidebarWidth - 4;
        this.paletteBoxY = this.guiY + customSidebarYOffset + 48;
        int contentX = this.sidebarX + 7;
        this.paletteY = this.paletteBoxY + 3;
        this.paletteCol1X = contentX + 5;
        this.paletteCol2X = contentX + 23;
        this.paletteCol3X = contentX + 47;

        this.sidebarHeight = this.bgHeight - 16;

        this.popupX = ((this.screenWidth - this.popupW) / 2) + 40;
        this.popupY = (this.screenHeight - this.popupH) / 2;

        this.conflictPopupX = (this.screenWidth - this.conflictPopupW) / 2;
        this.conflictPopupY = (this.screenHeight - this.conflictPopupH) / 2;
    }
}
