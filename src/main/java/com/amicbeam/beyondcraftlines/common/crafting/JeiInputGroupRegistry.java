package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Session cache of JEI category input groups discovered from client recipe layouts. */
public final class JeiInputGroupRegistry
{
    private static volatile Map<String, Set<String>> groupsByType = Map.of();
    private JeiInputGroupRegistry() {}

    public static Set<String> groups(Object type)
    { return type == null ? Set.of() : groupsByType.getOrDefault(type.toString(), Set.of()); }

    public static synchronized void rememberEncoded(Collection<String> encoded)
    {
        Map<String, LinkedHashSet<String>> building = new HashMap<>();
        groupsByType.forEach((type, groups) -> building.put(type, new LinkedHashSet<>(groups)));
        if (encoded != null) encoded.stream().limit(512).forEach(value -> {
            int separator = value.indexOf('|');
            if (separator < 3 || separator == value.length() - 1) return;
            String type = value.substring(0, separator);
            String group = value.substring(separator + 1);
            if (type.length() <= 256 && type.contains(":") && JeiSlotInputGroup.isValid(group))
                building.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(group);
        });
        Map<String, Set<String>> frozen = new HashMap<>();
        building.forEach((type, groups) -> frozen.put(type, Set.copyOf(groups)));
        groupsByType = Map.copyOf(frozen);
    }

    public static List<String> encode(Map<?, Set<String>> groups)
    {
        return groups.entrySet().stream().sorted(java.util.Comparator.comparing(entry -> entry.getKey().toString()))
                .flatMap(entry -> entry.getValue().stream().sorted().map(group -> entry.getKey() + "|" + group))
                .limit(512).toList();
    }

    public static synchronized void clear() { groupsByType = Map.of(); }
}
