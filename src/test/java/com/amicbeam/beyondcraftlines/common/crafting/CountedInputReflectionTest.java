package com.amicbeam.beyondcraftlines.common.crafting;

import mekanism.test.PerTickChemicalRecipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CountedInputReflectionTest
{
    @BeforeEach
    void installProfiles()
    { RecipeIoProfileTestSupport.install("defaults.json", "mekanism.json"); }

    @Test
    void readsRecordStyleIngredientAndCount()
    {
        Object ingredient = new Object();
        var value = CountedInputReflection.read(new RecordStyleInput(ingredient, 4));
        assertEquals(ingredient, value.ingredient());
        assertEquals(4, value.count());
    }

    @Test
    void readsBeanStyleAndClampsInvalidCount()
    {
        Object ingredient = new Object();
        var value = CountedInputReflection.read(new BeanStyleInput(ingredient, 0));
        assertEquals(ingredient, value.ingredient());
        assertEquals(1, value.count());
    }

    @Test
    void flattensCollectionsAndArraysButKeepsEqualEntries()
    {
        Object repeated = new Object();
        assertEquals(List.of(repeated, repeated),
                CountedInputReflection.flatten(new Object[] { repeated, repeated }));
        assertEquals(List.of(repeated, repeated),
                CountedInputReflection.flatten(List.of(repeated, repeated)));
    }

    @Test
    void recursivelyFlattensNestedContainersAndOptionals()
    {
        Object first = new Object();
        Object second = new Object();
        Object third = new Object();
        assertEquals(List.of(first, second, third), CountedInputReflection.flatten(
                List.of(Optional.of(first), new Object[] { List.of(second), third })));
        assertEquals(List.of(), CountedInputReflection.flatten(Optional.empty()));
    }

    @Test
    void unwrapsCapabilityMapsAndContentRecordsWithoutLeakingMetadata()
    {
        Object item = new Object();
        Object fluid = new Object();
        Map<String, List<CapabilityContent>> contents = Map.of(
                "item", List.of(new CapabilityContent(item, 10_000, 10_000)),
                "fluid", List.of(new CapabilityContent(fluid, 5_000, 10_000)));

        var flattened = CountedInputReflection.flatten(contents);
        assertEquals(2, flattened.size());
        assertTrue(flattened.contains(item));
        assertTrue(flattened.contains(fluid));
    }

    @Test
    void unwrapsCapabilityContentClassesWithoutLeakingMetadata()
    {
        Object item = new Object();
        Object fluid = new Object();
        Map<String, List<ClassCapabilityContent>> contents = Map.of(
                "item", List.of(new ClassCapabilityContent(item, 10_000, 10_000)),
                "fluid", List.of(new ClassCapabilityContent(fluid, 5_000, 10_000)));

        var flattened = CountedInputReflection.flatten(contents);
        assertEquals(2, flattened.size());
        assertTrue(flattened.contains(item));
        assertTrue(flattened.contains(fluid));
    }

    @Test
    void safelyStopsOnSelfReferentialContainers()
    {
        List<Object> cyclic = new java.util.ArrayList<>();
        cyclic.add(cyclic);
        assertEquals(List.of(), CountedInputReflection.flatten(cyclic));
    }

    @Test
    void unwrapsNestedCountedIngredientsAndSaturatesCount()
    {
        Object ingredient = new Object();
        var nested = new RecordStyleInput(new RecordStyleInput(ingredient, 4), 3);
        var value = CountedInputReflection.read(nested);
        assertEquals(ingredient, value.ingredient());
        assertEquals(12, value.count());

        var saturated = CountedInputReflection.read(new LongCountInput(
                new LongCountInput(ingredient, Long.MAX_VALUE), 2));
        assertEquals(Long.MAX_VALUE, saturated.count());
    }

    @Test
    void preservesRepresentationProvidersThatAlsoLookLikeCountedWrappers()
    {
        Object inner = new Object();
        var provider = new RepresentationProvider(inner, 40);

        // The provider's own count belongs to each represented stack. Unwrapping it would
        // discard getRepresentations(), which is how chemical and fluid inputs disappeared.
        assertNull(CountedInputReflection.read(provider));

        var outer = CountedInputReflection.read(new RecordStyleInput(provider, 3));
        assertEquals(provider, outer.ingredient());
        assertEquals(3, outer.count());
    }

    @Test
    void discoversCreateStyleFluidIngredientsAndMatchingStacks()
    {
        Object steam = new Object();
        var provider = new MatchingFluidProvider(steam);
        assertEquals(List.of(steam), CountedInputReflection.representationValues(provider));
        assertNull(CountedInputReflection.read(provider));
        assertTrue(RecipeIoProfileRegistry.inputMembers(null).contains("getFluidIngredients"));
    }

    @Test
    void discoversSizedFluidIngredientStreamsWithoutUnwrappingThem()
    {
        Object steam = new Object();
        var provider = new FluidStreamProvider(steam, 26_000);
        assertEquals(List.of(steam), CountedInputReflection.representationValues(provider));
        assertNull(CountedInputReflection.read(provider));
    }

    @Test
    void discoversGoetyRitualActivationItem()
    {
        assertTrue(RecipeIoProfileRegistry.inputMembers(null).contains("activationItem"));
        assertTrue(RecipeIoProfileRegistry.inputMembers(null).contains("getActivationItem"));
        assertEquals("activation_item", CountedInputReflection.inputGroup("activationItem"));
        assertEquals("activation_item", CountedInputReflection.inputGroup("getActivationItem"));
    }

    @Test
    void discoversGenericCatalystInput()
    {
        assertTrue(RecipeIoProfileRegistry.inputMembers(null).contains("catalyst"));
        assertTrue(RecipeIoProfileRegistry.inputMembers(null).contains("getCatalyst"));
        assertEquals("catalyst", CountedInputReflection.inputGroup("catalyst"));
        assertEquals("catalyst", CountedInputReflection.inputGroup("getCatalyst"));
        assertTrue(RecipeIoProfileRegistry.distinctInputMember(null, "catalyst"));
        assertTrue(RecipeIoProfileRegistry.distinctInputMember(null, "getCatalyst"));
        assertFalse(RecipeResourceResolver.shouldSkipCanonicalInput(true, Set.of("same"), "same"));
        assertTrue(RecipeResourceResolver.shouldSkipCanonicalInput(false, Set.of("same"), "same"));
    }

    @Test
    void discoversMalumSpiritInputs()
    {
        Object modernShard = new Object();
        Object legacyShard = new Object();
        var recipe = new MalumLikeRecipe(List.of(
                new ModernSpiritProvider(modernShard), new LegacySpiritProvider(legacyShard)));

        assertTrue(RecipeIoProfileRegistry.inputMembers(null).contains("spirits"));
        assertTrue(RecipeIoProfileRegistry.inputMembers(null).contains("getSpirits"));
        assertEquals("spirits", CountedInputReflection.inputGroup("spirits"));
        assertEquals(recipe.spirits, RecipeReflection.readPublicMember(recipe, "spirits"));

        var spirits = CountedInputReflection.flatten(
                RecipeReflection.readPublicMember(recipe, "spirits"));
        assertEquals(recipe.spirits, spirits);
        assertEquals(List.of(modernShard),
                CountedInputReflection.representationValues(spirits.get(0)));
        assertEquals(List.of(legacyShard),
                CountedInputReflection.representationValues(spirits.get(1)));
    }

    @Test
    void cachedPublicMemberLookupPreservesMethodBeforeFieldSemantics()
    {
        var recipe = new PublicMethodAndFieldRecipe();
        assertEquals("method", RecipeReflection.readPublicMember(recipe, "input"));
        assertEquals("method", RecipeReflection.readPublicMember(recipe, "input"));
        assertEquals("field fallback", RecipeReflection.readPublicMember(recipe, "fallback"));
        assertNull(RecipeReflection.readPublicMember(recipe, "staticInput"));
        assertNull(RecipeReflection.readPublicMember(recipe, "missing"));
    }

    @Test
    void inputDiscoveryDoesNotProbeEnergyMetadata()
    {
        assertFalse(RecipeIoProfileRegistry.inputMembers(null).stream()
                .anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("energy")));
    }

    @Test
    void scalesOnlyMekanismPerTickChemicalInputs()
    {
        var perTick = new PerTickChemicalRecipe(true);
        assertEquals(200, CountedInputReflection.recipeInputMultiplier(perTick, "getChemicalInput"));
        assertEquals(1, CountedInputReflection.recipeInputMultiplier(perTick, "getItemInput"));
        assertEquals(1, CountedInputReflection.recipeInputMultiplier(
                new PerTickChemicalRecipe(false), "getChemicalInput"));
        assertEquals(1, CountedInputReflection.recipeInputMultiplier(
                new UnrelatedPerTickRecipe(), "getChemicalInput"));
        assertEquals(200, CountedInputReflection.recipeInputMultiplier(
                new mekanism.api.recipes.ItemStackGasToItemStackRecipe(), "getChemicalInput"));
    }

    @Test
    void batchLimitCountSemanticsNormalizesWrapperAndRepresentationCounts()
    {
        assertEquals(24, RecipeResourceResolver.interpretedInputAmount(3, 8,
                RecipeIoProfileRegistry.InputCountSemantics.REQUIRED));
        assertEquals(1, RecipeResourceResolver.interpretedInputAmount(3, 8,
                RecipeIoProfileRegistry.InputCountSemantics.BATCH_LIMIT));
        assertEquals(1, RecipeResourceResolver.interpretedInputAmount(8, 1,
                RecipeIoProfileRegistry.InputCountSemantics.BATCH_LIMIT));
    }

    @Test
    void rejectsObjectsWithoutAnIngredientAccessor()
    {
        assertNull(CountedInputReflection.read(new Object()));
    }

    private record RecordStyleInput(Object ingredient, int count) {}
    private record CapabilityContent(Object content, int chance, int maxChance) {}
    public static final class ClassCapabilityContent
    {
        public final Object content;
        public final int chance;
        public final int maxChance;

        public ClassCapabilityContent(Object content, int chance, int maxChance)
        {
            this.content = content;
            this.chance = chance;
            this.maxChance = maxChance;
        }
    }
    private record LongCountInput(Object ingredient, long count) {}
    private record RepresentationProvider(Object ingredient, long count)
    {
        public List<Object> getRepresentations() { return List.of(ingredient); }
    }
    private record MatchingFluidProvider(Object ingredient)
    {
        public List<Object> getMatchingFluidStacks() { return List.of(ingredient); }
    }
    private record FluidStreamProvider(Object ingredient, long amount)
    {
        public java.util.stream.Stream<Object> getFluids()
        { return java.util.stream.Stream.of(ingredient); }
    }
    public record ModernSpiritProvider(Object shard)
    {
        public java.util.stream.Stream<Object> getItems()
        { return java.util.stream.Stream.of(shard); }
    }
    public static final class LegacySpiritProvider
    {
        private final Object shard;
        private LegacySpiritProvider(Object shard) { this.shard = shard; }
        public List<Object> getStacks() { return List.of(shard); }
    }
    private static final class MalumLikeRecipe
    {
        public final List<Object> spirits;
        private MalumLikeRecipe(List<Object> spirits) { this.spirits = spirits; }
    }
    private static final class UnrelatedPerTickRecipe
    {
        public boolean perTickUsage() { return true; }
    }

    private static final class PublicMethodAndFieldRecipe
    {
        public static final String staticInput = "static";
        public final String input = "field";
        public final String fallback = "field fallback";
        public String input() { return "method"; }
        public String fallback() { throw new IllegalStateException("expected test failure"); }
    }

    private static final class BeanStyleInput
    {
        private final Object ingredient;
        private final int count;
        private BeanStyleInput(Object ingredient, int count)
        {
            this.ingredient = ingredient;
            this.count = count;
        }
        public Object getIngredient() { return ingredient; }
        public int getCount() { return count; }
    }
}
