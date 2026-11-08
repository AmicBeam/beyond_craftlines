package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Evidence-based root checks; a preview can never certify a dynamic execution result. */
public final class RootDiagnosticRules
{
    private RootDiagnosticRules() {}

    public static boolean matchesBinding(String category, Set<String> families, Set<String> jeiTypes)
    {
        String family = VanillaProvisionerRecipeTypes.runtimeFamily(category);
        if (family.equals("minecraft:crafting")) family = "crafting";
        return families.contains(family) || jeiTypes.contains(category);
    }

    public static String expectedCategoryStatus(boolean ready, boolean known, boolean enabled, int candidates)
    {
        if (!known) return "expected_category_unknown";
        if (!enabled) return "expected_category_disabled";
        if (!ready) return "pending";
        return candidates == 0 ? "expected_category_empty" : "expected_category_found";
    }

    public static List<String> missingCategories(Set<String> families, Set<String> viewerTypes,
                                                  Set<String> nativeFallbackTypes)
    {
        return families.stream().filter(family -> family.contains(":"))
                .filter(family -> !family.startsWith("minecraft:") && !viewerTypes.contains(family))
                .filter(family -> !nativeFallbackTypes.contains(family)).sorted().limit(16).toList();
    }

    public static String outputStatus(boolean indexReady, boolean selected, int sameResourceOutputs,
                                      boolean exact)
    {
        if (!indexReady) return "pending";
        if (!selected) return "no_recipe";
        if (exact) return "exact";
        if (sameResourceOutputs > 1) return "ambiguous";
        return sameResourceOutputs == 1 ? "components" : "wrong_output";
    }

    public static List<String> differences(Map<String, String> target, Map<String, String> output)
    {
        TreeSet<String> paths = new TreeSet<>(target.keySet());
        paths.addAll(output.keySet());
        List<String> result = new ArrayList<>();
        for (String path : paths)
            if (!Objects.equals(target.get(path), output.get(path)))
            {
                result.add(path + ": target=" + target.getOrDefault(path, "<absent>")
                        + "; output=" + output.getOrDefault(path, "<absent>"));
                if (result.size() == 16) break;
            }
        return List.copyOf(result);
    }
}
