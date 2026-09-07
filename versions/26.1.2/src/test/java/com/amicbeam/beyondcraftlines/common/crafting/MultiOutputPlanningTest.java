package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class MultiOutputPlanningTest
{
    @Test void oneProcessingBatchSatisfiesBothSolidAndFluidDependencies()
    {
        var coal = key("coal"); var coke = key("coke"); var oil = key("oil"); var product = key("product");
        var process = recipe("coking", coke, List.of(slot(0, coal, 1)), List.of(new KeyAmount(oil, 250)));
        var target = recipe("target", product, List.of(slot(0, coke, 1), slot(1, oil, 250)), List.of());
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(List.of(process, target)),
                Map.of(coal, 1L), product, 1, Map.of(), Map.of(), 32, 4096);
        assertTrue(result.craftable(), result.missing().toString());
        assertEquals(Map.of(coal, 1L), result.extraction());
        assertEquals(2, result.recipes().size());
    }

    @Test void aPossibleByproductCannotSatisfyGuaranteedDemand()
    {
        var coal = key("coal"); var coke = key("coke"); var oil = key("oil"); var product = key("product");
        var process = recipe("coking", coke, List.of(slot(0, coal, 1)), List.of());
        var target = recipe("target", product, List.of(slot(0, coke, 1), slot(1, oil, 250)), List.of());
        var result = ClientRecipePlanner.plan(new ClientRecipePlanner.Catalog(List.of(process, target)),
                Map.of(coal, 1L), product, 1, Map.of(), Map.of(), 32, 4096);
        assertFalse(result.craftable());
        assertEquals(250L, result.missing().get(oil));
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
