package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Set;

/** Security-sensitive selection rules for the provisioner's client-side manual fallback. */
public final class ManualRecipeSelectionPolicy
{
    private ManualRecipeSelectionPolicy() {}

    public static <T> boolean isManualMode(boolean boundMachineConfiguration,
                                           Set<T> candidates, Set<T> selected)
    {
        return !boundMachineConfiguration && candidates.isEmpty() && selected.isEmpty();
    }

    public static <T> boolean accepts(boolean boundMachineConfiguration, Set<T> candidates,
                                      Set<T> selected, Set<T> requested)
    {
        if (candidates.containsAll(requested)) return true;
        return isManualMode(boundMachineConfiguration, candidates, selected) && requested.size() == 1;
    }
}
