package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class FullCatalogNetworkScopeTest
{
    @Test void eachNetworkPlansOnlyWithItsOwnMachineTypesFromTheSharedCatalog()
    {
        var target = key("target");
        var raw = key("raw");
        var furnace = recipe("a_smelt", "smelting", target, raw);
        var mixer = recipe("b_mix", "create:mixing", target, raw);
        var catalog = new ClientRecipePlanner.Catalog(List.of(furnace, mixer));

        assertEquals(furnace.id(), plan(catalog.forFamilies(Set.of("smelting")), raw, target)
                .recipes().get(RecipeResourceResolver.resolutionKey(target)));
        assertEquals(mixer.id(), plan(catalog.forFamilies(Set.of("create:mixing")), raw, target)
                .recipes().get(RecipeResourceResolver.resolutionKey(target)));
        assertFalse(plan(catalog.forFamilies(Set.of()), raw, target).craftable());
        assertEquals(2, catalog.recipes().size());
        assertSame(furnace, catalog.forFamilies(Set.of("smelting")).recipes().getFirst());
    }

    @Test void networkFilteringAlsoAppliesToNestedDependenciesAndKeepsCraftingAvailable()
    {
        var target = key("target");
        var ingot = key("ingot");
        var raw = key("raw");
        var craft = recipe("craft", "crafting", target, ingot);
        var smelt = recipe("smelt", "smelting", ingot, raw);
        var catalog = new ClientRecipePlanner.Catalog(List.of(craft, smelt));
        var disabled = plan(catalog.forFamilies(Set.of()), raw, target);
        assertFalse(disabled.craftable());
        assertFalse(disabled.recipes().containsValue(smelt.id()));
        assertEquals(Map.of(ingot, 1L), disabled.missing());
        assertTrue(plan(catalog.forFamilies(Set.of("smelting")), raw, target).craftable());
    }

    private static ClientRecipePlanner.Proposal plan(ClientRecipePlanner.Catalog catalog,
                                                    IStackKey<?> raw, IStackKey<?> target)
    {
        return ClientRecipePlanner.plan(catalog, Map.of(raw, 1L), target, 1,
                Map.of(), Map.of(), 32, 4096, ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS, false);
    }

    private static ClientRecipePlanner.Recipe recipe(String id, String family, IStackKey<?> output, IStackKey<?> input)
    {
        return new ClientRecipePlanner.Recipe(Identifier.fromNamespaceAndPath("test", id), family, output, 1,
                RecipeIoProfileRegistry.OutputMatchSemantics.EXACT,
                List.of(new ClientRecipePlanner.Slot(0, List.of(new ClientRecipePlanner.Candidate(input, 1)),
                        VirtualInputUse.CONSUMED)), List.of());
    }

    static IStackKey<?> key(String name)
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
