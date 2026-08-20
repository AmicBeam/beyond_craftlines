package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath;
import com.amicbeam.beyondcraftlines.common.crafting.SimulatedCrafting;
import com.amicbeam.beyondcraftlines.common.data.BindingRecord;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.Comparator;

public final class RecipeOrderService
{
    private static final Set<String> NATIVE_FURNACE_FAMILIES = Set.of("smelting", "blasting", "smoking");
    private RecipeOrderService() {}

    public static RecipeOrderJob enqueue(ServerLevel level, UUID owner, int networkId,
                                         ResourceLocation target, long count, boolean blockingMode)
    {
        RecipePlan plan = RecipePlanningService.plan(level, networkId, target, count);
        if (!plan.craftable()) throw new IllegalStateException("missing: " + plan.missing());
        RecipeOrderJob job = new RecipeOrderJob(UUID.randomUUID(), owner, networkId, target, count,
                plan.steps(), 0, blockingMode, plan.steps().isEmpty() ? RecipeOrderJob.Status.COMPLETE
                : RecipeOrderJob.Status.QUEUED, "", level.getGameTime(), 0, null);
        RecipeOrderSavedData.get(level.getServer()).put(job);
        return job;
    }

    public static boolean cancel(MinecraftServer server, UUID owner, UUID id)
    {
        RecipeOrderSavedData data = RecipeOrderSavedData.get(server);
        RecipeOrderJob job = data.get(id);
        if (job == null || !job.owner().equals(owner) || terminal(job.status())) return false;
        data.put(job.with(RecipeOrderJob.Status.CANCELLED, "cancelled by owner"));
        return true;
    }

    public static void tick(MinecraftServer server)
    {
        RecipeOrderSavedData data = RecipeOrderSavedData.get(server);
        List<RecipeOrderJob> jobs = data.all().stream()
                .filter(job -> !terminal(job.status()))
                .sorted(Comparator.comparingLong(RecipeOrderJob::createdAt)
                        .thenComparing(job -> job.id().toString())).toList();
        Set<Integer> activeNetworks = new HashSet<>();
        for (RecipeOrderJob job : jobs)
        {
            if (!activeNetworks.add(job.networkId()))
            {
                data.put(job.with(RecipeOrderJob.Status.PAUSED, "waiting for network order transaction"));
                continue;
            }
            try { data.put(executeStep(server, job)); }
            catch (RuntimeException exception)
            { data.put(job.with(RecipeOrderJob.Status.ERROR, exception.getMessage())); }
        }
    }

    private static RecipeOrderJob executeStep(MinecraftServer server, RecipeOrderJob job)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(job.networkId());
        if (network == null) return job.with(RecipeOrderJob.Status.PAUSED, "network unavailable");
        if (job.externalWait() != null) return job.externalWait().nativeFurnace()
                ? tickNativeFurnace(server, network, job) : tickBoundMachine(server, network, job);
        if (job.nextStep() >= job.steps().size()) return job.with(RecipeOrderJob.Status.COMPLETE, "");
        RecipePlan.Step step = job.steps().get(job.nextStep());
        if (NATIVE_FURNACE_FAMILIES.contains(step.family()))
        {
            Optional<NativeFurnaceRegistry.NativeFurnace> furnace =
                    NativeFurnaceRegistry.furnaceFor(server, job.networkId(), step.family());
            return furnace.isPresent() ? reserveNativeFurnace(server, network, job, step, furnace.get())
                    : job.with(RecipeOrderJob.Status.PAUSED,
                    "BD network furnace unavailable for " + step.family());
        }
        Optional<DeviceBindingRegistry.ProvisionerTarget> provisioner =
                DeviceBindingRegistry.provisionerFor(server, job.networkId(), step.family());
        if (provisioner.isPresent()) return deliverToProvisioner(network, job, step, provisioner.get());
        Optional<DeviceBindingRegistry.BoundMachine> machine =
                DeviceBindingRegistry.machineFor(server, job.networkId(), step.family());
        if (machine.isPresent()) return reserveMachine(server, job, step, machine.get());
        if (!"crafting".equals(step.family()))
            return job.with(RecipeOrderJob.Status.PAUSED, "bound machine unavailable for " + step.family());
        long gameTime = server.overworld().getGameTime();
        if (!VirtualCraftingThrottle.ready(gameTime, job.nextCraftingTick()))
            return job.with(RecipeOrderJob.Status.PAUSED, "waiting for virtual crafting node interval");
        return executeCrafting(server.overworld(), network, job, step, gameTime);
    }

    private static RecipeOrderJob deliverToProvisioner(DimensionsNet network, RecipeOrderJob job,
                                                        RecipePlan.Step step,
                                                        DeviceBindingRegistry.ProvisionerTarget target)
    {
        ProvisionerStorage provisioner = target.provisioner().storage();
        for (RecipePlan.Material input : step.inputs())
            if (!provisioner.insertFromOrder(key(input.item()), input.amount(), true).isEmpty())
                return job.with(RecipeOrderJob.Status.PAUSED, "provisioner has no room for " + input.item());

        List<KeyAmount> extracted = new ArrayList<>();
        for (RecipePlan.Material input : step.inputs())
        {
            KeyAmount result = network.getUnifiedStorage().extract(key(input.item()), input.amount(), false, false);
            if (!StorageTransfer.isComplete(input.amount(), result.amount()))
            {
                if (!result.isEmpty()) extracted.add(result);
                extracted.forEach(value -> network.getUnifiedStorage().insert(value.key(), value.amount(), false));
                return job.with(RecipeOrderJob.Status.PAUSED, "waiting for " + input.item());
            }
            extracted.add(result);
        }

        List<KeyAmount> inserted = new ArrayList<>();
        for (KeyAmount value : extracted)
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
                return job.with(RecipeOrderJob.Status.PAUSED, "provisioner delivery rolled back");
            }
        }
        return job.advance();
    }

    private static RecipeOrderJob executeCrafting(ServerLevel level, DimensionsNet network, RecipeOrderJob job,
                                                  RecipePlan.Step step, long gameTime)
    {
        UnifiedStorage storage = network.getUnifiedStorage();
        SimulatedCrafting.Attempt attempt = SimulatedCrafting.craftOne(level, storage, step.recipe(), step.output());
        if (!attempt.success()) return job.with(RecipeOrderJob.Status.PAUSED, attempt.reason());
        int interval = CraftlinesConfig.VIRTUAL_CRAFTING_NODE_INTERVAL_TICKS.get();
        long nextTick = VirtualCraftingThrottle.nextAllowedTick(gameTime, interval);
        return job.completeSingleCraft(nextTick);
    }

    private static RecipeOrderJob reserveMachine(MinecraftServer server, RecipeOrderJob job,
                                                  RecipePlan.Step step,
                                                  DeviceBindingRegistry.BoundMachine machine)
    {
        BindingRecord binding = machine.binding();
        boolean busy = RecipeOrderSavedData.get(server).all().stream()
                .filter(other -> !other.id().equals(job.id()) && !terminal(other.status()))
                .map(RecipeOrderJob::externalWait).filter(Objects::nonNull)
                .anyMatch(wait -> wait.machineDimension().equals(binding.dimension())
                        && wait.machinePosition().equals(binding.position()));
        if (busy) return job.with(RecipeOrderJob.Status.PAUSED, "bound machine is busy");
        if (step.inputs().stream().anyMatch(input -> input.item().equals(step.output())))
            return job.with(RecipeOrderJob.Status.ERROR,
                    "generic machine automation does not support output items that are also inputs");
        Set<ResourceLocation> inputItems = step.inputs().stream().map(RecipePlan.Material::item)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (BlockingModeLogic.shouldWait(job.blockingMode(), BoundMachineAutomation.containsAny(
                machine.level(), binding.position(), inputItems)))
            return job.with(RecipeOrderJob.Status.PAUSED,
                    "blocking mode: target machine still contains a recipe input");
        long baseline = BoundMachineAutomation.countExtractable(
                machine.level(), binding.position(), step.output());
        long batchCrafts = BlockingModeLogic.craftsToDispatch(job.blockingMode(), step.crafts());
        long output = SaturatingLongMath.multiply(step.outputPerCraft(), batchCrafts);
        List<RecipePlan.Material> batchInputs = inputsToDispatch(job.blockingMode(), step);
        RecipeOrderJob.ExternalWait wait = new RecipeOrderJob.ExternalWait(binding.dimension(),
                binding.position(), step.output(), false, baseline, 0, 0, output, 0, batchInputs);
        return job.awaitExternal(wait, "bound machine reserved; preparing inputs");
    }

    private static RecipeOrderJob reserveNativeFurnace(MinecraftServer server, DimensionsNet network,
                                                        RecipeOrderJob job, RecipePlan.Step step,
                                                        NativeFurnaceRegistry.NativeFurnace nativeFurnace)
    {
        BaseNetFurnaceBlockEntity<?> furnace = nativeFurnace.blockEntity();
        boolean busy = RecipeOrderSavedData.get(server).all().stream()
                .filter(other -> !other.id().equals(job.id()) && !terminal(other.status()))
                .map(RecipeOrderJob::externalWait).filter(Objects::nonNull)
                .anyMatch(wait -> wait.machineDimension().equals(nativeFurnace.level().dimension())
                        && wait.machinePosition().equals(furnace.getBlockPos()));
        if (busy) return job.with(RecipeOrderJob.Status.PAUSED, "BD network furnace is busy");
        Set<ResourceLocation> inputItems = step.inputs().stream().map(RecipePlan.Material::item)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (BlockingModeLogic.shouldWait(job.blockingMode(),
                NativeFurnaceAutomation.containsAnyInput(furnace, inputItems)))
            return job.with(RecipeOrderJob.Status.PAUSED,
                    "blocking mode: BD network furnace still contains a recipe input");
        if (NativeFurnaceAutomation.countOutput(furnace, step.output()) > 0)
            return job.with(RecipeOrderJob.Status.PAUSED,
                    "waiting for pre-existing BD network furnace output to clear");
        long batchCrafts = BlockingModeLogic.craftsToDispatch(job.blockingMode(), step.crafts());
        long output = SaturatingLongMath.multiply(step.outputPerCraft(), batchCrafts);
        List<RecipePlan.Material> batchInputs = inputsToDispatch(job.blockingMode(), step);
        RecipeOrderJob.ExternalWait wait = new RecipeOrderJob.ExternalWait(
                nativeFurnace.level().dimension(), furnace.getBlockPos(), step.output(), true,
                0,
                networkAmount(network.getUnifiedStorage(), step.output()), 0, output, 0, batchInputs);
        return job.awaitExternal(wait, "BD network furnace reserved; preparing inputs");
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
            return job.with(RecipeOrderJob.Status.ERROR, "bound machine was removed or changed");

        if (!wait.remainingInputs().isEmpty())
        {
            List<RecipePlan.Material> remaining = new ArrayList<>();
            for (RecipePlan.Material input : wait.remainingInputs())
            {
                long capacity = BoundMachineAutomation.insertCapacity(level, wait.machinePosition(),
                        input.item(), input.amount());
                if (capacity <= 0)
                {
                    remaining.add(input);
                    continue;
                }
                KeyAmount taken = network.getUnifiedStorage().extract(key(input.item()), capacity, false, false);
                long inserted = BoundMachineAutomation.insert(level, wait.machinePosition(),
                        input.item(), taken.amount());
                if (inserted < taken.amount())
                    network.getUnifiedStorage().insert(taken.key(), taken.amount() - inserted, false);
                long left = ExternalOrderLogic.remainingInput(input.amount(), taken.amount(), inserted);
                if (left > 0) remaining.add(new RecipePlan.Material(input.item(), left));
            }
            wait = wait.withInputs(remaining);
            if (!remaining.isEmpty()) return job.awaitExternal(wait, "feeding bound machine inputs");
        }

        long current = BoundMachineAutomation.countExtractable(level, wait.machinePosition(), wait.output());
        long available = ExternalOrderLogic.availableMachineOutput(wait.baseline(), current);
        long transferable = Math.min(available, wait.amount() - wait.collected());
        if (transferable > 0)
        {
            KeyAmount simulatedRemainder = network.getUnifiedStorage().insert(key(wait.output()), transferable, true);
            long networkCapacity = transferable - simulatedRemainder.amount();
            long extracted = BoundMachineAutomation.extract(
                    level, wait.machinePosition(), wait.output(), networkCapacity);
            if (extracted > 0)
            {
                KeyAmount remainder = network.getUnifiedStorage().insert(key(wait.output()), extracted, false);
                wait = wait.withCollected(wait.collected() + extracted - remainder.amount());
            }
        }
        if (wait.collected() >= wait.amount()) return job.completeExternalBatch();
        return job.awaitExternal(wait,
                "machine processing; returned " + wait.collected() + "/" + wait.amount());
    }

    private static RecipeOrderJob tickNativeFurnace(MinecraftServer server, DimensionsNet network,
                                                     RecipeOrderJob job)
    {
        RecipeOrderJob.ExternalWait wait = job.externalWait();
        ServerLevel level = server.getLevel(wait.machineDimension());
        if (level == null || !level.isLoaded(wait.machinePosition())
                || !(level.getBlockEntity(wait.machinePosition()) instanceof BaseNetFurnaceBlockEntity<?> furnace)
                || furnace.getNetId() != job.networkId())
            return job.with(RecipeOrderJob.Status.ERROR, "BD network furnace was removed or unbound");

        String expectedFamily = job.steps().get(job.nextStep()).family();
        if (!NativeFurnaceRegistry.supports(furnace, expectedFamily))
            return job.with(RecipeOrderJob.Status.ERROR, "BD network furnace type changed");

        if (!wait.remainingInputs().isEmpty())
        {
            List<RecipePlan.Material> remaining = new ArrayList<>();
            for (RecipePlan.Material input : wait.remainingInputs())
            {
                long capacity = NativeFurnaceAutomation.insertCapacity(furnace, input.item(), input.amount());
                if (capacity <= 0)
                {
                    remaining.add(input);
                    continue;
                }
                KeyAmount taken = network.getUnifiedStorage().extract(key(input.item()), capacity, false, false);
                long inserted = NativeFurnaceAutomation.insert(furnace, input.item(), taken.amount());
                if (inserted < taken.amount())
                    network.getUnifiedStorage().insert(taken.key(), taken.amount() - inserted, false);
                long left = ExternalOrderLogic.remainingInput(input.amount(), taken.amount(), inserted);
                if (left > 0) remaining.add(new RecipePlan.Material(input.item(), left));
            }
            wait = wait.withInputs(remaining);
            if (!remaining.isEmpty()) return job.awaitExternal(wait, "feeding BD network furnace inputs");
        }

        long currentNetwork = networkAmount(network.getUnifiedStorage(), wait.output());
        ExternalOrderLogic.NetworkCredit networkCredit = ExternalOrderLogic.creditNetworkOutput(
                wait.networkBaseline(), currentNetwork, wait.networkObserved(), wait.collected(), wait.amount());
        wait = wait.withProgress(networkCredit.observed(), networkCredit.collected());

        long machineOutput = NativeFurnaceAutomation.countOutput(furnace, wait.output());
        long available = ExternalOrderLogic.availableMachineOutput(wait.baseline(), machineOutput);
        long transferable = Math.min(available, wait.amount() - wait.collected());
        if (transferable > 0)
        {
            KeyAmount simulated = network.getUnifiedStorage().insert(key(wait.output()), transferable, true);
            long capacity = transferable - simulated.amount();
            long extracted = NativeFurnaceAutomation.extractOutput(furnace, wait.output(), capacity);
            if (extracted > 0)
            {
                KeyAmount remainder = network.getUnifiedStorage().insert(key(wait.output()), extracted, false);
                long inserted = extracted - remainder.amount();
                if (!remainder.isEmpty()) NativeFurnaceAutomation.restoreOutput(
                        furnace, wait.output(), remainder.amount());
                long afterInsert = networkAmount(network.getUnifiedStorage(), wait.output());
                long afterObserved = ExternalOrderLogic.availableMachineOutput(wait.networkBaseline(), afterInsert);
                wait = wait.withProgress(Math.max(wait.networkObserved(), afterObserved),
                        Math.min(wait.amount(), wait.collected() + inserted));
            }
        }
        if (wait.collected() >= wait.amount()) return job.completeExternalBatch();
        return job.awaitExternal(wait,
                "BD network furnace processing; returned " + wait.collected() + "/" + wait.amount());
    }

    private static long networkAmount(UnifiedStorage storage, ResourceLocation itemId)
    {
        long total = 0;
        for (KeyAmount value : storage.getStorage())
            if (value.key() instanceof ItemStackKey itemKey
                    && BuiltInRegistries.ITEM.getKey(itemKey.getSource()).equals(itemId))
                total = SaturatingLongMath.add(total, value.amount());
        return total;
    }

    private static List<RecipePlan.Material> inputsToDispatch(boolean blockingMode, RecipePlan.Step step)
    {
        return step.inputs().stream().map(input -> new RecipePlan.Material(input.item(),
                BlockingModeLogic.amountToDispatch(blockingMode, input.amount(), step.crafts()))).toList();
    }

    private static ItemStackKey key(ResourceLocation id)
    { return new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.get(id))); }

    private static boolean terminal(RecipeOrderJob.Status status)
    {
        return status == RecipeOrderJob.Status.COMPLETE || status == RecipeOrderJob.Status.CANCELLED
                || status == RecipeOrderJob.Status.ERROR;
    }
}
