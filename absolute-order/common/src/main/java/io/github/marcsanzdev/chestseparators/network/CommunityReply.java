package io.github.marcsanzdev.chestseparators.network;
import java.util.*;
import io.github.marcsanzdev.chestseparators.data.CommunityData;
import io.github.marcsanzdev.chestseparators.data.CommunityData.Meta;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CommunityReply(int kind,int menu,String message,List<Meta> entries,byte[] data,List<io.github.marcsanzdev.chestseparators.data.CommunityAuthors.Author> authors) implements CustomPacketPayload {
    public CommunityReply(int kind,int menu,String message,List<Meta> entries,byte[] data){this(kind,menu,message,entries,data,List.of());}
    public static final int LIST=0,STATE=1,NOTICE=2,NEED_NAME=3;
    public static final Type<CommunityReply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("chestseparators","community_reply_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CommunityReply> CODEC=StreamCodec.ofMember(CommunityReply::write,CommunityReply::new);
    private CommunityReply(RegistryFriendlyByteBuf b){this(b.readVarInt(),b.readVarInt(),b.readUtf(256),readEntries(b),b.readByteArray(CommunityData.MAX_BYTES),readAuthors(b));}
    private static List<Meta> readEntries(RegistryFriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>200)throw new IllegalArgumentException("Catalog too large");List<Meta> result=new ArrayList<>();for(int i=0;i<n;i++)result.add(new Meta(b.readUUID(),b.readUUID(),b.readUtf(64),b.readUtf(64),b.readVarInt(),b.readVarInt()));return result;}
    private void write(RegistryFriendlyByteBuf b){b.writeVarInt(kind);b.writeVarInt(menu);b.writeUtf(message,256);b.writeVarInt(entries.size());for(var m:entries){b.writeUUID(m.id());b.writeUUID(m.owner());b.writeUtf(m.author(),64);b.writeUtf(m.name(),64);b.writeVarInt(m.size());b.writeVarInt(m.columns());}b.writeByteArray(data);b.writeVarInt(authors.size());for(var a:authors){b.writeUUID(a.id());b.writeUtf(a.name(),64);}}
    private static List<io.github.marcsanzdev.chestseparators.data.CommunityAuthors.Author> readAuthors(RegistryFriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>2000)throw new IllegalArgumentException("Too many players");var result=new ArrayList<io.github.marcsanzdev.chestseparators.data.CommunityAuthors.Author>();for(int i=0;i<n;i++)result.add(new io.github.marcsanzdev.chestseparators.data.CommunityAuthors.Author(b.readUUID(),b.readUtf(64)));return result;}
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
