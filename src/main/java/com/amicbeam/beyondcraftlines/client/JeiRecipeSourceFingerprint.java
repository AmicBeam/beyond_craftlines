package com.amicbeam.beyondcraftlines.client;

import java.util.List;

/** Source IDs/counts, never layout-derived virtual descriptor IDs or login generations. */
final class JeiRecipeSourceFingerprint
{
    private JeiRecipeSourceFingerprint() {}
    static String fingerprint(List<String> sources)
    { return PlanningCatalogCacheIdentity.fingerprint(sources.stream().sorted().toList()); }
}
