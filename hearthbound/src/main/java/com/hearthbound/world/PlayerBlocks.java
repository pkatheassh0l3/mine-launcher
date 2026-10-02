package com.hearthbound.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Positions (as {@code BlockPos.asLong}) of blocks placed by players in one chunk. Attached to chunks. */
public final class PlayerBlocks implements INBTSerializable<CompoundTag> {
    public final LongOpenHashSet positions = new LongOpenHashSet();

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putLongArray("p", positions.toLongArray());
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        positions.clear();
        for (long l : t.getLongArray("p")) positions.add(l);
    }
}
