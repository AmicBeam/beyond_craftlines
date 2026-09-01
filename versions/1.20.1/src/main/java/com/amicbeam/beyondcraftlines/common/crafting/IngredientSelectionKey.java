package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Forge 1.20.1 avoids serializing and hashing componentless ingredient candidates.
 * Tagged or capability-bearing stacks retain the full component-aware identity.
 */
public final class IngredientSelectionKey
{
    private static final String EXACT_PREFIX = "exact:";
    private IngredientSelectionKey() {}

    public static String exact(IStackKey<?> key)
    {
        if (key instanceof ItemStackKey itemKey
                && com.amicbeam.beyondcraftlines.compat.IngredientSelectionKeyCompat.hasDefaultIdentity(key))
            return legacy(BuiltInRegistries.ITEM.getKey(itemKey.getSource()));
        return exactResolution(RecipeResourceResolver.resolutionKey(key));
    }

    static String exactResolution(String resolutionKey)
    { return EXACT_PREFIX + resolutionKey; }

    public static String legacy(ResourceLocation item)
    { return item.toString(); }

    public static boolean matches(String selection, IStackKey<?> candidate)
    {
        if (selection == null || candidate == null) return false;
        if (selection.startsWith(EXACT_PREFIX)) return selection.equals(exact(candidate));
        ResourceLocation item = ResourceLocation.tryParse(selection);
        return item != null && candidate instanceof ItemStackKey itemKey
                && item.equals(BuiltInRegistries.ITEM.getKey(itemKey.getSource()));
    }

    public static ResourceLocation legacyItem(String selection)
    {
        if (selection == null || selection.startsWith(EXACT_PREFIX)) return null;
        return ResourceLocation.tryParse(selection);
    }
}
