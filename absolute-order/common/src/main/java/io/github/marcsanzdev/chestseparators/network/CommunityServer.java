package io.github.marcsanzdev.chestseparators.network;

import dev.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.data.*;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.compat.StorageCompatibility;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import java.util.*;
import static io.github.marcsanzdev.chestseparators.data.CommunityData.*;

public final class CommunityServer {
    private static final Map<MinecraftServer,CommunityStore> STORES=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> LAST_PUBLISH=new WeakHashMap<>();
    private static CommunityStore store(ServerPlayer p)throws java.io.IOException {
        var s=p.getServer();var result=STORES.get(s);
        if(result==null){result=new CommunityStore(s.getWorldPath(LevelResource.ROOT).resolve("data/absoluteorder"));STORES.put(s,result);}return result;
    }
    private record Target(String key,int size,int columns,Container container,StorageCompatibility.Access custom,Set<BlockPos> positions) {
        Map<Integer,SlotWhitelist> filters(){return custom!=null?custom.filters():((IWhitelistProvider)container).getWhitelists();}
        void filters(Map<Integer,SlotWhitelist> value){if(custom!=null)custom.filters(value);else{((IWhitelistProvider)container).setWhitelists(value);container.setChanged();}}
    }
    private static Target target(ServerPlayer p,BlockPos hint) {
        var menu=p.containerMenu;
        if(menu==p.inventoryMenu || !menu.stillValid(p))throw new IllegalArgumentException("Abre un cofre compatible");
        var custom=StorageCompatibility.get(menu);
        Container container=menu instanceof ChestMenu chest?chest.getContainer():menu.slots.stream().filter(s->!(s.container instanceof Inventory)).map(s->s.container).findFirst().orElse(null);
        if(custom==null && !(container instanceof IWhitelistProvider))throw new IllegalArgumentException("Almacenamiento no compatible con presets compartidos");
        String key; Set<BlockPos> positions=new HashSet<>();
        if(custom!=null)key="storage/"+custom.identity();
        else if(container instanceof Entity entity)key="entity/"+entity.getUUID();
        else {
            BlockEntity be=container instanceof BlockEntity b?b:null;
            if(be==null && container instanceof CompoundContainer compound) {
                var candidate=p.level().getBlockEntity(hint);
                if(candidate instanceof Container c && compound.contains(c))be=candidate;
            }
            if(be==null)throw new IllegalArgumentException("No se puede identificar este almacenamiento");
            BlockPos canonical=be.getBlockPos();positions.add(canonical);
            if(container instanceof CompoundContainer compound)for(var direction:net.minecraft.core.Direction.values()) {
                var other=p.level().getBlockEntity(be.getBlockPos().relative(direction));
                if(other instanceof Container c && compound.contains(c)){positions.add(other.getBlockPos());if(other.getBlockPos().compareTo(canonical)<0)canonical=other.getBlockPos();}
            }
            key=p.level().dimension().location()+"/block/"+canonical.asLong()+"/"+BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
        }
        Map<Integer,Set<Integer>> rows=new HashMap<>();
        for(var slot:menu.slots)if(custom!=null?custom.contains(slot):slot.container==container)rows.computeIfAbsent(slot.y,k->new HashSet<>()).add(slot.x);
        int size=custom!=null?custom.size():container.getContainerSize();
        int columns=rows.values().stream().mapToInt(Set::size).max().orElse(9);
        return new Target(key,size,columns,container,custom,positions);
    }
    private static void send(ServerPlayer p,CommunityReply reply){if(!p.getServer().getPlayerList().getPlayers().contains(p))return;if(NetworkManager.canPlayerReceive(p,CommunityReply.TYPE))NetworkManager.sendToPlayer(p,reply);}
    private static void list(ServerPlayer p,CommunityStore store){send(p,new CommunityReply(CommunityReply.LIST,p.containerMenu.containerId,"",store.list(),new byte[0],CommunityAuthors.choices(store.list(),p.getServer().getPlayerList().getPlayers().stream().map(player->new CommunityAuthors.Author(player.getUUID(),player.getGameProfile().getName())).toList())));}
    private static void askName(ServerPlayer p){send(p,new CommunityReply(CommunityReply.NEED_NAME,p.containerMenu.containerId,"Pon nombre al filtro para publicarlo",List.of(),new byte[0]));}
    private static void notice(ServerPlayer p,String text){send(p,new CommunityReply(CommunityReply.NOTICE,p.containerMenu.containerId,text,List.of(),new byte[0]));}
    private static void validate(Layout layout,Target target){
        layout.validate(target.size,target.columns);
        for(var rule:new HashSet<>(layout.filters().values()))for(String id:rule.allowedItems())if(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)))throw new IllegalArgumentException("El preset contiene un objeto no instalado");
    }
    private static void state(ServerPlayer p,Target target,Layout layout) {
        // Restrict filter synchronization to the actual open storage, never every player in its chunk.

        send(p,new CommunityReply(CommunityReply.STATE,p.containerMenu.containerId,"",List.of(),pack(layout)));
    }
    public static void handle(ServerPlayer p,CommunityRequest request) {
        try {
            if(request.menu()!=p.containerMenu.containerId)return;
            var store=store(p);
            if(request.action()==CommunityRequest.LIST){list(p,store);return;}
            if(request.action()==CommunityRequest.DELETE){store.delete(request.id(),p.getUUID(),p.hasPermissions(2));for(var viewer:p.getServer().getPlayerList().getPlayers())list(viewer,store);notice(p,"Preset eliminado");return;}
            var target=target(p,request.pos());
            if(request.action()==CommunityRequest.FETCH){
                var saved=store.chest(target.key);Map<Integer,int[]> visual=saved==null?new HashMap<>():saved.visual();
                if(saved==null)target.filters().forEach((slot,rule)->visual.put(slot,new int[]{0,0,0,0,0x38000000|(rule.groupId().hashCode()&0xFFFFFF),0,0,0,0}));
                // Container rules remain authoritative if another mod changed them.
                var current=new Layout(visual,target.filters());current.validate(target.size,target.columns);state(p,target,current);if(store.needsName(target.key))askName(p);return;
            }
            if(request.action()==CommunityRequest.NAME){
                var saved=store.chest(target.key);if(saved==null || (!store.needsName(target.key) && store.linked(target.key)==null))throw new IllegalArgumentException("No hay cambios pendientes de publicar");
                validate(saved,target);store.publishForChest(target.key,p.getUUID(),p.getGameProfile().getName(),request.name(),target.size,target.columns,saved);
                for(var viewer:p.getServer().getPlayerList().getPlayers())list(viewer,store);notice(p,"Preset publicado; los cambios siguientes se actualizarán automáticamente");return;
            }
            if(request.action()==CommunityRequest.PUBLISH){
                if(request.size()!=target.size || request.columns()!=target.columns)throw new IllegalArgumentException("El tamaño del cofre ha cambiado");
                long now=System.currentTimeMillis();if(now-LAST_PUBLISH.getOrDefault(p,0L)<1500)throw new IllegalArgumentException("Espera un momento antes de publicar otro preset");
                var layout=unpack(request.data());validate(layout,target);
                store.publishForChest(target.key,p.getUUID(),p.getGameProfile().getName(),request.name(),target.size,target.columns,layout);LAST_PUBLISH.put(p,now);
                for(var viewer:p.getServer().getPlayerList().getPlayers())list(viewer,store);notice(p,"Preset publicado para toda la comunidad");return;
            }
            Layout layout;
            if(request.action()==CommunityRequest.APPLY){var e=store.get(request.id());if(e.meta().size()!=target.size || e.meta().columns()!=target.columns)throw new IllegalArgumentException("Este preset necesita otro tamaño de almacenamiento");layout=e.layout();}
            else if(request.action()==CommunityRequest.SYNC || request.action()==CommunityRequest.PRESET)layout=unpack(request.data());
            else return;
            // Respect the existing editor lock for block chests.
            for(var pos:target.positions){var lock=io.github.marcsanzdev.chestseparators.ChestSeparatorsState.LOCKED_CHESTS.get(pos);if(lock!=null && !lock.equals(p.getUUID()))throw new IllegalArgumentException("Otro jugador está editando este cofre");}
            validate(layout,target);
            boolean filtersChanged=!target.filters().equals(layout.filters());
            var previous=store.chest(target.key);
            boolean visualsChanged=previous==null || previous.visual().size()!=layout.visual().size() || layout.visual().entrySet().stream().anyMatch(e->!Arrays.equals(e.getValue(),previous.visual().get(e.getKey())));
            store.chest(target.key,layout);target.filters(layout.filters());p.containerMenu.broadcastChanges();
            if(request.action()==CommunityRequest.APPLY || request.action()==CommunityRequest.PRESET){
                if(store.updateLinked(target.key,target.size,target.columns,layout))for(var viewer:p.getServer().getPlayerList().getPlayers())list(viewer,store);
                store.requireName(target.key,false);
            }
            else if(request.action()==CommunityRequest.SYNC && (filtersChanged || visualsChanged)){
                if(store.linked(target.key)!=null && store.updateLinked(target.key,target.size,target.columns,layout)){
                    for(var viewer:p.getServer().getPlayerList().getPlayers())list(viewer,store);
                    notice(p,"Preset de comunidad actualizado automáticamente");
                }else if(filtersChanged)store.requireName(target.key,true);
            }
            for(var viewer:p.getServer().getPlayerList().getPlayers()) {
                try{var other=target(viewer,request.pos());if(other.key.equals(target.key))state(viewer,other,layout);}catch(IllegalArgumentException ignored){}
            }
            if(request.action()==CommunityRequest.SYNC && store.needsName(target.key))askName(p);
            if(request.action()==CommunityRequest.APPLY)notice(p,"Preset aplicado al cofre para todos sus usuarios");
        } catch(IllegalArgumentException e){notice(p,e.getMessage()==null?"Preset no válido":e.getMessage());}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger("AbsoluteOrderCommunity").error("Cannot persist shared presets",e);notice(p,"No se pudo guardar el preset en el servidor");}
    }
}
