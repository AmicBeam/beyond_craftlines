package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService;
import com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath;
import com.amicbeam.beyondcraftlines.common.crafting.SimulatedCrafting;
import com.amicbeam.beyondcraftlines.common.data.BindingRecord;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;

import static com.amicbeam.beyondcraftlines.common.localization.OrderStatusMessage.encode;
import static com.amicbeam.beyondcraftlines.common.localization.OrderStatusMessage.hasId;

public final class RecipeOrderService
{
    private static final String RETURN_AFTER_ERROR = encode("execution_failed_returning");
    private static final Set<String> NATIVE_FURNACE_FAMILIES = Set.of("smelting", "blasting", "smoking");
    private RecipeOrderService() {}

    public static RecipeOrderJob enqueue(ServerLevel level, UUID owner, int networkId,
                                         Identifier target, long count, boolean blockingMode)
    {
        return enqueue(level, owner, networkId, target, count, blockingMode,
                com.amicbeam.beyondcraftlines.common.crafting.RecipeResolutionOverrides.EMPTY);
    }

    public static RecipeOrderJob enqueue(ServerLevel level, UUID owner, int networkId,
                                         Identifier target, long count, boolean blockingMode,
                                         com.amicbeam.beyondcraftlines.common.crafting.RecipeResolutionOverrides overrides)
    {
        RecipePlan plan = RecipePlanningService.plan(level, networkId, target, count, overrides);
        if (!plan.craftable()) throw new IllegalStateException("missing: " + plan.missing());
        return enqueueValidated(level, owner, networkId, target, count, blockingMode, plan);
    }

    public static RecipeOrderJob enqueueValidated(ServerLevel level, UUID owner, int networkId,
                                                  Identifier target, long count, boolean blockingMode,
                                                  RecipePlan plan)
    {
        if (!plan.target().equals(target) || plan.requested() != count || !plan.craftable())
            throw new IllegalArgumentException("validated plan does not match the order");
        List<RecipeOrderJob> active = RecipeOrderSavedData.get(level.getServer()).active();
        if (active.size() >= CraftlinesConfig.MAX_ACTIVE_ORDERS.get()
                || active.stream().filter(job -> job.owner().equals(owner)).count()
                >= CraftlinesConfig.MAX_ACTIVE_ORDERS_PER_PLAYER.get())
            throw new IllegalStateException("too many active recipe orders");
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        if (network == null) throw new IllegalStateException("network unavailable");
        List<RecipePlan.ReservedMaterial> reserved = reserveInitial(
                network.getUnifiedStorage(), plan.reserved());
        RecipeOrderJob job = new RecipeOrderJob(UUID.randomUUID(), owner, networkId, target, count,
                plan.steps(), 0, blockingMode, plan.steps().isEmpty() ? RecipeOrderJob.Status.COMPLETE
                : RecipeOrderJob.Status.QUEUED, "", level.getGameTime(),
                plan.steps().isEmpty() ? level.getGameTime() : 0, 0, null, reserved);
        try { RecipeOrderSavedData.get(level.getServer()).put(job); }
        catch (RuntimeException exception)
        {
            releaseReservations(network.getUnifiedStorage(), reserved);
            throw exception;
        }
        return job;
    }

    public static boolean cancel(MinecraftServer server, UUID owner, UUID id)
    {
        RecipeOrderSavedData data = RecipeOrderSavedData.get(server);
        RecipeOrderJob job = data.get(id);
        if (job == null || !job.owner().equals(owner) || terminal(job.status())) return false;
        DimensionsNet network = DimensionsNet.getNetFromId(job.networkId());
        if (network == null) return false;
        releaseReservations(network.getUnifiedStorage(), job.reserved());
        data.put(job.withReserved(List.of()).with(RecipeOrderJob.Status.CANCELLED, encode("cancelled_by_owner"))
                .finishedAt(server.overworld().getGameTime()));
        return true;
    }

    public static void tick(MinecraftServer server)
    {
        RecipeOrderSavedData data = RecipeOrderSavedData.get(server);
        long gameTime = server.overworld().getGameTime();
        data.removeExpiredDisplayedTerminal(gameTime);
        List<RecipeOrderJob> jobs = data.active().stream()
                .sorted(Comparator.comparingLong(RecipeOrderJob::createdAt)
                        .thenComparing(RecipeOrderJob::id)).toList();
        RuntimeOrderIndex<Integer, MachineKey> index = new RuntimeOrderIndex<>();
        for (RecipeOrderJob job : jobs)
            if (job.externalWait() != null) index.occupyMachine(new MachineKey(
                    job.externalWait().machineDimension(), job.externalWait().machinePosition()));
        for (RecipeOrderJob job : jobs)
        {
            if (hasId(job.message(), "execution_failed_returning"))
            {
                DimensionsNet network = DimensionsNet.getNetFromId(job.networkId());
                if (network != null)
                {
                    try
                    {
                        releaseReservations(network.getUnifiedStorage(), job.reserved());
                        data.put(job.withReserved(List.of()).with(
                                RecipeOrderJob.Status.ERROR, encode("execution_failed")).finishedAt(gameTime));
                    }
                    catch (RuntimeException ignored) {}
                }
                continue;
            }
            if (!index.claimNetwork(job.networkId()))
            {
                String reason = encode("waiting_network_transaction");
                if (job.status() != RecipeOrderJob.Status.PAUSED || !job.message().equals(reason))
                    data.put(job.with(RecipeOrderJob.Status.PAUSED, reason));
                continue;
            }
            try
            {
                RecipeOrderJob result = executeStep(server, job, index);
                if (terminal(result.status()) && !result.reserved().isEmpty())
                {
                    DimensionsNet network = DimensionsNet.getNetFromId(result.networkId());
                    if (network != null)
                    {
                        try
                        {
                            releaseReservations(network.getUnifiedStorage(), result.reserved());
                            result = result.withReserved(List.of());
                        }
                        catch (RuntimeException exception)
                        {
                            result = result.with(RecipeOrderJob.Status.PAUSED, encode("waiting_return_reserved"));
                        }
                    }
                }
                if (terminal(result.status()) && !terminal(job.status()) && result.finishedAt() <= 0)
                    result = result.finishedAt(gameTime);
                data.put(result);
            }
            catch (RuntimeException exception)
            {
                DimensionsNet network = DimensionsNet.getNetFromId(job.networkId());
                RecipeOrderJob failed = job;
                if (network != null)
                {
                    try
                    {
                        releaseReservations(network.getUnifiedStorage(), job.reserved());
                        failed = failed.withReserved(List.of());
                    }
                    catch (RuntimeException releaseFailure)
                    {
                        data.put(failed.with(RecipeOrderJob.Status.PAUSED, RETURN_AFTER_ERROR));
                        continue;
                    }
                }
                data.put(failed.with(RecipeOrderJob.Status.ERROR, encode("execution_failed")).finishedAt(gameTime));
            }
        }
    }

    private static RecipeOrderJob executeStep(MinecraftServer server, RecipeOrderJob job,
                                               RuntimeOrderIndex<Integer, MachineKey> index)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(job.networkId());
        if (network == null) return job.with(RecipeOrderJob.Status.PAUSED, encode("network_unavailable"));
        if (job.externalWait() != null) return job.externalWait().provisioner()
                ? tickProvisioner(server, network, job) : job.externalWait().nativeFurnace()
                ? tickNativeFurnace(server, network, job) : tickBoundMachine(server, network, job);
        if (job.nextStep() >= job.steps().size()) return job.with(RecipeOrderJob.Status.COMPLETE, "");
        RecipePlan.Step step = job.steps().get(job.nextStep());
        if (NATIVE_FURNACE_FAMILIES.contains(step.family()))
        {
            Optional<NativeFurnaceRegistry.NativeFurnace> furnace =
                    NativeFurnaceRegistry.furnaceFor(server, job.networkId(), step.family());
            return furnace.isPresent() ? reserveNativeFurnace(network, job, step, furnace.get(), index)
                    : job.with(RecipeOrderJob.Status.PAUSED,
                    encode("native_furnace_unavailable", step.family()));
        }
        Optional<DeviceBindingRegistry.ProvisionerTarget> provisioner =
                DeviceBindingRegistry.provisionerFor(server, job.networkId(), step.family());
        if (provisioner.isPresent()) return deliverToProvisioner(
                server.overworld(), network, job, step, provisioner.get(), index);
        Optional<DeviceBindingRegistry.BoundMachine> machine =
                DeviceBindingRegistry.machineFor(server, job.networkId(), step.family());
        if (machine.isPresent()) return reserveMachine(job, step, machine.get(), index);
        if (!"crafting".equals(step.family()))
            return job.with(RecipeOrderJob.Status.PAUSED, encode("bound_machine_unavailable", step.family()));
        long gameTime = server.overworld().getGameTime();
        if (!VirtualCraftingThrottle.ready(gameTime, job.nextCraftingTick()))
            return job.with(RecipeOrderJob.Status.PAUSED, encode("virtual_crafting_interval"));
        return executeCrafting(server.overworld(), network, job, step, gameTime);
    }

    private static RecipeOrderJob deliverToProvisioner(ServerLevel level, DimensionsNet network, RecipeOrderJob job,
                                                        RecipePlan.Step step,
                                                        DeviceBindingRegistry.ProvisionerTarget target,
                                                        RuntimeOrderIndex<Integer, MachineKey> index)
    {
        BindingRecord binding = target.binding();
        ResourceKey<net.minecraft.world.level.Level> dimension = binding.provisionerDimension() == null
                ? binding.dimension() : binding.provisionerDimension();
        BlockPos position = binding.provisionerPosition() == null
                ? binding.position() : binding.provisionerPosition();
        MachineKey machineKey = new MachineKey(dimension, position);
        if (index.isMachineOccupied(machineKey))
            return job.with(RecipeOrderJob.Status.PAUSED, encode("provisioner_waiting_earlier"));
        ProvisionerStorage provisioner = target.provisioner().storage();
        List<RecipePlan.Material> batchInputs = inputsToDispatch(job.blockingMode(), step);
        InputSelection selection = selectInputs(level, network.getUnifiedStorage(), job, step, batchInputs);
        if (selection == null)
            return job.with(RecipeOrderJob.Status.PAUSED, encode("matching_provisioner_inputs"));
        for (InputChunk input : selection.chunks())
            if (!provisioner.insertFromOrder(input.key(), input.amount(), true).isEmpty())
                return job.with(RecipeOrderJob.Status.PAUSED, encode("provisioner_no_room", input.key()));

        List<KeyAmount> extracted = new ArrayList<>();
        for (InputChunk input : selection.chunks())
        {
            if (input.fromReserved()) continue;
            KeyAmount result = network.getUnifiedStorage().extract(input.key(), input.amount(), false, false);
            if (!StorageTransfer.isComplete(input.amount(), result.amount()))
            {
                if (!result.isEmpty()) extracted.add(result);
                extracted.forEach(value -> network.getUnifiedStorage().insert(value.key(), value.amount(), false));
                return job.with(RecipeOrderJob.Status.PAUSED, encode("waiting_resource", input.key()));
            }
            extracted.add(result);
        }

        List<KeyAmount> inserted = new ArrayList<>();
        List<KeyAmount> delivery = selection.chunks().stream()
                .map(input -> new KeyAmount(input.key(), input.amount())).toList();
        for (KeyAmount value : delivery)
        {
            KeyAmount remainder = provisioner.insertFromOrder(value.key(), value.amount(), false);
            long accepted = value.amount() - remainder.amount();
            if (accepted > 0) inserted.add(new KeyAmount(value.key(), accepted));
            if (!remainder.isEmpty())
            {
                inserted.forEach(delivered -> provisioner.extract(
                        delivered.key(), delivered.amount(), false, false));
                extracted.forEach(original -> network.getUnifiedStorage().insert(
                        original.key(), original.amount(), false));
                return job.with(RecipeOrderJob.Status.PAUSED, encode("provisioner_delivery_rolled_back"));
            }
        }
        long batchCrafts = BlockingModeLogic.craftsToDispatch(job.blockingMode(), step.crafts());
        long output = SaturatingLongMath.multiply(step.outputPerCraft(), batchCrafts);
        long networkBaseline = networkAmount(job.networkId(), step.outputKey());
        RecipeOrderJob.ExternalWait wait = new RecipeOrderJob.ExternalWait(dimension, position,
                step.outputKey(), false, true, 0, networkBaseline, 0, output, 0, List.of(),
                outputBaseline(job.networkId(), step.outputKey()));
        index.occupyMachine(machineKey);
        return consumeReserved(job, selection.consumedReserved()).awaitExternal(wait,
                encode("provisioner_waiting_output", 0, output));
    }

    private static RecipeOrderJob executeCrafting(ServerLevel level, DimensionsNet network, RecipeOrderJob job,
                                                  RecipePlan.Step step, long gameTime)
    {
        UnifiedStorage storage = network.getUnifiedStorage();
        SimulatedCrafting.Attempt attempt = SimulatedCrafting.craftBatch(
                level, storage, step.recipe(), step.output(), step.crafts(), step.ingredientSelections(),
                job.reserved(), job.nextStep() + 1 < job.steps().size(),
                PlanningSnapshotService.capture(job.networkId()));
        if (!attempt.success()) return job.with(RecipeOrderJob.Status.PAUSED, attempt.reason());
        int interval = CraftlinesConfig.VIRTUAL_CRAFTING_NODE_INTERVAL_TICKS.get();
        long nextTick = VirtualCraftingThrottle.nextAllowedTick(gameTime, interval);
        RecipeOrderJob updated = addReserved(consumeReserved(job, attempt.consumedReserved()),
                attempt.producedReserved());
        return updated.completeCrafts(attempt.crafts(), nextTick);
    }

    private static RecipeOrderJob reserveMachine(RecipeOrderJob job,
                                                  RecipePlan.Step step,
                                                  DeviceBindingRegistry.BoundMachine machine,
                                                  RuntimeOrderIndex<Integer, MachineKey> index)
    {
        BindingRecord binding = machine.binding();
        MachineKey machineKey = new MachineKey(binding.dimension(), binding.position());
        if (index.isMachineOccupied(machineKey))
            return job.with(RecipeOrderJob.Status.PAUSED, encode("bound_machine_busy"));
        if (step.inputs().stream().map(RecipePlan.Material::item)
                .anyMatch(item -> step.output().equals(item)))
            return job.with(RecipeOrderJob.Status.ERROR, encode("shared_input_output_unsupported"));
        if (BlockingModeLogic.shouldWait(job.blockingMode(), BoundMachineAutomation.containsAnyResources(
                machine.level(), binding.position(), step.inputs())))
            return job.with(RecipeOrderJob.Status.PAUSED, encode("blocking_machine_input"));
        long baseline = BoundMachineAutomation.countExtractable(
                machine.level(), binding.position(), step.outputKey());
        for (KeyAmount output : recipeOutputs(machine.level(), step))
            if (!step.outputKey().isSame(output.key()) && BoundMachineAutomation.countExtractable(
                    machine.level(), binding.position(), output.key()) > 0)
                return job.with(RecipeOrderJob.Status.PAUSED,
                        encode("bound_machine_byproducts_clear"));
        long batchCrafts = BlockingModeLogic.craftsToDispatch(job.blockingMode(), step.crafts());
        long output = SaturatingLongMath.multiply(step.outputPerCraft(), batchCrafts);
        List<RecipePlan.Material> batchInputs = inputsToDispatch(job.blockingMode(), step);
        RecipeOrderJob.ExternalWait wait = new RecipeOrderJob.ExternalWait(binding.dimension(),
                binding.position(), step.outputKey(), false, false, baseline, 0, 0, output, 0,
                batchInputs, List.of());
        index.occupyMachine(machineKey);
        return job.awaitExternal(wait, encode("bound_machine_preparing"));
    }

    private static RecipeOrderJob reserveNativeFurnace(DimensionsNet network,
                                                        RecipeOrderJob job, RecipePlan.Step step,
                                                        NativeFurnaceRegistry.NativeFurnace nativeFurnace,
                                                        RuntimeOrderIndex<Integer, MachineKey> index)
    {
        BaseNetFurnaceBlockEntity<?> furnace = nativeFurnace.blockEntity();
        MachineKey machineKey = new MachineKey(nativeFurnace.level().dimension(), furnace.getBlockPos());
        if (index.isMachineOccupied(machineKey))
            return job.with(RecipeOrderJob.Status.PAUSED, encode("native_furnace_busy"));
        Set<Identifier> inputItems = step.inputs().stream().map(RecipePlan.Material::item)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (BlockingModeLogic.shouldWait(job.blockingMode(),
                NativeFurnaceAutomation.containsAnyInput(furnace, inputItems)))
            return job.with(RecipeOrderJob.Status.PAUSED, encode("blocking_native_furnace_input"));
        if (NativeFurnaceAutomation.countOutput(furnace, step.output()) > 0)
            return job.with(RecipeOrderJob.Status.PAUSED, encode("native_furnace_output_clear"));
        long batchCrafts = BlockingModeLogic.craftsToDispatch(job.blockingMode(), step.crafts());
        long output = SaturatingLongMath.multiply(step.outputPerCraft(), batchCrafts);
        List<RecipePlan.Material> batchInputs = inputsToDispatch(job.blockingMode(), step);
        RecipeOrderJob.ExternalWait wait = new RecipeOrderJob.ExternalWait(
                nativeFurnace.level().dimension(), furnace.getBlockPos(), step.output(), true,
                false, 0,
                networkAmount(job.networkId(), step.output()), 0, output, 0, batchInputs,
                outputBaseline(job.networkId(), step.output()));
        index.occupyMachine(machineKey);
        return job.awaitExternal(wait, encode("native_furnace_preparing"));
    }

    private static RecipeOrderJob tickBoundMachine(MinecraftServer server, DimensionsNet network,
                                                    RecipeOrderJob job)
    {
        RecipeOrderJob.ExternalWait wait = job.externalWait();
        ServerLevel level = server.getLevel(wait.machineDimension());
        BindingRecord binding = level == null ? null
                : BindingSavedData.get(server).at(wait.machineDimension(), wait.machinePosition());
        if (level == null || !level.isLoaded(wait.machinePosition()) || binding == null
                || binding.networkId() != job.networkId()
                || binding.deviceType() != DeviceType.EXTERNAL_RECIPE_MACHINE
                || !BuiltInRegistries.BLOCK.getKey(level.getBlockState(wait.machinePosition()).getBlock())
                .equals(binding.lastBlockId())
                || !BoundMachineAutomation.isAutomatable(level, wait.machinePosition()))
            return job.with(RecipeOrderJob.Status.ERROR, encode("bound_machine_removed"));

        if (!wait.remainingInputs().isEmpty())
        {
            List<RecipePlan.Material> remaining = new ArrayList<>();
            RecipeOrderJob working = job;
            RecipePlan.Step step = job.steps().get(job.nextStep());
            for (RecipePlan.Material input : wait.remainingInputs())
            {
                InputSelection selection = selectInputs(level, network.getUnifiedStorage(),
                        working, step, List.of(input));
                if (selection == null)
                {
                    remaining.add(input);
                    continue;
                }
                long delivered = 0;
                List<RecipePlan.ReservedMaterial> consumed = new ArrayList<>();
                for (InputChunk chunk : selection.chunks())
                {
                    long capacity = BoundMachineAutomation.insertCapacity(
                            level, wait.machinePosition(), chunk.key(), chunk.amount());
                    if (capacity <= 0) continue;
                    long offered = capacity;
                    KeyAmount taken = chunk.fromReserved() ? new KeyAmount(chunk.key(), offered)
                            : network.getUnifiedStorage().extract(chunk.key(), offered, false, false);
                    long inserted = BoundMachineAutomation.insert(
                            level, wait.machinePosition(), chunk.key(), taken.amount());
                    if (!chunk.fromReserved() && inserted < taken.amount())
                        network.getUnifiedStorage().insert(taken.key(), taken.amount() - inserted, false);
                    if (chunk.fromReserved() && inserted > 0)
                        consumed.add(new RecipePlan.ReservedMaterial(chunk.key(), inserted));
                    delivered = SaturatingLongMath.add(delivered, inserted);
                }
                working = consumeReserved(working, consumed);
                long left = Math.max(0, input.amount() - delivered);
                if (left > 0) remaining.add(new RecipePlan.Material(
                        input.key(), left, input.ingredientSlot()));
            }
            wait = wait.withInputs(remaining);
            if (!remaining.isEmpty()) return working.awaitExternal(wait, encode("feeding_bound_machine"));
            job = working;
        }

        drainBoundMachineByproducts(level, network.getUnifiedStorage(), job.steps().get(job.nextStep()), wait);

        long current = BoundMachineAutomation.countExtractable(level, wait.machinePosition(), wait.outputKey());
        long available = ExternalOrderLogic.availableMachineOutput(wait.baseline(), current);
        long transferable = Math.min(available, wait.amount() - wait.collected());
        if (transferable > 0)
        {
            long inserted = 0;
            List<RecipePlan.ReservedMaterial> produced = new ArrayList<>();
            boolean escrowOutput = job.nextStep() + 1 < job.steps().size();
            for (KeyAmount output : BoundMachineAutomation.extractStacks(
                    level, wait.machinePosition(), wait.outputKey(), transferable))
            {
                if (escrowOutput)
                {
                    produced.add(new RecipePlan.ReservedMaterial(output.key(), output.amount()));
                    inserted = SaturatingLongMath.add(inserted, output.amount());
                    continue;
                }
                KeyAmount remainder = network.getUnifiedStorage().insert(output.key(), output.amount(), false);
                inserted = SaturatingLongMath.add(inserted, output.amount() - remainder.amount());
                if (!remainder.isEmpty()) BoundMachineAutomation.insert(level, wait.machinePosition(),
                        remainder.key(), remainder.amount());
            }
            job = addReserved(job, produced);
            wait = wait.withCollected(wait.collected() + inserted);
        }
        if (wait.collected() >= wait.amount()) return job.completeExternalBatch();
        return job.awaitExternal(wait, encode("machine_processing", wait.collected(), wait.amount()));
    }

    private static void drainBoundMachineByproducts(ServerLevel level, UnifiedStorage storage,
                                                     RecipePlan.Step step, RecipeOrderJob.ExternalWait wait)
    {
        long batchCrafts = SaturatingLongMath.ceilDiv(wait.amount(), step.outputPerCraft());
        for (KeyAmount expected : recipeOutputs(level, step))
        {
            if (wait.outputKey().isSame(expected.key())) continue;
            long visible = BoundMachineAutomation.countExtractable(
                    level, wait.machinePosition(), expected.key());
            long limit = Math.min(visible, SaturatingLongMath.multiply(expected.amount(), batchCrafts));
            if (limit <= 0) continue;
            KeyAmount simulatedRemainder = storage.insert(expected.key(), limit, true);
            long transferable = limit - simulatedRemainder.amount();
            if (transferable <= 0) continue;
            for (KeyAmount output : BoundMachineAutomation.extractStacks(
                    level, wait.machinePosition(), expected.key(), transferable))
            {
                KeyAmount remainder = storage.insert(output.key(), output.amount(), false);
                if (!remainder.isEmpty()) BoundMachineAutomation.insert(
                        level, wait.machinePosition(), remainder.key(), remainder.amount());
            }
        }
    }

    private static List<KeyAmount> recipeOutputs(ServerLevel level, RecipePlan.Step step)
    {
        var holder = level.recipeAccess().byKey(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE, step.recipe())).orElse(null);
        return holder == null ? List.of() : RecipeOutputResolver.outputs(holder.value(), level);
    }

    private static RecipeOrderJob tickProvisioner(MinecraftServer server, DimensionsNet network,
                                                   RecipeOrderJob job)
    {
        RecipeOrderJob.ExternalWait wait = job.externalWait();
        ServerLevel level = server.getLevel(wait.machineDimension());
        if (level == null || !level.isLoaded(wait.machinePosition())
                || !(level.getBlockEntity(wait.machinePosition())
                instanceof CraftlineProvisionerBlockEntity provisioner)
                || provisioner.getNetId() != job.networkId())
            return job.with(RecipeOrderJob.Status.ERROR, encode("provisioner_removed"));
        RecipePlan.Step step = job.steps().get(job.nextStep());
        BindingRecord binding = BindingSavedData.get(server).at(wait.machineDimension(), wait.machinePosition());
        boolean stillAssigned = binding != null && binding.networkId() == job.networkId()
                && binding.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING
                && binding.recipeFamilies().contains(step.family());
        if (!stillAssigned)
            return job.with(RecipeOrderJob.Status.ERROR, encode("provisioner_assignment_changed"));

        long currentNetwork = networkAmount(job.networkId(), wait.outputKey());
        ExternalOrderLogic.NetworkCredit credit = ExternalOrderLogic.creditNetworkOutput(
                wait.networkBaseline(), currentNetwork, wait.networkObserved(), wait.collected(), wait.amount());
        long newlyCredited = Math.max(0, credit.collected() - wait.collected());
        if (newlyCredited > 0 && job.nextStep() + 1 < job.steps().size())
        {
            List<KeyAmount> captured = extractOutputDelta(job.networkId(), network.getUnifiedStorage(),
                    wait.outputKey(), newlyCredited, wait.networkBaselineStacks());
            long capturedAmount = 0;
            for (KeyAmount value : captured)
                capturedAmount = SaturatingLongMath.add(capturedAmount, value.amount());
            job = addReserved(job, captured.stream().map(value ->
                    new RecipePlan.ReservedMaterial(value.key(), value.amount())).toList());
            long afterCapture = networkAmount(job.networkId(), wait.outputKey());
            credit = new ExternalOrderLogic.NetworkCredit(
                    ExternalOrderLogic.availableMachineOutput(wait.networkBaseline(), afterCapture),
                    wait.collected() + capturedAmount);
        }
        wait = wait.withProgress(credit.observed(), credit.collected());
        if (wait.collected() >= wait.amount()) return job.completeExternalBatch();
        return job.awaitExternal(wait, encode("provisioner_waiting_output", wait.collected(), wait.amount()));
    }

    private static RecipeOrderJob tickNativeFurnace(MinecraftServer server, DimensionsNet network,
                                                     RecipeOrderJob job)
    {
        RecipeOrderJob.ExternalWait wait = job.externalWait();
        ServerLevel level = server.getLevel(wait.machineDimension());
        if (level == null || !level.isLoaded(wait.machinePosition())
                || !(level.getBlockEntity(wait.machinePosition()) instanceof BaseNetFurnaceBlockEntity<?> furnace)
                || furnace.getNetId() != job.networkId())
            return job.with(RecipeOrderJob.Status.ERROR, encode("native_furnace_removed"));

        String expectedFamily = job.steps().get(job.nextStep()).family();
        if (!NativeFurnaceRegistry.supports(furnace, expectedFamily))
            return job.with(RecipeOrderJob.Status.ERROR, encode("native_furnace_type_changed"));

        if (!wait.remainingInputs().isEmpty())
        {
            List<RecipePlan.Material> remaining = new ArrayList<>();
            RecipeOrderJob working = job;
            RecipePlan.Step step = job.steps().get(job.nextStep());
            for (RecipePlan.Material input : wait.remainingInputs())
            {
                InputSelection selection = selectInputs(level, network.getUnifiedStorage(),
                        working, step, List.of(input));
                if (selection == null)
                {
                    remaining.add(input);
                    continue;
                }
                long delivered = 0;
                List<RecipePlan.ReservedMaterial> consumed = new ArrayList<>();
                for (InputChunk chunk : selection.chunks())
                {
                    long capacity = NativeFurnaceAutomation.insertCapacity(
                            furnace, chunk.key(), chunk.amount());
                    if (capacity <= 0) continue;
                    KeyAmount taken = chunk.fromReserved() ? new KeyAmount(chunk.key(), capacity)
                            : network.getUnifiedStorage().extract(chunk.key(), capacity, false, false);
                    long inserted = NativeFurnaceAutomation.insert(furnace, chunk.key(), taken.amount());
                    if (!chunk.fromReserved() && inserted < taken.amount())
                        network.getUnifiedStorage().insert(taken.key(), taken.amount() - inserted, false);
                    if (chunk.fromReserved() && inserted > 0)
                        consumed.add(new RecipePlan.ReservedMaterial(chunk.key(), inserted));
                    delivered = SaturatingLongMath.add(delivered, inserted);
                }
                working = consumeReserved(working, consumed);
                long left = Math.max(0, input.amount() - delivered);
                if (left > 0) remaining.add(new RecipePlan.Material(
                        input.key(), left, input.ingredientSlot()));
            }
            wait = wait.withInputs(remaining);
            if (!remaining.isEmpty()) return working.awaitExternal(wait, encode("feeding_native_furnace"));
            job = working;
        }

        long currentNetwork = networkAmount(job.networkId(), wait.output());
        ExternalOrderLogic.NetworkCredit networkCredit = ExternalOrderLogic.creditNetworkOutput(
                wait.networkBaseline(), currentNetwork, wait.networkObserved(), wait.collected(), wait.amount());
        long credited = Math.max(0, networkCredit.collected() - wait.collected());
        if (credited > 0 && job.nextStep() + 1 < job.steps().size())
        {
            List<KeyAmount> captured = extractOutputDelta(job.networkId(), network.getUnifiedStorage(),
                    wait.output(), credited, wait.networkBaselineStacks());
            long capturedAmount = 0;
            for (KeyAmount value : captured)
                capturedAmount = SaturatingLongMath.add(capturedAmount, value.amount());
            job = addReserved(job, captured.stream().map(value ->
                    new RecipePlan.ReservedMaterial((ItemStackKey) value.key(), value.amount())).toList());
            long afterCapture = networkAmount(job.networkId(), wait.output());
            networkCredit = new ExternalOrderLogic.NetworkCredit(
                    ExternalOrderLogic.availableMachineOutput(wait.networkBaseline(), afterCapture),
                    wait.collected() + capturedAmount);
        }
        wait = wait.withProgress(networkCredit.observed(), networkCredit.collected());

        long machineOutput = NativeFurnaceAutomation.countOutput(furnace, wait.output());
        long available = ExternalOrderLogic.availableMachineOutput(wait.baseline(), machineOutput);
        long transferable = Math.min(available, wait.amount() - wait.collected());
        if (transferable > 0)
        {
            long inserted = 0;
            List<RecipePlan.ReservedMaterial> produced = new ArrayList<>();
            boolean escrowOutput = job.nextStep() + 1 < job.steps().size();
            for (KeyAmount output : NativeFurnaceAutomation.extractOutputStacks(
                    furnace, wait.output(), transferable))
            {
                if (escrowOutput)
                {
                    produced.add(new RecipePlan.ReservedMaterial(
                            (ItemStackKey) output.key(), output.amount()));
                    inserted = SaturatingLongMath.add(inserted, output.amount());
                    continue;
                }
                KeyAmount remainder = network.getUnifiedStorage().insert(output.key(), output.amount(), false);
                inserted = SaturatingLongMath.add(inserted, output.amount() - remainder.amount());
                if (!remainder.isEmpty()) NativeFurnaceAutomation.restoreOutput(
                        furnace, (ItemStackKey) remainder.key(), remainder.amount());
            }
            job = addReserved(job, produced);
            long afterInsert = networkAmount(job.networkId(), wait.output());
            long afterObserved = ExternalOrderLogic.availableMachineOutput(wait.networkBaseline(), afterInsert);
            wait = wait.withProgress(Math.max(wait.networkObserved(), afterObserved),
                    Math.min(wait.amount(), wait.collected() + inserted));
        }
        if (wait.collected() >= wait.amount()) return job.completeExternalBatch();
        return job.awaitExternal(wait, encode("native_furnace_processing", wait.collected(), wait.amount()));
    }

    private static long networkAmount(int networkId, Identifier itemId)
    {
        long amount = 0;
        for (PlanningSnapshotService.ComponentEntry value :
                PlanningSnapshotService.capture(networkId).componentEntries())
            if (itemId.equals(value.item())) amount = SaturatingLongMath.add(amount, value.amount());
        return amount;
    }

    private static long networkAmount(int networkId, IStackKey<?> key)
    {
        long amount = 0;
        for (PlanningSnapshotService.ComponentEntry value :
                PlanningSnapshotService.capture(networkId).componentEntries())
            if (key.isSame(value.key())) amount = SaturatingLongMath.add(amount, value.amount());
        return amount;
    }

    private static List<KeyAmount> extractOutputDelta(int networkId, UnifiedStorage storage,
                                                      Identifier itemId, long amount,
                                                      List<RecipePlan.ReservedMaterial> baseline)
    {
        java.util.HashMap<com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>, Long> original = new java.util.HashMap<>();
        for (RecipePlan.ReservedMaterial value : baseline)
            original.merge(value.key(), value.amount(), SaturatingLongMath::add);
        List<KeyAmount> result = new ArrayList<>();
        long remaining = amount;
        for (PlanningSnapshotService.ComponentEntry value :
                PlanningSnapshotService.capture(networkId).componentEntries())
        {
            if (remaining <= 0) break;
            if (!itemId.equals(value.item())) continue;
            long delta = Math.max(0, value.amount() - original.getOrDefault(value.key(), 0L));
            if (delta <= 0) continue;
            KeyAmount taken = storage.extract(value.key(), Math.min(remaining, delta), false, false);
            if (!taken.isEmpty())
            {
                result.add(taken);
                remaining -= taken.amount();
            }
        }
        return List.copyOf(result);
    }

    private static List<KeyAmount> extractOutputDelta(int networkId, UnifiedStorage storage,
                                                      IStackKey<?> outputKey, long amount,
                                                      List<RecipePlan.ReservedMaterial> baseline)
    {
        java.util.HashMap<IStackKey<?>, Long> original = new java.util.HashMap<>();
        for (RecipePlan.ReservedMaterial value : baseline)
            original.merge(value.key(), value.amount(), SaturatingLongMath::add);
        List<KeyAmount> result = new ArrayList<>();
        long remaining = amount;
        for (PlanningSnapshotService.ComponentEntry value :
                PlanningSnapshotService.capture(networkId).componentEntries())
        {
            if (remaining <= 0) break;
            if (!outputKey.isSame(value.key())) continue;
            long delta = Math.max(0, value.amount() - original.getOrDefault(value.key(), 0L));
            if (delta <= 0) continue;
            KeyAmount taken = storage.extract(value.key(), Math.min(remaining, delta), false, false);
            if (!taken.isEmpty()) { result.add(taken); remaining -= taken.amount(); }
        }
        return List.copyOf(result);
    }

    private static List<RecipePlan.ReservedMaterial> outputBaseline(int networkId, Identifier itemId)
    {
        return PlanningSnapshotService.capture(networkId).componentEntries().stream()
                .filter(value -> itemId.equals(value.item()))
                .map(value -> new RecipePlan.ReservedMaterial(value.key(), value.amount())).toList();
    }

    private static List<RecipePlan.ReservedMaterial> outputBaseline(int networkId, IStackKey<?> outputKey)
    {
        return PlanningSnapshotService.capture(networkId).componentEntries().stream()
                .filter(value -> outputKey.isSame(value.key()))
                .map(value -> new RecipePlan.ReservedMaterial(value.key(), value.amount())).toList();
    }

    private static List<RecipePlan.Material> inputsToDispatch(boolean blockingMode, RecipePlan.Step step)
    {
        return step.inputs().stream().map(input -> new RecipePlan.Material(input.key(),
                BlockingModeLogic.amountToDispatch(blockingMode, input.amount(), step.crafts()),
                input.ingredientSlot())).toList();
    }

    private static ItemStackKey key(Identifier id)
    { return new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(id))); }

    private static List<RecipePlan.ReservedMaterial> reserveInitial(
            UnifiedStorage storage, List<RecipePlan.ReservedMaterial> requested)
    {
        List<RecipePlan.ReservedMaterial> reserved = new ArrayList<>();
        for (RecipePlan.ReservedMaterial material : requested)
        {
            KeyAmount taken = storage.extract(material.key(), material.amount(), false, false);
            if (taken.amount() != material.amount())
            {
                if (!taken.isEmpty()) reserved.add(new RecipePlan.ReservedMaterial(
                        taken.key(), taken.amount()));
                releaseReservations(storage, reserved);
                throw new IllegalStateException("required resource changed: "
                        + RecipeResourceResolver.sortKey(material.key()));
            }
            reserved.add(new RecipePlan.ReservedMaterial(material.key(), material.amount()));
        }
        return List.copyOf(reserved);
    }

    private static void releaseReservations(UnifiedStorage storage,
                                            List<RecipePlan.ReservedMaterial> reserved)
    {
        List<KeyAmount> inserted = new ArrayList<>();
        for (RecipePlan.ReservedMaterial material : reserved)
        {
            KeyAmount remainder = storage.insert(material.key(), material.amount(), false);
            long accepted = material.amount() - remainder.amount();
            if (accepted > 0) inserted.add(new KeyAmount(material.key(), accepted));
            if (!remainder.isEmpty())
            {
                inserted.forEach(value -> storage.extract(value.key(), value.amount(), false, false));
                throw new IllegalStateException("network has no room to return reserved resource: "
                        + RecipeResourceResolver.sortKey(material.key()));
            }
        }
    }

    private static RecipeOrderJob consumeReserved(RecipeOrderJob job,
                                                   List<RecipePlan.ReservedMaterial> consumed)
    {
        if (consumed.isEmpty()) return job;
        java.util.LinkedHashMap<com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>, Long> remaining = new java.util.LinkedHashMap<>();
        for (RecipePlan.ReservedMaterial material : job.reserved())
            remaining.merge(material.key(), material.amount(), SaturatingLongMath::add);
        java.util.LinkedHashMap<com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>, Long> used = new java.util.LinkedHashMap<>();
        for (RecipePlan.ReservedMaterial material : consumed)
            used.merge(material.key(), material.amount(), SaturatingLongMath::add);
        remaining = ReservationLedger.subtract(remaining, used);
        return job.withReserved(remaining.entrySet().stream().map(entry ->
                new RecipePlan.ReservedMaterial(entry.getKey(), entry.getValue())).toList());
    }

    private static RecipeOrderJob addReserved(RecipeOrderJob job,
                                              List<RecipePlan.ReservedMaterial> added)
    {
        if (added.isEmpty()) return job;
        java.util.LinkedHashMap<com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>, Long> values = new java.util.LinkedHashMap<>();
        for (RecipePlan.ReservedMaterial material : job.reserved())
            values.merge(material.key(), material.amount(), SaturatingLongMath::add);
        for (RecipePlan.ReservedMaterial material : added)
            values.merge(material.key(), material.amount(), SaturatingLongMath::add);
        return job.withReserved(values.entrySet().stream().map(entry ->
                new RecipePlan.ReservedMaterial(entry.getKey(), entry.getValue())).toList());
    }

    private static InputSelection selectInputs(ServerLevel level, UnifiedStorage storage,
                                               RecipeOrderJob job, RecipePlan.Step step,
                                               List<RecipePlan.Material> materials)
    {
        java.util.LinkedHashMap<com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>, Long> reserved = new java.util.LinkedHashMap<>();
        for (RecipePlan.ReservedMaterial material : job.reserved())
            reserved.merge(material.key(), material.amount(), SaturatingLongMath::add);
        java.util.LinkedHashMap<com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>, Long> network = new java.util.LinkedHashMap<>();
        for (PlanningSnapshotService.ComponentEntry available :
                PlanningSnapshotService.capture(job.networkId()).componentEntries())
            network.merge(available.key(), available.amount(), SaturatingLongMath::add);

        List<InputChunk> chunks = new ArrayList<>();
        for (RecipePlan.Material material : materials)
        {
            Ingredient ingredient = ingredient(level, step, material.ingredientSlot());
            long needed = material.amount();
            needed = selectFrom(reserved, material.key(), ingredient, needed, true, chunks);
            needed = selectFrom(network, material.key(), ingredient, needed, false, chunks);
            if (needed > 0) return null;
        }
        List<RecipePlan.ReservedMaterial> consumed = chunks.stream().filter(InputChunk::fromReserved)
                .collect(java.util.stream.Collectors.groupingBy(InputChunk::key, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.summingLong(InputChunk::amount)))
                .entrySet().stream().map(entry ->
                        new RecipePlan.ReservedMaterial(entry.getKey(), entry.getValue())).toList();
        return new InputSelection(List.copyOf(chunks), consumed);
    }

    private static long selectFrom(java.util.LinkedHashMap<com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>, Long> available,
                                   com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> requested,
                                   Ingredient ingredient, long needed,
                                   boolean reserved, List<InputChunk> selected)
    {
        if (needed <= 0) return 0;
        for (var entry : available.entrySet())
        {
            if (entry.getValue() <= 0 || !requested.isSame(entry.getKey())) continue;
            if (ingredient != null && (!(entry.getKey() instanceof ItemStackKey itemKey)
                    || !ingredient.test(itemKey.getReadOnlyStack()))) continue;
            long amount = Math.min(entry.getValue(), needed);
            entry.setValue(entry.getValue() - amount);
            selected.add(new InputChunk(entry.getKey(), amount, reserved));
            needed -= amount;
            if (needed == 0) break;
        }
        return needed;
    }

    private static Ingredient ingredient(ServerLevel level, RecipePlan.Step step, int slot)
    {
        if (slot < 0) return null;
        var holder = level.recipeAccess().byKey(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE, step.recipe())).orElse(null);
        if (holder == null) return null;
        var ingredients = com.amicbeam.beyondcraftlines.common.crafting.RecipeIngredientResolver
                .ingredients(holder.value());
        if (slot >= ingredients.size()) return null;
        return ingredients.get(slot);
    }

    private record InputChunk(com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> key,
                              long amount, boolean fromReserved) {}
    private record InputSelection(List<InputChunk> chunks,
                                  List<RecipePlan.ReservedMaterial> consumedReserved) {}

    private static boolean terminal(RecipeOrderJob.Status status)
    {
        return status == RecipeOrderJob.Status.COMPLETE || status == RecipeOrderJob.Status.CANCELLED
                || status == RecipeOrderJob.Status.ERROR;
    }

    private record MachineKey(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                              net.minecraft.core.BlockPos position) {}
}
