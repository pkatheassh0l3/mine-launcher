package com.hearthbound.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthbound.Hearthbound;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;

import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Datapack content: cultures, buildings and contract templates. Server side only. */
public final class HBData {
    private static final Gson GSON = new Gson();

    private static Map<ResourceLocation, Culture> cultures = Map.of();
    private static Map<ResourceLocation, BuildingDef> buildings = Map.of();
    private static Map<ResourceLocation, ContractTemplate> contracts = Map.of();
    private static Map<ResourceLocation, Persona> personas = Map.of();
    /** Number of trade origins per profession id (texts: hearthbound.origin.<role>.<n>). */
    private static Map<String, Integer> origins = Map.of();
    private static Map<ResourceLocation, ProjectDef> projects = Map.of();

    public static Collection<ProjectDef> projects() {
        return projects.values();
    }

    public static ProjectDef project(String id) {
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        return rl == null ? null : projects.get(rl);
    }

    public static int origins(String role) {
        return origins.getOrDefault(role, 0);
    }

    private HBData() {}

    public static Collection<Culture> cultures() {
        return cultures.values();
    }

    public static Culture culture(ResourceLocation id) {
        Culture c = cultures.get(id);
        if (c == null && !cultures.isEmpty()) c = cultures.values().iterator().next();
        return c;
    }

    public static Map<ResourceLocation, Culture> cultureMap() {
        return Collections.unmodifiableMap(cultures);
    }

    public static BuildingDef building(ResourceLocation id) {
        return buildings.get(id);
    }

    public static Collection<BuildingDef> buildings() {
        return buildings.values();
    }

    public static Collection<ContractTemplate> contracts() {
        return contracts.values();
    }

    public static ContractTemplate contract(ResourceLocation id) {
        return contracts.get(id);
    }

    public static Collection<Persona> personas() {
        return personas.values();
    }

    public static Persona persona(ResourceLocation id) {
        return id == null ? null : personas.get(id);
    }

    /** Honors {@code "required_mods"}, {@code "incompatible_mods"} and {@code "disabled"} in any file. */
    static boolean active(JsonObject o) {
        if (Json.bool(o, "disabled", false)) return false;
        for (String m : Json.strings(o, "required_mods")) if (!ModList.get().isLoaded(m)) return false;
        for (String m : Json.strings(o, "incompatible_mods")) if (ModList.get().isLoaded(m)) return false;
        return true;
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<String, Map<ResourceLocation, JsonObject>>> {
        private static final String[] DIRS = {"cultures", "buildings", "contracts", "personas", "origins", "projects"};

        @Override
        protected Map<String, Map<ResourceLocation, JsonObject>> prepare(ResourceManager rm, ProfilerFiller profiler) {
            Map<String, Map<ResourceLocation, JsonObject>> out = new LinkedHashMap<>();
            for (String dir : DIRS) {
                Map<ResourceLocation, JsonObject> files = new LinkedHashMap<>();
                FileToIdConverter conv = FileToIdConverter.json(Hearthbound.MOD_ID + "/" + dir);
                for (Map.Entry<ResourceLocation, Resource> e : conv.listMatchingResources(rm).entrySet()) {
                    ResourceLocation id = conv.fileToId(e.getKey());
                    try (Reader r = e.getValue().openAsReader()) {
                        JsonElement el = GsonHelper.fromJson(GSON, r, JsonElement.class);
                        if (el != null && el.isJsonObject()) files.put(id, el.getAsJsonObject());
                    } catch (Exception ex) {
                        Hearthbound.LOGGER.error("Could not read {} {}: {}", dir, id, ex.toString());
                    }
                }
                out.put(dir, files);
            }
            com.hearthbound.village.Templates.reload(rm);
            return out;
        }

        @Override
        protected void apply(Map<String, Map<ResourceLocation, JsonObject>> data, ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, Culture> c = new LinkedHashMap<>();
            Map<ResourceLocation, BuildingDef> b = new LinkedHashMap<>();
            Map<ResourceLocation, ContractTemplate> k = new LinkedHashMap<>();
            data.getOrDefault("cultures", Map.of()).forEach((id, j) -> {
                if (!active(j)) return;
                try {
                    c.put(id, new Culture(id, j));
                } catch (Exception e) {
                    Hearthbound.LOGGER.error("Bad culture {}: {}", id, e.toString());
                }
            });
            data.getOrDefault("buildings", Map.of()).forEach((id, j) -> {
                if (!active(j)) return;
                try {
                    b.put(id, new BuildingDef(id, j));
                } catch (Exception e) {
                    Hearthbound.LOGGER.error("Bad building {}: {}", id, e.toString());
                }
            });
            data.getOrDefault("contracts", Map.of()).forEach((id, j) -> {
                if (!active(j)) return;
                try {
                    k.put(id, new ContractTemplate(id, j));
                } catch (Exception e) {
                    Hearthbound.LOGGER.error("Bad contract {}: {}", id, e.toString());
                }
            });
            Map<ResourceLocation, Persona> ps = new LinkedHashMap<>();
            data.getOrDefault("personas", Map.of()).forEach((id, j) -> {
                if (!active(j)) return;
                try {
                    ps.put(id, new Persona(id, j));
                } catch (Exception e) {
                    Hearthbound.LOGGER.error("Bad persona {}: {}", id, e.toString());
                }
            });
            personas = ps;
            Map<String, Integer> os = new java.util.HashMap<>();
            data.getOrDefault("origins", Map.of()).forEach((id, j) -> {
                if (!active(j)) return;
                os.merge(id.getPath(), Math.max(0, Json.integer(j, "count", 0)), Math::max);
            });
            origins = os;
            java.util.List<ProjectDef> pd = new java.util.ArrayList<>();
            data.getOrDefault("projects", Map.of()).forEach((id, j) -> {
                if (!active(j)) return;
                try {
                    ProjectDef def = new ProjectDef(id, j);
                    if (!def.levels.isEmpty()) pd.add(def);
                } catch (Exception e) {
                    Hearthbound.LOGGER.error("Bad project {}: {}", id, e.toString());
                }
            });
            pd.sort(java.util.Comparator.comparingInt((ProjectDef d) -> d.sort).thenComparing(d -> d.id.toString()));
            Map<ResourceLocation, ProjectDef> pm = new LinkedHashMap<>();
            for (ProjectDef d : pd) pm.put(d.id, d);
            projects = pm;
            cultures = c;
            buildings = b;
            contracts = k;
            Hearthbound.LOGGER.info("Hearthbound loaded {} cultures, {} buildings, {} contracts and {} personas", c.size(), b.size(), k.size(), ps.size());
        }
    }
}
