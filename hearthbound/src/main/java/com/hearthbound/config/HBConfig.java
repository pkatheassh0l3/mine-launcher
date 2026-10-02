package com.hearthbound.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server configuration ({@code serverconfig/hearthbound-server.toml}). Synced to clients.
 * Every gameplay number of the mod lives here so packs can rebalance without code.
 */
public final class HBConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue NIGHT_GOLEMS;
    public static final ModConfigSpec.IntValue RESIDENTS_PER_GOLEM;
    public static final ModConfigSpec.IntValue MAX_GOLEMS;

    // ------------------------------------------------------------------ world
    public static final ModConfigSpec.BooleanValue NATURAL_VILLAGES;
    public static final ModConfigSpec.IntValue SPAWN_CHECK_INTERVAL;
    public static final ModConfigSpec.DoubleValue SPAWN_CHANCE;
    public static final ModConfigSpec.IntValue MIN_SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue VILLAGE_SPACING;
    public static final ModConfigSpec.IntValue WORLD_SPAWN_EXCLUSION;
    public static final ModConfigSpec.IntValue MAX_VILLAGES;
    public static final ModConfigSpec.IntValue STARTER_BUILDINGS;
    public static final ModConfigSpec.IntValue MAX_SLOPE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;

    // ------------------------------------------------------------------ growth
    public static final ModConfigSpec.IntValue ECONOMY_INTERVAL;
    public static final ModConfigSpec.DoubleValue PRODUCTION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue COST_MULTIPLIER;
    public static final ModConfigSpec.IntValue CONSTRUCTION_INTERVAL;
    public static final ModConfigSpec.IntValue BLOCKS_PER_STEP;
    public static final ModConfigSpec.BooleanValue REQUIRE_BUILDER;
    public static final ModConfigSpec.BooleanValue GROW_WITHOUT_PLAYERS;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> TIER_THRESHOLDS;
    public static final ModConfigSpec.IntValue MAX_TIER;
    public static final ModConfigSpec.IntValue BASE_RADIUS;
    public static final ModConfigSpec.IntValue RADIUS_PER_TIER;
    public static final ModConfigSpec.BooleanValue BUILD_PATHS;
    public static final ModConfigSpec.BooleanValue PROTECT_BUILDINGS;

    // ------------------------------------------------------------------ diplomacy
    public static final ModConfigSpec.BooleanValue DIPLOMACY;
    public static final ModConfigSpec.IntValue DIPLOMACY_RANGE;
    public static final ModConfigSpec.IntValue BORDER_RANGE;
    public static final ModConfigSpec.BooleanValue CARAVANS;
    public static final ModConfigSpec.DoubleValue CARAVAN_CHANCE;
    public static final ModConfigSpec.BooleanValue WARS;
    public static final ModConfigSpec.IntValue WAR_THRESHOLD;
    public static final ModConfigSpec.DoubleValue WAR_CHANCE;
    public static final ModConfigSpec.IntValue WAR_MIN_DAYS;
    public static final ModConfigSpec.DoubleValue PEACE_CHANCE;
    public static final ModConfigSpec.IntValue TRUCE_DAYS;
    public static final ModConfigSpec.IntValue WARBAND_BASE;
    public static final ModConfigSpec.IntValue WARBAND_PER_TIER;
    public static final ModConfigSpec.IntValue WAR_KILL_PENALTY;
    public static final ModConfigSpec.BooleanValue RAIDERS_ATTACK_PLAYERS;
    public static final ModConfigSpec.BooleanValue RAIDERS_PILLAGE;
    public static final ModConfigSpec.IntValue RAID_DURATION;
    public static final ModConfigSpec.DoubleValue RAIDER_DAMAGE;
    public static final ModConfigSpec.DoubleValue RAIDER_HEALTH;
    public static final ModConfigSpec.IntValue MEDIATION_COST;
    public static final ModConfigSpec.IntValue GIFT_COST;
    public static final ModConfigSpec.IntValue GIFT_RELATION;
    public static final ModConfigSpec.DoubleValue ALLY_SHARE;
    public static final ModConfigSpec.DoubleValue ENEMY_SHARE;
    public static final ModConfigSpec.ConfigValue<String> ASC_AGE_WARS;

    // ------------------------------------------------------------------ terrain
    public static final ModConfigSpec.BooleanValue TERRAFORM;
    public static final ModConfigSpec.IntValue TERRAFORM_PAD;
    public static final ModConfigSpec.IntValue TERRAFORM_SKIRT;
    public static final ModConfigSpec.BooleanValue REMOVE_TREES;
    public static final ModConfigSpec.IntValue TREE_LIMIT;
    public static final ModConfigSpec.IntValue BUILDING_SPACING;

    // ------------------------------------------------------------------ player builds
    public static final ModConfigSpec.BooleanValue TRACK_PLAYER_BLOCKS;
    public static final ModConfigSpec.BooleanValue RESPECT_PLAYER_BUILDS;
    public static final ModConfigSpec.BooleanValue AVOID_ARTIFICIAL;
    public static final ModConfigSpec.BooleanValue BUILD_REWARDS;
    public static final ModConfigSpec.IntValue BLOCKS_PER_REP;
    public static final ModConfigSpec.IntValue BUILD_REP_DAILY_CAP;
    public static final ModConfigSpec.DoubleValue BUILD_XP_PER_BLOCK;
    public static final ModConfigSpec.IntValue BUILD_PROSPERITY_EVERY;

    // ------------------------------------------------------------------ structures
    public static final ModConfigSpec.BooleanValue USE_SCHEMATICS;
    public static final ModConfigSpec.DoubleValue SCHEMATIC_CHANCE;
    public static final ModConfigSpec.BooleanValue BUNDLED_SCHEMATICS;
    public static final ModConfigSpec.BooleanValue FOLDER_SCHEMATICS;

    // ------------------------------------------------------------------ settlers
    public static final ModConfigSpec.IntValue RESIDENTS_PER_HOUSE;
    public static final ModConfigSpec.IntValue MAX_POPULATION;
    public static final ModConfigSpec.IntValue RESPAWN_DAYS;
    public static final ModConfigSpec.BooleanValue PAID_REVIVAL;
    public static final ModConfigSpec.IntValue REVIVAL_COPPER;
    public static final ModConfigSpec.IntValue REVIVAL_SILVER;
    public static final ModConfigSpec.DoubleValue SETTLER_HEALTH;
    public static final ModConfigSpec.DoubleValue GUARD_HEALTH;
    public static final ModConfigSpec.DoubleValue GUARD_DAMAGE;
    public static final ModConfigSpec.IntValue MIN_GUARDS;
    public static final ModConfigSpec.IntValue RESIDENTS_PER_GUARD;
    public static final ModConfigSpec.BooleanValue SEEK_SHELTER;
    public static final ModConfigSpec.BooleanValue ALL_MONSTERS_HUNT_SETTLERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SETTLER_HUNTERS;

    // ------------------------------------------------------------------ reputation
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RANK_THRESHOLDS;
    public static final ModConfigSpec.IntValue DISCOVER_REP;
    public static final ModConfigSpec.DoubleValue TRADE_REP_PER_COIN;
    public static final ModConfigSpec.DoubleValue DONATE_REP_PER_POINT;
    public static final ModConfigSpec.IntValue DEFEND_REP;
    public static final ModConfigSpec.IntValue HURT_SETTLER_REP;
    public static final ModConfigSpec.IntValue KILL_SETTLER_REP;
    public static final ModConfigSpec.IntValue BREAK_BLOCK_REP;
    public static final ModConfigSpec.BooleanValue SHARE_REP_WITH_CULTURE;
    public static final ModConfigSpec.DoubleValue CULTURE_SHARE_RATIO;

    // ------------------------------------------------------------------ economy
    public static final ModConfigSpec.DoubleValue BUY_PRICE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SELL_PRICE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue RANK_DISCOUNT;
    public static final ModConfigSpec.IntValue DAILY_STOCK;
    public static final ModConfigSpec.IntValue LORD_TRIBUTE_PER_SETTLER;

    // ------------------------------------------------------------------ contracts
    public static final ModConfigSpec.IntValue BOARD_SIZE;
    public static final ModConfigSpec.IntValue BOARD_SIZE_PER_TIER;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_CONTRACTS;
    public static final ModConfigSpec.IntValue CONTRACT_DAYS;
    public static final ModConfigSpec.DoubleValue CONTRACT_REWARD_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CONTRACT_TIER_SCALING;

    // ------------------------------------------------------------------ rpg
    public static final ModConfigSpec.IntValue MAX_LEVEL;
    public static final ModConfigSpec.IntValue XP_BASE;
    public static final ModConfigSpec.DoubleValue XP_EXPONENT;
    public static final ModConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ModConfigSpec.IntValue SKILL_POINTS_PER_LEVEL;
    public static final ModConfigSpec.IntValue MAX_SKILL_RANK;
    public static final ModConfigSpec.IntValue XP_DISCOVER;
    public static final ModConfigSpec.IntValue XP_DEFEND;
    public static final ModConfigSpec.DoubleValue XP_PER_COIN_TRADED;
    public static final ModConfigSpec.DoubleValue XP_PER_DONATION_POINT;
    public static final ModConfigSpec.BooleanValue ALLOW_CLASS_CHANGE;
    public static final ModConfigSpec.IntValue CLASS_START_COINS;
    // skill effects
    public static final ModConfigSpec.DoubleValue SKILL_TRADE_DISCOUNT;
    public static final ModConfigSpec.DoubleValue SKILL_DIPLOMACY_BONUS;
    public static final ModConfigSpec.IntValue SKILL_LEADERSHIP_CONTRACTS_EVERY;
    public static final ModConfigSpec.IntValue SKILL_LEADERSHIP_COMPANIONS_EVERY;
    public static final ModConfigSpec.DoubleValue SKILL_CRAFT_BONUS;
    public static final ModConfigSpec.DoubleValue SKILL_VALOR_HEALTH;
    public static final ModConfigSpec.DoubleValue SKILL_VALOR_DAMAGE;
    public static final ModConfigSpec.DoubleValue SKILL_SCOUTING_XP;
    public static final ModConfigSpec.IntValue SKILL_SCOUTING_RADAR;

    // class perks
    public static final ModConfigSpec.DoubleValue PERK_WARRIOR_DEFEND;
    public static final ModConfigSpec.DoubleValue PERK_ROGUE_SELL;
    public static final ModConfigSpec.DoubleValue PERK_CLERIC_BLESSING;
    public static final ModConfigSpec.DoubleValue PERK_RANGER_DISCOVERY;
    public static final ModConfigSpec.DoubleValue PERK_ARTISAN_DONATION;
    public static final ModConfigSpec.DoubleValue PERK_NOBLE_HIRE;

    // ------------------------------------------------------------------ shrines
    public static final ModConfigSpec.BooleanValue BLESSINGS_ENABLED;
    public static final ModConfigSpec.IntValue BLESSING_COST;
    public static final ModConfigSpec.IntValue BLESSING_MINUTES;

    // ------------------------------------------------------------------ companions
    public static final ModConfigSpec.BooleanValue COMPANIONS_ENABLED;
    public static final ModConfigSpec.IntValue MAX_COMPANIONS;
    public static final ModConfigSpec.IntValue HIRE_BASE_COST;
    public static final ModConfigSpec.IntValue HIRE_COST_PER_TIER;
    public static final ModConfigSpec.IntValue HIRE_DAYS;
    public static final ModConfigSpec.ConfigValue<String> HIRE_MIN_RANK;

    // ------------------------------------------------------------------ raids
    public static final ModConfigSpec.BooleanValue RAIDS_ENABLED;
    public static final ModConfigSpec.DoubleValue RAID_CHANCE;
    public static final ModConfigSpec.IntValue RAID_MIN_TIER;
    public static final ModConfigSpec.IntValue RAID_BASE_SIZE;
    public static final ModConfigSpec.IntValue RAID_SIZE_PER_TIER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RAID_MOBS;
    public static final ModConfigSpec.IntValue RAID_TIMEOUT;
    public static final ModConfigSpec.BooleanValue BONDS;
    public static final ModConfigSpec.BooleanValue PROJECTS;
    public static final ModConfigSpec.BooleanValue CENTRAL_MARKET;
    public static final ModConfigSpec.IntValue MAX_PROJECTS;
    public static final ModConfigSpec.BooleanValue CITIZENSHIP;
    public static final ModConfigSpec.ConfigValue<String> CITIZEN_RANK;
    public static final ModConfigSpec.DoubleValue CITIZEN_DISCOUNT;
    public static final ModConfigSpec.DoubleValue CITIZEN_PROJECT_BONUS;
    public static final ModConfigSpec.BooleanValue CITIZEN_RESPAWN;
    public static final ModConfigSpec.BooleanValue CITIZEN_NAME_FLAG;
    public static final ModConfigSpec.IntValue CITIZEN_LEAVE_REP;
    public static final ModConfigSpec.IntValue CITIZEN_COOLDOWN_DAYS;
    public static final ModConfigSpec.BooleanValue FLAGS;
    public static final ModConfigSpec.IntValue FLAG_COPY_COST;
    public static final ModConfigSpec.IntValue BOND_TALK;
    public static final ModConfigSpec.IntValue DIALOGUES_PER_DAY;
    public static final ModConfigSpec.IntValue BOND_DIALOGUE;
    public static final ModConfigSpec.IntValue BOND_GIFT_LIKED;
    public static final ModConfigSpec.IntValue BOND_GIFT;
    public static final ModConfigSpec.IntValue BOND_FOLLOW;
    public static final ModConfigSpec.IntValue BOND_MOVE;
    public static final ModConfigSpec.IntValue MAX_FOLLOWERS;
    public static final ModConfigSpec.IntValue MOVE_REP_COST;
    public static final ModConfigSpec.DoubleValue STORY_REWARD_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue PILLAGER_RAIDS;
    public static final ModConfigSpec.DoubleValue PILLAGER_CHANCE;
    public static final ModConfigSpec.IntValue PILLAGER_MIN_TIER;
    public static final ModConfigSpec.IntValue PILLAGER_BASE;
    public static final ModConfigSpec.IntValue PILLAGER_PER_TIER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PILLAGER_MOBS;
    public static final ModConfigSpec.IntValue PILLAGER_RAVAGER_TIER;
    public static final ModConfigSpec.BooleanValue PILLAGER_LOOT;
    public static final ModConfigSpec.ConfigValue<String> ASC_AGE_PILLAGERS;

    // ------------------------------------------------------------------ charter / lordship
    public static final ModConfigSpec.BooleanValue CHARTER_ENABLED;
    public static final ModConfigSpec.IntValue CHARTER_SPACING;
    public static final ModConfigSpec.BooleanValue LORD_CHOOSES_BUILDINGS;

    // ------------------------------------------------------------------ ascension
    public static final ModConfigSpec.BooleanValue ASC_ENABLED;
    public static final ModConfigSpec.BooleanValue ASC_LEVEL_CAPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ASC_LEVEL_CAP_LIST;
    public static final ModConfigSpec.BooleanValue ASC_TIER_CAPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ASC_TIER_CAP_LIST;
    public static final ModConfigSpec.ConfigValue<String> ASC_AGE_CONTRACTS;
    public static final ModConfigSpec.ConfigValue<String> ASC_AGE_TRADE;
    public static final ModConfigSpec.ConfigValue<String> ASC_AGE_SKILLS;
    public static final ModConfigSpec.ConfigValue<String> ASC_AGE_COMPANIONS;
    public static final ModConfigSpec.ConfigValue<String> ASC_AGE_CHARTER;
    public static final ModConfigSpec.ConfigValue<String> ASC_AGE_LORDSHIP;
    public static final ModConfigSpec.BooleanValue ASC_RESPECT_SETTLER_LOCK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Where and how villages appear in the world.").push("world");
        NATURAL_VILLAGES = b.comment("Villages found themselves naturally near exploring players.").define("naturalVillages", true);
        SPAWN_CHECK_INTERVAL = b.comment("Ticks between attempts to settle a new village near each player.").defineInRange("spawnCheckInterval", 600, 20, 72000);
        SPAWN_CHANCE = b.comment("Chance per attempt that a village is founded (if a good spot is found).").defineInRange("spawnChance", 0.35, 0.0, 1.0);
        MIN_SPAWN_DISTANCE = b.comment("Minimum distance from the player where new villages appear.").defineInRange("minSpawnDistance", 112, 32, 512);
        MAX_SPAWN_DISTANCE = b.comment("Maximum distance from the player where new villages appear.").defineInRange("maxSpawnDistance", 192, 48, 1024);
        VILLAGE_SPACING = b.comment("Minimum distance between two villages.").defineInRange("villageSpacing", 640, 64, 10000);
        WORLD_SPAWN_EXCLUSION = b.comment("No natural village closer than this to the world spawn (0 = allowed).").defineInRange("worldSpawnExclusion", 96, 0, 10000);
        MAX_VILLAGES = b.comment("Maximum number of natural villages per dimension (0 = unlimited).").defineInRange("maxVillages", 0, 0, 100000);
        STARTER_BUILDINGS = b.comment("Buildings a natural village already has when it is founded (built instantly).").defineInRange("starterBuildings", 3, 0, 20);
        MAX_SLOPE = b.comment("Maximum height difference accepted inside a building plot (the ground is levelled).").defineInRange("maxSlope", 6, 1, 24);
        DIMENSIONS = b.comment("Dimensions where natural villages may appear.").defineList("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld", o -> o instanceof String);
        b.pop();

        b.comment("Village economy and construction. Resources are abstract: food, wood, stone and goods.").push("growth");
        ECONOMY_INTERVAL = b.comment("Ticks between economy updates (production and upkeep).").defineInRange("economyInterval", 1200, 100, 72000);
        PRODUCTION_MULTIPLIER = b.comment("Multiplier for everything villages produce.").defineInRange("productionMultiplier", 1.0, 0.0, 100.0);
        COST_MULTIPLIER = b.comment("Multiplier for building costs.").defineInRange("costMultiplier", 1.0, 0.0, 100.0);
        CONSTRUCTION_INTERVAL = b.comment("Ticks between construction steps.").defineInRange("constructionInterval", 10, 1, 1200);
        BLOCKS_PER_STEP = b.comment("Blocks placed per construction step.").defineInRange("blocksPerStep", 2, 1, 256);
        REQUIRE_BUILDER = b.comment("Construction only advances while the village has a living builder.").define("requireBuilder", false);
        GROW_WITHOUT_PLAYERS = b.comment("Villages in loaded chunks keep growing even when no player is near.").define("growWithoutPlayers", false);
        TIER_THRESHOLDS = b.comment("Completed buildings needed for each tier (Camp, Hamlet, Village, Town, City).").defineList("tierThresholds", List.of(0, 3, 7, 12, 18), () -> 0, o -> o instanceof Integer);
        MAX_TIER = b.comment("Highest tier a village may reach (0-4).").defineInRange("maxTier", 4, 0, 4);
        BASE_RADIUS = b.comment("Village radius at tier 0.").defineInRange("baseRadius", 40, 8, 256);
        RADIUS_PER_TIER = b.comment("Extra radius per tier.").defineInRange("radiusPerTier", 14, 0, 128);
        BUILD_PATHS = b.comment("Lay paths from every new building to the hearth.").define("buildPaths", true);
        PROTECT_BUILDINGS = b.comment("Breaking blocks of village buildings costs reputation (lords are exempt).").define("protectBuildings", true);
        b.pop();

        b.comment("Relations between villages: trade caravans, rivalries, wars, truces and alliances.").push("diplomacy");
        DIPLOMACY = b.define("enabled", true);
        DIPLOMACY_RANGE = b.comment("Villages closer than this know each other and trade.").defineInRange("range", 2000, 100, 100000);
        BORDER_RANGE = b.comment("Rival villages closer than this build up tension.").defineInRange("borderRange", 900, 50, 100000);
        CARAVANS = b.define("caravans", true);
        CARAVAN_CHANCE = b.comment("Chance per day that a village with a merchant sends a caravan to a friendly neighbour.").defineInRange("caravanChance", 0.6, 0.0, 1.0);
        WARS = b.define("wars", true);
        WAR_THRESHOLD = b.comment("Relation (-100..100) at which rivals may go to war.").defineInRange("warThreshold", -60, -100, 0);
        WAR_CHANCE = b.comment("Chance per day of declaring war once relations are that bad.").defineInRange("warChance", 0.25, 0.0, 1.0);
        WAR_MIN_DAYS = b.defineInRange("warMinDays", 3, 0, 1000);
        PEACE_CHANCE = b.comment("Chance per day of peace after the minimum war length.").defineInRange("peaceChance", 0.3, 0.0, 1.0);
        TRUCE_DAYS = b.comment("Days of truce after peace.").defineInRange("truceDays", 5, 0, 1000);
        WARBAND_BASE = b.comment("Warriors in an attack.").defineInRange("warbandBase", 3, 1, 30);
        WARBAND_PER_TIER = b.defineInRange("warbandPerTier", 1, 0, 10);
        WAR_KILL_PENALTY = b.comment("Reputation lost with the attackers for each warrior of theirs you kill.").defineInRange("warKillPenalty", 25, 0, 10000);
        RAIDERS_ATTACK_PLAYERS = b.comment("Raiders attack players who come close to them, unless the player is a friend of the attacking village.").define("raidersAttackPlayers", true);
        RAIDERS_PILLAGE = b.comment("Raiders loot the houses of the attacked village (they steal resources, never blocks).").define("raidersPillage", true);
        RAID_DURATION = b.comment("Seconds a war attack lasts before the raiders withdraw.").defineInRange("raidDurationSeconds", 360, 60, 3600);
        RAIDER_DAMAGE = b.comment("Melee damage of a raider.").defineInRange("raiderDamage", 5.0, 0.5, 100.0);
        RAIDER_HEALTH = b.comment("Health of a raider.").defineInRange("raiderHealth", 26.0, 1.0, 1000.0);
        MEDIATION_COST = b.comment("Copper coins to broker peace.").defineInRange("mediationCost", 60, 0, 100000);
        GIFT_COST = b.comment("Copper coins for a diplomatic gift.").defineInRange("giftCost", 20, 0, 100000);
        GIFT_RELATION = b.comment("Relation gained by a gift.").defineInRange("giftRelation", 6, 0, 100);
        ALLY_SHARE = b.comment("Share of your reputation gains that also reach the village's allies.").defineInRange("allyShare", 0.15, 0.0, 1.0);
        ENEMY_SHARE = b.comment("Share of your reputation gains lost with the village's enemies at war.").defineInRange("enemyShare", 0.10, 0.0, 1.0);
        ASC_AGE_WARS = b.comment("Ascension age a nearby player must have reached before wars can start (empty = always).").define("warsAge", "ascension:copper");
        b.pop();

        b.comment("How villages prepare the ground: they level each plot, clear trees and blend the edges into the landscape.").push("terrain");
        TERRAFORM = b.comment("Level and blend the terrain around new buildings.").define("terraform", true);
        TERRAFORM_PAD = b.comment("Flat yard around each building (blocks).").defineInRange("yard", 2, 0, 8);
        TERRAFORM_SKIRT = b.comment("Maximum width of the slope that blends the yard into the natural terrain.").defineInRange("blendWidth", 8, 0, 16);
        REMOVE_TREES = b.comment("Fell whole trees that touch the building area.").define("removeTrees", true);
        TREE_LIMIT = b.comment("Maximum blocks removed per felled tree group.").defineInRange("treeBlockLimit", 1500, 0, 20000);
        BUILDING_SPACING = b.comment("Free space kept between village buildings, so players have room to build.").defineInRange("buildingSpacing", 7, 1, 40);
        b.pop();

        b.comment("Player constructions: villages never build over them, and building inside a village earns reputation.").push("playerBuilds");
        TRACK_PLAYER_BLOCKS = b.comment("Remember which blocks were placed by players (stored per chunk).").define("trackPlayerBlocks", true);
        RESPECT_PLAYER_BUILDS = b.comment("Villages never place, clear or level where players built.").define("respectPlayerBuilds", true);
        AVOID_ARTIFICIAL = b.comment("Also treat man-made looking blocks (#hearthbound:artificial) as protected, e.g. builds from before the mod.").define("avoidManMadeBlocks", true);
        BUILD_REWARDS = b.comment("Building inside a village earns reputation and XP.").define("buildRewards", true);
        BLOCKS_PER_REP = b.comment("Blocks placed for each point of reputation.").defineInRange("blocksPerReputation", 8, 1, 1000);
        BUILD_REP_DAILY_CAP = b.comment("Maximum reputation per day from building, per village.").defineInRange("dailyReputationCap", 40, 0, 10000);
        BUILD_XP_PER_BLOCK = b.defineInRange("xpPerBlock", 0.25, 0.0, 100.0);
        BUILD_PROSPERITY_EVERY = b.comment("Every N blocks placed in a village raise its prosperity by 1.").defineInRange("prosperityEvery", 40, 1, 100000);
        b.pop();

        b.comment("Pre-built buildings from schematics (.nbt / .schem). Buildings without a schematic are generated procedurally.",
                "Drop your own in config/hearthbound/structures/<culture>/<building>/ and run /reload.").push("structures");
        USE_SCHEMATICS = b.comment("Use schematics at all.").define("useSchematics", true);
        SCHEMATIC_CHANCE = b.comment("Chance to use a schematic (when one exists) instead of the procedural design. Multiplies the culture's own chance.")
                .defineInRange("schematicChance", 0.85, 0.0, 1.0);
        BUNDLED_SCHEMATICS = b.comment("Use the schematics bundled with the mod (listed in each culture file).").define("bundledSchematics", true);
        FOLDER_SCHEMATICS = b.comment("Use schematics from the config folder (they take priority over bundled ones).").define("folderSchematics", true);
        b.pop();

        b.push("settlers");
        RESIDENTS_PER_HOUSE = b.comment("Settlers that move in when a house is finished.").defineInRange("residentsPerHouse", 2, 0, 8);
        MAX_POPULATION = b.comment("Maximum settlers per village.").defineInRange("maxPopulation", 40, 1, 500);
        RESPAWN_DAYS = b.comment("Days before a dead settler is replaced (0 = never).").defineInRange("respawnDays", 2, 0, 100);
        PAID_REVIVAL = b.comment("Allow immediate paid revival from the village's residents page.").define("paidRevival", true);
        REVIVAL_COPPER = b.comment("Copper part of the revival price. Other coins are accepted with change.").defineInRange("revivalCopper", 10, 0, 100000);
        REVIVAL_SILVER = b.comment("Silver part of the revival price (one silver = ten copper).").defineInRange("revivalSilver", 2, 0, 100000);
        SETTLER_HEALTH = b.defineInRange("settlerHealth", 20.0, 1.0, 1024.0);
        GUARD_HEALTH = b.defineInRange("guardHealth", 40.0, 1.0, 1024.0);
        GUARD_DAMAGE = b.defineInRange("guardDamage", 6.0, 0.0, 1024.0);
        MIN_GUARDS = b.comment("Guards every village keeps from the start, even without a watchtower.")
                .defineInRange("minGuards", 2, 0, 20);
        RESIDENTS_PER_GUARD = b.comment("Villages keep one guard for every this many residents, never fewer than minGuards or their watchtower posts",
                        "(0 = only minGuards and watchtowers). Unemployed villagers take up the post when the village is short of guards.")
                .defineInRange("residentsPerGuard", 4, 0, 100);
        SEEK_SHELTER = b.comment("Civilians run home and stay indoors at night and while monsters or raiders are around.")
                .define("seekShelter", true);
        ALL_MONSTERS_HUNT_SETTLERS = b.comment("If true, every hostile monster hunts settlers (old behaviour).",
                        "If false, only the mobs that attack vanilla villagers do (zombies, illagers, ravagers), plus raid mobs and settlerHunters.")
                .define("allMonstersHuntSettlers", false);
        SETTLER_HUNTERS = b.comment("Extra mobs (entity ids, e.g. from other mods) that hunt settlers.")
                .defineList("settlerHunters", List.of(), () -> "minecraft:zombie", o -> o instanceof String);
        b.pop();

        b.comment("Reputation per village. Ranks: Hostile, Wary, Stranger, Acquaintance, Friend, Ally, Hero.").push("reputation");
        RANK_THRESHOLDS = b.comment("Minimum reputation of each rank from Wary to Hero (6 values). Below the first is Hostile.")
                .defineList("rankThresholds", List.of(-100, 0, 50, 150, 400, 1000), () -> 0, o -> o instanceof Integer);
        DISCOVER_REP = b.defineInRange("discoverReputation", 5, 0, 1000);
        TRADE_REP_PER_COIN = b.defineInRange("tradeReputationPerCoin", 0.25, 0.0, 100.0);
        DONATE_REP_PER_POINT = b.defineInRange("donateReputationPerPoint", 0.5, 0.0, 100.0);
        DEFEND_REP = b.comment("Reputation for each hostile mob killed inside a village.").defineInRange("defendReputation", 3, 0, 1000);
        HURT_SETTLER_REP = b.comment("Reputation lost when hurting a settler.").defineInRange("hurtSettlerPenalty", 15, 0, 10000);
        KILL_SETTLER_REP = b.comment("Reputation lost when killing a settler.").defineInRange("killSettlerPenalty", 200, 0, 10000);
        BREAK_BLOCK_REP = b.comment("Reputation lost per block broken from a village building.").defineInRange("breakBlockPenalty", 2, 0, 1000);
        SHARE_REP_WITH_CULTURE = b.comment("Reputation gains also spread to the other known villages of the same culture.").define("shareWithCulture", true);
        CULTURE_SHARE_RATIO = b.defineInRange("cultureShareRatio", 0.25, 0.0, 1.0);
        b.pop();

        b.comment("Coins: copper = 1, silver = 10, gold = 100.").push("economy");
        BUY_PRICE_MULTIPLIER = b.defineInRange("buyPriceMultiplier", 1.0, 0.01, 100.0);
        SELL_PRICE_MULTIPLIER = b.defineInRange("sellPriceMultiplier", 1.0, 0.0, 100.0);
        RANK_DISCOUNT = b.comment("Price discount per rank above Stranger.").defineInRange("rankDiscount", 0.04, 0.0, 0.5);
        DAILY_STOCK = b.comment("How many times each offer can be bought per day (0 = unlimited).").defineInRange("dailyStock", 8, 0, 10000);
        LORD_TRIBUTE_PER_SETTLER = b.comment("Copper coins a lord receives per settler each day.").defineInRange("lordTributePerSettler", 1, 0, 1000);
        b.pop();

        b.push("contracts");
        BOARD_SIZE = b.comment("Contracts offered by a tier 0 village.").defineInRange("boardSize", 3, 0, 20);
        BOARD_SIZE_PER_TIER = b.defineInRange("boardSizePerTier", 1, 0, 10);
        MAX_ACTIVE_CONTRACTS = b.defineInRange("maxActiveContracts", 3, 1, 50);
        CONTRACT_DAYS = b.comment("Days before an accepted contract expires (0 = never).").defineInRange("contractDays", 3, 0, 1000);
        CONTRACT_REWARD_MULTIPLIER = b.defineInRange("rewardMultiplier", 1.0, 0.0, 100.0);
        CONTRACT_TIER_SCALING = b.comment("Extra rewards and amounts per village tier.").defineInRange("tierScaling", 0.25, 0.0, 10.0);
        b.pop();

        b.comment("Character progression: levels, backgrounds and skills.").push("rpg");
        MAX_LEVEL = b.defineInRange("maxLevel", 50, 1, 1000);
        XP_BASE = b.comment("XP needed from level 1 to 2.").defineInRange("xpBase", 100, 1, 1000000);
        XP_EXPONENT = b.comment("XP to next level = xpBase * level ^ xpExponent.").defineInRange("xpExponent", 1.35, 0.0, 5.0);
        XP_MULTIPLIER = b.defineInRange("xpMultiplier", 1.0, 0.0, 100.0);
        SKILL_POINTS_PER_LEVEL = b.defineInRange("skillPointsPerLevel", 1, 0, 10);
        MAX_SKILL_RANK = b.defineInRange("maxSkillRank", 5, 1, 20);
        XP_DISCOVER = b.defineInRange("xpDiscoverVillage", 60, 0, 100000);
        XP_DEFEND = b.defineInRange("xpDefendKill", 8, 0, 100000);
        XP_PER_COIN_TRADED = b.defineInRange("xpPerCoinTraded", 0.5, 0.0, 100.0);
        XP_PER_DONATION_POINT = b.defineInRange("xpPerDonationPoint", 0.5, 0.0, 100.0);
        ALLOW_CLASS_CHANGE = b.comment("Players can change their class later from the character screen.").define("allowClassChange", false);
        CLASS_START_COINS = b.comment("Copper coins given when picking a class.").defineInRange("classStartCoins", 15, 0, 100000);
        b.push("skills");
        SKILL_TRADE_DISCOUNT = b.comment("Commerce: price improvement per rank.").defineInRange("commerceDiscount", 0.04, 0.0, 0.5);
        SKILL_DIPLOMACY_BONUS = b.comment("Diplomacy: extra reputation gained per rank.").defineInRange("diplomacyBonus", 0.10, 0.0, 5.0);
        SKILL_LEADERSHIP_CONTRACTS_EVERY = b.comment("Leadership: +1 active contract every N ranks.").defineInRange("leadershipContractsEvery", 2, 1, 20);
        SKILL_LEADERSHIP_COMPANIONS_EVERY = b.comment("Leadership: +1 companion every N ranks.").defineInRange("leadershipCompanionsEvery", 2, 1, 20);
        SKILL_CRAFT_BONUS = b.comment("Craftsmanship: donations are worth this much more per rank.").defineInRange("craftDonationBonus", 0.15, 0.0, 5.0);
        SKILL_VALOR_HEALTH = b.comment("Valor: max health per rank.").defineInRange("valorHealth", 2.0, 0.0, 100.0);
        SKILL_VALOR_DAMAGE = b.comment("Valor: attack damage per rank.").defineInRange("valorDamage", 0.5, 0.0, 100.0);
        SKILL_SCOUTING_XP = b.comment("Scouting: extra XP from all sources per rank.").defineInRange("scoutingXp", 0.05, 0.0, 5.0);
        SKILL_SCOUTING_RADAR = b.comment("Scouting: village compass range per rank (blocks).").defineInRange("scoutingRadar", 400, 0, 100000);
        b.pop();
        b.comment("Passive talent of each class.").push("classPerks");
        PERK_WARRIOR_DEFEND = b.comment("Warrior: extra reputation and XP for defending villages.").defineInRange("warriorDefendBonus", 0.5, 0.0, 10.0);
        PERK_ROGUE_SELL = b.comment("Rogue: better prices when selling.").defineInRange("rogueSellBonus", 0.15, 0.0, 5.0);
        PERK_CLERIC_BLESSING = b.comment("Cleric: blessings cost less and last longer by this ratio.").defineInRange("clericBlessingBonus", 0.5, 0.0, 0.9);
        PERK_RANGER_DISCOVERY = b.comment("Ranger: extra XP and reputation for discovering villages.").defineInRange("rangerDiscoveryBonus", 1.0, 0.0, 10.0);
        PERK_ARTISAN_DONATION = b.comment("Artisan: donations are worth more.").defineInRange("artisanDonationBonus", 0.25, 0.0, 10.0);
        PERK_NOBLE_HIRE = b.comment("Noble: companions are cheaper by this ratio and +1 companion slot.").defineInRange("nobleHireDiscount", 0.25, 0.0, 0.9);
        b.pop();
        b.pop();

        b.comment("Shrine blessings sold by priests (temporary potion effects).").push("shrines");
        BLESSINGS_ENABLED = b.define("enabled", true);
        BLESSING_COST = b.comment("Copper coins for a blessing.").defineInRange("blessingCost", 12, 0, 100000);
        BLESSING_MINUTES = b.defineInRange("blessingMinutes", 5, 1, 600);
        b.pop();

        b.comment("Hired companions (from innkeepers).").push("companions");
        COMPANIONS_ENABLED = b.define("enabled", true);
        MAX_COMPANIONS = b.defineInRange("maxCompanions", 1, 0, 20);
        HIRE_BASE_COST = b.comment("Copper coins.").defineInRange("hireBaseCost", 30, 0, 100000);
        HIRE_COST_PER_TIER = b.defineInRange("hireCostPerTier", 15, 0, 100000);
        HIRE_DAYS = b.defineInRange("hireDays", 3, 1, 1000);
        HIRE_MIN_RANK = b.comment("Minimum rank needed: hostile, wary, stranger, acquaintance, friend, ally, hero.").define("minRank", "friend");
        b.pop();

        b.comment("Night raids on villages.").push("raids");
        RAIDS_ENABLED = b.define("enabled", true);
        RAID_CHANCE = b.comment("Chance per night that a loaded village is raided.").defineInRange("chancePerNight", 0.15, 0.0, 1.0);
        RAID_MIN_TIER = b.defineInRange("minTier", 1, 0, 4);
        RAID_BASE_SIZE = b.defineInRange("baseSize", 3, 1, 50);
        RAID_SIZE_PER_TIER = b.defineInRange("sizePerTier", 2, 0, 50);
        RAID_MOBS = b.defineList("mobs", List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:husk", "minecraft:spider"), () -> "minecraft:zombie", o -> o instanceof String);
        RAID_TIMEOUT = b.comment("Seconds after which the attackers of a raid that was not beaten retreat.").defineInRange("timeoutSeconds", 480, 60, 7200);
        b.comment("Pillager raids: bands of illagers that attack villages at a random moment of the day or night.").push("pillagers");
        PILLAGER_RAIDS = b.define("enabled", true);
        PILLAGER_CHANCE = b.comment("Chance per day that a village with players around is attacked by pillagers.").defineInRange("chancePerDay", 0.12, 0.0, 1.0);
        PILLAGER_MIN_TIER = b.comment("Minimum village tier (0 Camp .. 4 City).").defineInRange("minTier", 1, 0, 4);
        PILLAGER_BASE = b.defineInRange("baseSize", 4, 1, 50);
        PILLAGER_PER_TIER = b.defineInRange("sizePerTier", 2, 0, 50);
        PILLAGER_MOBS = b.comment("Mobs of the band (repeat an id to make it more common). Evokers and witches can be added.")
                .defineList("mobs", List.of("minecraft:pillager", "minecraft:pillager", "minecraft:vindicator"), () -> "minecraft:pillager", o -> o instanceof String);
        PILLAGER_RAVAGER_TIER = b.comment("From this village tier on, the band brings a ravager (5 = never).").defineInRange("ravagerFromTier", 3, 0, 5);
        PILLAGER_LOOT = b.comment("Pillagers loot houses: the village loses resources (never blocks).").define("loot", true);
        b.pop();
        b.pop();

        b.comment("Friendship with individual settlers: each one has a life story with personal quests.").push("bonds");
        BONDS = b.comment("Right-click a settler to talk to them (sneak + right-click opens the village).").define("enabled", true);
        BOND_TALK = b.comment("Friendship gained by talking to a settler (once a day).").defineInRange("talk", 3, 0, 100);
        DIALOGUES_PER_DAY = b.comment("New story dialogues a settler tells you per day (0 = no limit). Each phase of a story has up to 15.").defineInRange("dialoguesPerDay", 3, 0, 100);
        BOND_DIALOGUE = b.comment("Friendship gained with each new story dialogue.").defineInRange("dialogueBond", 1, 0, 100);
        BOND_GIFT_LIKED = b.comment("Friendship gained with a gift the settler likes (one gift a day).").defineInRange("giftLiked", 8, 0, 100);
        BOND_GIFT = b.comment("Friendship gained with any other gift.").defineInRange("gift", 2, 0, 100);
        BOND_FOLLOW = b.comment("Friendship (0-100) needed to ask a settler to travel with you.").defineInRange("followAt", 60, 0, 100);
        BOND_MOVE = b.comment("Friendship (0-100) needed to invite a settler to move to a village you rule.").defineInRange("moveAt", 80, 0, 100);
        MAX_FOLLOWERS = b.comment("Friends that can travel with you at once.").defineInRange("maxFollowers", 2, 0, 20);
        MOVE_REP_COST = b.comment("Reputation lost with a village when one of its people moves to yours.").defineInRange("moveReputationCost", 10, 0, 10000);
        STORY_REWARD_MULTIPLIER = b.comment("Multiplier for coins and XP of personal quests.").defineInRange("rewardMultiplier", 1.0, 0.0, 100.0);
        b.pop();

        b.comment("Building projects players take from a village and build themselves (library, butcher's...). Finished levels unlock hall wares.").push("projects");
        PROJECTS = b.define("enabled", true);
        MAX_PROJECTS = b.comment("Projects a player can have in progress at once.").defineInRange("maxActive", 2, 1, 20);
        CENTRAL_MARKET = b.comment("Allow buying every ware from the village ledger (hearth), which then shows a Trade tab. When false (default) the ledger has no Trade tab and each ware is only sold inside its shop: right-click the foundation stone of the finished building.").define("centralMarket", false);
        b.pop();

        b.comment("Citizenship: a player can be a citizen of one village.").push("citizenship");
        CITIZENSHIP = b.define("enabled", true);
        CITIZEN_RANK = b.comment("Rank needed: hostile, wary, stranger, acquaintance, friend, ally, hero.").define("minRank", "friend");
        CITIZEN_DISCOUNT = b.comment("Discount on purchases in your village.").defineInRange("discount", 0.10, 0.0, 0.9);
        CITIZEN_PROJECT_BONUS = b.comment("Extra reputation from building projects in your village.").defineInRange("projectBonus", 0.25, 0.0, 10.0);
        CITIZEN_RESPAWN = b.comment("Citizens without a bed respawn at their village hearth.").define("respawnAtHearth", true);
        CITIZEN_NAME_FLAG = b.comment("Show a flag in the village colour next to citizens' names (chat and player list).").define("nameFlag", true);
        CITIZEN_LEAVE_REP = b.comment("Reputation lost with a village when you give up its citizenship.").defineInRange("leaveReputation", 30, 0, 10000);
        CITIZEN_COOLDOWN_DAYS = b.comment("Days before you can change citizenship again.").defineInRange("cooldownDays", 3, 0, 1000);
        b.pop();

        b.comment("Village banners.").push("flags");
        FLAGS = b.comment("Villages raise a flagpole with their banner next to the plaza.").define("flagpoles", true);
        FLAG_COPY_COST = b.comment("Copper coins for a copy of the village banner (citizens and lord).").defineInRange("copyCost", 15, 0, 100000);
        b.pop();

        b.comment("Village charters let players found their own village and rule it.").push("lordship");
        CHARTER_ENABLED = b.define("charterEnabled", true);
        CHARTER_SPACING = b.comment("Minimum distance from other villages when founding with a charter.").defineInRange("charterSpacing", 200, 32, 10000);
        LORD_CHOOSES_BUILDINGS = b.comment("Lords may pick the next building their village constructs.").define("lordChoosesBuildings", true);
        b.pop();

        b.comment("Integration with Ascension: Vanilla Ages (ignored when it is not installed).",
                "Age ids look like 'ascension:iron'. Leave a gate empty to never lock that feature.").push("ascension");
        ASC_ENABLED = b.define("enabled", true);
        ASC_LEVEL_CAPS = b.comment("Your maximum character level depends on your highest unlocked age.").define("levelCaps", true);
        ASC_LEVEL_CAP_LIST = b.defineList("levelCapByAge", List.of(
                "ascension:wood=5", "ascension:stone=10", "ascension:copper=15", "ascension:chainmail=20",
                "ascension:iron=26", "ascension:diamond=34", "ascension:nether=42", "ascension:end=48", "ascension:ascended=50"),
                () -> "ascension:wood=5", o -> o instanceof String);
        ASC_TIER_CAPS = b.comment("One player reaching an age unlocks its village tier server-wide, permanently, even while that player is offline.").define("tierCaps", true);
        ASC_TIER_CAP_LIST = b.defineList("tierCapByAge", List.of(
                "ascension:wood=1", "ascension:stone=1", "ascension:copper=2", "ascension:chainmail=2",
                "ascension:iron=3", "ascension:diamond=4"),
                () -> "ascension:wood=1", o -> o instanceof String);
        ASC_AGE_CONTRACTS = b.comment("Age needed to accept contracts.").define("contractsAge", "");
        ASC_AGE_PILLAGERS = b.comment("Pillager raids only happen once a player near the village has reached this age.").define("pillagerRaidsAge", "ascension:copper");
        ASC_AGE_TRADE = b.comment("Age needed to trade with settlers.").define("tradeAge", "");
        ASC_AGE_SKILLS = b.comment("Age needed to spend skill points.").define("skillsAge", "ascension:stone");
        ASC_AGE_COMPANIONS = b.comment("Age needed to hire companions.").define("companionsAge", "ascension:iron");
        ASC_AGE_CHARTER = b.comment("Age needed to use a village charter.").define("charterAge", "ascension:diamond");
        ASC_AGE_LORDSHIP = b.comment("Age needed to be offered the lordship of a village as a Hero.").define("lordshipAge", "ascension:nether");
        ASC_RESPECT_SETTLER_LOCK = b.comment("Treat Hearthbound settlers like vanilla villagers for Ascension's entity locks.").define("respectVillagerLock", false);
        b.pop();

        b.push("nightGolems");
        NIGHT_GOLEMS = b.comment("Spawn village golems at night. They retire at dawn without drops.").define("enabled", true);
        RESIDENTS_PER_GOLEM = b.comment("Living residents required for each golem; dead residents do not count.")
                .defineInRange("residentsPerGolem", 10, 1, 1000);
        MAX_GOLEMS = b.comment("Maximum golems spawned per village per night. Killed golems return next night.")
                .defineInRange("maxGolems", 3, 0, 32);
        b.pop();
        SPEC = b.build();
    }

    private HBConfig() {}

    /** Parses "key=value" lines into a string map (value may be empty). */
    public static Map<String, String> parseStringMap(List<? extends String> list) {
        Map<String, String> out = new HashMap<>();
        for (String s : list) {
            int i = s.indexOf('=');
            if (i <= 0) continue;
            out.put(s.substring(0, i).trim(), s.substring(i + 1).trim());
        }
        return out;
    }

    /** Parses "key=value" lines into a map, ignoring malformed entries. */
    public static Map<String, Integer> parseMap(List<? extends String> list) {
        Map<String, Integer> out = new HashMap<>();
        for (String s : list) {
            int i = s.lastIndexOf('=');
            if (i <= 0) continue;
            try {
                out.put(s.substring(0, i).trim(), Integer.parseInt(s.substring(i + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}

