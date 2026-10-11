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
                             boolean blockingMode, OrderOutputDestination outputDestination,
                             OrderOrigin origin,
                             Status status, String message,
                             long createdAt, long finishedAt,
                             List<RecipePlan.ReservedMaterial> reserved)
{
    public enum Status { QUEUED, RUNNING, PAUSED, COMPLETE, CANCELLED, ERROR }

    public RecipeOrderJob
    {
        executions = List.copyOf(executions);
        reserved = List.copyOf(reserved);
        message = message == null ? "" : message;
        outputDestination = outputDestination == null ? OrderOutputDestination.NETWORK : outputDestination;
        origin = origin == null ? OrderOrigin.MANUAL : origin;
        if (activeStep < -1 || activeStep >= executions.size())
            throw new IllegalArgumentException("invalid active recipe step");
        java.util.Set<Integer> bundledProducers = new java.util.HashSet<>();
        for (int consumer = 0; consumer < executions.size(); consumer++)
        {
            ExternalWait wait = executions.get(consumer).externalWait();
            if (wait == null || wait.bundle() == null) continue;
            MachineRecipeBundle bundle = wait.bundle();
            if (!bundledProducers.add(bundle.producerStep()) || bundle.producerStep() >= consumer
                    || executions.get(bundle.producerStep()).complete()
                    || executions.get(bundle.producerStep()).externalWait() != null
                    || bundle.producerCrafts() > executions.get(bundle.producerStep()).step().crafts()
                    || bundle.consumerCrafts() > executions.get(consumer).step().crafts()
                    || !executions.get(consumer).step().dependencies().contains(bundle.producerStep()))
                throw new IllegalArgumentException("invalid persisted machine bundle");
        }
    }

    public RecipeOrderJob(UUID id, UUID owner, int networkId, Identifier target,
                          long requested, List<StepExecution> executions, int activeStep,
                          boolean blockingMode, Status status, String message,
                          long createdAt, long finishedAt, List<RecipePlan.ReservedMaterial> reserved)
    {
        this(id, owner, networkId, target, requested, executions, activeStep, blockingMode,
                OrderOutputDestination.NETWORK, OrderOrigin.MANUAL, status, message, createdAt, finishedAt, reserved);
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
                blockingMode, OrderOutputDestination.NETWORK, OrderOrigin.MANUAL, status, message, createdAt, finishedAt, reserved);
    }

    public RecipeOrderJob(UUID id, UUID owner, int networkId, Identifier target,
                          long requested, List<RecipePlan.Step> steps, int nextStep,
                          boolean blockingMode, OrderOutputDestination outputDestination,
                          Status status, String message, long createdAt, long finishedAt,
                          long nextCraftingTick, ExternalWait externalWait,
                          List<RecipePlan.ReservedMaterial> reserved)
    {
        this(id, owner, networkId, target, requested,
                migrateLegacy(steps, nextStep, nextCraftingTick, externalWait), -1,
                blockingMode, outputDestination, OrderOrigin.MANUAL, status, message, createdAt, finishedAt, reserved);
    }

    public static RecipeOrderJob create(UUID id, UUID owner, int networkId, Identifier target,
                                        long requested, List<RecipePlan.Step> steps, boolean blockingMode,
                                        OrderOutputDestination outputDestination,
                                        Status status, String message, long createdAt, long finishedAt,
                                        List<RecipePlan.ReservedMaterial> reserved)
    {
        return new RecipeOrderJob(id, owner, networkId, target, requested,
                steps.stream().map(StepExecution::pending).toList(), -1, blockingMode,
                outputDestination, OrderOrigin.MANUAL, status, message, createdAt, finishedAt, reserved);
    }

    public static RecipeOrderJob createAutomatic(UUID id, UUID owner, int networkId, Identifier target,
                                                  long requested, List<RecipePlan.Step> steps,
                                                  boolean blockingMode, Status status, String message,
                                                  long createdAt, long finishedAt,
                                                  List<RecipePlan.ReservedMaterial> reserved)
    {
        return createAutomatic(id, owner, networkId, target, requested, steps, blockingMode,
                OrderOutputDestination.NETWORK, status, message, createdAt, finishedAt, reserved);
    }

    public static RecipeOrderJob createAutomatic(UUID id, UUID owner, int networkId, Identifier target,
                                                  long requested, List<RecipePlan.Step> steps,
                                                  boolean blockingMode, OrderOutputDestination outputDestination,
                                                  Status status, String message, long createdAt, long finishedAt,
                                                  List<RecipePlan.ReservedMaterial> reserved)
    {
        return new RecipeOrderJob(id, owner, networkId, target, requested,
                steps.stream().map(StepExecution::pending).toList(), -1, blockingMode,
                outputDestination, OrderOrigin.AUTOMATIC,
                status, message, createdAt, finishedAt, reserved);
    }

    public static RecipeOrderJob create(UUID id, UUID owner, int networkId, Identifier target,
                                        long requested, List<RecipePlan.Step> steps, boolean blockingMode,
                                        Status status, String message, long createdAt, long finishedAt,
                                        List<RecipePlan.ReservedMaterial> reserved)
    {
        return create(id, owner, networkId, target, requested, steps, blockingMode,
                OrderOutputDestination.NETWORK, status, message, createdAt, finishedAt, reserved);
    }

    public List<RecipePlan.Step> steps()
    { return executions.stream().map(StepExecution::step).toList(); }

    /** Exact component-aware order target, recovered from the root manufacturing step. */
    public IStackKey<?> targetKey()
    {
        for (int index = executions.size() - 1; index >= 0; index--)
            if (executions.get(index).step().output().equals(target))
                return executions.get(index).step().outputKey();
        return new ItemStackKey(new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(target)));
    }

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
        if (activeStep >= 0 || step < 0 || step >= executions.size() || executions.get(step).complete()
                || bundledStep(step))
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
                current.ingredientSelections(), current.dependencies(), current.selfIncrementSeed(), current.byproducts());
        updated.set(activeStep, executions.get(activeStep).with(remaining, nextAllowedTick, null));
        return copy(updated, activeStep, Status.RUNNING, "", createdAt, finishedAt, reserved);
    }

    public boolean bundledStep(int step)
    {
        return executions.stream().map(StepExecution::externalWait).filter(java.util.Objects::nonNull)
                .anyMatch(wait -> wait.bundle() != null && wait.bundle().producerStep() == step);
    }

    public RecipeOrderJob awaitMachineBundle(int consumerStep, ExternalWait wait)
    {
        requireActive();
        if (wait.bundle() == null || wait.bundle().producerStep() != activeStep
                || consumerStep <= activeStep || executions.get(consumerStep).complete()
                || executions.get(consumerStep).externalWait() != null)
            throw new IllegalArgumentException("invalid machine bundle coordinator");
        List<StepExecution> updated = new java.util.ArrayList<>(executions);
        StepExecution producer = updated.get(activeStep);
        updated.set(activeStep, producer.with(producer.step(), producer.nextCraftingTick(), null));
        StepExecution consumer = updated.get(consumerStep);
        updated.set(consumerStep, consumer.with(consumer.step(), consumer.nextCraftingTick(), wait));
        return copy(updated, consumerStep, Status.RUNNING, "", createdAt, finishedAt, reserved);
    }

    private RecipeOrderJob completeMachineBundle(MachineRecipeBundle bundle)
    {
        List<StepExecution> updated = new java.util.ArrayList<>(executions);
        completeMachineCrafts(updated, bundle.producerStep(), bundle.producerCrafts());
        completeMachineCrafts(updated, activeStep, bundle.consumerCrafts());
        Status next = updated.stream().allMatch(StepExecution::complete) ? Status.COMPLETE : Status.RUNNING;
        return copy(updated, activeStep, next, "", createdAt, finishedAt, reserved);
    }

    private static void completeMachineCrafts(List<StepExecution> executions, int index, long crafts)
    {
        StepExecution execution = executions.get(index);
        RecipePlan.Step step = execution.step();
        if (crafts < 1 || crafts > step.crafts()) throw new IllegalArgumentException("invalid bundle craft count");
        if (crafts == step.crafts())
        {
            executions.set(index, execution.completed(execution.nextCraftingTick()));
            return;
        }
        List<RecipePlan.Material> inputs = new java.util.ArrayList<>();
        for (RecipePlan.Material input : step.inputs())
        {
            long left = input.amount() - MachineRecipeBundle.portion(input.amount(), crafts, step.crafts());
            if (left > 0) inputs.add(new RecipePlan.Material(input.key(), left,
                    input.ingredientSlot(), input.inputGroup()));
        }
        RecipePlan.Step remaining = new RecipePlan.Step(step.recipe(), step.family(), step.outputKey(),
                step.outputPerCraft(), step.crafts() - crafts, inputs, step.ingredientSelections(),
                step.dependencies(), step.selfIncrementSeed(), step.byproducts());
        executions.set(index, execution.with(remaining, execution.nextCraftingTick(), null));
    }

    public RecipeOrderJob completeExternalBatch()
    {
        requireActive();
        if (externalWait() != null && externalWait().bundle() != null)
            return completeMachineBundle(externalWait().bundle());
        RecipePlan.Step current = executions.get(activeStep).step();
        boolean sequential = blockingMode || current.selfIncrementSeed() > 0;
        if (!sequential || current.crafts() <= 1) return advance();
        List<StepExecution> updated = new java.util.ArrayList<>(executions);
        List<RecipePlan.Material> remainingInputs = new java.util.ArrayList<>();
        for (RecipePlan.Material input : current.inputs())
        {
            long amount = com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                    .exact(current.outputKey(), input.key()) ? input.amount()
                    : input.amount() - BlockingModeLogic.amountToDispatch(
                            true, input.amount(), current.crafts());
            if (amount > 0) remainingInputs.add(new RecipePlan.Material(
                    input.key(), amount, input.ingredientSlot(), input.inputGroup()));
        }
        RecipePlan.Step remaining = new RecipePlan.Step(current.recipe(), current.family(), current.outputKey(),
                current.outputPerCraft(), current.crafts() - 1, remainingInputs,
                current.ingredientSelections(), current.dependencies(), current.selfIncrementSeed(), current.byproducts());
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
                blockingMode, outputDestination, origin, nextStatus, nextMessage,
                nextCreatedAt, nextFinishedAt, nextReserved);
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
                    source.ingredientSelections(), dependencies, source.selfIncrementSeed(), source.byproducts());
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
                               List<RecipePlan.ReservedMaterial> networkBaselineStacks,
                               List<MachineLocation> occupiedMachines, MachineRecipeBundle bundle)
    {
        public ExternalWait(ResourceKey<Level> machineDimension, BlockPos machinePosition,
                            IStackKey<?> outputKey, boolean nativeFurnace, boolean provisioner,
                            long baseline, long networkBaseline, long networkObserved,
                            long amount, long collected, List<RecipePlan.Material> remainingInputs,
                            List<RecipePlan.ReservedMaterial> networkBaselineStacks,
                            List<MachineLocation> occupiedMachines)
        { this(machineDimension, machinePosition, outputKey, nativeFurnace, provisioner,
                baseline, networkBaseline, networkObserved, amount, collected, remainingInputs,
                networkBaselineStacks, occupiedMachines, null); }

        public ExternalWait(ResourceKey<Level> machineDimension, BlockPos machinePosition,
                            IStackKey<?> outputKey, boolean nativeFurnace, boolean provisioner,
                            long baseline, long networkBaseline, long networkObserved,
                            long amount, long collected, List<RecipePlan.Material> remainingInputs,
                            List<RecipePlan.ReservedMaterial> networkBaselineStacks)
        { this(machineDimension, machinePosition, outputKey, nativeFurnace, provisioner,
                baseline, networkBaseline, networkObserved, amount, collected, remainingInputs,
                networkBaselineStacks, List.of(new MachineLocation(machineDimension, machinePosition, ""))); }

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
            occupiedMachines = List.copyOf(occupiedMachines);
            if (occupiedMachines.isEmpty()) throw new IllegalArgumentException("external wait has no machine");
            if (bundle != null && (nativeFurnace || provisioner || occupiedMachines.stream().anyMatch(machine ->
                    !machine.dimension().equals(machineDimension) || !machine.position().equals(machinePosition))))
                throw new IllegalArgumentException("machine bundle requires a direct machine");
        }

        public ExternalWait withInputs(List<RecipePlan.Material> inputs)
        { return new ExternalWait(machineDimension, machinePosition, outputKey, nativeFurnace, provisioner,
                baseline, networkBaseline, networkObserved, amount, collected, inputs, networkBaselineStacks,
                occupiedMachines, bundle); }

        public ExternalWait withCollected(long value)
        { return new ExternalWait(machineDimension, machinePosition, outputKey, nativeFurnace, provisioner,
                baseline, networkBaseline, networkObserved, amount, value, remainingInputs, networkBaselineStacks,
                occupiedMachines, bundle); }

        public ExternalWait withProgress(long observed, long value)
        { return new ExternalWait(machineDimension, machinePosition, outputKey, nativeFurnace, provisioner,
                baseline, networkBaseline, observed, amount, value, remainingInputs, networkBaselineStacks,
                occupiedMachines, bundle); }

        public Identifier output()
        {
            if (outputKey instanceof ItemStackKey item)
                return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getSource());
            return outputKey.getTypeId();
        }
    }

    public record MachineLocation(ResourceKey<Level> dimension, BlockPos position, String inputGroup)
    {
        public MachineLocation
        {
            if (dimension == null || position == null || inputGroup == null)
                throw new IllegalArgumentException("invalid machine location");
            position = position.immutable();
        }
    }
}
