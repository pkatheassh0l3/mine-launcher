package es.ascension.pale;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.Attributes;
@Mod("ascension_pale_compat")
public final class PaleCompat {
    public PaleCompat(IEventBus bus) { if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER) bus.addListener(PaleCompat::attributes); bus.addListener(PaleCompat::packs); }
    private static void packs(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        if (event.getPackType() != net.minecraft.server.packs.PackType.SERVER_DATA) return;
        try {
            var original = net.neoforged.fml.ModList.get().getModFileById("mr_pale_gardenremastered").getFile();
            var root = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve(".ascension/pale-compat-v1");
            java.nio.file.Files.createDirectories(root);
            java.nio.file.Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"Pale Garden Remastered: 1.21.1 registry compatibility\"}}");
            var functions = original.findResource("data/main/function");
            try (var paths = java.nio.file.Files.walk(functions)) {
                for (var path : paths.filter(java.nio.file.Files::isRegularFile).toList()) {
                    var target = root.resolve("data/main/function/" + functions.relativize(path).toString());
                    java.nio.file.Files.createDirectories(target.getParent());
                    var lines = new java.util.ArrayList<String>();
                    for (String line : java.nio.file.Files.readAllLines(path)) {
                        line = line.replace("if biome ~ ~ ~ pale_garden", "if biome ~ ~ ~ palegardenbackport:pale_garden")
                            .replace("type=creaking", "type=palegardenbackport:creaking")
                            .replace("summon creaking", "summon palegardenbackport:creaking");
                        for (String attribute : java.util.List.of("scale", "attack_damage", "movement_speed", "max_health"))
                            line = line.replace("attribute @s " + attribute + " ", "attribute @s minecraft:generic." + attribute + " ");
                        // Modern SNBT text components became JSON components in this older command parser.
                        int text = line.indexOf(" run tellraw ");
                        if (line.startsWith("tellraw ")) text = 0;
                        if (text >= 0) {
                            int array = line.indexOf('[', text);
                            if (array >= 0) {
                                var textComponent = com.google.gson.JsonParser.parseString(line.substring(array));
                                String json = textComponent.toString();
                                line = line.substring(0,array) + json.replace("\"hover_event\"", "\"hoverEvent\"").replace("\"click_event\"", "\"clickEvent\"").replace("\"command\":", "\"value\":");
                            }
                        }
                        lines.add(line);
                    }
                    java.nio.file.Files.write(target, lines);
                }
            }
            // Activate the author's original tree definitions with the backport registry names.
            var trees = original.findResource("1.21.2/data/minecraft/worldgen/configured_feature");
            try (var paths = java.nio.file.Files.list(trees)) {
                for (var path : paths.filter(java.nio.file.Files::isRegularFile).toList()) {
                    String json = java.nio.file.Files.readString(path).replace("minecraft:pale_", "palegardenbackport:pale_")
                        .replace("minecraft:creaking_heart", "palegardenbackport:creaking_heart");
                    var target = root.resolve("data/palegardenbackport/worldgen/configured_feature/" + path.getFileName());
                    java.nio.file.Files.createDirectories(target.getParent());
                    java.nio.file.Files.writeString(target, json);
                }
            }
            var pack = net.minecraft.server.packs.repository.Pack.readMetaAndCreate(
                new net.minecraft.server.packs.PackLocationInfo("ascension/pale-registry-compat", net.minecraft.network.chat.Component.literal("Pale Garden Remastered · 1.21.1"), net.minecraft.server.packs.repository.PackSource.BUILT_IN, java.util.Optional.empty()),
                net.minecraft.server.packs.repository.BuiltInPackSource.fromName(info -> new net.minecraft.server.packs.PathPackResources(info,root)),
                event.getPackType(), new net.minecraft.server.packs.PackSelectionConfig(true, net.minecraft.server.packs.repository.Pack.Position.TOP, false));
            if (pack == null) throw new IllegalStateException("Compatibility pack could not be loaded");
            event.addRepositorySource(consumer -> consumer.accept(pack));
        } catch (Exception ex) { throw new IllegalStateException("Unable to adapt the official Pale Garden Remastered for 1.21.1", ex); }
    }
    @SuppressWarnings("unchecked")
    private static void attributes(EntityAttributeCreationEvent event) {
        var id = ResourceLocation.parse("palegardenbackport:creaking");
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            var type = (EntityType<? extends LivingEntity>) BuiltInRegistries.ENTITY_TYPE.get(id);
            try {
                var builder = (net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder) Class.forName("com.eagle.palegarden.entity.CreakingEntity").getMethod("createAttributes").invoke(null);
                event.put(type, builder.build());
            } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
        }
    }
}
