package com.amicbeam.beyondcraftlines.common.block;

import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity;
import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class CraftlineDashboardBlock extends NetedBlock implements EntityBlock {
    public static final Property<Direction> FACING=BlockStateProperties.FACING;
    private static final VoxelShape NORTH=box(4,4,14,12,12,16),SOUTH=box(4,4,0,12,12,2),WEST=box(14,4,4,16,12,12),EAST=box(0,4,4,2,12,12),UP=box(4,0,4,12,2,12),DOWN=box(4,14,4,12,16,12);
    public CraftlineDashboardBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace());}
    public static Direction supportSide(BlockState state){return state.getValue(FACING);}
    public static BlockPos supportPos(BlockPos pos,BlockState state){return pos.relative(supportSide(state).getOpposite());}
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){Direction facing=supportSide(state);BlockPos support=supportPos(pos,state);return level.getBlockState(support).isFaceSturdy(level,support,facing);}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return switch(supportSide(state)){case NORTH->NORTH;case SOUTH->SOUTH;case WEST->WEST;case EAST->EAST;case UP->UP;case DOWN->DOWN;};}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide()&&placer instanceof ServerPlayer player&&level.getBlockEntity(pos) instanceof CraftlineDashboardBlockEntity dashboard)dashboard.setOwner(player.getUUID());}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(player.isShiftKeyDown())return super.useWithoutItem(state,level,pos,player,hit);if(!level.isClientSide()&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof CraftlineDashboardBlockEntity dashboard)dashboard.openConfiguration(server);return level.isClientSide()?InteractionResult.SUCCESS:InteractionResult.SUCCESS_SERVER;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CraftlineDashboardBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){if(level.isClientSide())return null;return(tickLevel,pos,tickState,be)->{if(be instanceof CraftlineDashboardBlockEntity dashboard)CraftlineDashboardBlockEntity.serverTick(tickLevel,pos,tickState,dashboard);};}
}
