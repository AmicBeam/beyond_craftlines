package com.amicbeam.beyondcraftlines.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class CraftlineProvisionerItem extends BlockItem
{
    public CraftlineProvisionerItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.beyond_craftlines.provisioner.description")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.beyond_craftlines.provisioner.open_gui")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.beyond_craftlines.provisioner.wireless_binding")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.beyond_craftlines.provisioner.delivery_strategy")
                .withStyle(ChatFormatting.GRAY));
    }
}
