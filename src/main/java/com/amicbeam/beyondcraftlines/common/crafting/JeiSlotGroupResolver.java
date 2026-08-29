package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Infers stable input groups from one materialized JEI layout without mod or category rules. */
public final class JeiSlotGroupResolver
{
    private JeiSlotGroupResolver() {}

    public static List<String> resolve(List<Slot> slots)
    {
        List<String> groups = new java.util.ArrayList<>(java.util.Collections.nCopies(
                slots.size(), RecipeResourceResolver.VANILLA_INPUT_GROUP));
        List<Integer> unnamed = new java.util.ArrayList<>();
        for (int index = 0; index < slots.size(); index++)
        {
            Slot slot = slots.get(index);
            if (slot.rawName() == null || slot.rawName().isBlank()) unnamed.add(index);
            else groups.set(index, JeiSlotInputGroup.fromSlotName(slot.rawName()));
        }
        if (unnamed.size() < 2) return List.copyOf(groups);

        Set<String> types = new LinkedHashSet<>();
        for (int index : unnamed)
        {
            String type = typeGroup(slots.get(index).ingredientTypes());
            if (!type.isBlank()) types.add(type);
        }
        if (types.size() > 1)
        {
            for (int index : unnamed)
            {
                String type = typeGroup(slots.get(index).ingredientTypes());
                if (!type.isBlank()) groups.set(index, "input_" + type);
            }
            return List.copyOf(groups);
        }

        return List.copyOf(groups);
    }

    private static String typeGroup(Set<String> ingredientTypes)
    {
        if (ingredientTypes == null || ingredientTypes.size() != 1) return "";
        String uid = ingredientTypes.iterator().next();
        int separator = uid.indexOf(':');
        String path = separator >= 0 ? uid.substring(separator + 1) : uid;
        String value = path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("^[_\u002e-]+|[_\u002e-]+$", "");
        return value.substring(0, Math.min(48, value.length()));
    }

    public record Slot(String rawName, Set<String> ingredientTypes)
    {
        public Slot
        {
            ingredientTypes = ingredientTypes == null ? Set.of() : Set.copyOf(ingredientTypes);
        }
    }
}
