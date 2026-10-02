package es.ascension.waystones;

import java.util.*;
import java.text.Normalizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.menu.WaystoneSelectionMenu;
import net.blay09.mods.waystones.network.message.SelectWaystoneMessage;
import net.blay09.mods.waystones.client.gui.screen.WaystoneSelectionScreen;

/** Uses the real selection menu and the original server-validated teleport packet. */
public class RoutesScreen extends Screen implements MenuAccess<WaystoneSelectionMenu> {
    private static final int BG=0xFF151A20, PANEL=0xFF1D242B, EDGE=0xFF485057,
            COPPER=0xFFE0A16C, TEXT=0xFFEDE6DA, MUTED=0xFF9CAAAF;
    private final WaystoneSelectionMenu menu;
    private List<Waystone> all=List.of(), filtered=List.of();
    private final List<ResourceKey<Level>> dimensions=new ArrayList<>();
    private ResourceKey<Level> dimension;
    private Waystone selected, hovered;
    private WarpRequirement requirement;
    private EditBox search;
    private CopperButton travel, dimButton;
    private int x,y,w,h,side,mx,my,mw,mh,scroll;
    private double centerX,centerZ,scale=0.3;
    private long terrainAt, sentAt, lastSample;
    private int[] terrain=new int[0];
    private int cols,rows;
    public RoutesScreen(WaystoneSelectionMenu menu) { super(Component.literal("Rutas de Ascension")); this.menu=menu; }
    @Override public WaystoneSelectionMenu getMenu() { return menu; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if(minecraft.player!=null) minecraft.player.closeContainer(); super.onClose(); }
    @Override public void tick() {
        if(minecraft.player==null || minecraft.player.containerMenu!=menu) { minecraft.setScreen(null); return; }
        if(sentAt!=0 && System.currentTimeMillis()-sentAt>1200) sentAt=0;
        travel.active=canTravel() && sentAt==0;
    }
    @Override protected void init() {
        String query=search==null?"":search.getValue();
        w=Math.min(760,width-16);h=Math.min(460,height-16);x=(width-w)/2;y=(height-h)/2;
        side=Math.max(120,Math.min(215,w/3));mx=x+side+16;my=y+66;mw=w-side-28;mh=h-120;
        all=new ArrayList<>(menu.getWaystones());
        all.sort(Comparator.comparing(a->fold(a.getEffectiveName().getString())));
        dimensions.clear();
        if(minecraft.level!=null) dimensions.add(minecraft.level.dimension());
        for(Waystone a:all) if(!dimensions.contains(a.getDimension())) dimensions.add(a.getDimension());
        if(dimension==null || !dimensions.contains(dimension)) dimension=dimensions.isEmpty()?Level.OVERWORLD:dimensions.getFirst();
        search=addRenderableWidget(new EditBox(font,x+12,y+36,side-12,20,Component.literal("Buscar destino")));
        search.setHint(Component.literal("Buscar destino…"));search.setMaxLength(80);search.setValue(query);
        search.setResponder(value->filter());
        dimButton=addRenderableWidget(new CopperButton(mx,y+36,mw-80,20,dimensionName(),b->{
            dimension=dimensions.get((dimensions.indexOf(dimension)+1)%dimensions.size());
            dimButton.setMessage(dimensionName());selected=null;requirement=null;filter();
        }));
        addRenderableWidget(new CopperButton(mx+mw-74,y+36,34,20,"−",b->zoom(0.7)));
        addRenderableWidget(new CopperButton(mx+mw-36,y+36,36,20,"+",b->zoom(1.4)));
        travel=addRenderableWidget(new CopperButton(x+w-112,y+h-31,100,20,"Viajar",b->{
            if(canTravel() && sentAt==0) {
                Balm.getNetworking().sendToServer(new SelectWaystoneMessage(selected.getWaystoneUid()));
                sentAt=System.currentTimeMillis();travel.active=false;
            }
        }));
        addRenderableWidget(new CopperButton(x+12,y+h-31,Math.min(100,side-16),20,"Menú clásico",b->{
            Routes.classicOnce=true;
            minecraft.setScreen(new WaystoneSelectionScreen(menu,minecraft.player.getInventory(),getTitle()));
        }));
        filter();travel.active=canTravel();setInitialFocus(search);
    }
    static String fold(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
    }
    private Component dimensionName() {
        String id=dimension.location().toString();
        return Component.literal(id.equals("minecraft:overworld")?"Superficie":id.equals("minecraft:the_nether")?"Nether":id.equals("minecraft:the_end")?"End":dimension.location().getPath());
    }
    private void filter() {
        String q=fold(search.getValue());
        filtered=all.stream().filter(a->a.getDimension().equals(dimension) && fold(a.getEffectiveName().getString()).contains(q)).toList();
        scroll=0;if(selected!=null && !filtered.contains(selected)){selected=null;requirement=null;}
        fit();
    }
    private void fit() {
        if(filtered.isEmpty()) {
            centerX=minecraft.player==null?0:minecraft.player.getX();centerZ=minecraft.player==null?0:minecraft.player.getZ();scale=0.5;
        } else {
            double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,minZ=Double.MAX_VALUE,maxZ=-Double.MAX_VALUE;
            for(Waystone a:filtered){minX=Math.min(minX,a.getPos().getX());maxX=Math.max(maxX,a.getPos().getX());minZ=Math.min(minZ,a.getPos().getZ());maxZ=Math.max(maxZ,a.getPos().getZ());}
            centerX=(minX+maxX)/2;centerZ=(minZ+maxZ)/2;
            scale=Math.max(0.000001,Math.min(1,Math.min((mw-40)/Math.max(96,maxX-minX),(mh-40)/Math.max(96,maxZ-minZ))));
        }
        terrainAt=0;
    }
    private void zoom(double factor) {scale=Math.max(0.000001,Math.min(4,scale*factor));terrainAt=0;}
    private void select(Waystone a) {
        selected=a;
        if(minecraft.player==null)return;
        var context=WaystonesAPI.createUnboundTeleportContext(minecraft.player,a).setFromWaystone(menu.getWaystoneFrom())
                .setWarpItem(menu.getWarpItem()).addFlags(menu.getFlags());
        requirement=WaystonesAPI.resolveRequirements(context);
        travel.active=canTravel();
    }
    private boolean canTravel() {
        return selected!=null && minecraft.player!=null && requirement!=null
                && (menu.getWaystoneFrom()==null || !menu.getWaystoneFrom().getWaystoneUid().equals(selected.getWaystoneUid()))
                && (minecraft.player.getAbilities().instabuild || requirement.canAfford(minecraft.player));
    }
    private int px(Waystone a){return mx+mw/2+(int)Math.round((a.getPos().getX()-centerX)*scale);}
    private int pz(Waystone a){return my+mh/2+(int)Math.round((a.getPos().getZ()-centerZ)*scale);}
    private int visibleRows(){return Math.max(1,(mh-18)/31);}
    private boolean overMap(double a,double b){return a>=mx&&a<mx+mw&&b>=my&&b<my+mh;}
    private void sampleTerrain() {
        if(System.currentTimeMillis()-lastSample<150 || System.currentTimeMillis()-terrainAt<1500)return;
        lastSample=System.currentTimeMillis();
        terrainAt=System.currentTimeMillis();cols=Math.max(1,(mw+5)/6);rows=Math.max(1,(mh+5)/6);terrain=new int[cols*rows];
        if(minecraft.level==null || !minecraft.level.dimension().equals(dimension))return;
        for(int rz=0;rz<rows;rz++)for(int cx=0;cx<cols;cx++){
            int bx=(int)Math.floor(centerX+(cx*6-mw/2)/scale),bz=(int)Math.floor(centerZ+(rz*6-mh/2)/scale);
            BlockPos base=new BlockPos(bx,0,bz);
            if(!minecraft.level.hasChunkAt(base))continue;
            int by=minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE,bx,bz)-1;
            BlockPos pos=new BlockPos(bx,by,bz);
            int c=minecraft.level.getBlockState(pos).getMapColor(minecraft.level,pos).col;
            terrain[rz*cols+cx]=0xFF000000|((c>>16&255)*6/10<<16)|((c>>8&255)*6/10<<8)|((c&255)*6/10);
        }
    }
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial) {}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        g.fill(0,0,width,height,0xCC090C10);g.fill(x+3,y+3,x+w+3,y+h+3,0x99000000);
        g.fill(x,y,x+w,y+h,BG);g.renderOutline(x,y,w,h,EDGE);g.fill(x,y,x+w,y+2,COPPER);
        g.drawString(font,"ASCENSION  /  RUTAS",x+12,y+13,COPPER,false);
        String total=all.size()+" destinos";g.drawString(font,total,x+w-12-font.width(total),y+13,MUTED,false);
        g.fill(x+8,my,x+side+4,my+mh,PANEL);g.fill(mx,my,mx+mw,my+mh,0xFF101B21);
        sampleTerrain();g.enableScissor(mx,my,mx+mw,my+mh);
        for(int rz=0;rz<rows;rz++)for(int cx=0;cx<cols;cx++)if(terrain[rz*cols+cx]!=0)g.fill(mx+cx*6,my+rz*6,mx+cx*6+6,my+rz*6+6,terrain[rz*cols+cx]);
        for(int gx=mx;gx<mx+mw;gx+=32)g.fill(gx,my,gx+1,my+mh,0x202F7380);
        for(int gz=my;gz<my+mh;gz+=32)g.fill(mx,gz,mx+mw,gz+1,0x202F7380);
        hovered=null;
        for(Waystone a:filtered){int ax=px(a),az=pz(a);boolean sel=a==selected;int color=sel?0xFFFFDAA2:COPPER;
            if(overMap(ax,az)){
                if(Math.abs(mouseX-ax)<8&&Math.abs(mouseY-az)<8)hovered=a;
                if(sel)g.renderOutline(ax-8,az-8,17,17,0x99E0A16C);
                g.fill(ax-4,az-5,ax+5,az+6,0xFF12171C);g.fill(ax-2,az-4,ax+3,az+5,color);g.fill(ax-4,az-2,ax+5,az+3,color);
                if(sel || filtered.size()<5){String label=font.plainSubstrByWidth(a.getEffectiveName().getString(),Math.max(10,mx+mw-ax-10));g.fill(ax+7,az-6,ax+10+font.width(label),az+6,0xBB111820);g.drawString(font,label,ax+9,az-4,TEXT,false);}
            }
        }
        if(minecraft.player!=null && minecraft.player.level().dimension().equals(dimension)){
            int ax=mx+mw/2+(int)((minecraft.player.getX()-centerX)*scale),az=my+mh/2+(int)((minecraft.player.getZ()-centerZ)*scale);
            g.fill(ax-2,az-2,ax+3,az+3,0xFFB1EDDF);
        }
        g.disableScissor();g.renderOutline(mx,my,mw,mh,EDGE);
        g.drawString(font,"N",mx+mw-13,my+6,COPPER,false);
        g.drawString(font,font.plainSubstrByWidth("Arrastra · rueda para ampliar",mw-14),mx+7,my+mh-12,MUTED,false);
        g.drawString(font,filtered.size()+" en "+dimensionName().getString(),x+14,my+6,MUTED,false);
        for(int i=0;i<visibleRows() && i+scroll<filtered.size();i++){
            Waystone a=filtered.get(i+scroll);int ry=my+18+i*31;boolean active=a==selected;
            g.fill(x+12,ry,x+side,ry+28,active?0xFF35403E:0xFF252E35);
            g.fill(x+12,ry,x+14,ry+28,active?COPPER:0xFF526067);
            g.drawString(font,font.plainSubstrByWidth(a.getEffectiveName().getString(),side-32),x+21,ry+5,active?COPPER:TEXT,false);
            g.drawString(font,font.plainSubstrByWidth(a.getPos().getX()+"  /  "+a.getPos().getZ(),side-32),x+21,ry+16,MUTED,false);
        }
        if(filtered.isEmpty())g.drawString(font,"Sin resultados",x+19,my+27,MUTED,false);
        if(filtered.size()>visibleRows())g.drawString(font,(scroll+1)+"–"+Math.min(filtered.size(),scroll+visibleRows())+" / "+filtered.size(),x+15,my+mh-11,MUTED,false);
        String status=selected==null?"Selecciona un destino en el mapa o la lista":selected.getEffectiveName().getString()+" · "+(canTravel()?"Listo para viajar":"Revisa los requisitos");
        g.drawString(font,font.plainSubstrByWidth(status,w-26),x+12,y+h-47,selected==null?MUTED:TEXT,false);
        super.render(g,mouseX,mouseY,partial);
        if(search.getValue().isEmpty())g.drawString(font,Component.literal("Buscar destino…"),search.getX()+7,search.getY()+6,MUTED,false);
        if(hovered!=null)g.renderTooltip(font,hovered.getEffectiveName(),mouseX,mouseY);
        else if(travel.isHovered() && requirement!=null){var tips=new ArrayList<Component>();requirement.appendHoverText(minecraft.player,tips);if(!tips.isEmpty())g.renderTooltip(font,tips,Optional.empty(),mouseX,mouseY);}
    }
    @Override public boolean mouseClicked(double a,double b,int button) {
        if(button==0 && overMap(a,b)){
            Waystone nearest=null;double best=100;
            for(Waystone s:filtered){double d=Math.pow(a-px(s),2)+Math.pow(b-pz(s),2);if(d<best){best=d;nearest=s;}}
            if(nearest!=null)select(nearest);return true;
        }
        if(button==0 && a>=x+12 && a<x+side && b>=my+18 && b<my+18+visibleRows()*31){int i=(int)(b-my-18)/31+scroll;if(i<filtered.size()){select(filtered.get(i));centerX=selected.getPos().getX();centerZ=selected.getPos().getZ();terrainAt=0;}return true;}
        return super.mouseClicked(a,b,button);
    }
    @Override public boolean mouseDragged(double a,double b,int button,double dx,double dy){
        if(button==0&&overMap(a,b)){centerX-=dx/scale;centerZ-=dy/scale;terrainAt=0;return true;}return super.mouseDragged(a,b,button,dx,dy);
    }
    @Override public boolean mouseScrolled(double a,double b,double dx,double dy){
        if(overMap(a,b)){zoom(dy>0?1.25:0.8);return true;}
        if(a>=x&&a<mx&&b>=my&&b<my+mh){scroll=Math.max(0,Math.min(Math.max(0,filtered.size()-visibleRows()),scroll-(int)Math.signum(dy)));return true;}
        return super.mouseScrolled(a,b,dx,dy);
    }
    private static final class CopperButton extends Button {
        CopperButton(int x,int y,int w,int h,String label,OnPress press){this(x,y,w,h,Component.literal(label),press);}
        CopperButton(int x,int y,int w,int h,Component label,OnPress press){super(x,y,w,h,label,press,DEFAULT_NARRATION);}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
            g.fill(getX(),getY(),getX()+width,getY()+height,!active?0xFF22282C:isHoveredOrFocused()?0xFF514235:0xFF302E2B);
            g.renderOutline(getX(),getY(),width,height,active?COPPER:EDGE);
            var f=Minecraft.getInstance().font;String label=f.plainSubstrByWidth(getMessage().getString(),width-8);
            g.drawString(f,label,getX()+(width-f.width(label))/2,getY()+(height-8)/2,active?TEXT:MUTED,false);
        }
    }
}



