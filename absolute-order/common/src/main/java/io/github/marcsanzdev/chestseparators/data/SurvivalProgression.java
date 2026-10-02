package io.github.marcsanzdev.chestseparators.data;

import java.util.*;
import static io.github.marcsanzdev.chestseparators.data.FunctionalPresets.*;

/** Estimated progression priority, not measured ownership probabilities or guaranteed play times. */
public final class SurvivalProgression {
    private SurvivalProgression() {}
    private static boolean has(String path, String... words) {
        for (String word : words) if (("_" + path + "_").contains("_" + word + "_")) return true;
        return false;
    }
    public static int score(ItemInfo item) {
        String p = item.id().substring(item.id().indexOf(':') + 1);
        if (has(p, "creaking_heart", "resin")) return 190;
        if (has(p, "pale_oak", "pale_moss", "pale_hanging_moss", "eyeblossom", "pale_pumpkin", "pale_jack")) return 145;
        // Stage * 100 + acquisition difficulty. Check rare variants before their common materials.
        if (has(p, "netherite", "nether_star", "beacon", "elytra", "dragon", "shulker", "end_crystal", "heavy_core", "mace", "enchanted_golden_apple")) return 450;
        if (has(p, "echo_shard", "recovery_compass", "totem", "trident", "wither", "ancient_debris", "smithing_template", "music_disc", "sponge")) return 390;
        if (has(p, "end_stone", "purpur", "chorus", "end_rod", "dragon_breath")) return 420;
        if (has(p, "blaze", "ghast", "magma_cream", "nether_wart", "soul", "wither_skeleton", "crying_obsidian", "respawn_anchor")) return 330;
        if (has(p, "nether", "netherrack", "quartz", "crimson", "warped", "blackstone", "basalt", "glowstone")) return 300;
        if (has(p, "diamond", "enchanting", "enchanted", "ender", "obsidian", "brewing", "potion", "prismarine", "sea_lantern", "breeze", "trial", "ominous", "vault")) return 260;
        if (has(p, "precision", "electron", "computer", "refined", "advanced", "ultimate", "jetpack", "turbine", "reactor")) return 320;
        if (has(p, "brass", "steel", "motor", "engine", "deployer", "sequenced", "controller")) return 230;
        if (has(p, "gold", "golden", "emerald", "lapis", "amethyst", "redstone", "repeater", "comparator", "piston", "hopper", "rail", "minecart", "anvil", "clock", "compass", "slime")) return 170;
        if (has(p, "iron", "copper", "zinc", "bucket", "shears", "shield", "lantern", "flint_and_steel", "glass", "bottle", "book", "paper", "leather", "bow", "arrow", "fishing_rod", "backpack", "cogwheel", "shaft", "water_wheel", "wrench")) return 110;
        if (has(p, "stick", "crafting_table", "torch", "chest", "furnace", "dirt", "cobblestone", "coal", "charcoal", "oak", "birch", "spruce", "planks", "log", "wooden")) return 10;
        if (has(p, "stone", "sand", "gravel", "flint", "wheat", "bread", "apple", "beef", "porkchop", "chicken", "mutton", "seed", "seeds", "sapling", "wool", "bed")) return 25;
        if (has(p, "string", "bone", "rotten_flesh", "feather", "gunpowder", "spider_eye", "sugar", "egg", "clay", "flower", "dandelion", "poppy")) return 60;
        if (item.tags().stream().anyMatch(t -> t.endsWith(":logs") || t.endsWith(":planks") || t.endsWith(":saplings"))) return 30;
        // Mod items lacking progression evidence get a neutral estimate, never a namespace penalty.
        return switch (classify(item)) {
            case FOOD, NATURE, WOOD, STONE, BUILDING -> 130;
            case TOOLS, WEAPONS, EQUIPMENT, MOB_DROPS -> 160;
            case MINERALS, UTILITIES -> 180;
            case ENGINEERING -> 210;
            case ALCHEMY -> 270;
        };
    }
}
