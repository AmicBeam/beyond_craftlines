package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public final class RecipeOrderSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_recipe_orders";
    private final Map<UUID, RecipeOrderJob> jobs = new LinkedHashMap<>();

    public static RecipeOrderSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(RecipeOrderSavedData::new, RecipeOrderSavedData::load), NAME);
    }

    public static RecipeOrderSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        RecipeOrderSavedData data = new RecipeOrderSavedData();
        ListTag jobs = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size(); i++)
        {
            try
            {
                CompoundTag value = jobs.getCompound(i);
                List<RecipePlan.Step> steps = new ArrayList<>();
                ListTag encodedSteps = value.getList("steps", Tag.TAG_COMPOUND);
                for (int j = 0; j < encodedSteps.size(); j++)
                    steps.add(readStep(encodedSteps.getCompound(j), registries));
                RecipeOrderJob job = new RecipeOrderJob(value.getUUID("id"), value.getUUID("owner"),
                        value.getInt("network"), ResourceLocation.parse(value.getString("target")),
                        value.getLong("requested"), steps, value.getInt("next"),
                        value.getBoolean("blocking_mode"),
                        RecipeOrderJob.Status.valueOf(value.getString("status")), value.getString("message"),
                        value.getLong("created"), value.getLong("finished"), value.getLong("next_crafting_tick"),
                        readExternalWait(value, registries),
                        readReserved(value, registries));
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

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (RecipeOrderJob job : jobs.values())
        {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", job.id()); value.putUUID("owner", job.owner());
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
            if (job.externalWait() != null)
            {
                CompoundTag wait = new CompoundTag();
                wait.putString("dimension", job.externalWait().machineDimension().location().toString());
                wait.putLong("position", job.externalWait().machinePosition().asLong());
                wait.putString("output", job.externalWait().output().toString());
                CompoundTag waitOutputKey = new CompoundTag();
                writeKey(waitOutputKey, job.externalWait().outputKey(), registries);
                wait.put("output_key", waitOutputKey);
                wait.putBoolean("native_furnace", job.externalWait().nativeFurnace());
                wait.putBoolean("provisioner", job.externalWait().provisioner());
                wait.putLong("baseline", job.externalWait().baseline());
                wait.putLong("network_baseline", job.externalWait().networkBaseline());
                wait.putLong("network_observed", job.externalWait().networkObserved());
                wait.putLong("amount", job.externalWait().amount());
                wait.putLong("collected", job.externalWait().collected());
                ListTag networkBaselineStacks = new ListTag();
                for (RecipePlan.ReservedMaterial material : job.externalWait().networkBaselineStacks())
                {
                    CompoundTag encoded = new CompoundTag();
                    writeKey(encoded, material.key(), registries);
                    encoded.putLong("amount", material.amount());
                    networkBaselineStacks.add(encoded);
                }
                wait.put("network_baseline_stacks", networkBaselineStacks);
                ListTag remaining = new ListTag();
                for (RecipePlan.Material input : job.externalWait().remainingInputs())
                {
                    CompoundTag encoded = new CompoundTag();
                    writeKey(encoded, input.key(), registries);
                    encoded.putLong("amount", input.amount());
                    encoded.putInt("ingredient_slot", input.ingredientSlot());
                    remaining.add(encoded);
                }
                wait.put("remaining_inputs", remaining);
                value.put("external_wait", wait);
            }
            ListTag steps = new ListTag();
            job.steps().forEach(step -> steps.add(writeStep(step, registries)));
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
        return tag;
    }

    private static RecipePlan.Step readStep(CompoundTag tag, HolderLookup.Provider registries)
    {
        List<RecipePlan.Material> inputs = new ArrayList<>();
        ListTag encoded = tag.getList("inputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < encoded.size(); i++)
        {
            CompoundTag value = encoded.getCompound(i);
            inputs.add(new RecipePlan.Material(readKey(value, registries),
                    value.getLong("amount"), value.contains("ingredient_slot", Tag.TAG_INT)
                    ? value.getInt("ingredient_slot") : -1));
        }
        List<RecipePlan.IngredientSelection> selections = new ArrayList<>();
        ListTag encodedSelections = tag.getList("ingredient_selections", Tag.TAG_COMPOUND);
        for (int i = 0; i < encodedSelections.size(); i++)
        {
            CompoundTag value = encodedSelections.getCompound(i);
            selections.add(new RecipePlan.IngredientSelection(value.getInt("slot"),
                    ResourceLocation.parse(value.getString("item"))));
        }
        IStackKey<?> output = tag.contains("output_key", Tag.TAG_COMPOUND)
                ? readKey(tag.getCompound("output_key"), registries)
                : new ItemStackKey(new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.parse(tag.getString("output")))));
        return new RecipePlan.Step(ResourceLocation.parse(tag.getString("recipe")), tag.getString("family"),
                output, tag.getLong("per"), tag.getLong("crafts"), inputs, selections);
    }

    private static RecipeOrderJob.ExternalWait readExternalWait(CompoundTag job,
                                                                 HolderLookup.Provider registries)
    {
        if (!job.contains("external_wait", Tag.TAG_COMPOUND)) return null;
        CompoundTag wait = job.getCompound("external_wait");
        List<RecipePlan.Material> remaining = new ArrayList<>();
        ListTag encoded = wait.getList("remaining_inputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < encoded.size(); i++)
        {
            CompoundTag input = encoded.getCompound(i);
            remaining.add(new RecipePlan.Material(readKey(input, registries),
                    input.getLong("amount"), input.contains("ingredient_slot", Tag.TAG_INT)
                    ? input.getInt("ingredient_slot") : -1));
        }
        return new RecipeOrderJob.ExternalWait(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(wait.getString("dimension"))),
                net.minecraft.core.BlockPos.of(wait.getLong("position")),
                wait.contains("output_key", Tag.TAG_COMPOUND)
                        ? readKey(wait.getCompound("output_key"), registries)
                        : new ItemStackKey(new net.minecraft.world.item.ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                ResourceLocation.parse(wait.getString("output"))))),
                wait.getBoolean("native_furnace"), wait.getBoolean("provisioner"), wait.getLong("baseline"),
                wait.getLong("network_baseline"), wait.getLong("network_observed"), wait.getLong("amount"),
                wait.getLong("collected"), remaining, readReservedList(
                wait, "network_baseline_stacks", registries));
    }

    private static List<RecipePlan.ReservedMaterial> readReserved(CompoundTag job,
                                                                  HolderLookup.Provider registries)
    { return readReservedList(job, "reserved", registries); }

    private static List<RecipePlan.ReservedMaterial> readReservedList(CompoundTag owner, String field,
                                                                      HolderLookup.Provider registries)
    {
        List<RecipePlan.ReservedMaterial> result = new ArrayList<>();
        ListTag encoded = owner.getList(field, Tag.TAG_COMPOUND);
        for (int i = 0; i < encoded.size(); i++)
        {
            CompoundTag value = encoded.getCompound(i);
            IStackKey<?> key = readKey(value, registries);
            long amount = value.getLong("amount");
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
        ResourceLocation type = ResourceLocation.tryParse(owner.getString("key_type"));
        if (type != null)
        {
            try { return StackKeyRegistry.getType(type).deserializeNBT(owner.getCompound("key"), registries); }
            catch (RuntimeException | LinkageError ignored) {}
        }
        ResourceLocation item = ResourceLocation.tryParse(owner.getString("item"));
        if (item != null) return new ItemStackKey(new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item)));
        return ItemStackKey.EMPTY.deserializeNBT(owner.getCompound("key"), registries);
    }
}
