package com.amicbeam.beyondcraftlines.client;

import java.util.Locale;

/** Shared category-name and exact-ID search for provisioner and diagnostic inputs. */
public final class RecipeCategorySearch
{
    private RecipeCategorySearch() {}

    public static boolean matches(String query, String id, String title)
    {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || id.toLowerCase(Locale.ROOT).contains(normalized)
                || title.toLowerCase(Locale.ROOT).contains(normalized);
    }
}
