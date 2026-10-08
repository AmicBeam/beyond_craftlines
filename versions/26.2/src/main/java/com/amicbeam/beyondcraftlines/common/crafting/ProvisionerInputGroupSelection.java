package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Set;

/** Pure selection semantics shared by the provisioner GUI and server-side validation. */
public final class ProvisionerInputGroupSelection
{
    public static final String ALL = "*";
    public static final int EXPLICIT_PRIORITY = 0;
    public static final int WILDCARD_PRIORITY = 1;
    public static final int REJECTED_PRIORITY = 2;
    private ProvisionerInputGroupSelection() {}

    public static Set<String> accepted(Set<String> available, Set<String> selected)
    { return available.size() <= 1 || selected.isEmpty() ? Set.of(ALL) : Set.copyOf(selected); }

    /** Treats legacy persisted empty sets like the GUI's empty-selection wildcard. */
    public static Set<String> normalizeStored(Set<String> accepted)
    { return accepted.isEmpty() ? Set.of(ALL) : Set.copyOf(accepted); }

    /** Restricted endpoints win; endpoints with no subgroup selection remain a wildcard fallback. */
    public static int routingPriority(Set<String> accepted, String requested)
    {
        if (accepted.contains(requested) && !accepted.contains(ALL)) return EXPLICIT_PRIORITY;
        if (accepted.contains(ALL)) return WILDCARD_PRIORITY;
        return REJECTED_PRIORITY;
    }
}
