package com.hearthbound.village;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("hearthbound")
@PrefixGameTestTemplate(false)
public final class SharedGrowthChecks {
    @GameTest(template = "empty")
    public static void onePlayerAndOfflinePersistence(GameTestHelper h) {
        var data = new VillageData();
        check(data.sharedTierCap(1, 4) == 1, "new world starts at baseline");
        for (int i = 0; i < 10; i++) data.recordTierCap(1);
        data.recordTierCap(3);
        check(data.sharedTierCap(1, 4) == 3, "one advanced player unlocks growth for everyone");
        data.recordTierCap(1);
        check(data.sharedTierCap(1, 4) == 3, "less advanced players cannot lower cap");
        var saved = data.save(new CompoundTag(), h.getLevel().registryAccess());
        var restored = VillageData.load(saved, h.getLevel().registryAccess());
        check(restored.sharedTierCap(1, 4) == 3, "restart with no online players retains unlock");
        check(restored.sharedTierCap(1, 2) == 2, "server maximum still applies");
        check(VillageData.load(new CompoundTag(), h.getLevel().registryAccess()).sharedTierCap(1, 4) == 1,
                "old saves without shared cap do not unlock all tiers");
        h.succeed();
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
