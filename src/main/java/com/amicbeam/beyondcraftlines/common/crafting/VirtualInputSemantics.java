package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

import java.util.List;

/** Reads stable public recipe accessors to refine ambiguous JEI slot roles. */
public final class VirtualInputSemantics
{
    private VirtualInputSemantics() {}

    public static Decision decide(Object displayedRecipe, List<KeyAmount> candidates, boolean catalyst)
    {
        Object recipe = unwrap(displayedRecipe);
        return classify(matches(recipe, candidates, "getOutputContainer")
                || matches(recipe, candidates, "getContainer"),
                matches(recipe, candidates, "getTool"), catalyst);
    }

    static Decision classify(boolean container, boolean tool, boolean catalyst)
    {
        if (container) return new Decision(true, "container", VirtualInputUse.CONSUMED);
        if (tool) return new Decision(true, "tool", VirtualInputUse.durability(1));
        // An unmatched JEI catalyst is commonly the workstation itself. Do not invent a material demand.
        return new Decision(!catalyst, "", VirtualInputUse.CONSUMED);
    }

    private static boolean matches(Object recipe, List<KeyAmount> candidates, String accessor)
    {
        Object raw = RecipeReflection.readPublicMember(recipe, accessor);
        if (raw instanceof net.minecraft.world.item.crafting.Ingredient ingredient)
            return candidates.stream().anyMatch(candidate ->
                    candidate.key() instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey item
                            && ingredient.test(item.getReadOnlyStack()));
        KeyAmount expected = RecipeResourceResolver.fromStack(raw);
        return expected != null && candidates.stream().anyMatch(candidate ->
                StackKeyMatch.exact(expected.key(), candidate.key()));
    }

    private static Object unwrap(Object displayedRecipe)
    {
        if (displayedRecipe == null) return null;
        Object value = RecipeReflection.readPublicMember(displayedRecipe, "value");
        return value == null ? displayedRecipe : value;
    }

    public record Decision(boolean included, String inputGroup, VirtualInputUse use) {}
}
