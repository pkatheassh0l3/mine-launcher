package io.github.marcsanzdev.chestseparators.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import io.github.marcsanzdev.chestseparators.client.input.*;
import io.github.marcsanzdev.chestseparators.data.CommunityData;
import io.github.marcsanzdev.chestseparators.network.CommunityRequest;

/** Modal inside the container screen: never closes the server's open menu. */
public final class CommunityNamePrompt {
    private static Screen screen;
    private static int menu=-1;
    private static EditBox name;
    private static int x,y,w;
    public static boolean active(){var mc=Minecraft.getInstance();return menu>=0 && mc.screen==screen && mc.player!=null && mc.player.containerMenu.containerId==menu;}
    public static void open(int menuId){if(active())return;menu=menuId;screen=Minecraft.getInstance().screen;name=null;}
    private static void submit(){if(name==null || name.getValue().isBlank())return;CommunityClient.request(CommunityRequest.NAME,CommunityData.NONE,name.getValue());menu=-1;}
    public static void render(GuiGraphics g,int mx,int my){
        var mc=Minecraft.getInstance();w=Math.min(290,mc.getWindow().getGuiScaledWidth()-16);x=(mc.getWindow().getGuiScaledWidth()-w)/2;y=(mc.getWindow().getGuiScaledHeight()-114)/2;
        EditorRenderer.flushAndClearDepth(g);g.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0x99000000);UiTheme.panel(g,x,y,w,114);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(g, mc.font,Component.literal("Publicar filtro en Comunidad"),x+w/2,y+10,UiTheme.TEXT);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(g, mc.font,Component.literal("Los cambios futuros se publicarán solos"),x+w/2,y+27,UiTheme.TEXT_MUTED);
        if(name==null){name=new EditBox(mc.font,x+12,y+47,w-24,18,Component.literal("Nombre del preset"));name.setMaxLength(64);name.setHint(Component.literal("Nombre del preset"));name.setFocused(true);}
        UiTheme.inset(g,x+12,y+47,w-24,18);name.setBordered(false);name.setTextColor(UiTheme.TEXT);name.setX(x+18);name.setY(y+52);name.setWidth(w-36);name.render(g,mx,my,0);
        int half=(w-30)/2;
        UiTheme.button(g,x+10,y+82,half,20,mx>=x+10&&mx<x+10+half&&my>=y+82&&my<y+102,false);
        UiTheme.button(g,x+20+half,y+82,half,20,mx>=x+20+half&&mx<x+w-10&&my>=y+82&&my<y+102,false);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(g, mc.font,Component.literal("Más tarde"),x+10+half/2,y+88,UiTheme.TEXT);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(g, mc.font,Component.literal("Publicar"),x+20+half+half/2,y+88,name.getValue().isBlank()?UiTheme.TEXT_MUTED:UiTheme.ACCENT);
    }
    public static void click(double mx,double my,int button){if(button!=0)return;int half=(w-30)/2;if(my>=y+82&&my<y+102){if(mx>=x+10&&mx<x+10+half)menu=-1;else if(mx>=x+20+half&&mx<x+w-10)submit();}else if(name!=null){name.setFocused(true);name.mouseClicked(mx,my,button);}}
    public static boolean key(KeyEvent e){if(e.key()==256){menu=-1;return true;}if(e.key()==257 || e.key()==335){submit();return true;}if(name!=null)name.keyPressed(e.key(),e.scancode(),e.modifiers());return true;}
    public static boolean character(CharacterEvent e){if(name!=null)name.charTyped((char)e.codepoint(),e.modifiers());return true;}
}


