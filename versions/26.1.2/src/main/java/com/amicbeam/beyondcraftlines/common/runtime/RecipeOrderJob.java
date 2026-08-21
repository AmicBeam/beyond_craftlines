package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public record RecipeOrderJob(UUID id, UUID owner, int networkId, Identifier target,
                             long requested, List<StepExecution> executions, int activeStep,
                             boolean blockingMode, Status status, String message,
                             long createdAt, long finishedAt,
                             List<RecipePlan.ReservedMaterial> reserved)
{
    public enum Status { QUEUED, RUNNING, PAUSED, COMPLETE, CANCELLED, ERROR }

    public RecipeOrderJob
    {
        executions = List.copyOf(executions);
        reserved = List.copyOf(reserved);
        message = message == null ? "" : message;
        if (activeStep < -1 || activeStep >= executions.size())
            throw new IllegalArgumentException("invalid active recipe step");
    }

    /** Loads the pre-parallel order shape conservatively as a serial dependency chain. */
    public RecipeOrderJob(UUID id, UUID owner, int networkId, Identifier target,
                          long requested, List<RecipePlan.Step> steps, int nextStep,
                          boolean blockingMode, Status status, String message,
                          long createdAt, long finishedAt, long nextCraftingTick, ExternalWait externalWait,
                          List<RecipePlan.ReservedMaterial> reserved)
    {
        this(id, owner, networkId, target, requested,
                migrateLegacy(steps, nextStep, nextCraftingTick, externalWait), -1,
                blockingMode, status, message, createdAt, finishedAt, reserved);
    }

    public static RecipeOrderJob create(UUID id, UUID owner, int networkId, Identifier target,
                                        long requested, List<RecipePlan.Step> steps, boolean blockingMode,
                                        Status status, String message, long createdAt, long finishedAt,
                                        List<RecipePlan.ReservedMaterial> reserved)
    {
        return new RecipeOrderJob(id, owner, networkId, target, requested,
                steps.stream().map(StepExecution::pending).toList(), -1, blockingMode, status,
                message, createdAt, finishedAt, reserved);
    }

    public List<RecipePlan.Step> steps()
    { return executions.stream().map(StepExecution::step).toList(); }

    /** Constant-time accessors for the server tick hot path. */
    public int stepCount() { return executions.size(); }
    public RecipePlan.Step step(int index) { return executions.get(index).step(); }

    public int nextStep()
    {
        if (activeStep >= 0) return activeStep;
        for (int i = 0; i < executions.size(); i++) if (!executions.get(i).complete()) return i;
        return executions.size();
    }

    public long nextCraftingTick()
    { return activeStep >= 0 ? executions.get(activeStep).nextCraftingTick() : 0; }

    public ExternalWait externalWait()
    {
        if (activeStep >= 0) return executions.get(activeStep).externalWait();
        return executions.stream().map(StepExecution::externalWait).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    public RecipeOrderJob activate(int step)
    {
        if (activeStep >= 0 || step < 0 || step >= executions.size() || executions.get(step).complete())
            throw new IllegalStateException("recipe step cannot be activated");
        return copy(executions, step, status, message, createdAt, finishedAt, reserved);
    }

    public RecipeOrderJob deactivate()
    { return copy(executions, -1, status, message, createdAt, finishedAt, reserved); }

    public boolean dependenciesComplete(int step)
    { return StepDependencyGraph.ready(step, executions.size(), executions.get(step).step().dependencies(),
            dependency -> executions.get(dependency).complete()); }

    public RecipeOrderJob advance()
    { return advanceAfterCrafting(nextCraftingTick()); }

    public RecipeOrderJob advanceAfterCrafting(long nextAllowedTick)
    {
        requireActive();
        List<StepExecution> updated = new java.util.ArrayList<>(executions);
        updated.set(activeStep, executions.get(activeStep).completed(nextAllowedTick));
        Status next = updated.stream().allMatch(StepExecution::complete) ? Status.COMPLETE : Status.RUNNING;
        return copy(updated, activeStep, next, "", createdAt, finishedAt, reserved);
    }

    public RecipeOrderJob completeSingleCraft(long nextAllowedTick)
    {
        return completeCrafts(1, nextAllowedTick);
    }

    public RecipeOrderJob completeCrafts(long completedCrafts, long nextAllowedTick)
    {
        requireActive();
        RecipePlan.Step current = executions.get(activeStep).step();
        if (completedCrafts < 1 || completedCrafts > current.crafts())
            throw new IllegalArgumentException("invalid completed craft count");
        if (completedCrafts == current.crafts()) return advanceAfterCrafting(nextAllowedTick);
        List<StepExecution> updated = new java.util.ArrayList<>(executions);
        RecipePlan.Step remaining = new RecipePlan.Step(current.recipe(), current.family(), current.outputKey(),
                current.outputPerCraft(), current.crafts() - completedCrafts, current.inputs(),
                current.ingredientSelections(), current.dependencies());
        updated.set(activeStep, executions.get(activeStep).with(remaining, nextAllowedTick, null));
        return copy(updated, activeStep, Status.RUNNING, "", createdAt, finishedAt, reserved);
    }

    public RecipeOrderJob completeExternalBatch()
    {
        requireActive();
        if (!blockingMode || executions.get(activeStep).step().crafts() <= 1) return advance();
        List<StepExecution> updated = new java.util.ArrayList<>(executions);
        RecipePlan.Step current = executions.get(activeStep).step();
        List<RecipePlan.Material> remainingInputs = new java.util.ArrayList<>();
        for (RecipePlan.Material input : current.inputs())
        {
            long amount = input.amount() - BlockingModeLogic.amountToDispatch(
                    true, input.amount(), current.crafts());
            if (amount > 0) remainingInputs.add(new RecipePlan.Material(
                    input.key(), amount, input.ingredientSlot()));
        }
        RecipePlan.Step remaining = new RecipePlan.Step(current.recipe(), current.family(), current.outputKey(),
                current.outputPerCraft(), current.crafts() - 1, remainingInputs,
                current.ingredientSelections(), current.dependencies());
        updated.set(activeStep, executions.get(activeStep).with(remaining, nextCraftingTick(), null));
        return copy(updated, activeStep, Status.RUNNING, "", createdAt, finishedAt, reserved);
    }

    public RecipeOrderJob with(Status next, String reason)
    {
        return copy(executions, activeStep, next, reason, createdAt, finishedAt, reserved);
    }

    public RecipeOrderJob awaitExternal(ExternalWait wait, String reason)
    {
        requireActive();
        List<StepExecution> updated = new java.util.ArrayList<>(executions);
        updated.set(activeStep, executions.get(activeStep).with(
                executions.get(activeStep).step(), nextCraftingTick(), wait));
        return copy(updated, activeStep, Status.PAUSED, reason, createdAt, finishedAt, reserved);
    }

    public RecipeOrderJob withReserved(List<RecipePlan.ReservedMaterial> values)
    {
        return copy(executions, activeStep, status, message, createdAt, finishedAt, values);
    }

    public RecipeOrderJob finishedAt(long gameTime)
    {
        return copy(executions, activeStep, status, message, createdAt, Math.max(1, gameTime), reserved);
    }

    private void requireActive()
    { if (activeStep < 0) throw new IllegalStateException("no active recipe step"); }

    private RecipeOrderJob copy(List<StepExecution> nextExecutions, int nextActive, Status nextStatus,
                                String nextMessage, long nextCreatedAt, long nextFinishedAt,
                                List<RecipePlan.ReservedMaterial> nextReserved)
    {
        return new RecipeOrderJob(id, owner, networkId, target, requested, nextExecutions, nextActive,
                blockingMode, nextStatus, nextMessage, nextCreatedAt, nextFinishedAt, nextReserved);
    }

    private static List<StepExecution> migrateLegacy(List<RecipePlan.Step> steps, int nextStep,
                                                     long nextCraftingTick, ExternalWait externalWait)
    {
        List<StepExecution> migrated = new java.util.ArrayList<>();
        for (int i = 0; i < steps.size(); i++)
        {
            RecipePlan.Step source = steps.get(i);
            List<Integer> dependencies = source.dependencies().isEmpty() && i > 0
                    ? java.util.stream.IntStream.range(0, i).boxed().toList() : source.dependencies();
            RecipePlan.Step step = new RecipePlan.Step(source.recipe(), source.family(), source.outputKey(),
                    source.outputPerCraft(), source.crafts(), source.inputs(),
                    source.ingredientSelections(), dependencies);
            migrated.add(new StepExecution(step, i < nextStep,
                    i == nextStep ? nextCraftingTick : 0, i == nextStep ? externalWait : null));
        }
        return List.copyOf(migrated);
    }

    public record StepExecution(RecipePlan.Step step, boolean complete, long nextCraftingTick,
                                ExternalWait externalWait)
    {
        public StepExecution
        {
            if (step == null || nextCraftingTick < 0 || complete && externalWait != null)
                throw new IllegalArgumentException("invalid recipe step execution");
        }
        static StepExecution pending(RecipePlan.Step step) { return new StepExecution(step, false, 0, null); }
        StepExecution completed(long tick) { return new StepExecution(step, true, tick, null); }
        StepExecution with(RecipePlan.Step value, long tick, ExternalWait wait)
        { return new StepExecution(value, false, tick, wait); }
    }

    public record ExternalWait(ResourceKey<Level> machineDimension, BlockPos machinePosition,
                               IStackKey<?> outputKey, boolean nativeFurnace, boolean provisioner,
                               long baseline, long networkBaseline, long networkObserved,
                               long amount, long collected,
                               List<RecipePlan.Material> remainingInputs,
                               List<RecipePlan.ReservedMaterial> networkBaselineStacks)
    {
        public ExternalWait(ResourceKey<Level> machineDimension, BlockPos machinePosition,
                            Identifier output, boolean nativeFurnace, boolean provisioner,
                            long baseline, long networkBaseline, long networkObserved,
                            long amount, long collected, List<RecipePlan.Material> remainingInputs,
                            List<RecipePlan.ReservedMaterial> networkBaselineStacks)
        { this(machineDimension, machinePosition, new ItemStackKey(new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(output))), nativeFurnace, provisioner,
                baseline, networkBaseline, networkObserved, amount, collected, remainingInputs,
                networkBaselineStacks); }

        public ExternalWait
        {
            if (machineDimension == null || machinePosition == null || outputKey == null || outputKey.isEmpty()
                    || baseline < 0 || networkBaseline < 0 || networkObserved < 0 || amount < 1
                    || collected < 0 || collected > amount)
                throw new IllegalArgumentException("invalid external wait");
            remainingInputs = List.copyOf(remainingInputs);
            networkBaselineStacks = List.copyOf(networkBaselineStacks);
        }

        public ExternalWait withInputs(List<RecipePlan.Material> inputs)
        { return new ExternalWait(machineDimension, machinePosition, outputKey, nativeFurnace, provisioner,
                baseline, networkBaseline, networkObserved, amount, collected, inputs, networkBaselineStacks); }

        public ExternalWait withCollected(long value)
        { return new ExternalWait(machineDimension, machinePosition, outputKey, nativeFurnace, provisioner,
                baseline, networkBaseline, networkObserved, amount, value, remainingInputs, networkBaselineStacks); }

        public ExternalWait withProgress(long observed, long value)
        { return new ExternalWait(machineDimension, machinePosition, outputKey, nativeFurnace, provisioner,
                baseline, networkBaseline, observed, amount, value, remainingInputs, networkBaselineStacks); }

        public Identifier output()
        {
            if (outputKey instanceof ItemStackKey item)
                return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getSource());
            return outputKey.getTypeId();
        }
    }
}
