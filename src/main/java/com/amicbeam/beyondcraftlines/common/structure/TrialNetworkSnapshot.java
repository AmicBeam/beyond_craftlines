package com.amicbeam.beyondcraftlines.common.structure;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public record TrialNetworkSnapshot(int networkId, Map<String, Long> items, Map<String, Long> fluids, long energy)
{
    public TrialNetworkSnapshot
    {
        items = Map.copyOf(items);
        fluids = Map.copyOf(fluids);
        if (energy < 0) throw new IllegalArgumentException("energy must not be negative");
    }

    public static TrialNetworkSnapshot capture(int networkId)
    {
        DimensionsNet net = DimensionsNet.getNetFromId(networkId);
        if (net == null || net.deleted) throw new IllegalStateException("network is not available");
        UnifiedStorage storage = net.getUnifiedStorage();
        Map<String, Long> items = new LinkedHashMap<>();
        Map<String, Long> fluids = new LinkedHashMap<>();
        for (KeyAmount entry : storage.getStorage())
        {
            if (entry.isEmpty()) continue;
            IStackKey<?> key = entry.key();
            Object stack = key.getReadOnlyStack();
            if (stack instanceof ItemStack item && !item.isEmpty())
            {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
                items.merge(id.toString(), entry.amount(), Math::addExact);
            }
            else if (stack instanceof FluidStack fluid && !fluid.isEmpty())
            {
                ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
                fluids.merge(id.toString(), entry.amount(), Math::addExact);
            }
        }
        long energy = Math.max(0L, storage.getStackByKey(EnergyStackKey.INSTANCE).amount());
        return new TrialNetworkSnapshot(networkId, items, fluids, energy);
    }
}
