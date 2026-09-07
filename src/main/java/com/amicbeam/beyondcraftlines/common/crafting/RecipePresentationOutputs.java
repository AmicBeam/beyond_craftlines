package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import java.util.LinkedHashSet;
import java.util.Set;

/** Explicitly declared JEI illustration items are not manufactured resources. */
public final class RecipePresentationOutputs
{
    private RecipePresentationOutputs() {}

    public static Set<IStackKey<?>> ignored(Object displayedRecipe)
    {
        Object value = RecipeReflection.readPublicMember(displayedRecipe, "value");
        Object recipe = value == null ? displayedRecipe : value;
        Set<IStackKey<?>> ignored = new LinkedHashSet<>();
        for (String member : RecipeIoProfileRegistry.ignoredOutputFields(recipe))
        {
            var stack = RecipeResourceResolver.fromStack(RecipeReflection.readPublicMember(recipe, member));
            if (stack == null || stack.isEmpty())
                throw new IllegalArgumentException("presentation output profile cannot resolve " + member);
            ignored.add(stack.key());
        }
        return Set.copyOf(ignored);
    }
}
