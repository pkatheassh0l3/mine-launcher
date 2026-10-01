package io.github.marcsanzdev.chestseparators.neoforge;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import io.github.marcsanzdev.chestseparators.data.*;
import java.util.*;

/** Included only with -PstorageValidation, never in the delivered jar. */
@EventBusSubscriber(modid = "chestseparators")
public final class StorageRuntimeValidation {
    private static boolean passed;
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        System.exit(passed ? 0 : 1);
    }
    @SubscribeEvent public static void validate(ServerStartedEvent event) {
        try {
            StoragePresetLibrary.refresh();
            Set<String> covered = new HashSet<>();
            for (int i = 46; i < 46 + StoragePresetLibrary.count(); i++) {
                var entry = StoragePresetLibrary.get(i);
                var preview = StoragePresetLibrary.preview(entry, 54);
                for (int slot = 0; slot < 54; slot++) if (!preview.filters().get(slot).allowedItems().equals(entry.rows().get((slot / entry.columns()) % entry.rows().size()))) throw new AssertionError("Row filter mismatch");
                for (String id : entry.items()) if (!covered.add(id)) throw new AssertionError("Duplicate item: " + id);
                if (entry.items().isEmpty() || entry.items().stream().mapToInt(id -> id.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 5).sum() > FunctionalPresets.MAX_ITEM_BYTES) throw new AssertionError("Invalid catalog partition");
            }
            for (var item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR && !(item instanceof SpawnEggItem) && SurvivalRows.eligible(new FunctionalPresets.ItemInfo(BuiltInRegistries.ITEM.getKey(item).toString(), item.builtInRegistryHolder().tags().map(t -> t.location().toString()).collect(java.util.stream.Collectors.toSet()), FunctionalPresets.Kind.OTHER)) && !covered.contains(BuiltInRegistries.ITEM.getKey(item).toString()))
                    throw new AssertionError("Uncovered item " + item);
            }
            ItemStack backpack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
            var wrapper = net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(backpack);
            wrapper.setContentsUuid(UUID.randomUUID());
            wrapper.onInit(event.getServer().overworld());
            var rule = new SlotWhitelist(UUID.randomUUID(), List.of("minecraft:diamond"), true, true, true, 0);
            SophisticatedCompatibility.write(wrapper, Map.of(0, rule));
            if (!SophisticatedCompatibility.read(wrapper).equals(Map.of(0, rule))) throw new AssertionError("Rule persistence");
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(event.getServer().overworld());
            if (io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(player.inventoryMenu) != null)
                throw new AssertionError("Player inventory must not be a storage target");
            var slot = new net.p3pp3rf1y.sophisticatedcore.common.gui.StorageInventorySlot(false, wrapper, 0, player);
            if (slot.mayPlace(new ItemStack(Items.DIRT))) throw new AssertionError("Manual filter accepted dirt");
            if (!slot.mayPlace(new ItemStack(Items.DIAMOND))) throw new AssertionError("Manual filter rejected diamond");
            var inventory = wrapper.getInventoryHandler();
            if (inventory.insertItem(0, new ItemStack(Items.DIRT), false).isEmpty()) throw new AssertionError("Wrong item accepted");
            if (!inventory.getStackInSlot(0).isEmpty()) throw new AssertionError("Rejected item changed inventory");
            if (!inventory.insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty()) throw new AssertionError("Allowed item rejected");
            if (!inventory.getStackInSlot(0).is(Items.DIAMOND)) throw new AssertionError("Allowed item missing");
            var same = new net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper(backpack);
            if (!SophisticatedCompatibility.read(same).equals(Map.of(0, rule))) throw new AssertionError("Reopened storage lost rules");
            var level = event.getServer().overworld();
            var author = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PresetAuthor"));
            var otherPlayer = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PresetReader"));
            var firstPos = new net.minecraft.core.BlockPos(5, -59, 5);
            var secondPos = new net.minecraft.core.BlockPos(8, -59, 5);
            level.setBlockAndUpdate(firstPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(secondPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            var firstChest = (net.minecraft.world.Container) level.getBlockEntity(firstPos);
            var secondChest = (net.minecraft.world.Container) level.getBlockEntity(secondPos);
            author.setPos(5.5,-58,5.5); otherPlayer.setPos(8.5,-58,5.5);
            author.containerMenu = net.minecraft.world.inventory.ChestMenu.threeRows(21, author.getInventory(), firstChest);
            otherPlayer.containerMenu = net.minecraft.world.inventory.ChestMenu.threeRows(22, otherPlayer.getInventory(), secondChest);
            var shared = new CommunityData.Layout(Map.of(0,new int[]{0,0,0,0,0x3867B7DC,0,0,0,0}),Map.of(0,rule));
            var sharedBytes = CommunityData.pack(shared);
            io.github.marcsanzdev.chestseparators.network.CommunityServer.handle(author, new io.github.marcsanzdev.chestseparators.network.CommunityRequest(1,21,firstPos,CommunityData.NONE,"Prueba pública",27,9,sharedBytes));
            var storePath = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/absoluteorder");
            var savedCommunity = new CommunityStore(storePath);
            var published = savedCommunity.list().stream().filter(m -> m.owner().equals(author.getUUID())).findFirst().orElseThrow(() -> new AssertionError("Publication failed"));
            if(!published.author().equals("PresetAuthor")) throw new AssertionError("Wrong attribution");
            io.github.marcsanzdev.chestseparators.network.CommunityServer.handle(otherPlayer, new io.github.marcsanzdev.chestseparators.network.CommunityRequest(3,22,secondPos,published.id(),"",27,9,new byte[0]));
            if(!((io.github.marcsanzdev.chestseparators.access.IWhitelistProvider)secondChest).getWhitelists().equals(shared.filters())) throw new AssertionError("Other player could not apply community preset");
            String sharedKey=level.dimension().location()+"/block/"+secondPos.asLong()+"/minecraft:chest";
            var storedLayout=new CommunityStore(storePath).chest(sharedKey);
            if(storedLayout==null || !Arrays.equals(storedLayout.visual().get(0),shared.visual().get(0))) throw new AssertionError("Shared chest visual persistence failed");
            var revised = new CommunityData.Layout(shared.visual(),Map.of(0,new SlotWhitelist(UUID.randomUUID(),List.of("minecraft:iron_ingot"),true,true,true,0)));
            io.github.marcsanzdev.chestseparators.network.CommunityServer.handle(otherPlayer,new io.github.marcsanzdev.chestseparators.network.CommunityRequest(5,22,secondPos,CommunityData.NONE,"",27,9,CommunityData.pack(revised)));
            if(!new CommunityStore(storePath).needsName(sharedKey))throw new AssertionError("Manual edit did not request name");
            io.github.marcsanzdev.chestseparators.network.CommunityServer.handle(otherPlayer,new io.github.marcsanzdev.chestseparators.network.CommunityRequest(7,22,secondPos,CommunityData.NONE,"Automático",27,9,new byte[0]));
            var automatic = new CommunityStore(storePath).linked(sharedKey);
            if(automatic==null || !automatic.author().equals("PresetReader"))throw new AssertionError("Named edit was not published");
            io.github.marcsanzdev.chestseparators.network.CommunityServer.handle(otherPlayer,new io.github.marcsanzdev.chestseparators.network.CommunityRequest(5,22,secondPos,CommunityData.NONE,"",27,9,sharedBytes));
            var updated = new CommunityStore(storePath);
            if(!updated.linked(sharedKey).id().equals(automatic.id()) || !updated.get(automatic.id()).layout().filters().equals(shared.filters()))throw new AssertionError("Automatic update failed");
            if(!updated.get(published.id()).layout().filters().equals(shared.filters()))throw new AssertionError("Editing a copy changed its source");
            io.github.marcsanzdev.chestseparators.network.CommunityServer.handle(otherPlayer,new io.github.marcsanzdev.chestseparators.network.CommunityRequest(2,22,secondPos,automatic.id(),"",27,9,new byte[0]));
            System.out.println("AUTOPUBLISH_VALIDATION_OK name-request publish same-id-update source-preserved");            io.github.marcsanzdev.chestseparators.network.CommunityServer.handle(otherPlayer, new io.github.marcsanzdev.chestseparators.network.CommunityRequest(2,22,secondPos,published.id(),"",27,9,new byte[0]));
            if(new CommunityStore(storePath).get(published.id())==null) throw new AssertionError("Non-author deleted preset");
            io.github.marcsanzdev.chestseparators.network.CommunityServer.handle(author, new io.github.marcsanzdev.chestseparators.network.CommunityRequest(2,21,firstPos,published.id(),"",27,9,new byte[0]));
            if(new CommunityStore(storePath).list().stream().anyMatch(m->m.id().equals(published.id()))) throw new AssertionError("Author deletion failed");
            System.out.println("COMMUNITY_VALIDATION_OK publish author apply-other-player protected-delete shared-visual-persistence");            System.out.println("ABSOLUTE_VALIDATION_OK presets=" + StoragePresetLibrary.count() + " items=" + covered.size() + " storageSlots=" + inventory.getSlots());
            java.nio.file.Files.writeString(java.nio.file.Path.of("storage-validation.txt"),
                "PASS\nPresets: " + StoragePresetLibrary.count() + "\nRegistered items covered: " + covered.size()
                + "\nSophisticated slots: " + inventory.getSlots() + "\nRejected item preserved, allowed item inserted, filters survived reopening.\n");
            java.nio.file.Files.writeString(java.nio.file.Path.of("functional-catalog.txt"), java.util.stream.IntStream.range(46, 46 + StoragePresetLibrary.count()).mapToObj(StoragePresetLibrary::get).map(e -> e.name() + " — " + e.items().size() + " objetos\n" + java.util.stream.IntStream.range(0, e.rows().size()).mapToObj(r -> "  Fila " + (r + 1) + ": " + String.join(", ", e.rows().get(r))).collect(java.util.stream.Collectors.joining("\n"))).collect(java.util.stream.Collectors.joining("\n")));
            passed = true;
        } catch (Throwable failure) {
            System.out.println("ABSOLUTE_VALIDATION_FAILED");
            failure.printStackTrace();
        } finally { event.getServer().halt(false); }
    }
}
