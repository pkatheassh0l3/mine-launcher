package io.github.marcsanzdev.chestseparators.network;
import java.util.UUID;
import io.github.marcsanzdev.chestseparators.data.CommunityData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CommunityRequest(int action,int menu,BlockPos pos,UUID id,String name,int size,int columns,byte[] data) implements CustomPacketPayload {
    public static final int LIST=0,PUBLISH=1,DELETE=2,APPLY=3,FETCH=4,SYNC=5,PRESET=6,NAME=7;
    public static final Type<CommunityRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("chestseparators","community_request_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CommunityRequest> CODEC=StreamCodec.ofMember(CommunityRequest::write,CommunityRequest::new);
    private CommunityRequest(RegistryFriendlyByteBuf b){this(b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readUUID(),b.readUtf(64),b.readVarInt(),b.readVarInt(),b.readByteArray(CommunityData.MAX_BYTES));}
    private void write(RegistryFriendlyByteBuf b){b.writeVarInt(action);b.writeVarInt(menu);b.writeBlockPos(pos);b.writeUUID(id);b.writeUtf(name,64);b.writeVarInt(size);b.writeVarInt(columns);b.writeByteArray(data);}
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
