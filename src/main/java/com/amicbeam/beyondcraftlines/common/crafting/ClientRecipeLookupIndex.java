package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Session lookup derived from the persisted planning catalog without touching recipe objects in UI paths. */
public final class ClientRecipeLookupIndex
{
    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private ClientRecipeLookupIndex() {}

    public static Builder begin(ClientRecipePlanner.Catalog catalog)
    { return new Builder(catalog.recipes()); }

    public static void clear() { snapshot = Snapshot.EMPTY; }
    public static boolean ready() { return snapshot.ready(); }
    public static List<String> recipeIdsForOutput(IStackKey<?> output)
    { return recipeIdsForOutput(RecipeResourceResolver.resolutionKey(output),
            RecipeResourceResolver.sortKey(output), snapshot.byOutput(), snapshot.byCompatibleOutput()); }
    public static List<String> recipeIdsForItem(String item)
    { return snapshot.byItem().getOrDefault(item, List.of()); }
    public static String itemForOutputToken(String token) { return snapshot.itemsByOutput().get(token); }
    public static boolean recipeProduces(String recipe, String token)
    { return snapshot.outputsByRecipe().getOrDefault(recipe, Set.of()).contains(token); }
    public static List<String> recipeIds() { return snapshot.recipeIds(); }

    public static final class Builder
    {
        private final List<ClientRecipePlanner.Recipe> recipes;
        private final Map<String, List<String>> byOutput = new LinkedHashMap<>();
        private final Map<String, List<String>> byCompatibleOutput = new LinkedHashMap<>();
        private final Map<String, List<String>> byItem = new LinkedHashMap<>();
        private final Map<String, String> itemsByOutput = new LinkedHashMap<>();
        private final Map<String, Set<String>> outputsByRecipe = new LinkedHashMap<>();
        private final LinkedHashSet<String> recipeIds = new LinkedHashSet<>();
        private int next;
        private boolean complete;

        private Builder(List<ClientRecipePlanner.Recipe> recipes) { this.recipes = recipes; }

        public void advance(long timeBudgetNanos)
        {
            if (complete || timeBudgetNanos < 1) return;
            long started = System.nanoTime();
            int processed = 0;
            while (next < recipes.size() && (processed < 1 || System.nanoTime() - started < timeBudgetNanos))
            {
                ClientRecipePlanner.Recipe recipe = recipes.get(next++);
                String id = recipe.id().toString();
                String token = RecipeResourceResolver.resolutionKey(recipe.output());
                String coarseToken = RecipeResourceResolver.sortKey(recipe.output());
                recipeIds.add(id);
                byOutput.computeIfAbsent(token, ignored -> new ArrayList<>()).add(id);
                if (recipe.outputMatch() == RecipeIoProfileRegistry.OutputMatchSemantics.SAME_RESOURCE)
                    byCompatibleOutput.computeIfAbsent(coarseToken, ignored -> new ArrayList<>()).add(id);
                outputsByRecipe.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(token);
                outputsByRecipe.get(id).add(coarseToken);
                if (recipe.output() instanceof ItemStackKey itemKey)
                {
                    String item = BuiltInRegistries.ITEM.getKey(itemKey.getSource()).toString();
                    itemsByOutput.putIfAbsent(token, item);
                    itemsByOutput.putIfAbsent(coarseToken, item);
                    byItem.computeIfAbsent(item, ignored -> new ArrayList<>()).add(id);
                }
                processed++;
            }
            if (next < recipes.size()) return;
            snapshot = new Snapshot(freezeLists(byOutput), freezeLists(byCompatibleOutput),
                    freezeLists(byItem), Map.copyOf(itemsByOutput), freezeSets(outputsByRecipe),
                    List.copyOf(recipeIds), true);
            complete = true;
        }

        public boolean complete() { return complete; }

        private static Map<String, List<String>> freezeLists(Map<String, List<String>> source)
        {
            LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(key, List.copyOf(value)));
            return Map.copyOf(result);
        }

        private static Map<String, Set<String>> freezeSets(Map<String, Set<String>> source)
        {
            LinkedHashMap<String, Set<String>> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(key, Set.copyOf(value)));
            return Map.copyOf(result);
        }
    }

    static List<String> recipeIdsForOutput(String resolutionKey, String coarseKey,
                                           Map<String, List<String>> exact,
                                           Map<String, List<String>> compatible)
    {
        List<String> result = exact.getOrDefault(resolutionKey, List.of());
        return result.isEmpty() ? compatible.getOrDefault(coarseKey, List.of()) : result;
    }

    private record Snapshot(Map<String, List<String>> byOutput,
                            Map<String, List<String>> byCompatibleOutput,
                            Map<String, List<String>> byItem,
                            Map<String, String> itemsByOutput, Map<String, Set<String>> outputsByRecipe,
                            List<String> recipeIds, boolean ready)
    {
        private static final Snapshot EMPTY = new Snapshot(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), false);
    }
}
