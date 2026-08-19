package com.amicbeam.beyondcraftlines.common.block;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.amicbeam.beyondcraftlines.common.runtime.DuplicatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SchematicDuplicatorBlock extends Block implements EntityBlock
{
    public SchematicDuplicatorBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DuplicatorBlockEntity duplicator)
        {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty() && duplicator.duplicate(player, held))
            {
                player.sendSystemMessage(Component.translatable("message.beyond_craftlines.duplicated"));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.translatable("error.beyond_craftlines.duplicate_failed"));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new DuplicatorBlockEntity(pos, state);
    }
}
