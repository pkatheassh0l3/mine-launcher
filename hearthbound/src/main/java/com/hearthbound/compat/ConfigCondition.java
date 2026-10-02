package com.hearthbound.compat;

import com.hearthbound.config.HBCommonConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Datapack condition {@code {"type": "hearthbound:config", "option": "replace_vanilla_villages"}}. */
public record ConfigCondition(String option) implements ICondition {
    public static final MapCodec<ConfigCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("option").forGetter(ConfigCondition::option)
    ).apply(i, ConfigCondition::new));

    @Override
    public boolean test(IContext context) {
        try {
            return switch (option) {
                case "replace_vanilla_villages" -> HBCommonConfig.REPLACE_VANILLA_VILLAGES.get();
                case "replace_villagers" -> HBCommonConfig.REPLACE_VILLAGERS.get();
                default -> false;
            };
        } catch (Exception e) {
            return true; // config not loaded yet: use the default
        }
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
