package com.hearthbound.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("hearthbound")
@PrefixGameTestTemplate(false)
public final class PaleGardenChecks {
    @GameTest(template="empty")
    public static void creakingVariantsAndRegistry(GameTestHelper h) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("ascension_pale_compat")) { h.succeed(); return; }
        var level=h.getLevel();
        h.assertTrue(level.getServer().getFunctions().get(ResourceLocation.parse("main:handler/player/triggers")).isPresent(), "Official settings menu loads");
        var id=ResourceLocation.parse("palegardenbackport:creaking");
        var entity=(LivingEntity) BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
        h.assertTrue(entity!=null && entity.getMaxHealth()>0, "Creaking has registered attributes");
        var p=h.absolutePos(new BlockPos(1,2,1));
        entity.moveTo(p.getX(),p.getY(),p.getZ());
        level.addFreshEntity(entity);
        var source=entity.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source,"function main:handler/ai/select_type");
        h.assertTrue(entity.getTags().contains("PGR"), "Variant function executes");
        double scale=entity.getAttributeValue(Attributes.SCALE);
        h.assertTrue(scale==0.7 || scale==1 || scale==1.5, "Valid variant scale");
        entity.discard();
        h.assertTrue(level.registryAccess().registryOrThrow(Registries.BIOME).containsKey(ResourceLocation.parse("palegardenbackport:pale_garden")), "Biome registered");
        var ids=BuiltInRegistries.ITEM.keySet().stream().filter(k->k.getNamespace().equals("palegardenbackport")).map(Object::toString).sorted().toList();
        java.nio.file.Files.writeString(java.nio.file.Path.of("pale-items.json"), new com.google.gson.Gson().toJson(ids));
        h.assertTrue(ids.contains("palegardenbackport:creaking_heart"),"Heart registered");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void tallTreesGenerate(GameTestHelper h) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("ascension_pale_compat")) { h.succeed(); return; }
        var level=h.getLevel();
        var p=h.absolutePos(new BlockPos(50,2,50));
        for(var q:BlockPos.betweenClosed(p.offset(-8,-1,-8),p.offset(8,35,8)))
            level.setBlock(q,q.getY()==p.getY()-1?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        var feature=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(ResourceLocation.parse("palegardenbackport:pale_oak"));
        h.assertTrue(feature!=null,"Tree feature present");
        boolean placed=feature.place(level,level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(12345),p);
        h.assertTrue(placed,"Tall tree actually generates");
        boolean tall=false;
        for(var q:BlockPos.betweenClosed(p.offset(-5,12,-5),p.offset(5,32,5)))
            if(BuiltInRegistries.BLOCK.getKey(level.getBlockState(q).getBlock()).toString().equals("palegardenbackport:pale_oak_log")) tall=true;
        h.assertTrue(tall,"Tree grows above twelve blocks");
        if (net.neoforged.fml.ModList.get().isLoaded("fallingtree")) {
            var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            player.setPos(p.getX()+2,p.getY(),p.getZ());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE));
            var mod=Class.forName("fr.rakambda.fallingtree.neoforge.FallingTree").getMethod("getMod").invoke(null);
            var handler=mod.getClass().getMethod("getTreeHandler").invoke(mod);
            var cut=p.above(3);
            h.assertTrue(level.getBlockState(cut).is(net.minecraft.tags.BlockTags.LOGS),"Cut starts at an actual pale log");
            var upperLogs=new java.util.ArrayList<BlockPos>();
            for(var q:BlockPos.betweenClosed(p.offset(-8,12,-8),p.offset(8,35,8)))
                if(level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) upperLogs.add(q.immutable());
            h.assertTrue(!upperLogs.isEmpty(),"Upper logs exist before felling");
            Object world=wrap("LevelWrapper",level), actor=wrap("ServerPlayerWrapper",player), pos=wrap("BlockPosWrapper",cut), state=wrap("BlockStateWrapper",level.getBlockState(cut));
            for(var m:handler.getClass().getMethods()) if(m.getName().equals("breakTree")) m.invoke(handler,false,world,actor,pos,state,null);
            h.assertTrue(upperLogs.stream().noneMatch(q->level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)),"FallingTree fells all upper logs of official tall tree");
        }
        h.succeed();
    }
    private static Object wrap(String name,Object raw) throws Exception {
        for(var c:Class.forName("fr.rakambda.fallingtree.neoforge.common.wrapper."+name).getConstructors())
            if(c.getParameterCount()==1 && c.getParameterTypes()[0].isInstance(raw)) return c.newInstance(raw);
        throw new IllegalStateException(name);
    }
}
