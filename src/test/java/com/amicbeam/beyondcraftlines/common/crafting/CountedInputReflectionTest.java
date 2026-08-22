package com.amicbeam.beyondcraftlines.common.crafting;

import mekanism.test.PerTickChemicalRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CountedInputReflectionTest
{
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
    void canonicalizesGetterAliasesWithoutRecipeSpecificRegistration()
    {
        assertEquals("activation_item", CountedInputReflection.inputGroup("activationItem"));
        assertEquals("activation_item", CountedInputReflection.inputGroup("getActivationItem"));
    }

    @Test
    void discoversLogicalSectionsFromStructureWithoutRecipeSpecificNames()
    {
        var reagent = new RepresentationProvider(new Object(), 1);
        var pedestal = new RepresentationProvider(new Object(), 1);
        var sections = CountedInputReflection.inputSections(
                new StructuralRecipe(reagent, List.of(pedestal, pedestal)));

        assertEquals(List.of("reagent", "pedestal_items"),
                sections.stream().map(CountedInputReflection.InputSection::inputGroup).toList());
        assertEquals(1, sections.get(0).inputs().size());
        assertEquals(2, sections.get(1).inputs().size());
    }

    @Test
    void excludesMergedIngredientsAndOutputAccessors()
    {
        var value = new RepresentationProvider(new Object(), 1);
        var sections = CountedInputReflection.inputSections(
                new StructuralRecipe(value, List.of(value)));

        assertFalse(sections.stream().anyMatch(section ->
                section.inputGroup().equals("ingredients") || section.inputGroup().equals("result")));
    }

    @Test
    void identityMatchingPreservesRepeatedOccurrences()
    {
        Object reagent = new Object();
        Object repeated = new Object();
        assertArrayEquals(new int[] { 0, 1, 2, -1 },
                CountedInputReflection.matchIdentityOccurrences(
                        List.of(reagent, repeated, repeated),
                        List.of(reagent, repeated, repeated, new Object())));
    }

    @Test
    void inputDiscoveryDoesNotProbeEnergyMetadata()
    {
        var value = new RepresentationProvider(new Object(), 1);
        assertFalse(CountedInputReflection.inputSections(
                        new StructuralRecipe(value, List.of(value))).stream()
                .anyMatch(section -> section.inputGroup().contains("energy")));
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
    void rejectsObjectsWithoutAnIngredientAccessor()
    {
        assertNull(CountedInputReflection.read(new Object()));
    }

    private record RecordStyleInput(Object ingredient, int count) {}
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
    private static final class UnrelatedPerTickRecipe
    {
        public boolean perTickUsage() { return true; }
    }

    private static final class StructuralRecipe
    {
        private final RepresentationProvider reagent;
        private final List<RepresentationProvider> pedestalItems;

        private StructuralRecipe(RepresentationProvider reagent,
                                 List<RepresentationProvider> pedestalItems)
        {
            this.reagent = reagent;
            this.pedestalItems = pedestalItems;
        }

        public RepresentationProvider reagent() { return reagent; }
        public RepresentationProvider getReagent() { return reagent; }
        public List<RepresentationProvider> pedestalItems() { return pedestalItems; }
        public List<RepresentationProvider> getIngredients()
        { return java.util.stream.Stream.concat(java.util.stream.Stream.of(reagent),
                pedestalItems.stream()).toList(); }
        public RepresentationProvider result() { return reagent; }
        public int energy() { return 100; }
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
