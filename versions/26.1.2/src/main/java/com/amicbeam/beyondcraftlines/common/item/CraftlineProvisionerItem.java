package com.amicbeam.beyondcraftlines.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public final class CraftlineProvisionerItem extends BlockItem
{
    public CraftlineProvisionerItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable("tooltip.beyond_craftlines.provisioner.description")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.beyond_craftlines.provisioner.open_gui")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.beyond_craftlines.provisioner.wireless_binding")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.beyond_craftlines.provisioner.delivery_strategy")
                .withStyle(ChatFormatting.GRAY));
    }
}
