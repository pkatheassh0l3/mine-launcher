package io.github.marcsanzdev.chestseparators.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Crisp, single-stroke symbols on a shared 16-unit grid. No baked-in backgrounds or colors. */
public final class UiIcons {
    private UiIcons() {}

    public static boolean draw(GuiGraphics g, ResourceLocation icon, int x, int y, int w, int h, int color) {
        if (!icon.getNamespace().equals("chestseparators") || !icon.getPath().contains("icon_sm_")) return false;
        String name = icon.getPath().replace("textures/gui/icon_sm_", "").replace(".png", "");
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(w / 16f, h / 16f, 1);
        int c = color == -1 ? UiTheme.TEXT : color;
        switch (name) {
            case "filter", "hopper" -> path(g,c,2,3,13,3,9,8,9,12,6,14,6,8,2,3);
            case "search" -> { ring(g,c,3,3,7); path(g,c,9,9,13,13); }
            case "check", "allow_all" -> path(g,c,3,8,6,11,12,4);
            case "cancel", "clear" -> { path(g,c,4,4,12,12); path(g,c,12,4,4,12); }
            case "trash", "broom" -> { path(g,c,3,4,13,4); path(g,c,6,2,10,2); path(g,c,4,6,5,13,11,13,12,6); path(g,c,7,7,7,11); path(g,c,9,7,9,11); }
            case "save" -> { box(g,c,3,2,10,12); box(g,c,5,2,6,4); box(g,c,5,9,6,5); }
            case "copy", "inv_presets" -> { box(g,c,5,5,8,9); path(g,c,10,3,3,3,3,11); }
            case "chest_presets" -> { box(g,c,2,4,12,9); path(g,c,2,7,13,7); box(g,c,7,6,2,4); }
            case "paste" -> { box(g,c,4,3,9,11); box(g,c,6,2,5,3); path(g,c,6,8,10,8); path(g,c,6,11,10,11); }
            case "deposit", "import" -> { path(g,c,8,2,8,10,4,6); path(g,c,8,10,12,6); path(g,c,3,11,3,14,13,14,13,11); }
            case "fill", "shift" -> { path(g,c,8,13,8,3,4,7); path(g,c,8,3,12,7); }
            case "undo" -> { path(g,c,6,3,2,7,6,11); path(g,c,3,7,10,7,13,10,13,13); }
            case "redo" -> { path(g,c,10,3,14,7,10,11); path(g,c,13,7,6,7,3,10,3,13); }
            case "area_select", "eraser_area" -> { path(g,c,2,6,2,2,6,2); path(g,c,10,2,14,2,14,6); path(g,c,14,10,14,14,10,14); path(g,c,6,14,2,14,2,10); }
            case "trace_select", "eraser_trace" -> path(g,c,3,3,12,3,12,7,7,7,7,12,3,12,3,3);
            case "palette" -> { ring(g,c,2,2,12); box(g,c,5,5,1,1); box(g,c,9,4,1,1); box(g,c,11,8,1,1); path(g,c,4,11,7,11,8,14); }
            case "edit", "pencil", "combo" -> { path(g,c,3,10,10,3,13,6,6,13,3,13,3,10); path(g,c,8,5,11,8); }
            case "brush" -> { path(g,c,5,8,10,3,13,6,8,11,5,8); path(g,c,5,9,3,10,2,14,6,13,7,11); }
            case "eyedropper" -> { path(g,c,10,2,14,6); path(g,c,12,4,4,12,2,14,3,11,10,4); }
            case "manual", "pickup" -> { path(g,c,5,8,5,3,7,3,7,8,10,6,13,8,11,13,6,13,3,9,5,8); }
            case "conflict" -> { path(g,c,8,2,14,13,2,13,8,2); path(g,c,8,6,8,9); box(g,c,8,11,1,1); }
            case "mask_area", "mask_trace" -> { }
            default -> { g.pose().popPose(); return false; }
        }
        g.pose().popPose();
        return true;
    }
    private static void box(GuiGraphics g,int c,int x,int y,int w,int h) {
        path(g,c,x,y,x+w-1,y,x+w-1,y+h-1,x,y+h-1,x,y);
    }
    private static void ring(GuiGraphics g,int c,int x,int y,int size) {
        path(g,c,x+3,y,x+size-3,y,x+size,y+3,x+size,y+size-3,x+size-3,y+size,x+3,y+size,x,y+size-3,x,y+3,x+3,y);
    }
    private static void path(GuiGraphics g,int c,int... xy) {
        for(int i=2;i<xy.length;i+=2) {
            int x=xy[i-2], y=xy[i-1], dx=xy[i]-x, dy=xy[i+1]-y;
            int n=Math.max(Math.abs(dx),Math.abs(dy));
            for(int j=0;j<=n;j++) {
                int px=x+(n==0?0:Math.round(dx*j/(float)n));
                int py=y+(n==0?0:Math.round(dy*j/(float)n));
                g.fill(px,py,px+1,py+1,c);
            }
        }
    }
}
