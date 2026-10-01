package io.github.marcsanzdev.chestseparators.data;
import java.util.*;
public final class CommunityAuthors {
    public record Author(UUID id,String name) {}
    public static List<Author> choices(List<CommunityData.Meta> entries,List<Author> online){
        Map<UUID,Author> result=new HashMap<>();
        for(var m:entries)result.put(m.owner(),new Author(m.owner(),m.author()));
        for(var player:online)result.put(player.id(),player);
        return result.values().stream().sorted(Comparator.comparing(Author::name,String.CASE_INSENSITIVE_ORDER).thenComparing(a->a.id().toString())).toList();
    }
    public static List<CommunityData.Meta> filter(List<CommunityData.Meta> entries,UUID author,int size,int columns){return entries.stream().filter(m->m.size()==size&&m.columns()==columns&&(author==null||author.equals(m.owner()))).toList();}
}
