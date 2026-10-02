package com.hearthbound.block;

import com.hearthbound.village.VillageService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The eternal flame at the heart of every village. Right click to open the village ledger. */
public class VillageHearthBlock extends Block {
    public static final MapCodec<VillageHearthBlock> CODEC = simpleCodec(VillageHearthBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 4, 15),
            Block.box(3, 4, 3, 13, 10, 13));

    public VillageHearthBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            VillageService.openAt(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.7, z = pos.getZ() + 0.5;
        if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.FLAME, x + (r.nextDouble() - 0.5) * 0.3, y, z + (r.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
        if (r.nextInt(5) == 0) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.3, z, 0, 0.05, 0);
        if (r.nextInt(8) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, x + (r.nextDouble() - 0.5) * 0.5, y - 0.1, z + (r.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
        if (r.nextInt(40) == 0) level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6f, 0.9f + r.nextFloat() * 0.2f, false);
    }
}
