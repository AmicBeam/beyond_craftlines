package com.amicbeam.beyondcraftlines.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Filter decisions and fixed-slot normalization without loader or registry initialization. */
public final class ResourceFilterRules
{
    public static final int COLUMNS = 9;
    public static final int ROWS = 4;
    public static final int SIZE = COLUMNS * ROWS;
    private ResourceFilterRules() {}

    public static boolean matchesItemMetadata(boolean sameNbt, boolean damageable, boolean sameDamage,
            boolean matchNbt, boolean matchDurability)
    {
        return (!matchNbt || sameNbt) && (!matchDurability || !damageable || sameDamage);
    }

    public static <T> boolean accepts(boolean whitelist, List<T> entries, T key,
            Predicate<T> empty, BiPredicate<T, T> same)
    {
        if (key == null || empty.test(key)) return false;
        boolean matches = entries.stream().anyMatch(entry -> entry != null && !empty.test(entry) && same.test(entry, key));
        return whitelist == matches;
    }

    public static <T> List<T> normalize(List<T> entries, T empty)
    {
        if (entries.size() > SIZE) throw new IllegalArgumentException("Too many resource filters");
        ArrayList<T> slots = new ArrayList<>(entries);
        slots.replaceAll(key -> key == null ? empty : key);
        while (slots.size() < SIZE) slots.add(empty);
        return List.copyOf(slots);
    }
}
