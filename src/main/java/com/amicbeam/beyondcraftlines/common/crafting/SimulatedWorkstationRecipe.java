package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.amicbeam.beyondcraftlines.common.localization.OrderStatusMessage.encode;

/** Executes one vanilla smithing or stonecutting operation directly against network storage. */
public final class SimulatedWorkstationRecipe
{
    private SimulatedWorkstationRecipe() {}

    public static Attempt craftOne(ServerLevel level, UnifiedStorage storage, RecipePlan.Step step,
                                   List<RecipePlan.ReservedMaterial> orderReserved, boolean escrowOutput)
    {
        var holder = level.getRecipeManager().byKey(step.recipe()).orElse(null);
        if (holder == null || !VanillaProvisionerRecipeTypes.isProxyFamily(step.family()))
            return Attempt.failed(encode("crafting_recipe_unavailable", step.recipe()));

        Map<Integer, RecipePlan.Material> materials = new LinkedHashMap<>();
        for (RecipePlan.Material material : step.inputs())
            materials.putIfAbsent(material.ingredientSlot(), material);
        List<ItemStack> input = new ArrayList<>();
        LinkedHashMap<IStackKey<?>, Long> consumption = new LinkedHashMap<>();
        for (int slot = 0; slot < RecipeIngredientResolver.ingredients(holder.value()).size(); slot++)
        {
            RecipePlan.Material material = materials.get(slot);
            if (material == null || !(material.key() instanceof ItemStackKey item))
                return Attempt.failed(encode("crafting_waiting_inputs"));
            input.add(item.getReadOnlyStack().copyWithCount(1));
            consumption.merge(item, 1L, Long::sum);
        }

        Map<IStackKey<?>, Long> reserved = reservedAmounts(orderReserved);
        for (var entry : consumption.entrySet())
        {
            long available = SaturatingLongMath.add(
                    storage.getStackByKey(entry.getKey()).amount(), reserved.getOrDefault(entry.getKey(), 0L));
            if (available < entry.getValue()) return Attempt.failed(encode("crafting_waiting_inputs"));
        }

        ItemStack output;
        try { output = VanillaWorkstationRecipeAssembler.assemble(holder, step.family(), input, level); }
        catch (LinkageError | RuntimeException exception)
        { return Attempt.failed(encode("crafting_simulation_failed")); }
        if (output.isEmpty() || !BuiltInRegistries.ITEM.getKey(output.getItem()).equals(step.output()))
            return Attempt.failed(encode("crafting_unexpected_output"));
        ItemStackKey outputKey = new ItemStackKey(output.copyWithCount(1));
        if (!escrowOutput && !storage.insert(outputKey, output.getCount(), true).isEmpty())
            return Attempt.failed(encode("crafting_network_full"));

        List<KeyAmount> extracted = new ArrayList<>();
        LinkedHashMap<IStackKey<?>, Long> consumedReserved = new LinkedHashMap<>();
        for (var entry : consumption.entrySet())
        {
            long fromReserved = Math.min(reserved.getOrDefault(entry.getKey(), 0L), entry.getValue());
            if (fromReserved > 0) consumedReserved.put(entry.getKey(), fromReserved);
            long networkNeeded = entry.getValue() - fromReserved;
            KeyAmount taken = storage.extract(entry.getKey(), networkNeeded, false, false);
            if (taken.amount() != networkNeeded)
            {
                if (!taken.isEmpty()) extracted.add(taken);
                rollback(storage, extracted);
                return Attempt.failed(encode("crafting_inputs_changed"));
            }
            if (!taken.isEmpty()) extracted.add(taken);
        }

        if (!escrowOutput)
        {
            KeyAmount remainder = storage.insert(outputKey, output.getCount(), false);
            if (!remainder.isEmpty())
            {
                rollback(storage, extracted);
                return Attempt.failed(encode("crafting_rolled_back"));
            }
        }
        return new Attempt(true, "", 1,
                consumedReserved.entrySet().stream().map(entry ->
                        new RecipePlan.ReservedMaterial(entry.getKey(), entry.getValue())).toList(),
                escrowOutput ? List.of(new RecipePlan.ReservedMaterial(outputKey, output.getCount())) : List.of());
    }

    private static Map<IStackKey<?>, Long> reservedAmounts(List<RecipePlan.ReservedMaterial> values)
    {
        LinkedHashMap<IStackKey<?>, Long> result = new LinkedHashMap<>();
        for (RecipePlan.ReservedMaterial value : values)
            result.merge(value.key(), value.amount(), SaturatingLongMath::add);
        return result;
    }

    private static void rollback(UnifiedStorage storage, List<KeyAmount> extracted)
    { extracted.forEach(value -> storage.insert(value.key(), value.amount(), false)); }

    public record Attempt(boolean success, String reason, long crafts,
                          List<RecipePlan.ReservedMaterial> consumedReserved,
                          List<RecipePlan.ReservedMaterial> producedReserved)
    {
        public Attempt
        {
            consumedReserved = List.copyOf(consumedReserved);
            producedReserved = List.copyOf(producedReserved);
        }

        static Attempt failed(String reason)
        { return new Attempt(false, reason, 0, List.of(), List.of()); }
    }
}
