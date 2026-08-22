package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Set;

/** Pure selection semantics shared by the provisioner GUI and server-side validation. */
public final class ProvisionerInputGroupSelection
{
    public static final String ALL = "*";
    private ProvisionerInputGroupSelection() {}

    public static Set<String> accepted(Set<String> available, Set<String> selected)
    { return available.size() <= 1 || selected.isEmpty() ? Set.of(ALL) : Set.copyOf(selected); }
}
