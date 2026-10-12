package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FeasibleClientRecipePlannerTest
{
    @Test
    void rollsBackPreferredCopperWhenASiblingStillNeedsIt()
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
                List.of(preferredA, fallbackA, partBRecipe, root)), Map.of(copper, 1L, iron, 1L),
                product, 1, Map.of(), Map.of(),
                Map.of(RecipeResourceResolver.resolutionKey(partA), preferredA.id()), Map.of(),
                32, 4096, ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS, true);
        assertTrue(result.craftable(), result.missing().toString());
        assertEquals(fallbackA.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(partA)));
        assertEquals(partBRecipe.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(partB)));
    }

    @Test
    void keepsPreferredWhenItIsFeasibleEvenIfAShorterRecipeExists()
    {
        var raw = key("raw");
        var mid = key("mid");
        var shorter = key("short");
        var product = key("product");
        var preferred = recipe("preferred", product, List.of(slot(0, mid, 1)));
        var shortRecipe = recipe("short", product, List.of(slot(0, shorter, 1)));
        var midRecipe = recipe("mid", mid, List.of(slot(0, raw, 1)));
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(
                List.of(preferred, shortRecipe, midRecipe)), Map.of(raw, 1L, shorter, 1L),
                product, 1, Map.of(), Map.of(),
                Map.of(RecipeResourceResolver.resolutionKey(product), preferred.id()), Map.of(),
                32, 4096, ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS, true);
        assertTrue(result.craftable(), result.missing().toString());
        assertEquals(preferred.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(product)));
    }

    private static ClientRecipePlanner.Recipe recipe(String id, IStackKey<?> output,
                                                     List<ClientRecipePlanner.Slot> slots)
    {
        return new ClientRecipePlanner.Recipe(Identifier.fromNamespaceAndPath("test", id), "test:machine", output,
                1, RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, slots, List.of());
    }

    private static ClientRecipePlanner.Slot slot(int index, IStackKey<?> key, long count)
    {
        return new ClientRecipePlanner.Slot(index, List.of(new ClientRecipePlanner.Candidate(key, count)),
                VirtualInputUse.CONSUMED);
    }

    private static IStackKey<?> key(String name)
    {
        return (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEmpty" -> false;
                    case "getTypeId" -> Identifier.fromNamespaceAndPath("test", "resource");
                    case "getModId" -> "test";
                    case "getSource", "getReadOnlyStack", "toString" -> name;
                    case "isSame", "isSameTypeSameComponents", "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
