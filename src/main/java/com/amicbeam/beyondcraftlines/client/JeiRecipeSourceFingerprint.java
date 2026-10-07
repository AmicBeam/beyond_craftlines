package com.amicbeam.beyondcraftlines.client;

import java.util.List;

/** Source IDs/counts, never layout-derived virtual descriptor IDs or login generations. */
public final class JeiRecipeSourceFingerprint
{
    private JeiRecipeSourceFingerprint() {}
    public static String stableClassName(Class<?> type)
    {
        String name = type.getName();
        String interfaces = java.util.Arrays.stream(type.getInterfaces()).map(Class::getName).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        if (java.lang.reflect.Proxy.isProxyClass(type)) return "proxy:" + interfaces;
        int lambda = name.indexOf("$$Lambda");
        return lambda < 0 ? name : name.substring(0, lambda) + "$$Lambda:" + interfaces;
    }
    static String fingerprint(List<String> sources)
    { return PlanningCatalogCacheIdentity.fingerprint(sources.stream().sorted().toList()); }
}
