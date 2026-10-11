package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

import java.util.ArrayList;
import java.util.List;

/** Recover actual outputs while preserving explicitly declared input-tank intermediates. */
final class MachineBundleResources
{
    interface Access
    {
        long extractable(IStackKey<?> key);
        List<KeyAmount> extract(IStackKey<?> key, long amount);
    }

    private MachineBundleResources() {}

    static List<KeyAmount> collect(List<KeyAmount> outputs, MachineRecipeBundle bundle, Access machine)
    {
        List<KeyAmount> collected = new ArrayList<>();
        for (KeyAmount output : outputs)
        {
            long retained = bundle == null ? 0 : bundle.retainedOutputs().stream()
                    .filter(value -> StackKeyMatch.exact(value.key(), output.key()))
                    .mapToLong(KeyAmount::amount).reduce(0, com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath::add);
            long amount = Math.max(0, machine.extractable(output.key()) - retained);
            if (amount > 0) for (KeyAmount taken : machine.extract(output.key(), amount))
                MachineRecipeBundle.merge(collected, taken.key(), taken.amount());
        }
        return List.copyOf(collected);
    }
}
