package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Locale;

/** Converts JEI input slot names into stable, protocol-safe provisioner sublabels. */
public final class JeiSlotInputGroup
{
    private static final int MAX_LENGTH = 64;

    private JeiSlotInputGroup() {}

    public static String fromSlotName(String slotName)
    {
        if (slotName == null || slotName.isBlank()) return RecipeResourceResolver.VANILLA_INPUT_GROUP;
        String value = slotName.strip().replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("^[_\u002e-]+|[_\u002e-]+$", "")
                .replaceAll("[_-]?\\d+$", "");
        if (value.isBlank() || value.equals("input") || value.equals("inputs")
                || value.equals("ingredient") || value.equals("ingredients"))
            return RecipeResourceResolver.VANILLA_INPUT_GROUP;
        return value.substring(0, Math.min(MAX_LENGTH, value.length()));
    }

    public static boolean isValid(String group)
    {
        return group != null && !group.isBlank() && group.length() <= MAX_LENGTH
                && group.matches("[a-z0-9_.-]+");
    }

    /** Resolves generated JEI ingredient-type group IDs without changing their routing identity. */
    public static String resourceType(String group)
    {
        if (group == null || group.isBlank()) return "";
        String value = group.toLowerCase(Locale.ROOT);
        if (value.startsWith("input_")) value = value.substring("input_".length());
        String compact = value.replace("_", "").replace("-", "").replace(".", "");
        if (value.equals("item") || value.equals("items") || compact.endsWith("itemstack")) return "item";
        if (value.equals("fluid") || value.equals("fluids") || compact.endsWith("fluidstack")) return "fluid";
        if (value.equals("chemical") || value.equals("chemicals")
                || value.startsWith("mekanism.api.chemical.") && compact.endsWith("stack")) return "chemical";
        return "";
    }
}
