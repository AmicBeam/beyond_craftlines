package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Immutable component-aware inventory view used by client proposals and authoritative verification. */
public final class PlanningSnapshotService
{
    private static final Map<Object, List<RecipeIdentity>> RECIPE_IDENTITIES =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UnifiedStorage, InventoryCache> INVENTORY_CACHES =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private PlanningSnapshotService() {}

    public static Snapshot capture(int networkId)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        if (network == null) throw new IllegalArgumentException("network not found");
        UnifiedStorage storage = network.getUnifiedStorage();
        synchronized (INVENTORY_CACHES)
        {
            InventoryCache cache = INVENTORY_CACHES.get(storage);
            if (cache == null)
            {
                cache = new InventoryCache(storage);
                INVENTORY_CACHES.put(storage, cache);
            }
            return cache.snapshot(storage);
        }
    }

    public static long recipeEpoch(Level level, Set<String> availableFamilies)
    {
        if (level instanceof ServerLevel serverLevel)
            return com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu
                    .serverRecipeEpoch(serverLevel, availableFamilies);
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
        Object catalog = level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess() : RecipeCatalog.class;
        return RECIPE_IDENTITIES.computeIfAbsent(catalog, ignored -> {
            List<RecipeIdentity> result = new ArrayList<>();
            for (var holder : RecipePlanningService.visibleRecipes(level))
            {
                String family = RecipePlanningService.family(holder);
                var recipe = holder.value();
                var output = RecipeOutputResolver.primary(recipe, level);
                if (output == null) continue;
                StringBuilder identity = new StringBuilder(holder.id().identifier().toString()).append('|').append(family)
                        .append('|').append(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()))
                        .append('|').append(RecipeResourceResolver.sortKey(output.key())).append(':')
                        .append(output.amount());
                int slot = 0;
                for (var ingredient : RecipeResourceResolver.ingredients(recipe))
                {
                    identity.append('|').append(slot++).append('=');
                    List<String> candidates = new ArrayList<>();
                    for (var value : ingredient.candidates()) candidates.add(
                            RecipeResourceResolver.sortKey(value.key()) + ":" + value.amount());
                    candidates.stream().distinct().sorted().forEach(candidate -> identity.append(candidate).append(','));
                }
                result.add(new RecipeIdentity(family, identity.toString()));
            }
            result.sort(Comparator.comparing(RecipeIdentity::value));
            return List.copyOf(result);
        });
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

    public record Entry(Identifier item, long amount)
    {
        public Entry
        {
            if (item == null || amount < 1) throw new IllegalArgumentException("invalid planning stock entry");
        }
    }

    public record ComponentEntry(IStackKey<?> key, long amount)
    {
        public ComponentEntry
        {
            if (key == null || key.isEmpty() || amount < 1)
                throw new IllegalArgumentException("invalid component stock entry");
        }
        public Identifier item()
        { return key instanceof ItemStackKey item ? BuiltInRegistries.ITEM.getKey(item.getSource()) : null; }
    }

    public record Snapshot(List<ComponentEntry> componentEntries, long revision)
    {
        public Snapshot { componentEntries = List.copyOf(componentEntries); }
        public List<Entry> entries()
        {
            LinkedHashMap<Identifier, Long> aggregated = new LinkedHashMap<>();
            for (ComponentEntry entry : componentEntries)
                if (entry.item() != null) aggregated.merge(entry.item(), entry.amount(), SaturatingLongMath::add);
            return aggregated.entrySet().stream()
                    .map(entry -> new Entry(entry.getKey(), entry.getValue())).toList();
        }
        public Map<Identifier, Long> asMap()
        {
            LinkedHashMap<Identifier, Long> result = new LinkedHashMap<>();
            for (Entry entry : entries()) result.put(entry.item(), entry.amount());
            return result;
        }
    }

    private record RecipeIdentity(String family, String value) {}

    /** Main-thread cache maintained from Beyond Dimensions' exact-key storage deltas. */
    private static final class InventoryCache
    {
        private final IncrementalStock<IStackKey<?>> stock = new IncrementalStock<>();
        private boolean rebuildRequired;
        private Snapshot materialized;

        private InventoryCache(UnifiedStorage storage)
        {
            rebuild(storage);
            storage.subscribeDeltaWeak(this, InventoryCache::onDelta);
            storage.subscribeAnyWeak(this, cache -> cache.rebuildRequired = true);
        }

        private void onDelta(IStackKey<?> changed, long amount, boolean inserted)
        {
            if (changed == null || changed.isEmpty() || amount <= 0) return;
            stock.apply(changed, amount, inserted);
            materialized = null;
        }

        private Snapshot snapshot(UnifiedStorage storage)
        {
            if (rebuildRequired) rebuild(storage);
            if (materialized == null)
            {
                List<ComponentEntry> entries = stock.snapshot().entrySet().stream()
                        .map(entry -> new ComponentEntry(entry.getKey(), entry.getValue())).toList();
                materialized = new Snapshot(entries, stock.revision());
            }
            return materialized;
        }

        private void rebuild(UnifiedStorage storage)
        {
            LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
            for (var stored : storage.getStorage())
                if (stored.key() != null && !stored.key().isEmpty() && stored.amount() > 0)
                    exact.merge(stored.key(), stored.amount(), SaturatingLongMath::add);
            stock.replace(exact);
            rebuildRequired = false;
            materialized = null;
        }
    }
}
