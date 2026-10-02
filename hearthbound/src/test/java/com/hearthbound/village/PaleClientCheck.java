package com.hearthbound.village;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="hearthbound",value=Dist.CLIENT)
public final class PaleClientCheck {
    private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("ascension.pale.clientCheck")) return;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(done || mc.getOverlay()!=null || !(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
        done=true;
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("palegardenbackport:creaking"));
        boolean renderer=false;
        for(var field:mc.getEntityRenderDispatcher().getClass().getDeclaredFields()) if(java.util.Map.class.isAssignableFrom(field.getType())) {
            field.setAccessible(true); var value=field.get(mc.getEntityRenderDispatcher());
            if(value instanceof java.util.Map<?,?> map && map.containsKey(type)) renderer=true;
        }
        if(!renderer) throw new AssertionError("Missing Creaking renderer");
        int count=0;
        for(var id:net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) if(id.getNamespace().equals("palegardenbackport")) {
            var stack=new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
            var model=mc.getItemRenderer().getModel(stack,null,null,0);
            if(model==mc.getModelManager().getMissingModel()) throw new AssertionError("Missing model "+id);
            count++;
        }
        System.out.println("PALE_CLIENT_OK: Creaking renderer and "+count+" item models");
        mc.stop();
    }
}
