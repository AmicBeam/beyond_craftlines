package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class TrialReportCodec
{
    private TrialReportCodec() {}

    public static CompoundTag write(TrialObservation observation)
    {
        if (observation == null) throw new IllegalArgumentException("observation is required");
        CompoundTag tag = new CompoundTag();
        tag.put("inputs", writeAmounts(observation.inputs()));
        tag.put("outputs", writeAmounts(observation.outputs()));
        tag.put("fluid_inputs", writeFluids(observation.fluidInputs()));
        tag.put("fluid_outputs", writeFluids(observation.fluidOutputs()));
        tag.putLong("energy_net", observation.energyNet());
        tag.putLong("cycle_ticks", observation.cycleTicks());
        return tag;
    }

    public static TrialObservation read(CompoundTag tag)
    {
        if (tag == null) throw new IllegalArgumentException("tag is required");
        return new TrialObservation(
                readAmounts(tag.getList("inputs", Tag.TAG_COMPOUND)),
                readAmounts(tag.getList("outputs", Tag.TAG_COMPOUND)),
                readFluids(tag.getList("fluid_inputs", Tag.TAG_COMPOUND)),
                readFluids(tag.getList("fluid_outputs", Tag.TAG_COMPOUND)),
                tag.getLong("energy_net"),
                tag.getLong("cycle_ticks"));
    }

    private static ListTag writeAmounts(List<ResourceAmount> amounts)
    {
        ListTag result = new ListTag();
        for (ResourceAmount amount : amounts)
        {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", amount.itemId().toString());
            entry.putLong("amount", amount.amount());
            result.add(entry);
        }
        return result;
    }

    private static ListTag writeFluids(List<FluidAmount> amounts)
    {
        ListTag result = new ListTag();
        for (FluidAmount amount : amounts)
        {
            CompoundTag entry = new CompoundTag();
            entry.putString("fluid", amount.fluidId().toString());
            entry.putLong("amount", amount.amount());
            result.add(entry);
        }
        return result;
    }

    private static List<FluidAmount> readFluids(ListTag list)
    {
        List<FluidAmount> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            result.add(new FluidAmount(ResourceLocation.parse(entry.getString("fluid")), entry.getLong("amount")));
        }
        return result;
    }

    private static List<ResourceAmount> readAmounts(ListTag list)
    {
        List<ResourceAmount> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            result.add(new ResourceAmount(ResourceLocation.parse(entry.getString("item")), entry.getLong("amount")));
        }
        return result;
    }
}
