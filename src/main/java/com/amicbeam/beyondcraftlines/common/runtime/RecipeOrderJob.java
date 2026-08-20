package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public record RecipeOrderJob(UUID id, UUID owner, int networkId, ResourceLocation target,
                             long requested, List<RecipePlan.Step> steps, int nextStep,
                             boolean blockingMode, Status status, String message,
                             long createdAt, long nextCraftingTick, ExternalWait externalWait)
{
    public enum Status { QUEUED, RUNNING, PAUSED, COMPLETE, CANCELLED, ERROR }

    public RecipeOrderJob
    {
        steps = List.copyOf(steps);
        message = message == null ? "" : message;
    }

    public RecipeOrderJob advance()
    {
        int next = nextStep + 1;
        return new RecipeOrderJob(id, owner, networkId, target, requested, steps, next,
                blockingMode, next >= steps.size() ? Status.COMPLETE : Status.RUNNING, "",
                createdAt, nextCraftingTick, null);
    }

    public RecipeOrderJob advanceAfterCrafting(long nextAllowedTick)
    {
        int next = nextStep + 1;
        return new RecipeOrderJob(id, owner, networkId, target, requested, steps, next,
                blockingMode, next >= steps.size() ? Status.COMPLETE : Status.RUNNING, "",
                createdAt, nextAllowedTick, null);
    }

    public RecipeOrderJob completeSingleCraft(long nextAllowedTick)
    {
        return completeCrafts(1, nextAllowedTick);
    }

    public RecipeOrderJob completeCrafts(long completedCrafts, long nextAllowedTick)
    {
        RecipePlan.Step current = steps.get(nextStep);
        if (completedCrafts < 1 || completedCrafts > current.crafts())
            throw new IllegalArgumentException("invalid completed craft count");
        if (completedCrafts == current.crafts()) return advanceAfterCrafting(nextAllowedTick);
        List<RecipePlan.Step> remaining = new java.util.ArrayList<>(steps);
        remaining.set(nextStep, new RecipePlan.Step(current.recipe(), current.family(), current.output(),
                current.outputPerCraft(), current.crafts() - completedCrafts, current.inputs(),
                current.ingredientSelections()));
        return new RecipeOrderJob(id, owner, networkId, target, requested, remaining, nextStep,
                blockingMode, Status.RUNNING, "", createdAt, nextAllowedTick, null);
    }

    public RecipeOrderJob completeExternalBatch()
    {
        if (!blockingMode || steps.get(nextStep).crafts() <= 1) return advance();
        List<RecipePlan.Step> remaining = new java.util.ArrayList<>(steps);
        RecipePlan.Step current = steps.get(nextStep);
        List<RecipePlan.Material> remainingInputs = new java.util.ArrayList<>();
        for (RecipePlan.Material input : current.inputs())
        {
            long amount = input.amount() - BlockingModeLogic.amountToDispatch(
                    true, input.amount(), current.crafts());
            if (amount > 0) remainingInputs.add(new RecipePlan.Material(input.item(), amount));
        }
        remaining.set(nextStep, new RecipePlan.Step(current.recipe(), current.family(), current.output(),
                current.outputPerCraft(), current.crafts() - 1, remainingInputs,
                current.ingredientSelections()));
        return new RecipeOrderJob(id, owner, networkId, target, requested, remaining, nextStep,
                true, Status.RUNNING, "", createdAt, nextCraftingTick, null);
    }

    public RecipeOrderJob with(Status next, String reason)
    {
        return new RecipeOrderJob(id, owner, networkId, target, requested, steps, nextStep,
                blockingMode, next, reason, createdAt, nextCraftingTick, externalWait);
    }

    public RecipeOrderJob awaitExternal(ExternalWait wait, String reason)
    {
        return new RecipeOrderJob(id, owner, networkId, target, requested, steps, nextStep,
                blockingMode, Status.PAUSED, reason, createdAt, nextCraftingTick, wait);
    }

    public record ExternalWait(ResourceKey<Level> machineDimension, BlockPos machinePosition,
                               ResourceLocation output, boolean nativeFurnace,
                               long baseline, long networkBaseline, long networkObserved,
                               long amount, long collected,
                               List<RecipePlan.Material> remainingInputs)
    {
        public ExternalWait
        {
            if (machineDimension == null || machinePosition == null || output == null
                    || baseline < 0 || networkBaseline < 0 || networkObserved < 0 || amount < 1
                    || collected < 0 || collected > amount)
                throw new IllegalArgumentException("invalid external wait");
            remainingInputs = List.copyOf(remainingInputs);
        }

        public ExternalWait withInputs(List<RecipePlan.Material> inputs)
        { return new ExternalWait(machineDimension, machinePosition, output, nativeFurnace,
                baseline, networkBaseline, networkObserved, amount, collected, inputs); }

        public ExternalWait withCollected(long value)
        { return new ExternalWait(machineDimension, machinePosition, output, nativeFurnace,
                baseline, networkBaseline, networkObserved, amount, value, remainingInputs); }

        public ExternalWait withProgress(long observed, long value)
        { return new ExternalWait(machineDimension, machinePosition, output, nativeFurnace,
                baseline, networkBaseline, observed, amount, value, remainingInputs); }
    }
}
