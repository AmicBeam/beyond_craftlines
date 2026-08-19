package com.amicbeam.beyondcraftlines.common.block;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.amicbeam.beyondcraftlines.common.runtime.ExecutorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SchematicExecutorBlock extends Block implements EntityBlock
{
    public SchematicExecutorBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExecutorBlockEntity executor)
        {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.beyond_craftlines.executor_status",
                    executor.statusSummary(), executor.networkId()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ExecutorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != CraftlinesBlockEntities.SCHEMATIC_EXECUTOR.get()) return null;
        return (ignoredLevel, ignoredPos, ignoredState, entity) -> ((com.amicbeam.beyondcraftlines.common.runtime.ExecutorBlockEntity) entity).serverTick();
    }
}
