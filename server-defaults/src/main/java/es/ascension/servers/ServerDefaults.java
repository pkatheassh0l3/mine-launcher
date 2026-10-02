package es.ascension.servers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import java.io.IOException;
import java.nio.file.*;

public final class ServerDefaults {
    public static final String ADDRESS = "rails-needs.tun.ply.gg";
    private ServerDefaults() {}

    public static boolean ensure(Path file) throws IOException {
        CompoundTag root = NbtIo.read(file);
        if (root == null) root = new CompoundTag();
        if (root.contains("servers") && !root.contains("servers", Tag.TAG_LIST)) {
            throw new IOException("Invalid server list; leaving the file unchanged");
        }
        ListTag servers = root.getList("servers", Tag.TAG_COMPOUND);
        if (root.contains("servers", Tag.TAG_LIST) && !((ListTag) root.get("servers")).isEmpty()
                && servers.isEmpty()) throw new IOException("Invalid server entries");
        boolean changed = false;
        boolean found = false;
        for (int i = 0; i < servers.size(); i++) {
            CompoundTag server = servers.getCompound(i);
            String ip = server.getString("ip").trim();
            if (ip.equalsIgnoreCase(ADDRESS) || ip.equalsIgnoreCase(ADDRESS + ":25565")) {
                found = true;
                if (server.getBoolean("hidden")) {
                    server.putBoolean("hidden", false);
                    changed = true;
                }
            }
        }
        if (!found) {
            CompoundTag server = new CompoundTag();
            server.putString("name", "Ascension");
            server.putString("ip", ADDRESS);
            server.putBoolean("hidden", false);
            servers.add(server);
            changed = true;
        }
        if (!changed) return false;
        root.put("servers", servers);
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "ascension-servers-", ".dat");
        try {
            NbtIo.write(root, temporary);
            Path backup = file.resolveSibling("servers.dat.before-ascension");
            if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }
}
