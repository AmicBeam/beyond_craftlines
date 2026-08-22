package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.util.NbtCompat;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public final class RecipeOrderSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_recipe_orders";
    private static final SavedDataType<RecipeOrderSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, NAME),
            RecipeOrderSavedData::new,
            CompoundTag.CODEC.xmap(
                    tag -> load(tag, NbtCompat.builtinRegistries()),
                    data -> data.save(new CompoundTag(), NbtCompat.builtinRegistries())),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<UUID, RecipeOrderJob> jobs = new LinkedHashMap<>();

    public static RecipeOrderSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static RecipeOrderSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        RecipeOrderSavedData data = new RecipeOrderSavedData();
        ListTag jobs = tag.getListOrEmpty("jobs");
        for (int i = 0; i < jobs.size(); i++)
        {
            try
            {
                CompoundTag value = jobs.getCompoundOrEmpty(i);
                List<RecipePlan.Step> steps = new ArrayList<>();
                ListTag encodedSteps = value.getListOrEmpty("steps");
                for (int j = 0; j < encodedSteps.size(); j++)
                    steps.add(readStep(encodedSteps.getCompoundOrEmpty(j), registries));
                boolean parallel = !encodedSteps.isEmpty()
                        && encodedSteps.getCompoundOrEmpty(0).contains("execution_complete");
                RecipeOrderJob job;
                if (parallel)
                {
                    List<RecipeOrderJob.StepExecution> executions = new ArrayList<>();
                    for (int j = 0; j < encodedSteps.size(); j++)
                    {
                        CompoundTag encoded = encodedSteps.getCompoundOrEmpty(j);
                        executions.add(new RecipeOrderJob.StepExecution(steps.get(j),
                                encoded.getBooleanOr("execution_complete", false),
                                encoded.getLongOr("execution_next_crafting_tick", 0L),
                                readExternalWait(encoded, registries)));
                    }
                    job = new RecipeOrderJob(NbtCompat.getUuid(value, "id"), NbtCompat.getUuid(value, "owner"),
                            value.getIntOr("network", -1), Identifier.parse(value.getStringOr("target", "minecraft:air")),
                            value.getLongOr("requested", 0L), executions, -1,
                            value.getBooleanOr("blocking_mode", false),
                            RecipeOrderJob.Status.valueOf(value.getStringOr("status", "ERROR")),
                            value.getStringOr("message", ""), value.getLongOr("created", 0L),
                            value.getLongOr("finished", 0L), readReserved(value, registries));
                }
                else job = new RecipeOrderJob(NbtCompat.getUuid(value, "id"), NbtCompat.getUuid(value, "owner"),
                        value.getIntOr("network", -1), Identifier.parse(value.getStringOr("target", "minecraft:air")),
                        value.getLongOr("requested", 0L), steps, value.getIntOr("next", 0),
                        value.getBooleanOr("blocking_mode", false),
                        RecipeOrderJob.Status.valueOf(value.getStringOr("status", "ERROR")), value.getStringOr("message", ""),
                        value.getLongOr("created", 0L), value.getLongOr("finished", 0L), value.getLongOr("next_crafting_tick", 0L),
                        readExternalWait(value, registries), readReserved(value, registries));
                data.jobs.put(job.id(), job);
            }
            catch (RuntimeException ignored) {}
        }
        if (data.pruneTerminalHistory()) data.setDirty();
        return data;
    }

    public List<RecipeOrderJob> all() { return List.copyOf(jobs.values()); }
    public List<RecipeOrderJob> active()
    {
        return jobs.values().stream().filter(job -> !terminal(job.status())).toList();
    }
    public List<RecipeOrderJob> forOwner(UUID owner) { return jobs.values().stream().filter(j -> j.owner().equals(owner)).toList(); }
    public RecipeOrderJob get(UUID id) { return jobs.get(id); }
    public void removeExpiredDisplayedTerminal(long gameTime)
    {
        boolean removed = jobs.values().removeIf(job ->
                OrderRetention.expired(job.status(), job.finishedAt(), gameTime));
        if (removed) setDirty();
    }
    public void put(RecipeOrderJob job)
    {
        RecipeOrderJob previous = jobs.put(job.id(), job);
        boolean pruned = terminal(job.status()) && pruneTerminalHistory();
        if (!job.equals(previous) || pruned) setDirty();
    }

    private boolean pruneTerminalHistory()
    {
        List<UUID> excess = jobs.values().stream().filter(job -> terminal(job.status()))
                .sorted(Comparator.comparingLong(RecipeOrderJob::createdAt).reversed())
                .skip(CraftlinesConfig.TERMINAL_ORDER_HISTORY_LIMIT.get()).map(RecipeOrderJob::id).toList();
        excess.forEach(jobs::remove);
        return !excess.isEmpty();
    }

    private static boolean terminal(RecipeOrderJob.Status status)
    {
        return status == RecipeOrderJob.Status.COMPLETE || status == RecipeOrderJob.Status.CANCELLED
                || status == RecipeOrderJob.Status.ERROR;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (RecipeOrderJob job : jobs.values())
        {
            CompoundTag value = new CompoundTag();
            NbtCompat.putUuid(value, "id", job.id()); NbtCompat.putUuid(value, "owner", job.owner());
            value.putInt("network", job.networkId()); value.putString("target", job.target().toString());
            value.putLong("requested", job.requested()); value.putInt("next", job.nextStep());
            value.putBoolean("blocking_mode", job.blockingMode());
            value.putString("status", job.status().name()); value.putString("message", job.message());
            value.putLong("created", job.createdAt());
            value.putLong("finished", job.finishedAt());
            value.putLong("next_crafting_tick", job.nextCraftingTick());
            ListTag reserved = new ListTag();
            for (RecipePlan.ReservedMaterial material : job.reserved())
            {
                CompoundTag encoded = new CompoundTag();
                writeKey(encoded, material.key(), registries);
                encoded.putLong("amount", material.amount());
                reserved.add(encoded);
            }
            value.put("reserved", reserved);
            if (job.externalWait() != null) writeExternalWait(value, job.externalWait(), registries);
            ListTag steps = new ListTag();
            for (RecipeOrderJob.StepExecution execution : job.executions())
            {
                CompoundTag step = writeStep(execution.step(), registries);
                step.putBoolean("execution_complete", execution.complete());
                step.putLong("execution_next_crafting_tick", execution.nextCraftingTick());
                if (execution.externalWait() != null)
                    writeExternalWait(step, execution.externalWait(), registries);
                steps.add(step);
            }
            value.put("steps", steps); list.add(value);
        }
        tag.put("jobs", list);
        return tag;
    }

    private static CompoundTag writeStep(RecipePlan.Step step, HolderLookup.Provider registries)
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", step.recipe().toString()); tag.putString("family", step.family());
        tag.putString("output", step.output().toString());
        CompoundTag outputKey = new CompoundTag();
        writeKey(outputKey, step.outputKey(), registries);
        tag.put("output_key", outputKey);
        tag.putLong("per", step.outputPerCraft());
        tag.putLong("crafts", step.crafts());
        ListTag inputs = new ListTag();
        for (RecipePlan.Material input : step.inputs())
        {
            CompoundTag value = new CompoundTag();
            writeKey(value, input.key(), registries);
            value.putLong("amount", input.amount());
            value.putInt("ingredient_slot", input.ingredientSlot());
            value.putString("input_group", input.inputGroup());
            inputs.add(value);
        }
        tag.put("inputs", inputs);
        ListTag selections = new ListTag();
        for (RecipePlan.IngredientSelection selection : step.ingredientSelections())
        {
            CompoundTag value = new CompoundTag();
            value.putInt("slot", selection.slot());
            value.putString("item", selection.item().toString());
            selections.add(value);
        }
        tag.put("ingredient_selections", selections);
        tag.putIntArray("dependencies", step.dependencies().stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    private static RecipePlan.Step readStep(CompoundTag tag, HolderLookup.Provider registries)
    {
        List<RecipePlan.Material> inputs = new ArrayList<>();
        ListTag encoded = tag.getListOrEmpty("inputs");
        for (int i = 0; i < encoded.size(); i++)
        {
            CompoundTag value = encoded.getCompoundOrEmpty(i);
            inputs.add(new RecipePlan.Material(readKey(value, registries),
                    value.getLongOr("amount", 0L), value.contains("ingredient_slot")
                    ? value.getIntOr("ingredient_slot", -1) : -1,
                    value.getStringOr("input_group", "ingredients")));
        }
        List<RecipePlan.IngredientSelection> selections = new ArrayList<>();
        ListTag encodedSelections = tag.getListOrEmpty("ingredient_selections");
        for (int i = 0; i < encodedSelections.size(); i++)
        {
            CompoundTag value = encodedSelections.getCompoundOrEmpty(i);
            selections.add(new RecipePlan.IngredientSelection(value.getIntOr("slot", -1),
                    Identifier.parse(value.getStringOr("item", "minecraft:air"))));
        }
        IStackKey<?> output = tag.contains("output_key")
                ? readKey(tag.getCompoundOrEmpty("output_key"), registries)
                : new ItemStackKey(new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                        Identifier.parse(tag.getStringOr("output", "minecraft:air")))));
        int[] dependencies = tag.getIntArray("dependencies").orElse(new int[0]);
        return new RecipePlan.Step(Identifier.parse(tag.getStringOr("recipe", "minecraft:air")), tag.getStringOr("family", ""),
                output, tag.getLongOr("per", 0L), tag.getLongOr("crafts", 0L), inputs, selections,
                java.util.Arrays.stream(dependencies).boxed().toList());
    }

    private static void writeExternalWait(CompoundTag owner, RecipeOrderJob.ExternalWait externalWait,
                                          HolderLookup.Provider registries)
    {
        CompoundTag wait = new CompoundTag();
        wait.putString("dimension", externalWait.machineDimension().identifier().toString());
        wait.putLong("position", externalWait.machinePosition().asLong());
        wait.putString("output", externalWait.output().toString());
        CompoundTag waitOutputKey = new CompoundTag();
        writeKey(waitOutputKey, externalWait.outputKey(), registries);
        wait.put("output_key", waitOutputKey);
        wait.putBoolean("native_furnace", externalWait.nativeFurnace());
        wait.putBoolean("provisioner", externalWait.provisioner());
        wait.putLong("baseline", externalWait.baseline());
        wait.putLong("network_baseline", externalWait.networkBaseline());
        wait.putLong("network_observed", externalWait.networkObserved());
        wait.putLong("amount", externalWait.amount());
        wait.putLong("collected", externalWait.collected());
        ListTag occupiedMachines = new ListTag();
        for (RecipeOrderJob.MachineLocation machine : externalWait.occupiedMachines())
        {
            CompoundTag encoded = new CompoundTag();
            encoded.putString("dimension", machine.dimension().identifier().toString());
            encoded.putLong("position", machine.position().asLong());
            encoded.putString("input_group", machine.inputGroup());
            occupiedMachines.add(encoded);
        }
        wait.put("occupied_machines", occupiedMachines);
        ListTag networkBaselineStacks = new ListTag();
        for (RecipePlan.ReservedMaterial material : externalWait.networkBaselineStacks())
        {
            CompoundTag encoded = new CompoundTag();
            writeKey(encoded, material.key(), registries);
            encoded.putLong("amount", material.amount());
            networkBaselineStacks.add(encoded);
        }
        wait.put("network_baseline_stacks", networkBaselineStacks);
        ListTag remaining = new ListTag();
        for (RecipePlan.Material input : externalWait.remainingInputs())
        {
            CompoundTag encoded = new CompoundTag();
            writeKey(encoded, input.key(), registries);
            encoded.putLong("amount", input.amount());
            encoded.putInt("ingredient_slot", input.ingredientSlot());
            encoded.putString("input_group", input.inputGroup());
            remaining.add(encoded);
        }
        wait.put("remaining_inputs", remaining);
        owner.put("external_wait", wait);
    }

    private static RecipeOrderJob.ExternalWait readExternalWait(CompoundTag job,
                                                                 HolderLookup.Provider registries)
    {
        if (!job.contains("external_wait")) return null;
        CompoundTag wait = job.getCompoundOrEmpty("external_wait");
        List<RecipePlan.Material> remaining = new ArrayList<>();
        ListTag encoded = wait.getListOrEmpty("remaining_inputs");
        for (int i = 0; i < encoded.size(); i++)
        {
            CompoundTag input = encoded.getCompoundOrEmpty(i);
            remaining.add(new RecipePlan.Material(readKey(input, registries),
                    input.getLongOr("amount", 0L), input.contains("ingredient_slot")
                    ? input.getIntOr("ingredient_slot", -1) : -1,
                    input.getStringOr("input_group", "ingredients")));
        }
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        Identifier.parse(wait.getStringOr("dimension", "minecraft:overworld")));
        net.minecraft.core.BlockPos position = net.minecraft.core.BlockPos.of(wait.getLongOr("position", 0L));
        List<RecipeOrderJob.MachineLocation> occupiedMachines = new ArrayList<>();
        ListTag encodedMachines = wait.getListOrEmpty("occupied_machines");
        for (int i = 0; i < encodedMachines.size(); i++)
        {
            CompoundTag encodedMachine = encodedMachines.getCompoundOrEmpty(i);
            occupiedMachines.add(new RecipeOrderJob.MachineLocation(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            Identifier.parse(encodedMachine.getStringOr("dimension", "minecraft:overworld"))),
                    net.minecraft.core.BlockPos.of(encodedMachine.getLongOr("position", 0L)),
                    encodedMachine.getStringOr("input_group", "")));
        }
        if (occupiedMachines.isEmpty())
            occupiedMachines.add(new RecipeOrderJob.MachineLocation(dimension, position, ""));
        return new RecipeOrderJob.ExternalWait(
                dimension, position,
                wait.contains("output_key")
                        ? readKey(wait.getCompoundOrEmpty("output_key"), registries)
                        : new ItemStackKey(new net.minecraft.world.item.ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                                Identifier.parse(wait.getStringOr("output", "minecraft:air"))))),
                wait.getBooleanOr("native_furnace", false), wait.getBooleanOr("provisioner", false), wait.getLongOr("baseline", 0L),
                wait.getLongOr("network_baseline", 0L), wait.getLongOr("network_observed", 0L), wait.getLongOr("amount", 0L),
                wait.getLongOr("collected", 0L), remaining, readReservedList(
                wait, "network_baseline_stacks", registries), occupiedMachines);
    }

    private static List<RecipePlan.ReservedMaterial> readReserved(CompoundTag job,
                                                                  HolderLookup.Provider registries)
    { return readReservedList(job, "reserved", registries); }

    private static List<RecipePlan.ReservedMaterial> readReservedList(CompoundTag owner, String field,
                                                                      HolderLookup.Provider registries)
    {
        List<RecipePlan.ReservedMaterial> result = new ArrayList<>();
        ListTag encoded = owner.getListOrEmpty(field);
        for (int i = 0; i < encoded.size(); i++)
        {
            CompoundTag value = encoded.getCompoundOrEmpty(i);
            IStackKey<?> key = readKey(value, registries);
            long amount = value.getLongOr("amount", 0L);
            if (key != null && !key.isEmpty() && amount > 0)
                result.add(new RecipePlan.ReservedMaterial(key, amount));
        }
        return List.copyOf(result);
    }

    private static void writeKey(CompoundTag owner, IStackKey<?> key, HolderLookup.Provider registries)
    {
        owner.putString("key_type", key.getTypeId().toString());
        owner.put("key", key.serializeNBT(registries));
        if (key instanceof ItemStackKey item)
            owner.putString("item", net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(item.getSource()).toString());
    }

    private static IStackKey<?> readKey(CompoundTag owner, HolderLookup.Provider registries)
    {
        Identifier type = Identifier.tryParse(owner.getStringOr("key_type", ""));
        if (type != null)
        {
            try { return StackKeyRegistry.getType(type).deserializeNBT(owner.getCompoundOrEmpty("key"), registries); }
            catch (RuntimeException | LinkageError ignored) {}
        }
        Identifier item = Identifier.tryParse(owner.getStringOr("item", ""));
        if (item != null) return new ItemStackKey(new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(item)));
        return ItemStackKey.EMPTY.deserializeNBT(owner.getCompoundOrEmpty("key"), registries);
    }
}
