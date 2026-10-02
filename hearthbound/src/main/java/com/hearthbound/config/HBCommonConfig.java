package com.hearthbound.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common configuration ({@code config/hearthbound-common.toml}). These options affect world
 * generation, so they live outside the per-world server config and apply to new chunks.
 */
public final class HBCommonConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue REPLACE_VANILLA_VILLAGES;
    public static final ModConfigSpec.BooleanValue REPLACE_VILLAGERS;
    public static final ModConfigSpec.BooleanValue ADOPT_VANILLA_VILLAGES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Hearthbound replaces vanilla villages and villagers with its own peoples.").push("vanilla");
        REPLACE_VANILLA_VILLAGES = b.comment("Stop vanilla villages from generating in new chunks (Hearthbound villages appear instead). Needs a restart.")
                .define("replaceVanillaVillages", true);
        REPLACE_VILLAGERS = b.comment("Turn every vanilla villager (spawned, bred, cured or already in the world) into a Hearthbound settler.")
                .define("replaceVillagers", true);
        ADOPT_VANILLA_VILLAGES = b.comment("Existing vanilla villages become Hearthbound villages: a hearth is lit near their bell and they keep growing around the old houses.")
                .define("adoptVanillaVillages", true);
        b.pop();
        SPEC = b.build();
    }

    private HBCommonConfig() {}
}
