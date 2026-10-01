package io.github.marcsanzdev.chestseparators.client.ui;

import java.util.*;
import dev.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.network.*;
import io.github.marcsanzdev.chestseparators.data.*;
import io.github.marcsanzdev.chestseparators.compat.StorageCompatibility;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;

public final class CommunityClient {
    public static List<CommunityData.Meta> entries=List.of();
    public static String status="";
    public static List<CommunityAuthors.Author> authors=List.of();
    private static Object connection;
    public static boolean applyingPreset;
    public static boolean available(){return Minecraft.getInstance().getConnection()!=null && NetworkManager.canServerReceive(CommunityRequest.TYPE);}
    public static void reset(){entries=List.of();authors=List.of();status=available()?"Cargando…":"El servidor necesita Absolute Order ascension.7";connection=Minecraft.getInstance().getConnection();}
    public static void request(int action,UUID id,String name){
        var editor=ChestSeparatorsEditor.getInstance();var player=Minecraft.getInstance().player;
        if(editor==null || player==null || !available())return;
        if(connection!=Minecraft.getInstance().getConnection()){reset();}
        if(action==CommunityRequest.SYNC && applyingPreset)action=CommunityRequest.PRESET;
        try {
            byte[] bytes=new byte[0];
            if(action==CommunityRequest.PUBLISH || action==CommunityRequest.SYNC || action==CommunityRequest.PRESET){var manager=ChestConfigManager.getInstance();var layout=new CommunityData.Layout(manager.chestOnlyVisual(),manager.chestOnlyWhitelists());layout.validate(editor.chestPresetSize(),ChestSeparatorsEditor.storageColumns());bytes=CommunityData.pack(layout);}
            NetworkManager.sendToServer(new CommunityRequest(action,player.containerMenu.containerId,editor.session.currentChestPos==null?BlockPos.ZERO:editor.session.currentChestPos,id,name,editor.chestPresetSize(),ChestSeparatorsEditor.storageColumns(),bytes));
        } catch(IllegalArgumentException e){status=e.getMessage();player.displayClientMessage(Component.literal(status),true);}
    }
    public static void receive(CommunityReply reply){
        var mc=Minecraft.getInstance();if(connection!=mc.getConnection()){reset();}
        if(reply.kind()==CommunityReply.LIST){entries=List.copyOf(reply.entries());authors=List.copyOf(reply.authors());status="";return;}
        if(reply.kind()==CommunityReply.NOTICE){status=reply.message();if(mc.player!=null)mc.player.displayClientMessage(Component.literal(status),true);return;}
        var editor=ChestSeparatorsEditor.getInstance();
        if(mc.player==null || editor==null || mc.player.containerMenu.containerId!=reply.menu())return;
        if(reply.kind()==CommunityReply.NEED_NAME){CommunityNamePrompt.open(reply.menu());return;}
        var layout=CommunityData.unpack(reply.data());layout.validate(editor.chestPresetSize(),ChestSeparatorsEditor.storageColumns());
        ChestConfigManager.getInstance().applySharedLayout(layout);
        var custom=StorageCompatibility.get(mc.player.containerMenu);
        if(custom!=null)custom.filters(layout.filters());
        else for(var slot:mc.player.containerMenu.slots)if(!(slot.container instanceof Inventory) && slot.container instanceof IWhitelistProvider provider){provider.setWhitelists(layout.filters());break;}
    }
}
