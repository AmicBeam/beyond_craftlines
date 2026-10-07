package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class WorkstationIngredientCacheTest
{
    @Test void resourceAndSampleCaptureReuseTheSameWorkstationInputsUntilReload()
    {
        RecipeIngredientResolver.clearCache();
        try
        {
            AtomicInteger reads = new AtomicInteger();
            List<Ingredient> inputs = List.of(Ingredient.of(Items.IRON_INGOT));
            Recipe<?> recipe = (Recipe<?>) Proxy.newProxyInstance(SmithingRecipe.class.getClassLoader(),
                    new Class<?>[]{SmithingRecipe.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "placementInfo" -> { reads.incrementAndGet(); yield PlacementInfo.create(inputs); }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "getGroup" -> "";
                        case "toString" -> "test smithing recipe";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });

            assertEquals(1, RecipeIngredientResolver.vanillaIngredients(recipe).size());
            assertEquals(1, RecipeIngredientResolver.ingredients(recipe).size());
            assertEquals(1, RecipeIngredientResolver.vanillaIngredients(recipe).size());
            assertEquals(1, reads.get());
            RecipeIngredientResolver.clearCache();
            assertEquals(1, RecipeIngredientResolver.vanillaIngredients(recipe).size());
            assertEquals(2, reads.get());
        }
        finally { RecipeIngredientResolver.clearCache(); }
    }
}
