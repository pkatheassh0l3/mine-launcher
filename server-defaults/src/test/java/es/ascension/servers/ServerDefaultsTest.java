package es.ascension.servers;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.Arrays;

public final class ServerDefaultsTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ascension-server-test-");
        Path fresh = dir.resolve("new/servers.dat");
        check(ServerDefaults.ensure(fresh), "new installation");
        CompoundTag created = NbtIo.read(fresh);
        check(created.getList("servers", 10).size() == 1, "one default");
        check(created.getList("servers", 10).getCompound(0).getString("ip").equals(ServerDefaults.ADDRESS), "correct address");
        byte[] first = Files.readAllBytes(fresh);
        check(!ServerDefaults.ensure(fresh), "no duplicate on next launch");
        check(Arrays.equals(first, Files.readAllBytes(fresh)), "unchanged file on next launch");
        System.out.println("PASS new installation and repeated launch");

        Path existing = dir.resolve("existing/servers.dat");
        Files.createDirectories(existing.getParent());
        CompoundTag root = new CompoundTag();
        root.putString("customMetadata", "keep me");
        ListTag servers = new ListTag();
        CompoundTag personal = new CompoundTag();
        personal.putString("name", "Personal server");
        personal.putString("ip", "localhost:25566");
        personal.putString("icon", "personal-icon");
        servers.add(personal);
        root.put("servers", servers);
        NbtIo.write(root, existing);
        byte[] original = Files.readAllBytes(existing);
        check(ServerDefaults.ensure(existing), "existing list updated");
        CompoundTag updated = NbtIo.read(existing);
        check(updated.getList("servers", 10).size() == 2, "personal server retained");
        check(updated.getList("servers", 10).getCompound(0).equals(personal), "all personal entry fields retained");
        check(updated.getString("customMetadata").equals("keep me"), "unknown metadata retained");
        check(Arrays.equals(original, Files.readAllBytes(existing.resolveSibling("servers.dat.before-ascension"))), "backup preserved");
        System.out.println("PASS existing servers, metadata and backup");

        CompoundTag same = updated.getList("servers", 10).getCompound(1);
        same.putString("name", "My custom name");
        same.putString("ip", "RAILS-NEEDS.TUN.PLY.GG:25565");
        same.putBoolean("hidden", true);
        NbtIo.write(updated, existing);
        check(ServerDefaults.ensure(existing), "hidden entry made visible");
        updated = NbtIo.read(existing);
        check(updated.getList("servers", 10).size() == 2, "default port/case does not duplicate");
        check(updated.getList("servers", 10).getCompound(1).getString("name").equals("My custom name"), "custom server name preserved");
        check(!updated.getList("servers", 10).getCompound(1).getBoolean("hidden"), "visible server");
        System.out.println("PASS existing address, explicit port, case and hidden entries");

        Path corrupt = dir.resolve("broken.dat");
        byte[] bad = new byte[] {1,2,3};
        Files.write(corrupt, bad);
        boolean failed = false;
        try { ServerDefaults.ensure(corrupt); } catch (Exception e) { failed = true; }
        check(failed && Arrays.equals(bad, Files.readAllBytes(corrupt)), "corrupt files untouched");
        System.out.println("PASS malformed list remains untouched");
    }
}
