package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class ComponentLookupFastPathTest
{
    @Test void exactLookupDoesNotReserializeOrScoreUnrelatedCatalogEntries()
    {
        AtomicInteger serializations = new AtomicInteger();
        List<ClientRecipePlanner.Recipe> recipes = new ArrayList<>();
        IStackKey<?> target = null;
        for (int i = 0; i < 1000; i++)
        {
            IStackKey<?> key = key("item_" + i, serializations);
            if (i == 5) target = key;
            recipes.add(new ClientRecipePlanner.Recipe(DynamicCraftingFixtures.id("test:recipe_" + i), "crafting", key,
                    1, RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, List.of()));
        }
        var builder = ClientRecipeLookupIndex.begin(new ClientRecipePlanner.Catalog(recipes));
        while (!builder.complete()) builder.advance(Long.MAX_VALUE);
        builder.install();
        int before = serializations.get();
        for (int i = 0; i < 100; i++)
            assertEquals(List.of("test:recipe_5"), ClientRecipeLookupIndex.recipeIdsForOutput(target));
        assertEquals(before, serializations.get(), "exact lookup must stay on cached identity keys");
        ClientRecipeLookupIndex.clear();
    }
    private static IStackKey<?> key(String name, AtomicInteger serializations)
    {
        return (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTypeId" -> DynamicCraftingFixtures.id("test:fast_path");
                    case "getModId" -> "test";
                    case "getSource", "getReadOnlyStack", "toString" -> name;
                    case "isEmpty" -> false;
                    case "hashCode" -> name.hashCode();
                    case "equals", "isSameTypeSameComponents", "isSame" -> proxy == args[0];
                    case "serializeNBT" -> { serializations.incrementAndGet(); CompoundTag tag = new CompoundTag(); tag.putString("name", name); yield tag; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
