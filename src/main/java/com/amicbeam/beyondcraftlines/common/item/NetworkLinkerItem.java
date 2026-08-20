package com.amicbeam.beyondcraftlines.common.item;

import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.amicbeam.beyondcraftlines.common.network.BindMachinePayload;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;

public final class NetworkLinkerItem extends Item
{
    public NetworkLinkerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag)
    { tooltip.add(Component.translatable("tooltip.beyond_craftlines.linker_direct_binding")); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand)
    {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide())
        {
            boolean cleared = DeviceBindingRegistry.clearSelectedProvisionerRecipes(player);
            player.displayClientMessage(Component.translatable(cleared
                    ? "message.beyond_craftlines.provisioner_recipes_cleared"
                    : "error.beyond_craftlines.no_selected_provisioner_recipes"), false);
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
            if (!context.getPlayer().isShiftKeyDown()) return InteractionResult.FAIL;
            if (!context.getLevel().isClientSide())
            {
                boolean selected = DeviceBindingRegistry.selectProvisioner(
                        context.getPlayer(), context.getClickedPos());
                context.getPlayer().displayClientMessage(Component.translatable(selected
                        ? "message.beyond_craftlines.provisioner_selected"
                        : "error.beyond_craftlines.provisioner_selection_failed"), false);
            }
            return InteractionResult.SUCCESS;
        }
        if (DeviceType.fromBlockId(blockId.toString()).isNativeBeyondRecipeMachine())
        {
            if (context.getLevel().isClientSide()) context.getPlayer().displayClientMessage(
                    Component.translatable("error.beyond_craftlines.native_machine_not_bindable"), false);
            return InteractionResult.FAIL;
        }
        if (!DeviceType.isThirdPartyMachine(blockId.toString()))
        {
            if (context.getLevel().isClientSide()) context.getPlayer().displayClientMessage(
                    Component.translatable("error.beyond_craftlines.only_third_party_machines"), false);
            return InteractionResult.FAIL;
        }
        if (context.getPlayer().isShiftKeyDown())
        {
            if (context.getLevel().isClientSide())
            {
                ItemStack catalyst = new ItemStack(
                        context.getLevel().getBlockState(context.getClickedPos()).getBlock().asItem());
                Set<ResourceLocation> types = com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                        .recipeTypesFor(catalyst);
                PacketDistributor.sendToServer(BindMachinePayload.of(context.getClickedPos(), types, true));
            }
            return InteractionResult.SUCCESS;
        }
        if (context.getLevel().isClientSide())
        {
            ItemStack catalyst = new ItemStack(
                    context.getLevel().getBlockState(context.getClickedPos()).getBlock().asItem());
            Set<ResourceLocation> types = com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                    .recipeTypesFor(catalyst);
            if (types.isEmpty())
            {
                context.getPlayer().displayClientMessage(Component.translatable(
                        "error.beyond_craftlines.machine_recipe_type_unknown"), false);
                return InteractionResult.FAIL;
            }
            PacketDistributor.sendToServer(BindMachinePayload.of(context.getClickedPos(), types, false));
        }
        return InteractionResult.SUCCESS;
    }
}
