package io.github.marcsanzdev.chestseparators.neoforge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import io.github.marcsanzdev.chestseparators.client.ui.UiTheme;

/** Renders the actual shared components for visual inspection; validation build only. */
public final class ThemePreviewScreen extends Screen {
    private int frames;
    public ThemePreviewScreen(){super(Component.literal("Absolute Order visual validation"));}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        if(frames==80) verifyAscensionPanel(g);
        g.fill(0,0,width,height,0xFF0D111A);
        int w=Math.min(390,width-16),x=(width-w)/2,y=8;
        UiTheme.panel(g,x,y,w,222);
        UiTheme.centered(g,font,Component.literal("Editor de zonas · vista de componentes"),x+w/2,y+10,UiTheme.TEXT);
        String[] icons={"filter","palette","area_select","search","save","trash","copy","paste","undo","redo","deposit","cancel"};
        for(int i=0;i<icons.length;i++){
            int ix=x+12+i*30;
            UiTheme.button(g,ix,y+30,22,22,i==1,false);
            io.github.marcsanzdev.chestseparators.client.ui.UiIcons.draw(g,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chestseparators","textures/gui/icon_sm_"+icons[i]+".png"),ix+3,y+33,16,16,UiTheme.TEXT);
        }
        int bw=(w-36)/2;
        new io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget(x+12,y+66,bw,20,"Editar zona",io.github.marcsanzdev.chestseparators.client.ModTextures.ICON_SM_FILTER,()->{}).render(g,-1,-1,0);
        new io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget(x+24+bw,y+66,bw,20,"Color de la zona",io.github.marcsanzdev.chestseparators.client.ModTextures.ICON_SM_PALETTE,()->{}).render(g,-1,-1,0);
        new io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget(x+12,y+92,bw,20,"Permitir entrada manual",io.github.marcsanzdev.chestseparators.client.ModTextures.ICON_SM_MANUAL,()->{}).render(g,-1,-1,0);
        new io.github.marcsanzdev.chestseparators.client.ui.widgets.ActionIconButtonWidget(x+24+bw,y+92,bw,20,"Guardar",io.github.marcsanzdev.chestseparators.client.ModTextures.ICON_SM_SAVE,0,()->{}).render(g,-1,-1,0);
        UiTheme.card(g,x+12,y+124,w-24,34,false);
        g.drawString(font,"Filtro: lingotes de hierro",x+22,y+130,UiTheme.TEXT,false);
        g.drawString(font,"Color y filtro se guardan juntos",x+22,y+143,UiTheme.TEXT_MUTED,false);
        for(int row=0;row<2;row++)for(int col=0;col<9;col++){
            int sx=x+16+col*19,sy=y+173+row*19;
            UiTheme.roundRect(g,sx,sy,17,17,row==0?0xFF426A55:UiTheme.BTN_BG);
            UiTheme.roundBorder(g,sx,sy,17,17,row==0?0xFF7FD1AE:UiTheme.PANEL_BORDER);
        }
        g.drawString(font,"Zona seleccionada",x+201,y+180,UiTheme.TEXT,false);
        g.flush();
        if(++frames==90){
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of("theme-preview.png"));System.out.println("THEME_PREVIEW_OK theme-preview.png");}
            catch(Exception e){e.printStackTrace();}
            Minecraft.getInstance().stop();
        }
    }
    /** Compare actual pixels with the installed Ascension renderer, without loading the mod. */
    private void verifyAscensionPanel(GuiGraphics g) {
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{
                java.nio.file.Path.of("client-or-dev-incompatible-mods/ascension-neoforge-1.21.1-1.0.0-votaciones.jar").toUri().toURL()
        }, getClass().getClassLoader())) {
            g.fill(0,0,width,height,0xFF0D111A);
            UiTheme.panel(g,10,10,100,50);
            var draw = loader.loadClass("com.ascension.client.gui.Draw");
            draw.getMethod("panel",GuiGraphics.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class)
                .invoke(null,g,130,10,100,50,5,0xD0121826,0x28FFFFFF);
            g.flush();
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                int scale=(int)Minecraft.getInstance().getWindow().getGuiScale();
                for(int py=8*scale;py<64*scale;py++) for(int px=8*scale;px<114*scale;px++) {
                    if(image.getPixelRGBA(px,py)!=image.getPixelRGBA(px+120*scale,py))
                        throw new IllegalStateException("Panel pixels differ at "+px+","+py);
                }
            }
            System.out.println("ASCENSION_PANEL_PIXEL_MATCH_OK");
        } catch(Exception e) { throw new IllegalStateException("Ascension visual parity failed",e); }
    }
}



