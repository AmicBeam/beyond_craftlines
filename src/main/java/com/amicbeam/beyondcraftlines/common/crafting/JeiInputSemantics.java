package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import java.util.List;

/** Infers the execution semantics that JEI's INPUT/CATALYST roles do not encode on the wire. */
public final class JeiInputSemantics
{
    private JeiInputSemantics() {}

    /** Damageable inputs are tools: one instance is routed through all crafts instead of one per craft. */
    public static boolean reusableInput(List<KeyAmount> candidates)
    {
        boolean allDamageable = candidates != null && !candidates.isEmpty() && candidates.stream().allMatch(candidate ->
                candidate.key() instanceof ItemStackKey item
                        && item.getReadOnlyStack().isDamageableItem());
        return VirtualInputPolicy.reusable(false, false, allDamageable);
    }

    /**
     * JEI normally uses CATALYST for reusable tools. Some recipes explicitly expose a consumed
     * output container in that role (Farmer's Delight's cooking pot is the canonical case).
     */
    public static boolean reusableCatalyst(Object displayedRecipe, List<KeyAmount> candidates)
    {
        Object recipe = unwrap(displayedRecipe);
        Object rawContainer = RecipeReflection.readPublicMember(recipe, "getOutputContainer");
        KeyAmount container = RecipeResourceResolver.fromStack(rawContainer);
        boolean consumedContainer = container != null && candidates != null && candidates.stream()
                .anyMatch(candidate -> StackKeyMatch.exact(container.key(), candidate.key()));
        return VirtualInputPolicy.reusable(true, consumedContainer, false);
    }

    private static Object unwrap(Object displayedRecipe)
    {
        if (displayedRecipe == null) return null;
        Object value = RecipeReflection.readPublicMember(displayedRecipe, "value");
        return value == null ? displayedRecipe : value;
    }
}
