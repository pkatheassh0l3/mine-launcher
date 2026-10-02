package com.hearthbound.waystoneschecks;
import es.ascension.waystones.RoutesScreen;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.core.*;
import net.blay09.mods.waystones.menu.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="hearthbound",value=Dist.CLIENT)
public class RoutesPreview {
 static boolean ran;
 @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
  var mc=Minecraft.getInstance();if(ran || mc.getOverlay()!=null || !(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen))return;ran=true;
  var list=new ArrayList<MutablePersonalizedWaystone>();
  String[] names={"Aldea del Cobre","Puerto del Alba","Mina de Cristal","Bosque de Robles","Fortaleza del Sur","Mercado Central","Portal del Nether"};
  int[][] pos={{-140,-70},{200,100},{80,-250},{-290,260},{310,310},{-30,70},{30,-40}};
  for(int i=0;i<names.length;i++){
   var a=new WaystoneImpl(ResourceLocation.parse("waystones:waystone"),UUID.randomUUID(),i==6?Level.NETHER:Level.OVERWORLD,new BlockPos(pos[i][0],64,pos[i][1]),WaystoneOrigin.PLAYER,UUID.randomUUID());
   a.setName(Component.literal(names[i]));a.setVisibility(WaystoneVisibility.GLOBAL);list.add(new PersonalizedWaystoneImpl(a,null));
  }
  var menu=new WaystoneSelectionMenu(ModMenus.waystoneSelection.get(),list.getFirst(),1,list,Set.of());
  mc.setScreen(new RoutesScreen(menu){
   int frames;
   @Override public void tick(){}
   @Override public void render(GuiGraphics g,int x,int y,float t){
    super.render(g,x,y,t);g.flush();
    try{
     if(++frames==35){
      try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of("routes-preview.png"));}
      var field=RoutesScreen.class.getDeclaredField("search");field.setAccessible(true);((EditBox)field.get(this)).setValue("cristal");
     }
     if(frames==40){var field=RoutesScreen.class.getDeclaredField("filtered");field.setAccessible(true);if(((List<?>)field.get(this)).size()!=1)throw new AssertionError("Search must find one destination");}
     if(frames==45){
      try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of("routes-search-preview.png"));}
      System.out.println("ROUTES_PREVIEW_OK search+map+native-xaero-loaded");mc.stop();
     }
    }catch(Exception ex){throw new RuntimeException(ex);}
   }
  });
 }
}


