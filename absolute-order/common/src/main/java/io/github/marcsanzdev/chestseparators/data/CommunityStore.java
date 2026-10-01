package io.github.marcsanzdev.chestseparators.data;

import com.google.gson.Gson;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
import static io.github.marcsanzdev.chestseparators.data.CommunityData.*;

/** One server-world store. Author identity is provided exclusively by the server. */
public final class CommunityStore {
    private final Path root;
    private List<Entry> entries;
    private static final Gson JSON=new Gson();
    public CommunityStore(Path root) throws IOException {
        this.root=root; Files.createDirectories(root);
        Path file=root.resolve("community.json");
        entries=Files.exists(file)?new ArrayList<>(Arrays.asList(JSON.fromJson(Files.readString(file),Entry[].class))):new ArrayList<>();
    }
    public List<Meta> list(){return entries.stream().map(Entry::meta).toList();}
    public Entry get(UUID id){return entries.stream().filter(e->e.meta().id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("El preset ya no existe"));}
    public Meta publish(UUID owner,String author,String name,int size,int columns,Layout layout) throws IOException {
        name=name.strip().replaceAll("[\\p{Cntrl}§]", "");
        if(name.isBlank() || name.length()>64)throw new IllegalArgumentException("Escribe un nombre de 1 a 64 caracteres");
        if(entries.size()>=200 || entries.stream().filter(e->e.meta().owner().equals(owner)).count()>=40)throw new IllegalArgumentException("Límite de presets alcanzado (40 por jugador, 200 por servidor)");
        layout.validate(size,columns);
        var meta=new Meta(UUID.randomUUID(),owner,author,name,size,columns);
        var next=new ArrayList<>(entries);next.add(new Entry(meta,Base64.getEncoder().encodeToString(pack(layout))));
        atomic(root.resolve("community.json"),JSON.toJson(next).getBytes(StandardCharsets.UTF_8));entries=next;return meta;
    }
    public void delete(UUID id,UUID actor,boolean admin)throws IOException {
        var entry=get(id);
        if(!entry.meta().owner().equals(actor) && !admin)throw new IllegalArgumentException("Solo el autor o un administrador puede borrar este preset");
        var next=new ArrayList<>(entries);next.remove(entry);atomic(root.resolve("community.json"),JSON.toJson(next).getBytes(StandardCharsets.UTF_8));entries=next;
    }
    public Meta linked(String key) {return entries.stream().filter(e->key.equals(e.sourceKey())).map(Entry::meta).findFirst().orElse(null);}
    public Meta publishForChest(String key,UUID owner,String author,String name,int size,int columns,Layout layout)throws IOException {
        Meta existing=linked(key);
        if(existing!=null){updateLinked(key,size,columns,layout);requireName(key,false);return existing;}
        name=name.strip().replaceAll("[\\p{Cntrl}§]", "");
        if(name.isBlank() || name.length()>64)throw new IllegalArgumentException("Escribe un nombre de 1 a 64 caracteres");
        if(entries.size()>=200 || entries.stream().filter(e->e.meta().owner().equals(owner)).count()>=40)throw new IllegalArgumentException("Límite de presets alcanzado");
        layout.validate(size,columns);
        var meta=new Meta(UUID.randomUUID(),owner,author,name,size,columns);
        var next=new ArrayList<>(entries);next.add(new Entry(meta,Base64.getEncoder().encodeToString(pack(layout)),key));
        atomic(root.resolve("community.json"),JSON.toJson(next).getBytes(StandardCharsets.UTF_8));entries=next;requireName(key,false);return meta;
    }
    /** The source chest is authoritative; applying a copy elsewhere never attaches that copy. */
    public boolean updateLinked(String key,int size,int columns,Layout layout)throws IOException {
        var meta=linked(key);if(meta==null)return false;
        layout.validate(size,columns);var next=new ArrayList<>(entries);
        for(int i=0;i<next.size();i++)if(next.get(i).meta().id().equals(meta.id()))next.set(i,new Entry(new Meta(meta.id(),meta.owner(),meta.author(),meta.name(),size,columns),Base64.getEncoder().encodeToString(pack(layout)),key));
        atomic(root.resolve("community.json"),JSON.toJson(next).getBytes(StandardCharsets.UTF_8));entries=next;return true;
    }
    public void detach(String key)throws IOException {
        if(linked(key)!=null){var next=entries.stream().map(e->key.equals(e.sourceKey())?new Entry(e.meta(),e.encoded()):e).toList();atomic(root.resolve("community.json"),JSON.toJson(next).getBytes(StandardCharsets.UTF_8));entries=new ArrayList<>(next);}
        requireName(key,false);
    }
    private Path pendingPath(String key){return chestPath(key).resolveSibling(chestPath(key).getFileName()+".name-required");}
    public boolean needsName(String key){return Files.exists(pendingPath(key));}
    public void requireName(String key,boolean value)throws IOException {if(value)atomic(pendingPath(key),new byte[]{1});else Files.deleteIfExists(pendingPath(key));}
    private Path chestPath(String key){return root.resolve("chests").resolve(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))+".bin");}
    public Layout chest(String key)throws IOException {Path p=chestPath(key);return Files.exists(p)?unpack(Files.readAllBytes(p)):null;}
    public void chest(String key,Layout layout)throws IOException {atomic(chestPath(key),pack(layout));}
    private static void atomic(Path target,byte[] bytes)throws IOException {
        Files.createDirectories(target.getParent());Path tmp=Files.createTempFile(target.getParent(),"preset-",".tmp");
        try {Files.write(tmp,bytes);try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(tmp);}
    }
}
