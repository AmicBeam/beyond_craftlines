package com.amicbeam.beyondcraftlines.common.item;

import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.amicbeam.beyondcraftlines.common.network.BindMachinePayload;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import java.util.function.BiFunction;

public final class NetworkLinkerItem extends Item
{
    public static volatile BiFunction<UseOnContext, Boolean, InteractionResult> CLIENT_BIND_REQUEST =
            (context, unbind) -> InteractionResult.SUCCESS;

    public NetworkLinkerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level,
                                   BlockPos position, LivingEntity user)
    { return !(user instanceof Player player && player.getAbilities().instabuild); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand)
    {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide())
        {
            var result = DeviceBindingRegistry.useLinkerInAir(player);
            String message = switch (result)
            {
                case RECIPES_CLEARED -> "message.beyond_craftlines.provisioner_recipes_cleared";
                case CONNECTION_MODE_CLEARED -> "message.beyond_craftlines.provisioner_connection_mode_cleared";
                case NOTHING -> "error.beyond_craftlines.no_selected_provisioner_recipes";
            };
            player.sendSystemMessage(Component.translatable(message));
            com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload.sendTo(
                    (net.minecraft.server.level.ServerPlayer) player);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override public InteractionResult useOn(UseOnContext context)
    {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(
                context.getLevel().getBlockState(context.getClickedPos()).getBlock());
        if (context.getLevel().getBlockState(context.getClickedPos()).is(CraftlinesBlocks.CRAFTLINE_PROVISIONER.get()))
        {
            if (!context.getLevel().isClientSide())
            {
                boolean connectionMode = !context.getPlayer().isShiftKeyDown();
                boolean selected = connectionMode
                        ? DeviceBindingRegistry.selectProvisionerConnections(context.getPlayer(), context.getClickedPos())
                        : DeviceBindingRegistry.selectProvisioner(context.getPlayer(), context.getClickedPos());
                context.getPlayer().sendSystemMessage(Component.translatable(selected
                        ? connectionMode ? "message.beyond_craftlines.provisioner_connection_mode_selected"
                        : "message.beyond_craftlines.provisioner_selected"
                        : "error.beyond_craftlines.provisioner_selection_failed"));
                if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                    com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload.sendTo(serverPlayer);
            }
            return context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        if (DeviceType.fromBlockId(blockId.toString()).isNativeBeyondRecipeMachine())
        {
            if (context.getLevel().isClientSide()) context.getPlayer().sendSystemMessage(
                    Component.translatable("error.beyond_craftlines.native_machine_not_bindable"));
            return InteractionResult.FAIL;
        }
        if (!DeviceType.isBindableMachine(blockId.toString()))
        {
            if (context.getLevel().isClientSide()) context.getPlayer().sendSystemMessage(
                    Component.translatable("error.beyond_craftlines.network_component_not_bindable"));
            return InteractionResult.FAIL;
        }
        if (context.getLevel().isClientSide())
            return CLIENT_BIND_REQUEST.apply(context, context.getPlayer().isShiftKeyDown());
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context)
    {
        // Machines commonly consume the normal block interaction to open their menu before Item#useOn runs.
        // A linker is a tool interaction, so handle it in NeoForge's pre-block hook and consume the click.
        return useOn(context);
    }
}
