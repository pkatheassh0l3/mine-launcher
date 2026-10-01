package io.github.marcsanzdev.chestseparators;
import io.github.marcsanzdev.chestseparators.data.*;
import io.github.marcsanzdev.chestseparators.data.CommunityData.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityStoreTest {
    @Test void namedChestUpdatesSamePublicationAndKeepsOriginalAuthor()throws Exception {
        var store=new CommunityStore(dir);UUID owner=UUID.randomUUID();var original=layout();
        store.requireName("chest-a",true);assertTrue(new CommunityStore(dir).needsName("chest-a"));
        var meta=store.publishForChest("chest-a",owner,"Alex","Mi hierro",27,9,original);
        assertFalse(store.needsName("chest-a"));var revised=layout();
        assertTrue(store.updateLinked("chest-a",27,9,revised));
        var restarted=new CommunityStore(dir);assertEquals(1,restarted.list().size());assertEquals(meta,restarted.linked("chest-a"));
        assertEquals(revised.filters(),restarted.get(meta.id()).layout().filters());
        var duplicate=restarted.publishForChest("chest-a",UUID.randomUUID(),"Other","Ignored",27,9,original);
        assertEquals(meta,duplicate);assertEquals(1,restarted.list().size());assertEquals("Alex",restarted.list().getFirst().author());
    }
    @Test void applyingACopyDoesNotOverwriteItsSourceAndDetachPreservesPublishedSnapshot()throws Exception {
        var store=new CommunityStore(dir);var meta=store.publishForChest("source",UUID.randomUUID(),"Alex","Original",27,9,layout());
        assertFalse(store.updateLinked("another-chest",27,9,layout()));
        store.detach("source");assertNull(new CommunityStore(dir).linked("source"));assertEquals(meta,store.get(meta.id()).meta());
        assertFalse(store.updateLinked("source",27,9,layout()));
    }
    @TempDir Path dir;
    private Layout layout(){return new Layout(Map.of(0,new int[]{0,0,0,0,0x3867B7DC,0,0,0,0}),Map.of(0,new SlotWhitelist(UUID.randomUUID(),List.of("minecraft:iron_ingot"),true,true,true,0)));}
    @Test void persistsAttributionFiltersAndVisualsAndIsolatesWorlds()throws Exception {
        UUID owner=UUID.randomUUID();var original=layout();var first=new CommunityStore(dir.resolve("world-a"));
        var meta=first.publish(owner,"Alex","Hierro",27,9,original);
        var reopened=new CommunityStore(dir.resolve("world-a"));var loaded=reopened.get(meta.id());
        assertEquals(owner,loaded.meta().owner());assertEquals("Alex",loaded.meta().author());assertEquals("Hierro",loaded.meta().name());
        assertEquals(original.filters(),loaded.layout().filters());assertArrayEquals(original.visual().get(0),loaded.layout().visual().get(0));
        assertTrue(new CommunityStore(dir.resolve("world-b")).list().isEmpty());
        first.chest("dimension/chest/1",original);assertEquals(original.filters(),reopened.chest("dimension/chest/1").filters());assertNull(reopened.chest("dimension/chest/2"));
    }
    @Test void anotherPlayerCannotDeleteButAuthorOrAdminCan()throws Exception {
        var store=new CommunityStore(dir);UUID author=UUID.randomUUID(),other=UUID.randomUUID();
        var meta=store.publish(author,"Alex","Mi cofre",27,9,layout());
        assertThrows(IllegalArgumentException.class,()->store.delete(meta.id(),other,false));assertEquals(1,new CommunityStore(dir).list().size());
        store.delete(meta.id(),other,true);assertTrue(new CommunityStore(dir).list().isEmpty());
        var second=store.publish(author,"Alex","Otro",27,9,layout());store.delete(second.id(),author,false);assertTrue(store.list().isEmpty());
    }
    @Test void rejectsOutOfBoundsNamesAndOversizedPayloads()throws Exception {
        var store=new CommunityStore(dir);UUID owner=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->store.publish(owner,"Alex"," ",27,9,layout()));
        var bad=new Layout(Map.of(27,new int[9]),Map.of());assertThrows(IllegalArgumentException.class,()->bad.validate(27,9));
        var invalid=new Layout(Map.of(),Map.of(-1,new SlotWhitelist(owner,List.of("minecraft:iron_ingot"),true,true,true,0)));assertThrows(IllegalArgumentException.class,()->invalid.validate(27,9));
        assertThrows(IllegalArgumentException.class,()->CommunityData.unpack(new byte[CommunityData.MAX_BYTES+1]));
        assertTrue(store.list().isEmpty());
    }
    @Test void groupedRoundTripKeepsLargePresetsWithinPacketLimit() {
        var entry=new StoragePresetLibrary.Entry("test","test",java.util.stream.IntStream.range(0,500).mapToObj(i->"example:item_"+i).toList());
        var preview=StoragePresetLibrary.preview(entry,144);var original=new Layout(preview.visual(),preview.filters());original.validate(144,12);
        byte[] bytes=CommunityData.pack(original);assertTrue(bytes.length<29000);var result=CommunityData.unpack(bytes);
        assertEquals(original.filters(),result.filters());assertArrayEquals(original.visual().get(143),result.visual().get(143));
    }
}
