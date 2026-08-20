package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Immutable, item-id-level inventory view used by client proposals and server verification. */
public final class PlanningSnapshotService
{
    private static final Map<RecipeManager, List<RecipeIdentity>> RECIPE_IDENTITIES =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private PlanningSnapshotService() {}

    public static Snapshot capture(int networkId)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        if (network == null) throw new IllegalArgumentException("network not found");
        Map<ResourceLocation, Long> aggregated = new LinkedHashMap<>();
        for (var stored : network.getUnifiedStorage().getStorage())
        {
            if (!(stored.key() instanceof ItemStackKey key) || stored.amount() <= 0) continue;
            aggregated.merge(BuiltInRegistries.ITEM.getKey(key.getSource()), stored.amount(),
                    SaturatingLongMath::add);
        }
        List<Entry> entries = aggregated.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .map(entry -> new Entry(entry.getKey(), entry.getValue())).toList();
        return new Snapshot(entries, fingerprint(entries));
    }

    public static long recipeEpoch(Level level, Set<String> availableFamilies)
    {
        long hash = offset();
        for (RecipeIdentity identity : identities(level))
            if ("crafting".equals(identity.family()) || availableFamilies.contains(identity.family()))
                hash = mix(hash, identity.value());
        for (String family : availableFamilies.stream().sorted().toList()) hash = mix(hash, family);
        return positive(hash);
    }

    public static void clearRecipeEpochCache() { RECIPE_IDENTITIES.clear(); }

    private static List<RecipeIdentity> identities(Level level)
    {
        return RECIPE_IDENTITIES.computeIfAbsent(level.getRecipeManager(), ignored -> {
            List<RecipeIdentity> result = new ArrayList<>();
            for (var holder : RecipePlanningService.visibleRecipes(level))
            {
                String family = RecipePlanningService.family(holder);
                var recipe = holder.value();
                var output = recipe.getResultItem(level.registryAccess());
                StringBuilder identity = new StringBuilder(holder.id().toString()).append('|').append(family)
                        .append('|').append(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()))
                        .append('|').append(BuiltInRegistries.ITEM.getKey(output.getItem())).append(':')
                        .append(output.getCount()).append(':').append(output.getComponentsPatch());
                int slot = 0;
                for (var ingredient : recipe.getIngredients())
                {
                    identity.append('|').append(slot++).append('=');
                    List<String> candidates = new ArrayList<>();
                    for (var stack : ingredient.getItems()) candidates.add(
                            BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getCount()
                                    + ":" + stack.getComponentsPatch());
                    candidates.stream().distinct().sorted().forEach(candidate -> identity.append(candidate).append(','));
                }
                result.add(new RecipeIdentity(family, identity.toString()));
            }
            result.sort(Comparator.comparing(RecipeIdentity::value));
            return List.copyOf(result);
        });
    }

    private static long fingerprint(List<Entry> entries)
    {
        long hash = offset();
        for (Entry entry : entries)
        {
            hash = mix(hash, entry.item().toString());
            long value = entry.amount();
            for (int i = 0; i < Long.BYTES; i++)
            {
                hash ^= value & 0xFF;
                hash *= 0x100000001B3L;
                value >>>= 8;
            }
        }
        return positive(hash);
    }

    private static long offset() { return 0xCBF29CE484222325L; }
    private static long mix(long hash, String value)
    {
        for (int i = 0; i < value.length(); i++)
        {
            char character = value.charAt(i);
            hash ^= character & 0xFF;
            hash *= 0x100000001B3L;
            hash ^= character >>> 8;
            hash *= 0x100000001B3L;
        }
        return hash;
    }
    private static long positive(long hash) { return hash & Long.MAX_VALUE; }

    public record Entry(ResourceLocation item, long amount)
    {
        public Entry
        {
            if (item == null || amount < 1) throw new IllegalArgumentException("invalid planning stock entry");
        }
    }

    public record Snapshot(List<Entry> entries, long revision)
    {
        public Snapshot { entries = List.copyOf(entries); }
        public Map<ResourceLocation, Long> asMap()
        {
            LinkedHashMap<ResourceLocation, Long> result = new LinkedHashMap<>();
            for (Entry entry : entries) result.put(entry.item(), entry.amount());
            return result;
        }
    }

    private record RecipeIdentity(String family, String value) {}
}
