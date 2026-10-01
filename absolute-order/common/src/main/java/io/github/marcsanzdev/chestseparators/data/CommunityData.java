package io.github.marcsanzdev.chestseparators.data;

import io.github.marcsanzdev.chestseparators.network.GroupedWhitelistCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;

public final class CommunityData {
    public static final UUID NONE = new UUID(0,0);
    public static final int MAX_BYTES = 29000, MAX_SLOTS = 1024;
    public record Meta(UUID id, UUID owner, String author, String name, int size, int columns) {}
    public record Entry(Meta meta, String encoded, String sourceKey) {
        public Entry(Meta meta, String encoded) { this(meta, encoded, null); }
        public Layout layout() { return unpack(Base64.getDecoder().decode(encoded)); }
    }
    public record Layout(Map<Integer,int[]> visual, Map<Integer,SlotWhitelist> filters) {
        public static Layout empty() { return new Layout(Map.of(),Map.of()); }
        public void validate(int size, int columns) {
            if(size<1 || size>MAX_SLOTS || columns<1 || columns>size) throw new IllegalArgumentException("Tamaño de almacenamiento no válido");
            if(visual.size()>size || filters.size()>size) throw new IllegalArgumentException("Demasiadas ranuras");
            for(var e:visual.entrySet()) if(e.getKey()<0 || e.getKey()>=size || e.getValue()==null || e.getValue().length!=9) throw new IllegalArgumentException("Dibujo fuera del cofre");
            Set<SlotWhitelist> checked = new HashSet<>();
            for(var e:filters.entrySet()) {
                if(e.getKey()<0 || e.getKey()>=size || e.getValue()==null) throw new IllegalArgumentException("Filtro fuera del cofre");
                var rule=e.getValue();
                if(checked.add(rule)) {
                    if(rule.groupId()==null || rule.allowedItems().size()>4096 || rule.targetCount()<0) throw new IllegalArgumentException("Filtro no válido");
                    for(String id:rule.allowedItems()) if(id.length()>256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) throw new IllegalArgumentException("Objeto no válido");
                }
            }
            pack(this);
        }
    }
    public static byte[] pack(Layout layout) {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {
            b.writeVarInt(layout.visual().size());
            for(var e:layout.visual().entrySet()) { b.writeVarInt(e.getKey()); if(e.getValue().length!=9) throw new IllegalArgumentException("Dibujo no válido"); for(int v:e.getValue()) b.writeInt(v); }
            GroupedWhitelistCodec.write(b,layout.filters());
            if(b.readableBytes()>MAX_BYTES) throw new IllegalArgumentException("Preset demasiado grande para compartir");
            byte[] bytes=new byte[b.readableBytes()]; b.readBytes(bytes); return bytes;
        } finally { b.release(); }
    }
    public static Layout unpack(byte[] bytes) {
        if(bytes.length>MAX_BYTES) throw new IllegalArgumentException("Preset demasiado grande");
        var b=new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            int count=b.readVarInt(); if(count<0 || count>MAX_SLOTS) throw new IllegalArgumentException("Demasiadas ranuras");
            Map<Integer,int[]> visual=new HashMap<>();
            for(int i=0;i<count;i++){int slot=b.readVarInt();int[] v=new int[9];for(int j=0;j<9;j++)v[j]=b.readInt();if(visual.put(slot,v)!=null)throw new IllegalArgumentException("Ranura duplicada");}
            var filters=GroupedWhitelistCodec.read(b);
            if(b.readableBytes()!=0) throw new IllegalArgumentException("Datos sobrantes");
            return new Layout(visual,filters);
        } finally {b.release();}
    }
}
