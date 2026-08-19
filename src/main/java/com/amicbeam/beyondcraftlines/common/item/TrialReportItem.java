package com.amicbeam.beyondcraftlines.common.item;

import com.amicbeam.beyondcraftlines.common.structure.TrialObservation;
import com.amicbeam.beyondcraftlines.common.structure.TrialReportCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public final class TrialReportItem extends Item
{
    public TrialReportItem(Properties properties) { super(properties.stacksTo(1)); }

    public static ItemStack of(TrialObservation observation)
    {
        return of(observation, null, null);
    }

    public static ItemStack of(TrialObservation observation, java.util.UUID blueprintId, String structureHash)
    {
        CompoundTag tag = TrialReportCodec.write(observation);
        if (blueprintId != null) tag.putUUID("blueprint_id", blueprintId);
        if (structureHash != null && !structureHash.isBlank()) tag.putString("structure_hash", structureHash);
        ItemStack stack = new ItemStack(com.amicbeam.beyondcraftlines.common.init.CraftlinesItems.TRIAL_REPORT.get());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        return stack;
    }

    public static TrialObservation read(ItemStack stack)
    {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? null : TrialReportCodec.read(data.copyTag());
    }

    public static java.util.UUID blueprintId(ItemStack stack)
    {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().hasUUID("blueprint_id")) return null;
        return data.copyTag().getUUID("blueprint_id");
    }

    public static String structureHash(ItemStack stack)
    {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString("structure_hash");
    }

    public static boolean matches(ItemStack stack, java.util.UUID blueprintId, String structureHash)
    {
        return stack.is(com.amicbeam.beyondcraftlines.common.init.CraftlinesItems.TRIAL_REPORT.get())
                && blueprintId != null && blueprintId.equals(blueprintId(stack))
                && java.util.Objects.equals(structureHash, structureHash(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag)
    {
        TrialObservation observation = read(stack);
        if (observation == null) return;
        tooltip.add(Component.translatable("tooltip.beyond_craftlines.trial_report.inputs",
                observation.inputs().size(), observation.fluidInputs().size()));
        tooltip.add(Component.translatable("tooltip.beyond_craftlines.trial_report.outputs",
                observation.outputs().size(), observation.fluidOutputs().size()));
        tooltip.add(Component.translatable("tooltip.beyond_craftlines.trial_report.energy",
                observation.energyNet(), observation.cycleTicks()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        TrialObservation observation = read(context.getItemInHand());
        if (player == null || observation == null)
        {
            if (player != null) player.sendSystemMessage(Component.translatable("error.beyond_craftlines.trial_report_empty"));
            return InteractionResult.FAIL;
        }
        player.sendSystemMessage(Component.translatable("message.beyond_craftlines.trial_report",
                observation.inputs().size(), observation.outputs().size(), observation.energyNet(), observation.cycleTicks()));
        return InteractionResult.SUCCESS;
    }
}
