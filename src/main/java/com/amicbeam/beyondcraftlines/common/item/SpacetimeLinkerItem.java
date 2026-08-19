package com.amicbeam.beyondcraftlines.common.item;

import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.runtime.DuplicatorBlockEntity;
import com.amicbeam.beyondcraftlines.common.runtime.ExecutorBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class SpacetimeLinkerItem extends Item
{
    public SpacetimeLinkerItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        if (!context.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;

        boolean wasBound = DeviceBindingRegistry.find(
                com.amicbeam.beyondcraftlines.common.data.BindingSavedData.get(context.getPlayer().getServer()),
                context.getLevel().dimension(), context.getClickedPos()).isPresent();
        DeviceBindingRegistry.toggle(context.getPlayer(), context.getClickedPos(), context.getLevel().getBlockState(context.getClickedPos()));
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ExecutorBlockEntity executor && !wasBound) {
            var net = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet.getNetFromPlayer(context.getPlayer());
            if (net != null && net.isManager(context.getPlayer())) executor.setNetworkId(net.getId());
        }
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof DuplicatorBlockEntity duplicator && !wasBound) {
            var net = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet.getNetFromPlayer(context.getPlayer());
            if (net != null && net.isManager(context.getPlayer())) duplicator.setNetworkId(net.getId());
        }
        context.getPlayer().sendSystemMessage(Component.translatable(wasBound
                ? "message.beyond_craftlines.device_unbound"
                : "message.beyond_craftlines.device_bound"));
        return InteractionResult.SUCCESS;
    }
}
