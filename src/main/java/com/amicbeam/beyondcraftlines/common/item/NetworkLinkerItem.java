package com.amicbeam.beyondcraftlines.common.item;

import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.amicbeam.beyondcraftlines.common.network.BindMachinePayload;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public final class NetworkLinkerItem extends Item
{
    public NetworkLinkerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos position, Player player)
    { return !player.isCreative(); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.beyond_craftlines.linker.description")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand)
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
            player.displayClientMessage(Component.translatable(message), false);
            com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload.sendTo(
                    (net.minecraft.server.level.ServerPlayer) player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override public InteractionResult useOn(UseOnContext context)
    {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                context.getLevel().getBlockState(context.getClickedPos()).getBlock());
        if (context.getLevel().getBlockState(context.getClickedPos()).is(CraftlinesBlocks.CRAFTLINE_PROVISIONER.get()))
        {
            if (!context.getLevel().isClientSide())
            {
                boolean connectionMode = !context.getPlayer().isShiftKeyDown();
                boolean selected = connectionMode
                        ? DeviceBindingRegistry.selectProvisionerConnections(context.getPlayer(), context.getClickedPos())
                        : DeviceBindingRegistry.selectProvisioner(context.getPlayer(), context.getClickedPos());
                context.getPlayer().displayClientMessage(Component.translatable(selected
                        ? connectionMode ? "message.beyond_craftlines.provisioner_connection_mode_selected"
                        : "message.beyond_craftlines.provisioner_selected"
                        : "error.beyond_craftlines.provisioner_selection_failed"), false);
                if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                    com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload.sendTo(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        if (DeviceType.fromBlockId(blockId.toString()).isNativeBeyondRecipeMachine())
        {
            if (context.getLevel().isClientSide()) context.getPlayer().displayClientMessage(
                    Component.translatable("error.beyond_craftlines.native_machine_not_bindable"), false);
            return InteractionResult.FAIL;
        }
        if (!DeviceType.isBindableMachine(blockId.toString()))
        {
            if (context.getLevel().isClientSide()) context.getPlayer().displayClientMessage(
                    Component.translatable("error.beyond_craftlines.network_component_not_bindable"), false);
            return InteractionResult.FAIL;
        }
        if (context.getPlayer().isShiftKeyDown())
        {
            if (context.getLevel().isClientSide())
            {
                ItemStack catalyst = new ItemStack(
                        context.getLevel().getBlockState(context.getClickedPos()).getBlock().asItem());
                Set<ResourceLocation> types = recipeTypes(catalyst, blockId);
                PacketDistributor.sendToServer(BindMachinePayload.of(context.getClickedPos(), types,
                        context.getClickedFace(),
                        com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex.hintsFor(types), true));
            }
            return InteractionResult.SUCCESS;
        }
        if (context.getLevel().isClientSide())
        {
            ItemStack catalyst = new ItemStack(
                    context.getLevel().getBlockState(context.getClickedPos()).getBlock().asItem());
            Set<ResourceLocation> types = recipeTypes(catalyst, blockId);
            PacketDistributor.sendToServer(BindMachinePayload.of(context.getClickedPos(), types,
                    context.getClickedFace(),
                    com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex.hintsFor(types), false));
        }
        return InteractionResult.SUCCESS;
    }

    private static Set<ResourceLocation> recipeTypes(ItemStack catalyst, ResourceLocation blockId)
    {
        LinkedHashSet<ResourceLocation> types = new LinkedHashSet<>(
                com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                        .recipeTypesFor(catalyst));
        // Several JEI integrations (notably Mekanism 1.20.1) use the machine block id as their
        // category id. Preserve that authoritative fallback when JEI's catalyst snapshot is late
        // or omits a machine, and let the server validate it against loaded recipe families.
        types.add(blockId);
        return Set.copyOf(types);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context)
    {
        // Machines commonly consume the normal block interaction to open their menu before Item#useOn runs.
        // A linker is a tool interaction, so handle it in NeoForge's pre-block hook and consume the click.
        return useOn(context);
    }
}
