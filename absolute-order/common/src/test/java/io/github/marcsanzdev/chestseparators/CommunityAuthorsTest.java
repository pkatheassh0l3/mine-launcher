package io.github.marcsanzdev.chestseparators;
import io.github.marcsanzdev.chestseparators.data.*;
import io.github.marcsanzdev.chestseparators.network.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CommunityAuthorsTest {
    @Test void listsOnlinePlayersAndOfflineAuthorsWithoutDuplicatingAnIdentity(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        var entries=List.of(new CommunityData.Meta(UUID.randomUUID(),a,"OldName","A",27,9),new CommunityData.Meta(UUID.randomUUID(),c,"Offline","C",27,9));
        var choices=CommunityAuthors.choices(entries,List.of(new CommunityAuthors.Author(a,"NewName"),new CommunityAuthors.Author(b,"Online")));
        assertEquals(3,choices.size());assertTrue(choices.contains(new CommunityAuthors.Author(a,"NewName")));assertTrue(choices.contains(new CommunityAuthors.Author(c,"Offline")));
        assertEquals(List.of(entries.get(0)),CommunityAuthors.filter(entries,a,27,9));assertEquals(entries,CommunityAuthors.filter(entries,null,27,9));assertTrue(CommunityAuthors.filter(entries,b,27,9).isEmpty());assertTrue(CommunityAuthors.filter(entries,a,54,9).isEmpty());
    }
    @Test void serverRosterSurvivesNetworkRoundTrip(){
        var author=new CommunityAuthors.Author(UUID.randomUUID(),"Jugador");
        var reply=new CommunityReply(CommunityReply.LIST,7,"",List.of(),new byte[0],List.of(author));
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try{CommunityReply.CODEC.encode(buf,reply);var decoded=CommunityReply.CODEC.decode(buf);assertEquals(List.of(author),decoded.authors());assertEquals(7,decoded.menu());assertEquals(0,buf.readableBytes());}finally{buf.release();}
    }
}
