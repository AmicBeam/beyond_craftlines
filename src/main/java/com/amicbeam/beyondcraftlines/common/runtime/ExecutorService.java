package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintRecord;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintLibrarySavedData;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintReferenceValidator;
import com.amicbeam.beyondcraftlines.common.structure.CompiledBlueprint;
import com.amicbeam.beyondcraftlines.common.structure.ResourceAmount;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ExecutorService
{
    private ExecutorService() {}

    public static boolean matchesBlueprint(ServerLevel level, UUID blueprintId, String expectedHash)
    {
        return BlueprintLibrarySavedData.get(level.getServer()).get(blueprintId)
                .filter(record -> record.compiled() != null
                        && BlueprintReferenceValidator.isValid("beyond_craftlines", blueprintId.toString(), expectedHash)
                        && expectedHash.equals(record.compiled().structureHash())
                        && expectedHash.equals(record.snapshot().hash()))
                .isPresent();
    }

    public static Optional<ExecutorState> begin(ServerLevel level, UUID blueprintId, int networkId, long now)
    {
        BlueprintLibrarySavedData library = BlueprintLibrarySavedData.get(level.getServer());
        Optional<BlueprintRecord> record = library.get(blueprintId);
        if (record.isEmpty() || record.get().state() != BlueprintRecord.State.COMPILED) return Optional.empty();

        DimensionsNet net = DimensionsNet.getNetFromId(networkId);
        if (net == null) return Optional.empty();
        CompiledBlueprint compiled = record.get().compiled();
        if (compiled == null || !compiled.structureHash().equals(record.get().snapshot().hash())) return Optional.empty();
        UnifiedStorage storage = net.getUnifiedStorage();
        if (!hasAmounts(storage, compiled.inputs()) || !hasFluids(storage, compiled.fluidInputs())
                || compiled.energyNet() > 0
                && storage.getStackByKey(EnergyStackKey.INSTANCE).amount() < compiled.energyNet())
            return Optional.empty();

        List<ResourceAmount> extracted = new java.util.ArrayList<>();
        List<com.amicbeam.beyondcraftlines.common.structure.FluidAmount> extractedFluids = new java.util.ArrayList<>();
        for (ResourceAmount amount : compiled.inputs())
        {
            if (amount.amount() == 0) continue;
            if (!storage.extract(keyFor(amount), amount.amount(), false, false).isEmpty())
            {
                refund(storage, extracted, extractedFluids, 0);
                return Optional.empty();
            }
            extracted.add(amount);
        }
        for (var amount : compiled.fluidInputs())
        {
            if (amount.amount() == 0) continue;
            if (!storage.extract(fluidKey(amount), amount.amount(), false, false).isEmpty())
            {
                refund(storage, extracted, extractedFluids, 0);
                return Optional.empty();
            }
            extractedFluids.add(amount);
        }
        if (compiled.energyNet() > 0
                && !storage.extract(EnergyStackKey.INSTANCE, compiled.energyNet(), false, false).isEmpty())
        {
            refund(storage, extracted, extractedFluids, 0);
            return Optional.empty();
        }

        return Optional.of(new ExecutorState(ExecutorState.Status.RUNNING, now,
                now + compiled.cycleTicks(), compiled.structureHash()));
    }

    private static boolean hasAmounts(UnifiedStorage storage, List<ResourceAmount> amounts)
    {
        for (ResourceAmount amount : amounts)
        {
            if (amount.amount() > 0 && !storage.extract(keyFor(amount), amount.amount(), true, false).isEmpty()) return false;
        }
        return true;
    }

    private static boolean hasFluids(UnifiedStorage storage, List<com.amicbeam.beyondcraftlines.common.structure.FluidAmount> amounts)
    {
        for (var amount : amounts)
            if (amount.amount() > 0 && !storage.extract(fluidKey(amount), amount.amount(), true, false).isEmpty()) return false;
        return true;
    }

    private static void refund(UnifiedStorage storage, List<ResourceAmount> amounts,
                               List<com.amicbeam.beyondcraftlines.common.structure.FluidAmount> fluids,
                               long energy)
    {
        for (ResourceAmount amount : amounts)
            if (amount.amount() > 0) storage.insert(keyFor(amount), amount.amount(), false);
        for (var amount : fluids)
            if (amount.amount() > 0) storage.insert(fluidKey(amount), amount.amount(), false);
        if (energy > 0) storage.insert(EnergyStackKey.INSTANCE, energy, false);
    }

    private static void refundOutputs(UnifiedStorage storage, List<ResourceAmount> items,
                                      List<com.amicbeam.beyondcraftlines.common.structure.FluidAmount> fluids)
    {
        for (ResourceAmount amount : items)
            storage.extract(keyFor(amount), amount.amount(), false, false);
        for (var amount : fluids)
            storage.extract(fluidKey(amount), amount.amount(), false, false);
    }

    private static ItemStackKey keyFor(ResourceAmount amount)
    {
        Item item = BuiltInRegistries.ITEM.get(amount.itemId());
        return new ItemStackKey(new ItemStack(item));
    }

    private static FluidStackKey fluidKey(com.amicbeam.beyondcraftlines.common.structure.FluidAmount amount)
    {
        var fluid = BuiltInRegistries.FLUID.get(amount.fluidId());
        return new FluidStackKey(new FluidStack(fluid, 1));
    }

    public static ExecutorState tick(ServerLevel level, UUID blueprintId, int networkId, ExecutorState state, long now)
    {
        if ((state.status() != ExecutorState.Status.RUNNING && state.status() != ExecutorState.Status.PAUSED)
                || now < state.finishAt()) return state;

        BlueprintLibrarySavedData library = BlueprintLibrarySavedData.get(level.getServer());
        Optional<BlueprintRecord> record = library.get(blueprintId);
        DimensionsNet net = DimensionsNet.getNetFromId(networkId);
        if (record.isEmpty() || record.get().compiled() == null || net == null) {
            return new ExecutorState(ExecutorState.Status.ERROR, state.startedAt(), state.finishAt(), state.blueprintHash());
        }

        CompiledBlueprint compiled = record.get().compiled();
        UnifiedStorage storage = net.getUnifiedStorage();
        for (ResourceAmount amount : compiled.outputs())
        {
            if (amount.amount() > 0 && !storage.insert(keyFor(amount), amount.amount(), true).isEmpty()) {
                return new ExecutorState(ExecutorState.Status.PAUSED, state.startedAt(), state.finishAt(), state.blueprintHash());
            }
        }
        for (var amount : compiled.fluidOutputs())
        {
            if (amount.amount() > 0 && !storage.insert(fluidKey(amount), amount.amount(), true).isEmpty()) {
                return new ExecutorState(ExecutorState.Status.PAUSED, state.startedAt(), state.finishAt(), state.blueprintHash());
            }
        }
        List<ResourceAmount> insertedItems = new java.util.ArrayList<>();
        for (ResourceAmount amount : compiled.outputs())
        {
            if (amount.amount() == 0) continue;
            if (!storage.insert(keyFor(amount), amount.amount(), false).isEmpty()) {
                refundOutputs(storage, insertedItems, List.of());
                return new ExecutorState(ExecutorState.Status.ERROR, state.startedAt(), state.finishAt(), state.blueprintHash());
            }
            insertedItems.add(amount);
        }
        List<com.amicbeam.beyondcraftlines.common.structure.FluidAmount> insertedFluids = new java.util.ArrayList<>();
        for (var amount : compiled.fluidOutputs())
        {
            if (amount.amount() == 0) continue;
            if (!storage.insert(fluidKey(amount), amount.amount(), false).isEmpty()) {
                refundOutputs(storage, insertedItems, insertedFluids);
                return new ExecutorState(ExecutorState.Status.ERROR, state.startedAt(), state.finishAt(), state.blueprintHash());
            }
            insertedFluids.add(amount);
        }
        return new ExecutorState(ExecutorState.Status.IDLE, 0, 0, state.blueprintHash());
    }
}
