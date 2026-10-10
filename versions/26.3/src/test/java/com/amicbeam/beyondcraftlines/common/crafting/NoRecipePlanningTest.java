package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class NoRecipePlanningTest
{
    @Test void noRecipeStopsAtTheSelectedIntermediateInsteadOfExpandingItsRecipe()
    {
        var target = key("target"); var intermediate = key("intermediate"); var raw = key("raw");
        var result = fast(List.of(
                recipe("root", target, List.of(slot(0, intermediate, 3)), List.of()),
                recipe("intermediate", intermediate, List.of(slot(0, raw, 1)), List.of())),
                Map.of(intermediate, 1L, raw, 10L), target,
                RecipeResourceResolver.resolutionKey(intermediate));
        assertEquals(PlanningOutcome.MISSING_INPUTS, result.outcome());
        assertEquals(Map.of(intermediate, 2L), result.missing());
        assertEquals(Map.of(intermediate, 1L), result.extraction());
        assertEquals(RecipeResolutionOverrides.NO_RECIPE,
                result.recipes().get(RecipeResourceResolver.resolutionKey(intermediate)));
    }

    @Test void stockSatisfiedNoRecipeInputStillAllowsTheParentToCraft()
    {
        var target = key("target"); var input = key("input");
        var result = fast(List.of(recipe("root", target, List.of(slot(0, input, 2)), List.of())),
                Map.of(input, 2L), target,
                RecipeResourceResolver.resolutionKey(input));
        assertTrue(result.craftable());
        assertEquals(Map.of(input, 2L), result.extraction());
        assertTrue(result.missing().isEmpty());
    }

    @Test void noRecipeIsAvailableEvenWhenNoCandidateRecipeExists()
    {
        var target = key("target");
        var result = fast(List.of(), Map.of(), target,
                RecipeResourceResolver.resolutionKey(target));
        assertEquals(PlanningOutcome.NO_RECIPE, result.outcome());
        assertEquals(Map.of(target, 1L), result.missing());
        assertEquals(RecipeResolutionOverrides.NO_RECIPE,
                result.recipes().get(RecipeResourceResolver.resolutionKey(target)));
    }

    @Test void noRecipeBreaksACompressionCycleAtItsParent()
    {
        var target = key("target"); var intermediate = key("intermediate");
        var result = fast(List.of(
                recipe("root", target, List.of(slot(0, intermediate, 1)), List.of()),
                recipe("loop", intermediate, List.of(slot(0, target, 1)), List.of())),
                Map.of(), target,
                RecipeResourceResolver.resolutionKey(intermediate));
        assertEquals(PlanningOutcome.MISSING_INPUTS, result.outcome());
        assertEquals(Map.of(intermediate, 1L), result.missing());
    }

    @Test void rootNoRecipeCannotExtractAnExistingManufacturingTarget()
    {
        var target = key("target");
        var result = fast(List.of(recipe("root", target, List.of(), List.of())), Map.of(target, 8L), target,
                RecipeResourceResolver.resolutionKey(target));
        assertEquals(PlanningOutcome.NO_RECIPE, result.outcome());
        assertTrue(result.extraction().isEmpty());
        assertFalse(result.craftable());
    }

    private static ClientRecipePlanner.Proposal fast(List<ClientRecipePlanner.Recipe> recipes,
            Map<IStackKey<?>, Long> stock, IStackKey<?> target, String blockedToken)
    {
        return ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(recipes), stock, target, 1,
                Map.of(blockedToken, RecipeResolutionOverrides.NO_RECIPE), Map.of(), 32, 4096, ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS, false);
    }

    private static ClientRecipePlanner.Recipe recipe(String id, IStackKey<?> output,
                                                       List<ClientRecipePlanner.Slot> slots, List<KeyAmount> byproducts)
    {
        return new ClientRecipePlanner.Recipe(RecipeResolutionOverrides.NO_RECIPE.withPath(id), "test:machine", output,
                1, RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, slots, byproducts);
    }

    private static ClientRecipePlanner.Slot slot(int index, IStackKey<?> key, long count)
    { return new ClientRecipePlanner.Slot(index, List.of(new ClientRecipePlanner.Candidate(key, count)), VirtualInputUse.CONSUMED); }

    private static IStackKey<?> key(String name)
    {
        return (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEmpty" -> false;
                    case "getTypeId" -> RecipeResolutionOverrides.NO_RECIPE.withPath("resource");
                    case "getModId" -> "test";
                    case "getSource", "getReadOnlyStack", "toString" -> name;
                    case "isSame", "isSameTypeSameComponents", "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
