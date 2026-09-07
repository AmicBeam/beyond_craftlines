package com.amicbeam.beyondcraftlines.client.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Preserves JEI's explicit focus links, which the public slot view does not expose. */
public final class JeiLayoutRelations
{
    private static final Map<Object, List<List<Integer>>> LINKS = Collections.synchronizedMap(new WeakHashMap<>());
    private JeiLayoutRelations() {}

    public static void record(Object layout, List<?> visible, List<? extends List<?>> groups)
    {
        List<List<Integer>> links = new ArrayList<>();
        for (List<?> group : groups)
        {
            List<Integer> indices = new ArrayList<>();
            for (Object slot : group)
            {
                int index = visible.indexOf(slot);
                if (index >= 0) indices.add(index);
            }
            if (indices.size() > 1) links.add(List.copyOf(indices));
        }
        LINKS.put(layout, List.copyOf(links));
    }

    public static List<List<Integer>> links(Object layout)
    {
        List<List<Integer>> links = LINKS.get(layout);
        if (links == null) throw new IllegalArgumentException("JEI layout relation bridge is unavailable");
        return links;
    }
}
