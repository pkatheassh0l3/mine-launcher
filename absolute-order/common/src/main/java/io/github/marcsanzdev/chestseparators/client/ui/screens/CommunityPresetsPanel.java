package io.github.marcsanzdev.chestseparators.client.ui.screens;

import java.util.*;
import io.github.marcsanzdev.chestseparators.client.ui.*;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.*;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.data.CommunityData;
import io.github.marcsanzdev.chestseparators.network.CommunityRequest;
import io.github.marcsanzdev.chestseparators.client.input.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Server-owned presets, with explicit publication and persistent author attribution. */
public final class CommunityPresetsPanel {
    private EditBox name;
    private int page;
    private UUID author; private boolean authorOpen; private int authorOffset,ax,ay,aw;
    private List<io.github.marcsanzdev.chestseparators.data.CommunityAuthors.Author> options=List.of();
    private UUID pendingDelete;
    private final List<CustomWidget> buttons=new ArrayList<>();
    public boolean focused(){return name!=null && name.isFocused();}
    public void blur(){authorOpen=false;if(name!=null)name.setFocused(false);pendingDelete=null;}
    public void reset(){page=0;author=null;authorOpen=false;authorOffset=0;pendingDelete=null;CommunityClient.reset();CommunityClient.request(CommunityRequest.LIST,CommunityData.NONE,"");}
    public void render(GuiGraphics g,int x,int y,int width,int height,int mx,int my,ChestSeparatorsEditor editor){
        var mc=Minecraft.getInstance();buttons.clear();
        if(name==null){name=new EditBox(mc.font,x+8,y+52,width-16,16,Component.literal("Nombre del preset"));name.setHint(Component.literal("Nombre del nuevo preset"));name.setMaxLength(64);}
        UiTheme.inset(g,x+8,y+52,width-16,16);name.setBordered(false);name.setTextColor(UiTheme.TEXT);name.setX(x+14);name.setY(y+56);name.setWidth(width-28);name.render(g,mx,my,0);
        var publish=new WideButtonWidget(x+8,y+72,width-42,20,"Publicar cofre actual",ModTextures.ICON_SM_SAVE,()->CommunityClient.request(CommunityRequest.PUBLISH,CommunityData.NONE,name.getValue()));
        publish.texSize=128; publish.isDisabled=!CommunityClient.available() || name.getValue().isBlank();buttons.add(publish);
        buttons.add(textButton(x+width-30,y+72,22,20,"↻",()->CommunityClient.request(CommunityRequest.LIST,CommunityData.NONE,"")));
        options=CommunityClient.authors;authorOffset=Math.min(authorOffset,Math.max(0,options.size()+1-5)); ax=x+8; ay=y+96; aw=width-16;
        String selected=author==null?"Todos":options.stream().filter(a->a.id().equals(author)).map(a->a.name()).findFirst().orElse("Usuario");
        buttons.add(textButton(ax,ay,aw,18,"Usuario: "+selected+" ▾",()->{authorOpen=!authorOpen;authorOffset=0;}));
        var entries=io.github.marcsanzdev.chestseparators.data.CommunityAuthors.filter(CommunityClient.entries,author,editor.chestPresetSize(),ChestSeparatorsEditor.storageColumns());
        int pages=Math.max(1,(entries.size()+2)/3);page=Math.min(page,pages-1);
        for(int i=0;i<3;i++){
            int index=page*3+i;if(index>=entries.size())break;var entry=entries.get(index);int ry=y+118+i*28;
            UiTheme.card(g,x+6,ry,width-12,26,mx>=x+6&&mx<x+width-6&&my>=ry&&my<ry+26);
            String shortName=mc.font.plainSubstrByWidth(entry.name(),width-65);
            g.drawString(mc.font,shortName,x+12,ry+3,UiTheme.TEXT,false);
            g.drawString(mc.font,mc.font.plainSubstrByWidth("Por "+entry.author(),width-65),x+12,ry+14,UiTheme.TEXT_MUTED,false);
            if(mx>=x+8 && mx<x+width-50 && my>=ry && my<ry+26)g.renderTooltip(mc.font,Component.literal(entry.name()+" — por "+entry.author()+" · "+entry.size()+" ranuras"),mx,my);
            buttons.add(new ToolButtonWidget(x+width-50,ry+3,ModTextures.ICON_SM_IMPORT,"Aplicar al cofre",()->CommunityClient.request(CommunityRequest.APPLY,entry.id(),"")));
            var delete=new ToolButtonWidget(x+width-27,ry+3,ModTextures.ICON_SM_TRASH,entry.id().equals(pendingDelete)?"Pulsa otra vez para borrar":"Borrar mi preset",()->{if(entry.id().equals(pendingDelete)){CommunityClient.request(CommunityRequest.DELETE,entry.id(),"");pendingDelete=null;}else{pendingDelete=entry.id();CommunityClient.status="Pulsa de nuevo la papelera para confirmar";}});
            delete.isDisabled=mc.player==null || (!entry.owner().equals(mc.player.getUUID()) && !mc.player.hasPermissions(2));buttons.add(delete);
        }
        if(entries.isEmpty())io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(g, mc.font,Component.literal("Sin presets para este tamaño"),x+width/2,y+148,UiTheme.TEXT_MUTED);
        int bottom=y+height-24;
        buttons.add(textButton(x+8,bottom,30,18,"‹",()->page=Math.max(0,page-1)));
        buttons.add(textButton(x+width-38,bottom,30,18,"›",()->page=Math.min(pages-1,page+1)));
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(g, mc.font,Component.literal((page+1)+" / "+pages),x+width/2,bottom+5,UiTheme.TEXT);
        for(var b:buttons){ if(b instanceof ToolButtonWidget tool){tool.texSize=128;tool.tintByState=true;} b.render(g,mx,my,0); }
        if(!CommunityClient.status.isBlank()){
            String msg=mc.font.plainSubstrByWidth(CommunityClient.status,width-16);
            g.drawString(mc.font,msg,x+8,y+height-36,UiTheme.TEXT,false);
            if(mx>=x && mx<x+width && my>=y+height-36 && my<y+height-24)g.renderTooltip(mc.font,Component.literal(CommunityClient.status),mx,my);
        }
        renderAuthors(g,mx,my);
    }
    public boolean scroll(double amount){if(authorOpen){authorOffset=Math.max(0,Math.min(Math.max(0,options.size()+1-5),authorOffset-(int)Math.signum(amount)));return true;}return false;}
    private void renderAuthors(GuiGraphics g,int mx,int my){
        if(!authorOpen)return;int count=Math.min(5,options.size()+1-authorOffset);
        EditorRenderer.flushAndClearDepth(g);UiTheme.panel(g,ax,ay+19,aw,count*18+4);
        for(int i=0;i<count;i++){int index=authorOffset+i;String label=index==0?"Todos":options.get(index-1).name();int rowY=ay+21+i*18;UiTheme.button(g,ax+2,rowY,aw-4,18,mx>=ax&&mx<ax+aw&&my>=rowY&&my<rowY+18,false);g.drawString(Minecraft.getInstance().font,label,ax+6,rowY+5,UiTheme.TEXT,false);}
    }    private static CustomWidget textButton(int x,int y,int w,int h,String text,Runnable action){
        return new CustomWidget(x,y,w,h,action){ public void render(GuiGraphics g,int mx,int my,float delta){UiTheme.button(g,x,y,width,height,isHovering(mx,my),false);io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(g, Minecraft.getInstance().font,Component.literal(text),x+width/2,y+5,UiTheme.TEXT);} };
    }    public boolean click(double x,double y,int button){
        if(authorOpen){int count=Math.min(5,options.size()+1-authorOffset);if(x>=ax&&x<ax+aw&&y>=ay+21&&y<ay+21+count*18){int index=authorOffset+(int)((y-ay-21)/18);author=index==0?null:options.get(index-1).id();page=0;pendingDelete=null;}authorOpen=false;return true;}
        if(name!=null){name.setFocused(name.isMouseOver(x,y));if(name.isFocused()){name.mouseClicked(x,y,button);return true;}}
        for(var b:buttons)if(b.mouseClicked(x,y,button))return true;return true;
    }
    public boolean key(KeyEvent e){if(!focused())return false;if(e.key()==256){name.setFocused(false);return true;}name.keyPressed(e.key(),e.scancode(),e.modifiers());return true;}
    public boolean character(CharacterEvent e){if(!focused())return false;name.charTyped((char)e.codepoint(),e.modifiers());return true;}
}


