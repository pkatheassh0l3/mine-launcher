package com.hearthbound.village;

import com.hearthbound.Hearthbound;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * All schematics Hearthbound knows about:
 * <ul>
 *   <li>datapacks: {@code data/<ns>/hearthbound/structures/**.nbt|.schem} → id {@code <ns>:<path>}</li>
 *   <li>the config folder: {@code config/hearthbound/structures/<culture>/<building>/*.nbt|.schem}
 *       → used automatically by that culture for that building (id {@code file:<culture>/<building>/<name>})</li>
 *   <li>any vanilla/mod structure template, e.g. {@code minecraft:village/plains/houses/plains_small_house_1},
 *       resolved on demand through the structure manager</li>
 * </ul>
 */
public final class Templates {
    private static Map<String, Template> loaded = Map.of();
    private static Map<String, List<String>> folder = Map.of();
    private static final Map<String, Template> ON_DEMAND = new HashMap<>();
    private static final String DIR = Hearthbound.MOD_ID + "/structures";

    private Templates() {}

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve("hearthbound").resolve("structures");
    }

    /** Called from the datapack reload. */
    public static void reload(ResourceManager rm) {
        Map<String, Template> map = new LinkedHashMap<>();
        int errors = 0;
        for (String ext : new String[]{".nbt", ".schem"}) {
            FileToIdConverter conv = new FileToIdConverter(DIR, ext);
            for (Map.Entry<ResourceLocation, Resource> e : conv.listMatchingResources(rm).entrySet()) {
                ResourceLocation id = conv.fileToId(e.getKey());
                try (InputStream in = e.getValue().open()) {
                    map.put(id.toString(), Template.read(id.toString(), in, e.getKey().getPath(), null));
                } catch (Exception ex) {
                    errors++;
                    Hearthbound.LOGGER.warn("Skipping schematic {}: {}", id, ex.getMessage());
                }
            }
        }
        Map<String, List<String>> byFolder = new HashMap<>();
        Path root = configDir();
        try {
            Files.createDirectories(root);
            writeReadme(root);
            try (Stream<Path> walk = Files.walk(root, 3)) {
                for (Path p : walk.toList()) {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!Files.isRegularFile(p) || !(name.endsWith(".nbt") || name.endsWith(".schem"))) continue;
                    Path rel = root.relativize(p);
                    if (rel.getNameCount() != 3) continue;
                    String culture = rel.getName(0).toString();
                    String building = rel.getName(1).toString();
                    String id = "file:" + culture + "/" + building + "/" + rel.getName(2);
                    try (InputStream in = Files.newInputStream(p)) {
                        map.put(id, Template.read(id, in, name, null));
                        byFolder.computeIfAbsent(culture + "/" + building, k -> new ArrayList<>()).add(id);
                    } catch (Exception ex) {
                        errors++;
                        Hearthbound.LOGGER.warn("Skipping schematic {}: {}", p, ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            Hearthbound.LOGGER.warn("Could not read {}: {}", root, ex.toString());
        }
        loaded = map;
        folder = byFolder;
        ON_DEMAND.clear();
        Hearthbound.LOGGER.info("Hearthbound loaded {} schematics ({} from the config folder, {} skipped)", map.size(),
                byFolder.values().stream().mapToInt(List::size).sum(), errors);
    }

    /** Schematics dropped in the config folder for a culture (by path, e.g. "valdoran") and building id path. */
    public static List<String> fromFolder(String culture, String building) {
        return folder.getOrDefault(culture + "/" + building, List.of());
    }

    public static Template get(ServerLevel level, String id) {
        if (id == null) return null;
        Template t = loaded.get(id);
        if (t != null) return t;
        if (ON_DEMAND.containsKey(id)) return ON_DEMAND.get(id);
        Template out = null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && level != null) {
            try {
                var st = level.getStructureManager().get(rl);
                if (st.isPresent()) out = Template.fromStructureTag(id, st.get().save(new CompoundTag()), null);
            } catch (Exception ex) {
                Hearthbound.LOGGER.warn("Could not use structure {}: {}", id, ex.getMessage());
            }
        }
        ON_DEMAND.put(id, out);
        return out;
    }

    public static int count() {
        return loaded.size();
    }

    private static void writeReadme(Path root) {
        Path f = root.resolve("LEEME_README.txt");
        if (Files.exists(f)) return;
        try {
            Files.writeString(f, """
                    Hearthbound - custom buildings / edificios propios
                    ==================================================

                    Drop schematics here and villages will build them.
                    Pon aqui tus schematics y las aldeas los construiran.

                    Layout / estructura:
                      config/hearthbound/structures/<culture>/<building>/<file>.nbt
                      config/hearthbound/structures/<culture>/<building>/<file>.schem

                    <culture>  = valdoran, sylvaran, durnhal, solarys, brumaverde, hrimfell (or your own)
                    <building> = hall, house, manor, farm, forge, market, tavern, shrine, watchtower, storehouse, mage_tower

                    Formats: vanilla / Create structure .nbt, WorldEdit / Sponge .schem (v2, v3). Max 48x48x48.
                    Front: auto (village jigsaw or doors). Force it with the file name: house_front-east.schem
                    Ground level: the block under the entrance door.

                    Run /reload after adding files. / Ejecuta /reload tras anadir archivos.
                    """);
        } catch (Exception ignored) {
        }
    }
}
