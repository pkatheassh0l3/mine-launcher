package com.hearthbound.block;

import com.hearthbound.registry.ModRegistry;
import com.hearthbound.village.Projects;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The foundation stone of a player building project. Marks the centre of the plot; right click
 * to see the requirements, resize the plot or declare the building finished.
 */
public class ProjectStoneBlock extends BaseEntityBlock {
    public static final MapCodec<ProjectStoneBlock> CODEC = simpleCodec(ProjectStoneBlock::new);
    public static final BooleanProperty DONE = BooleanProperty.create("done");

    public ProjectStoneBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(DONE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(DONE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProjectStoneBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(DONE)) return null;
        return createTickerHelper(type, ModRegistry.PROJECT_STONE_BE.get(), (l, p, s, be) -> be.serverTick((ServerLevel) l));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) Projects.open(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Only the builder (or a creative player) can break an unfinished stone. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!state.getValue(DONE) && !player.isCreative() && level.getBlockEntity(pos) instanceof ProjectStoneBlockEntity be
                && be.owner != null && !be.owner.equals(player.getUUID())) return 0f;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !state.getValue(DONE) && player instanceof ServerPlayer sp
                && level.getBlockEntity(pos) instanceof ProjectStoneBlockEntity be) {
            Projects.coreBroken(sp, be);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(state.getValue(DONE) ? 12 : 4) != 0) return;
        level.addParticle(state.getValue(DONE) ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.WAX_ON,
                pos.getX() + r.nextDouble(), pos.getY() + 1.05, pos.getZ() + r.nextDouble(), 0, 0.02, 0);
    }
}
