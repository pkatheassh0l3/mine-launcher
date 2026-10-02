package com.hearthbound.registry;

import com.hearthbound.Hearthbound;
import com.hearthbound.block.VillageHearthBlock;
import com.hearthbound.entity.SettlerEntity;
import com.hearthbound.item.CharterItem;
import com.hearthbound.item.CoinItem;
import com.hearthbound.rpg.PlayerData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hearthbound.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hearthbound.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Hearthbound.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Hearthbound.MOD_ID);
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Hearthbound.MOD_ID);
    public static final Supplier<com.mojang.serialization.MapCodec<com.hearthbound.compat.ConfigCondition>> CONFIG_CONDITION =
            CONDITIONS.register("config", () -> com.hearthbound.compat.ConfigCondition.CODEC);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hearthbound.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Hearthbound.MOD_ID);

    // ------------------------------------------------------------------ blocks
    public static final DeferredBlock<VillageHearthBlock> VILLAGE_HEARTH = BLOCKS.register("village_hearth",
            () -> new VillageHearthBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1.0f, 3600000.0f)
                    .sound(SoundType.STONE)
                    .lightLevel(s -> 15)
                    .noOcclusion()));

    public static final DeferredBlock<com.hearthbound.block.ProjectStoneBlock> PROJECT_STONE = BLOCKS.register("project_stone",
            () -> new com.hearthbound.block.ProjectStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 1200.0f)
                    .sound(SoundType.STONE)
                    .lightLevel(s -> 6)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<com.hearthbound.block.ProjectStoneBlockEntity>> PROJECT_STONE_BE =
            BLOCK_ENTITIES.register("project_stone", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .of(com.hearthbound.block.ProjectStoneBlockEntity::new, PROJECT_STONE.get()).build(null));

    // ------------------------------------------------------------------ items
    public static final DeferredItem<CoinItem> COPPER_COIN = ITEMS.register("copper_coin", () -> new CoinItem(1, new Item.Properties()));
    public static final DeferredItem<CoinItem> SILVER_COIN = ITEMS.register("silver_coin", () -> new CoinItem(10, new Item.Properties()));
    public static final DeferredItem<CoinItem> GOLD_COIN = ITEMS.register("gold_coin", () -> new CoinItem(100, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<CharterItem> VILLAGE_CHARTER = ITEMS.register("village_charter", () -> new CharterItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<com.hearthbound.item.ProjectStoneItem> PROJECT_STONE_ITEM = ITEMS.register("project_stone",
            () -> new com.hearthbound.item.ProjectStoneItem(PROJECT_STONE.get(), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<BlockItem> VILLAGE_HEARTH_ITEM = ITEMS.register("village_hearth", () -> new BlockItem(VILLAGE_HEARTH.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredHolder<EntityType<?>, EntityType<com.hearthbound.entity.VillageGolemEntity>> VILLAGE_GOLEM = ENTITIES.register("village_golem",
            () -> EntityType.Builder.<com.hearthbound.entity.VillageGolemEntity>of(com.hearthbound.entity.VillageGolemEntity::new, MobCategory.MISC)
                    .sized(1.4f, 2.7f).clientTrackingRange(10).build("village_golem"));

    // ------------------------------------------------------------------ entities
    public static final DeferredHolder<EntityType<?>, EntityType<SettlerEntity>> SETTLER = ENTITIES.register("settler",
            () -> EntityType.Builder.<SettlerEntity>of(SettlerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(10)
                    .build("settler"));

    public static final DeferredItem<DeferredSpawnEggItem> SETTLER_SPAWN_EGG = ITEMS.register("settler_spawn_egg",
            () -> new DeferredSpawnEggItem(SETTLER, 0x6B4A2E, 0xE0B25A, new Item.Properties()));

    // ------------------------------------------------------------------ attachments
    public static final Supplier<AttachmentType<PlayerData>> PLAYER_DATA = ATTACHMENTS.register("player_data",
            () -> AttachmentType.serializable(PlayerData::new).copyOnDeath().build());

    public static final Supplier<AttachmentType<com.hearthbound.world.PlayerBlocks>> PLAYER_BLOCKS = ATTACHMENTS.register("player_blocks",
            () -> AttachmentType.serializable(com.hearthbound.world.PlayerBlocks::new).build());

    // ------------------------------------------------------------------ creative tab
    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hearthbound"))
            .icon(() -> new ItemStack(VILLAGE_HEARTH_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(VILLAGE_CHARTER.get());
                out.accept(COPPER_COIN.get());
                out.accept(SILVER_COIN.get());
                out.accept(GOLD_COIN.get());
                out.accept(SETTLER_SPAWN_EGG.get());
            })
            .build());

    private ModRegistry() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        TABS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        CONDITIONS.register(modBus);
        modBus.addListener(ModRegistry::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(VILLAGE_GOLEM.get(), net.minecraft.world.entity.animal.IronGolem.createAttributes().build());
        event.put(SETTLER.get(), SettlerEntity.createAttributes().build());
    }
}
