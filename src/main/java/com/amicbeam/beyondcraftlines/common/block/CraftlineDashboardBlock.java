package com.amicbeam.beyondcraftlines.common.block;

import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity;
import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class CraftlineDashboardBlock extends NetedBlock implements EntityBlock
{
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape NORTH = box(4, 4, 14, 12, 12, 16);
    private static final VoxelShape SOUTH = box(4, 4, 0, 12, 12, 2);
    private static final VoxelShape WEST = box(14, 4, 4, 16, 12, 12);
    private static final VoxelShape EAST = box(0, 4, 4, 2, 12, 12);
    private static final VoxelShape UP = box(4, 0, 4, 12, 2, 12);
    private static final VoxelShape DOWN = box(4, 14, 4, 12, 16, 12);

    public CraftlineDashboardBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    { builder.add(FACING); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context)
    { return defaultBlockState().setValue(FACING, context.getClickedFace()); }

    public static Direction supportSide(BlockState state) { return state.getValue(FACING); }
    public static BlockPos supportPos(BlockPos position, BlockState state)
    { return position.relative(supportSide(state).getOpposite()); }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos position)
    {
        Direction facing = supportSide(state);
        BlockPos support = supportPos(position, state);
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context)
    {
        return switch (supportSide(state))
        {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
                                      @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer player
                && level.getBlockEntity(position) instanceof CraftlineDashboardBlockEntity dashboard)
            dashboard.setOwner(player.getUUID());
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                          Player player, BlockHitResult hit)
    {
        if (player.isShiftKeyDown()) return super.useWithoutItem(state, level, position, player, hit);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(position) instanceof CraftlineDashboardBlockEntity dashboard)
            dashboard.openConfiguration(serverPlayer);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state)
    { return new CraftlineDashboardBlockEntity(position, state); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type)
    {
        if (level.isClientSide()) return null;
        return (tickLevel, position, tickState, blockEntity) -> {
            if (blockEntity instanceof CraftlineDashboardBlockEntity dashboard)
                CraftlineDashboardBlockEntity.serverTick(tickLevel, position, tickState, dashboard);
        };
    }
}
