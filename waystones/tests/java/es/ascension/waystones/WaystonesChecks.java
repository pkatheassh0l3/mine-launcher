package com.hearthbound.waystoneschecks;
import es.ascension.waystones.RoutesScreen;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.blay09.mods.waystones.config.*;
import net.blay09.mods.waystones.api.*;
import java.util.List;
@GameTestHolder("hearthbound") @PrefixGameTestTemplate(false)
public class WaystonesChecks {
 @GameTest(template="empty") public static void publicAndNoGeneration(GameTestHelper h) {
  var c=WaystonesConfig.getActive();
  check(c.general.defaultVisibility==DefaultWaystoneVisibility.GLOBAL,"Public by default");
  check(c.general.allowedVisibilities.size()==1 && c.general.allowedVisibilities.contains(WaystoneVisibility.GLOBAL),"Only public visibility offered");
  check(c.worldGen.chunksBetweenWildWaystones==0,"Wild generation disabled");
  check(c.worldGen.spawnInVillages==WaystonesConfigData.VillageWaystoneGeneration.DISABLED,"Village generation disabled");
  h.succeed();
 }
 @GameTest(template="empty") public static void copperRecipe(GameTestHelper h) {
  var holder=h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("waystones:waystone")).orElseThrow();
  check(holder.value() instanceof ShapedRecipe,"Recipe type");
  var recipe=(ShapedRecipe)holder.value();
  for(Item fuel:List.of(Items.COAL,Items.CHARCOAL)){
   var input=CraftingInput.of(3,3,List.of(new ItemStack(Items.COBBLESTONE),new ItemStack(Items.COPPER_INGOT),new ItemStack(Items.COBBLESTONE),new ItemStack(Items.COBBLESTONE),new ItemStack(fuel),new ItemStack(Items.COBBLESTONE),new ItemStack(Items.COBBLESTONE),new ItemStack(Items.COPPER_INGOT),new ItemStack(Items.COBBLESTONE)));
   check(recipe.matches(input,h.getLevel()),"Six cobblestone, two copper, one coal or charcoal");
   var out=recipe.assemble(input,h.getLevel().registryAccess());
   check(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(out.getItem()).toString().equals("waystones:waystone") && out.getCount()==1,"One waystone output");
  }
  h.succeed();
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

