package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath;
import com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

import java.util.ArrayList;
import java.util.List;

/** A physical, same-machine batch. Retained products never become network reservations. */
public record MachineRecipeBundle(int producerStep, long producerCrafts, long consumerCrafts,
                                  List<KeyAmount> retainedOutputs)
{
    public MachineRecipeBundle
    {
        if (producerStep < 0 || producerCrafts < 1 || consumerCrafts < 1)
            throw new IllegalArgumentException("invalid machine recipe bundle");
        retainedOutputs = List.copyOf(retainedOutputs);
        if (retainedOutputs.isEmpty() || retainedOutputs.stream().anyMatch(KeyAmount::isEmpty))
            throw new IllegalArgumentException("bundle has no intermediate products");
    }

    static <T> T singleMachine(List<List<T>> routes)
    {
        T selected = null;
        for (List<T> route : routes)
        {
            if (route.size() != 1) return null;
            T machine = route.get(0);
            if (selected != null && !selected.equals(machine)) return null;
            selected = machine;
        }
        return selected;
    }

    static MachineRecipeBundle plan(int producerIndex, RecipePlan.Step producer,
                                    RecipePlan.Step consumer, boolean blocking)
    {
        if (producer.selfIncrementSeed() > 0 || consumer.selfIncrementSeed() > 0
                || StackKeyMatch.exact(producer.outputKey(), consumer.outputKey())) return null;
        long consumerCrafts = blocking ? 1 : consumer.crafts();
        List<RecipePlan.Material> consumerInputs = inputs(consumer, consumerCrafts);
        long demand = consumerInputs.stream().filter(input ->
                        StackKeyMatch.exact(input.key(), producer.outputKey()))
                .mapToLong(RecipePlan.Material::amount).reduce(0, SaturatingLongMath::add);
        if (demand == 0) return null;
        long producerCrafts = Math.min(producer.crafts(), ceilDivide(demand, producer.outputPerCraft()));
        List<KeyAmount> outputs = new ArrayList<>();
        outputs.add(new KeyAmount(producer.outputKey(), producer.outputPerCraft()));
        outputs.addAll(producer.byproducts());
        List<KeyAmount> retained = new ArrayList<>();
        for (KeyAmount output : outputs)
        {
            // Identical final and intermediate resources cannot be distinguished by observation.
            if (StackKeyMatch.exact(output.key(), consumer.outputKey())) return null;
            long needed = consumerInputs.stream().filter(input -> StackKeyMatch.exact(input.key(), output.key()))
                    .mapToLong(RecipePlan.Material::amount).reduce(0, SaturatingLongMath::add);
            long amount = Math.min(needed, SaturatingLongMath.multiply(output.amount(), producerCrafts));
            if (amount > 0) merge(retained, output.key(), amount);
        }
        return new MachineRecipeBundle(producerIndex, producerCrafts, consumerCrafts, retained);
    }

    List<RecipePlan.Material> externalInputs(RecipePlan.Step producer, RecipePlan.Step consumer)
    {
        List<RecipePlan.Material> result = new ArrayList<>(inputs(producer, producerCrafts));
        List<Long> unused = retainedOutputs.stream().map(KeyAmount::amount)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (RecipePlan.Material input : inputs(consumer, consumerCrafts))
        {
            long left = input.amount();
            for (int i = 0; i < retainedOutputs.size() && left > 0; i++)
                if (StackKeyMatch.exact(input.key(), retainedOutputs.get(i).key()))
                {
                    long used = Math.min(left, unused.get(i));
                    left -= used;
                    unused.set(i, unused.get(i) - used);
                }
            if (left > 0) result.add(new RecipePlan.Material(input.key(), left, -1, input.inputGroup()));
        }
        return List.copyOf(result);
    }

    static List<RecipePlan.Material> inputs(RecipePlan.Step step, long crafts)
    {
        return step.inputs().stream().map(input -> new RecipePlan.Material(input.key(),
                portion(input.amount(), crafts, step.crafts()), -1, input.inputGroup())).toList();
    }

    static long portion(long amount, long crafts, long totalCrafts)
    {
        if (crafts == totalCrafts) return amount;
        // Avoid overflowing amount * crafts when the final fraction fits in a long.
        return java.math.BigInteger.valueOf(amount).multiply(java.math.BigInteger.valueOf(crafts))
                .add(java.math.BigInteger.valueOf(totalCrafts - 1))
                .divide(java.math.BigInteger.valueOf(totalCrafts)).longValueExact();
    }

    static long ceilDivide(long amount, long divisor)
    { return amount / divisor + (amount % divisor == 0 ? 0 : 1); }

    static void merge(List<KeyAmount> values, IStackKey<?> key, long amount)
    {
        for (int i = 0; i < values.size(); i++)
            if (StackKeyMatch.exact(values.get(i).key(), key))
            {
                values.set(i, new KeyAmount(key, SaturatingLongMath.add(values.get(i).amount(), amount)));
                return;
            }
        values.add(new KeyAmount(key, amount));
    }
}
