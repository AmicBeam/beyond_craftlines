package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class FastPlanningFallbackTest
{
    @Test void compressionLoopDoesNotHideSmeltingWithStockedRawIron()
    {
        var iron = key("iron"); var nuggets = key("nuggets"); var raw = key("raw_iron");
        var compression = recipe("a_compress", iron, List.of(slot(0, nuggets, 9)), List.of());
        var decomposition = recipe("decompose", nuggets, List.of(slot(0, iron, 1)), List.of());
        var smelting = recipe("z_smelt", iron, List.of(slot(0, raw, 1)), List.of());
        var result = fast(List.of(compression, decomposition, smelting), Map.of(raw, 1L), iron, Map.of());
        assertEquals(PlanningOutcome.READY, result.outcome());
        assertEquals(smelting.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(iron)));
        assertEquals(Map.of(raw, 1L), result.extraction());
    }

    @Test void missingRawIronIsMoreUsefulThanTheRejectedCompressionLoop()
    {
        var iron = key("iron"); var nuggets = key("nuggets"); var raw = key("raw_iron");
        var result = fast(List.of(
                recipe("a_compress", iron, List.of(slot(0, nuggets, 9)), List.of()),
                recipe("decompose", nuggets, List.of(slot(0, iron, 1)), List.of()),
                recipe("z_smelt", iron, List.of(slot(0, raw, 1)), List.of())), Map.of(), iron, Map.of());
        assertEquals(PlanningOutcome.MISSING_INPUTS, result.outcome());
        assertEquals(Map.of(raw, 1L), result.missing());
    }

    @Test void nestedCycleDoesNotHideAUsableSiblingRecipe()
    {
        var target = key("target"); var a = key("a"); var b = key("b"); var raw = key("raw");
        var fallback = recipe("z_fallback", target, List.of(slot(0, raw, 1)), List.of());
        var result = fast(List.of(
                recipe("a_root", target, List.of(slot(0, a, 1)), List.of()),
                recipe("loop_a", a, List.of(slot(0, b, 1)), List.of()),
                recipe("loop_b", b, List.of(slot(0, a, 1)), List.of()), fallback),
                Map.of(raw, 1L), target, Map.of());
        assertEquals(PlanningOutcome.READY, result.outcome());
        assertEquals(fallback.id(), result.recipes().get(RecipeResourceResolver.resolutionKey(target)));
    }

    @Test void tagAlternativeSearchContinuesAfterACyclicIngredient()
    {
        var target = key("target"); var loop = key("a_loop"); var viable = key("z_viable"); var raw = key("raw");
        var options = new ClientRecipePlanner.Slot(0, List.of(
                new ClientRecipePlanner.Candidate(loop, 1), new ClientRecipePlanner.Candidate(viable, 1)),
                VirtualInputUse.CONSUMED);
        var result = fast(List.of(
                recipe("root", target, List.of(options), List.of()),
                recipe("loop", loop, List.of(slot(0, target, 1)), List.of()),
                recipe("viable", viable, List.of(slot(0, raw, 1)), List.of())),
                Map.of(raw, 1L), target, Map.of());
        assertEquals(PlanningOutcome.READY, result.outcome());
        assertEquals(Map.of(raw, 1L), result.extraction());
    }

    @Test void explicitlyPinnedLoopRemainsRejected()
    {
        var iron = key("iron"); var nuggets = key("nuggets"); var raw = key("raw_iron");
        var compression = recipe("a_compress", iron, List.of(slot(0, nuggets, 9)), List.of());
        var result = fast(List.of(compression,
                recipe("decompose", nuggets, List.of(slot(0, iron, 1)), List.of()),
                recipe("z_smelt", iron, List.of(slot(0, raw, 1)), List.of())), Map.of(raw, 1L), iron,
                Map.of(RecipeResourceResolver.resolutionKey(iron), compression.id()));
        assertEquals(PlanningOutcome.CYCLE, result.outcome());
        assertFalse(result.craftable());
    }

    private static ClientRecipePlanner.Proposal fast(List<ClientRecipePlanner.Recipe> recipes,
            Map<IStackKey<?>, Long> stock, IStackKey<?> target, Map<String, Identifier> pinned)
    {
        return ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(recipes), stock, target, 1,
                pinned, Map.of(), 32, 4096, ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS, false);
    }

    private static ClientRecipePlanner.Recipe recipe(String id, IStackKey<?> output,
                                                       List<ClientRecipePlanner.Slot> slots, List<KeyAmount> byproducts)
    {
        return new ClientRecipePlanner.Recipe(Identifier.fromNamespaceAndPath("test", id), "test:machine", output,
                1, RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, slots, byproducts);
    }

    private static ClientRecipePlanner.Slot slot(int index, IStackKey<?> key, long count)
    { return new ClientRecipePlanner.Slot(index, List.of(new ClientRecipePlanner.Candidate(key, count)), VirtualInputUse.CONSUMED); }

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
