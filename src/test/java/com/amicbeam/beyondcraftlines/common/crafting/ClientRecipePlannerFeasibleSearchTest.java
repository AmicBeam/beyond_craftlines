package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientRecipePlannerFeasibleSearchTest
{
    @Test
    void allUnavailableAlternativesAreMissingNotReady()
    {
        var a = key("alt_a");
        var b = key("alt_b");
        var product = key("product");
        var result = plan(List.of(recipe("root", product, List.of(options(0, a, b)))), Map.of(), product);
        assertNotEquals(PlanningOutcome.READY, result.outcome());
        assertFalse(result.craftable());
        assertFalse(result.missing().isEmpty(), result.toString());
        assertTrue(result.missing().containsKey(a) || result.missing().containsKey(b), result.missing().toString());
    }

    @Test
    void exhaustedBudgetWithRemainingAlternativesIsNotReady()
    {
        var raw = key("raw");
        var other = key("other");
        var product = key("product");
        var first = recipe("a_first", product, List.of(slot(0, raw, 1)));
        var second = recipe("z_second", product, List.of(slot(0, other, 1)));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(List.of(first, second)),
                Map.of(raw, 1L, other, 1L), product, 1, Map.of(), Map.of(), Map.of(), Map.of(),
                8, 1, 1_000_000_000L, true);
        assertFalse(result.craftable());
        assertNotEquals(PlanningOutcome.READY, result.outcome());
        assertFalse(result.missing().isEmpty(), result.toString());
        assertTrue(result.searchExhausted() || result.outcome() == PlanningOutcome.BUDGET_EXHAUSTED);
        assertEquals(PlanningOutcome.BUDGET_EXHAUSTED, result.outcome());
    }

    @Test
    void invalidIngredientLockIsRejectedInsteadOfEmptyReady()
    {
        var raw = key("raw");
        var product = key("product");
        var root = recipe("root", product, List.of(slot(0, raw, 1)));
        assertThrows(IllegalArgumentException.class, () -> ClientRecipePlanner.plan(
                new ClientRecipePlanner.Catalog(List.of(root)), Map.of(raw, 1L), product, 1,
                Map.of(), Map.of(new ClientRecipePlanner.IngredientKey(root.id(), 0), "missing:item"),
                Map.of(), Map.of(), 8, 64, 1_000_000_000L, true));
    }

    @Test
    void laterSiblingCanUseAnEarlierGuaranteedByproductWhenTheAlternativeRouteIsUnavailable()
    {
        var coal = key("coal");
        var coke = key("coke");
        var tar = key("tar");
        var missingRaw = key("missing_raw");
        var extra = key("extra");
        var product = key("product");
        var coking = recipe("coking", coke, List.of(slot(0, coal, 1)), List.of(new KeyAmount(tar, 1)));
        var extraFromMissing = recipe("extra_from_missing", extra, List.of(slot(0, missingRaw, 1)));
        var root = recipe("root", product, List.of(slot(0, coke, 1), options(1, tar, extra)));
        var result = plan(List.of(coking, extraFromMissing, root), Map.of(coal, 1L), product);
        assertEquals(PlanningOutcome.READY, result.outcome(), result.missing().toString());
        assertTrue(result.craftable());
        assertEquals(Map.of(coal, 1L), result.extraction());
        assertFalse(result.recipes().containsValue(extraFromMissing.id()));
    }

    @Test
    void wideShallowDagCompletesUnderTheNormalStackAndNodeBudget()
    {
        var iron = key("iron");
        var product = key("product");
        List<ClientRecipePlanner.Recipe> recipes = new ArrayList<>();
        List<ClientRecipePlanner.Slot> groups = new ArrayList<>();
        for (int group = 0; group < 32; group++)
        {
            var groupKey = key("group_" + group);
            List<ClientRecipePlanner.Slot> intermediates = new ArrayList<>();
            for (int item = 0; item < 60; item++)
            {
                var intermediate = key("mid_" + group + "_" + item);
                recipes.add(recipe("mid_" + group + "_" + item, intermediate, List.of(slot(0, iron, 1))));
                intermediates.add(slot(item, intermediate, 1));
            }
            recipes.add(recipe("group_" + group, groupKey, intermediates));
            groups.add(slot(group, groupKey, 1));
        }
        recipes.add(recipe("root", product, groups));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(recipes),
                Map.of(iron, 1920L), product, 1, Map.of(), Map.of(), Map.of(), Map.of(),
                48, 4096, 5_000_000_000L, true);
        assertEquals(PlanningOutcome.READY, result.outcome(), result.missing().toString());
        assertEquals(Map.of(iron, 1920L), result.extraction());
    }

    @Test
    void keepsAPreferredRouteEvenWhenAShorterAlternativeExists()
    {
        var raw = key("raw");
        var mid = key("mid");
        var shorter = key("short");
        var product = key("product");
        var preferred = recipe("preferred", product, List.of(slot(0, mid, 1)));
        var shortRecipe = recipe("short", product, List.of(slot(0, shorter, 1)));
        var midRecipe = recipe("mid", mid, List.of(slot(0, raw, 1)));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(
                List.of(preferred, shortRecipe, midRecipe)), Map.of(raw, 1L, shorter, 1L), product, 1,
                Map.of(), Map.of(), Map.of(RecipeResourceResolver.resolutionKey(product), preferred.id()), Map.of(),
                8, 64, 1_000_000_000L, true);
        assertEquals(PlanningOutcome.READY, result.outcome());
        assertEquals(preferred.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(product)));
        assertEquals(Map.of(raw, 1L), result.extraction());
    }

    @Test
    void fallsBackWhenThePreferredRouteCannotComplete()
    {
        var iron = key("iron");
        var copper = key("copper");
        var product = key("product");
        var preferred = recipe("preferred", product, List.of(slot(0, copper, 1)));
        var fallback = recipe("fallback", product, List.of(slot(0, iron, 1)));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(List.of(preferred, fallback)),
                Map.of(iron, 1L), product, 1, Map.of(), Map.of(),
                Map.of(RecipeResourceResolver.resolutionKey(product), preferred.id()), Map.of(),
                8, 64, 1_000_000_000L, true);
        assertEquals(PlanningOutcome.READY, result.outcome());
        assertEquals(fallback.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(product)));
    }

    @Test
    void rollsBackAnEarlierRecipeWhenALaterSiblingNeedsTheSameStock()
    {
        var copper = key("copper");
        var iron = key("iron");
        var partA = key("part_a");
        var partB = key("part_b");
        var product = key("product");
        var preferredA = recipe("a_copper", partA, List.of(slot(0, copper, 1)));
        var fallbackA = recipe("a_iron", partA, List.of(slot(0, iron, 1)));
        var partBRecipe = recipe("b_copper", partB, List.of(slot(0, copper, 1)));
        var root = recipe("root", product, List.of(slot(0, partA, 1), slot(1, partB, 1)));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(
                List.of(preferredA, fallbackA, partBRecipe, root)), Map.of(copper, 1L, iron, 1L), product, 1,
                Map.of(), Map.of(), Map.of(RecipeResourceResolver.resolutionKey(partA), preferredA.id()), Map.of(),
                8, 64, 1_000_000_000L, true);
        assertEquals(PlanningOutcome.READY, result.outcome(), result.missing().toString());
        assertEquals(fallbackA.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(partA)));
        assertEquals(partBRecipe.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(partB)));
    }

    @Test
    void rollsBackAnEarlierIngredientWhenALaterSlotNeedsTheSameStock()
    {
        var copper = key("copper");
        var iron = key("iron");
        var product = key("product");
        var root = recipe("root", product, List.of(options(0, copper, iron), slot(1, copper, 1)));
        var result = plan(List.of(root), Map.of(copper, 1L, iron, 1L), product);
        assertEquals(PlanningOutcome.READY, result.outcome(), result.missing().toString());
        assertEquals("test:iron",
                result.ingredients().get(new ClientRecipePlanner.IngredientKey(root.id(), 0)));
        assertEquals(1L, result.extraction().get(copper));
        assertEquals(1L, result.extraction().get(iron));
    }

    @Test
    void stopsAtTheFirstCompletePlan()
    {
        var first = key("first");
        var second = key("second");
        var product = key("product");
        var aFirst = recipe("a_first", product, List.of(slot(0, first, 1)));
        var zSecond = recipe("z_second", product, List.of(slot(0, second, 1)));
        var result = plan(List.of(aFirst, zSecond), Map.of(first, 1L, second, 1L), product);
        assertEquals(PlanningOutcome.READY, result.outcome());
        assertEquals(aFirst.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(product)));
    }

    @Test
    void keepsASingleProducerForTheWholePlan()
    {
        var ore = key("ore");
        var ingot = key("ingot");
        var rod = key("rod");
        var product = key("product");
        var smelt = recipe("smelt", ingot, List.of(slot(0, ore, 1)));
        var alt = recipe("alt", ingot, List.of(slot(0, key("scrap"), 1)));
        var rodRecipe = recipe("rod", rod, List.of(slot(0, ingot, 1)));
        var root = recipe("root", product, List.of(slot(0, ingot, 1), slot(1, rod, 1)));
        var result = plan(List.of(root, smelt, alt, rodRecipe), Map.of(ore, 2L), product);
        assertEquals(PlanningOutcome.READY, result.outcome(), result.missing().toString());
        assertEquals(smelt.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(ingot)));
        assertFalse(result.recipes().containsValue(alt.id()));
        assertEquals(Map.of(ore, 2L), result.extraction());
    }

    @Test
    void ignoresInitialTargetStockAndCraftsNewUnits()
    {
        var raw = key("raw");
        var product = key("product");
        var result = plan(List.of(recipe("craft", product, List.of(slot(0, raw, 1)))),
                Map.of(product, 9L, raw, 1L), product);
        assertEquals(PlanningOutcome.READY, result.outcome());
        assertEquals(Map.of(raw, 1L), result.extraction());
        assertFalse(result.extraction().containsKey(product));
    }

    @Test
    void plansWithGuaranteedByproductsAndReusableTools()
    {
        var coal = key("coal");
        var coke = key("coke");
        var tar = key("tar");
        var hammer = key("hammer");
        var product = key("product");
        var coking = recipe("coke", coke, List.of(slot(0, coal, 1)), List.of(new KeyAmount(tar, 1)));
        var root = recipe("root", product, List.of(
                slot(0, coke, 1), slot(1, tar, 1),
                new ClientRecipePlanner.Slot(2, List.of(new ClientRecipePlanner.Candidate(hammer, 1)),
                        VirtualInputUse.REUSABLE)));
        var result = plan(List.of(coking, root), Map.of(coal, 1L, hammer, 1L), product);
        assertEquals(PlanningOutcome.READY, result.outcome(), result.missing().toString());
        assertEquals(Map.of(coal, 1L, hammer, 1L), result.extraction());
    }

    @Test
    void reportsACycleInsteadOfACraftableLoop()
    {
        var nugget = key("nugget");
        var product = key("product");
        var compress = recipe("compress", product, List.of(slot(0, nugget, 9)));
        var decompose = recipe("decompose", nugget, List.of(slot(0, product, 1)));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(List.of(compress, decompose)),
                Map.of(), product, 1,
                Map.of(RecipeResourceResolver.resolutionKey(product), compress.id()), Map.of(),
                Map.of(), Map.of(), 8, 64, 1_000_000_000L, true);
        assertEquals(PlanningOutcome.CYCLE, result.outcome());
        assertFalse(result.craftable());
    }

    @Test
    void reportsMissingInputsWhenStockCannotComplete()
    {
        var raw = key("raw");
        var product = key("product");
        var result = plan(List.of(recipe("craft", product, List.of(slot(0, raw, 1)))), Map.of(), product);
        assertEquals(PlanningOutcome.MISSING_INPUTS, result.outcome());
        assertEquals(Map.of(raw, 1L), result.missing());
    }

    @Test
    void doesNotTreatADepthLimitAsACompletePlan()
    {
        var raw = key("raw");
        var mid = key("mid");
        var product = key("product");
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(List.of(
                recipe("root", product, List.of(slot(0, mid, 1))),
                recipe("mid", mid, List.of(slot(0, raw, 1))))),
                Map.of(raw, 1L), product, 1, Map.of(), Map.of(), Map.of(), Map.of(),
                1, 64, 1_000_000_000L, true);
        assertEquals(PlanningOutcome.MISSING_INPUTS, result.outcome());
        assertTrue(result.missing().containsKey(mid));
        assertFalse(result.craftable());
    }

    @Test
    void respectsAnExplicitManualLockInsteadOfASavedPreference()
    {
        var iron = key("iron");
        var copper = key("copper");
        var product = key("product");
        var preferred = recipe("preferred", product, List.of(slot(0, copper, 1)));
        var manual = recipe("manual", product, List.of(slot(0, iron, 1)));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(List.of(preferred, manual)),
                Map.of(iron, 1L, copper, 1L), product, 1,
                Map.of(RecipeResourceResolver.resolutionKey(product), manual.id()), Map.of(),
                Map.of(RecipeResourceResolver.resolutionKey(product), preferred.id()), Map.of(),
                8, 64, 1_000_000_000L, true);
        assertEquals(PlanningOutcome.READY, result.outcome());
        assertEquals(manual.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(product)));
    }

    @Test
    void prefersAnIngredientByExactSelectionOrLegacyMatchSemantics()
    {
        var copper = key("copper");
        var iron = key("iron");
        var product = key("product");
        var ironCandidate = new ClientRecipePlanner.Candidate(iron, 1, id("test:iron"), "test:iron");
        var root = new ClientRecipePlanner.Recipe(id("test:root"), "test:machine", product, 1,
                RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, List.of(
                new ClientRecipePlanner.Slot(0, List.of(new ClientRecipePlanner.Candidate(copper, 1), ironCandidate),
                        VirtualInputUse.CONSUMED),
                slot(1, copper, 1)), List.of());
        assertTrue(ClientRecipePlanner.preferredIngredientMatches("test:iron", ironCandidate));
        assertTrue("test:iron".equals(ironCandidate.selection())
                || IngredientSelectionKey.matches("test:iron", iron));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(List.of(root)),
                Map.of(copper, 1L, iron, 1L), product, 1, Map.of(), Map.of(), Map.of(),
                Map.of(new ClientRecipePlanner.IngredientKey(root.id(), 0), "test:iron"),
                8, 64, 1_000_000_000L, true);
        assertEquals(PlanningOutcome.READY, result.outcome(), result.missing().toString());
        assertEquals("test:iron", result.ingredients().get(new ClientRecipePlanner.IngredientKey(root.id(), 0)));
    }

    @Test
    void keepsAllCandidatesMatchingASavedIngredientAlias()
    {
        var good = key("metal_good");
        var missing = key("metal_missing");
        var product = key("product");
        var root = recipe("alias_root", product, List.of(new ClientRecipePlanner.Slot(0, List.of(
                new ClientRecipePlanner.Candidate(good, 1, id("test:metal"), "test:metal"),
                new ClientRecipePlanner.Candidate(missing, 1, id("test:metal"), "test:metal")),
                VirtualInputUse.CONSUMED)));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(List.of(root)),
                Map.of(good, 1L), product, 1, Map.of(), Map.of(), Map.of(),
                Map.of(new ClientRecipePlanner.IngredientKey(root.id(), 0), "test:metal"),
                8, 64, 1_000_000_000L, true);
        assertTrue(result.craftable(), result.missing().toString());
        assertEquals(1L, result.extraction().get(good));
        assertFalse(result.extraction().containsKey(missing));
    }

    @Test
    void missingEarlyPreferenceDoesNotEnumerateIndependentLaterRoutes()
    {
        var product = key("product");
        var part = key("part");
        var absent = key("absent");
        var copper = key("copper");
        var clutter = key("clutter");
        var preferred = recipe("a_preferred", part, List.of(slot(0, absent, 1)));
        var fallback = recipe("b_fallback", part, List.of(slot(0, copper, 1)));
        List<ClientRecipePlanner.Recipe> recipes = new ArrayList<>(List.of(preferred, fallback));
        List<ClientRecipePlanner.Slot> demands = new ArrayList<>(List.of(slot(0, part, 1)));
        for (int i = 0; i < 18; i++)
        {
            var other = key("other_" + i);
            demands.add(slot(i + 1, other, 1));
            recipes.add(recipe("other_a_" + i, other, List.of(slot(0, clutter, 1))));
            recipes.add(recipe("other_b_" + i, other, List.of(slot(0, clutter, 1))));
        }
        recipes.add(recipe("root", product, demands));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(recipes),
                Map.of(copper, 1L, clutter, 18L), product, 1, Map.of(), Map.of(),
                Map.of(RecipeResourceResolver.resolutionKey(part), preferred.id()), Map.of(),
                48, 4096, ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS, true);
        assertTrue(result.craftable(), result.outcome() + ": " + result.missing());
        assertEquals(fallback.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(part)));
        assertEquals(1L, result.extraction().get(copper));
        assertEquals(18L, result.extraction().get(clutter));
    }

    @Test
    void optionalSearchCanCombineChangesBeyondTheQuickSingleSubstitutionPass()
    {
        var product = key("product");
        var a = key("a");
        var b = key("b");
        var c = key("c");
        var d = key("d");
        var root = recipe("root", product, List.of(options(0, a, b), options(1, c, d)));
        var preferences = Map.of(new ClientRecipePlanner.IngredientKey(root.id(), 0), "test:a",
                new ClientRecipePlanner.IngredientKey(root.id(), 1), "test:c");
        var catalog = new ClientRecipePlanner.Catalog(List.of(root));
        var stock = Map.of(b, 1L, d, 1L);
        var quick = ClientRecipePlanner.plan(catalog, stock, product, 1,
                Map.of(), Map.of(), Map.of(), preferences, 8, 64, 1_000_000_000L, false);
        assertFalse(quick.craftable());
        var complete = ClientRecipePlanner.plan(catalog, stock, product, 1,
                Map.of(), Map.of(), Map.of(), preferences, 8, 64, 1_000_000_000L, true);
        assertTrue(complete.craftable(), complete.missing().toString());
        assertEquals(1L, complete.extraction().get(b));
        assertEquals(1L, complete.extraction().get(d));
    }

    @Test
    void missingGrowthSeedBacktracksBeforeIndependentLaterRoutes()
    {
        var product = key("product");
        var part = key("part");
        var copper = key("copper");
        var clutter = key("clutter");
        var growth = new ClientRecipePlanner.Recipe(id("test:a_growth"), "test:machine", part, 2,
                RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, List.of(slot(0, part, 1)), List.of());
        var fallback = recipe("b_make_part", part, List.of(slot(0, copper, 1)));
        List<ClientRecipePlanner.Recipe> recipes = new ArrayList<>(List.of(growth, fallback));
        List<ClientRecipePlanner.Slot> demands = new ArrayList<>(List.of(slot(0, part, 1)));
        for (int i = 0; i < 18; i++)
        {
            var other = key("other_" + i);
            demands.add(slot(i + 1, other, 1));
            recipes.add(recipe("other_a_" + i, other, List.of(slot(0, clutter, 1))));
            recipes.add(recipe("other_b_" + i, other, List.of(slot(0, clutter, 1))));
        }
        recipes.add(recipe("root", product, demands));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(recipes),
                Map.of(copper, 1L, clutter, 18L), product, 1, Map.of(), Map.of(),
                Map.of(RecipeResourceResolver.resolutionKey(part), growth.id()), Map.of(),
                48, 4096, ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS, true);
        assertTrue(result.craftable(), result.outcome() + ": " + result.missing());
        assertEquals(fallback.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(part)));
        assertEquals(1L, result.extraction().get(copper));
    }

    private static ClientRecipePlanner.Proposal plan(List<ClientRecipePlanner.Recipe> recipes,
                                                     Map<IStackKey<?>, Long> stock, IStackKey<?> target)
    {
        return ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(recipes), stock, target, 1,
                Map.of(), Map.of(), Map.of(), Map.of(), 8, 64, 1_000_000_000L, true);
    }

    private static ClientRecipePlanner.Recipe recipe(String id, IStackKey<?> output,
                                                     List<ClientRecipePlanner.Slot> slots)
    {
        return recipe(id, output, slots, List.of());
    }

    private static ClientRecipePlanner.Recipe recipe(String id, IStackKey<?> output,
                                                     List<ClientRecipePlanner.Slot> slots,
                                                     List<KeyAmount> byproducts)
    {
        return new ClientRecipePlanner.Recipe(id("test:" + id), "test:machine", output, 1,
                RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, slots, byproducts);
    }

    private static ClientRecipePlanner.Slot slot(int index, IStackKey<?> key, long count)
    {
        return new ClientRecipePlanner.Slot(index, List.of(new ClientRecipePlanner.Candidate(key, count)),
                VirtualInputUse.CONSUMED);
    }

    private static ClientRecipePlanner.Slot options(int index, IStackKey<?>... keys)
    {
        List<ClientRecipePlanner.Candidate> candidates = new ArrayList<>();
        for (IStackKey<?> key : keys)
            candidates.add(new ClientRecipePlanner.Candidate(key, 1, id("test:" + key)));
        return new ClientRecipePlanner.Slot(index, candidates, VirtualInputUse.CONSUMED);
    }

    @SuppressWarnings("unchecked")
    private static <T> T id(String path)
    {
        String namespace = "test";
        String name = path;
        int colon = path.indexOf(':');
        if (colon >= 0)
        {
            namespace = path.substring(0, colon);
            name = path.substring(colon + 1);
        }
        for (String className : new String[] {
                "net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation"})
        {
            try
            {
                Class<?> type = Class.forName(className);
                try
                {
                    return (T) type.getMethod("fromNamespaceAndPath", String.class, String.class)
                            .invoke(null, namespace, name);
                }
                catch (NoSuchMethodException ignored) { }
                try
                {
                    return (T) type.getConstructor(String.class, String.class).newInstance(namespace, name);
                }
                catch (NoSuchMethodException ignored) { }
            }
            catch (ClassNotFoundException ignored) { }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no resource id type for " + path);
    }

    private static IStackKey<?> key(String name)
    {
        return (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEmpty" -> false;
                    case "getTypeId" -> id("test:resource");
                    case "getModId" -> "test";
                    case "getSource", "getReadOnlyStack", "toString" -> name;
                    case "isSame", "isSameTypeSameComponents", "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
