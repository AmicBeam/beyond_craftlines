package com.amicbeam.beyondcraftlines.common.block;

import com.amicbeam.beyondcraftlines.common.structure.AnchorSelectionSavedData;
import com.amicbeam.beyondcraftlines.common.structure.AnchorSelectionSavedData.Selection;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.amicbeam.beyondcraftlines.common.item.UnstableSchematicItem;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintLibrarySavedData;
import com.amicbeam.beyondcraftlines.common.structure.CaptureService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SchematicAnchorBlock extends Block
{
    public SchematicAnchorBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var data = AnchorSelectionSavedData.get(player.getServer());
        var current = data.get(player.getUUID());
        if (current == null || current.second() != null)
        {
            data.set(player.getUUID(), new Selection(pos, null));
            player.sendSystemMessage(Component.translatable("message.beyond_craftlines.anchor_first", pos.toShortString()));
            return InteractionResult.SUCCESS;
        }
        data.set(player.getUUID(), new Selection(current.first(), pos));
        player.sendSystemMessage(Component.translatable("message.beyond_craftlines.anchor_second", pos.toShortString()));
        return InteractionResult.SUCCESS;
    }

    public static boolean capture(Player player, String name)
    {
        var selection = AnchorSelectionSavedData.get(player.getServer()).get(player.getUUID());
        if (selection == null || selection.second() == null) return false;
        var validation = CaptureService.validate(player.level(), selection.first(), selection.second());
        if (!validation.valid())
        {
            player.sendSystemMessage(Component.translatable("error.beyond_craftlines.capture_" + validation.reason()));
            return false;
        }
        var library = BlueprintLibrarySavedData.get(player.getServer());
        var record = library.capture(player.level(), selection.first(), selection.second(), player.getUUID(), name);
        player.getInventory().placeItemBackInInventory(UnstableSchematicItem.of(library, record.id()));
        AnchorSelectionSavedData.get(player.getServer()).clear(player.getUUID());
        return true;
    }
}
