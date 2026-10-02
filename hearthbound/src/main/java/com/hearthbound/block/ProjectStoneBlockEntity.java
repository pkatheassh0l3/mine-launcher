package com.hearthbound.block;

import com.hearthbound.registry.ModRegistry;
import com.hearthbound.village.Projects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Remembers which project, village and builder a foundation stone belongs to. */
public class ProjectStoneBlockEntity extends BlockEntity {
    public UUID village;
    public String type = "";
    public int level = 1;
    public UUID owner;
    private int ticks;

    public ProjectStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PROJECT_STONE_BE.get(), pos, state);
    }

    public void serverTick(ServerLevel level) {
        if (++ticks % 10 == 0) Projects.tickStone(level, this, ticks);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, registries);
        return t;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider registries) {
        super.saveAdditional(t, registries);
        if (village != null) t.putUUID("village", village);
        t.putString("type", type);
        t.putInt("level", level);
        if (owner != null) t.putUUID("owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider registries) {
        super.loadAdditional(t, registries);
        village = t.hasUUID("village") ? t.getUUID("village") : null;
        type = t.getString("type");
        level = Math.max(1, t.getInt("level"));
        owner = t.hasUUID("owner") ? t.getUUID("owner") : null;
    }
}
