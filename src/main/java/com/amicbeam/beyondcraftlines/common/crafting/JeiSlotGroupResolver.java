package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.ArrayList;
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
        List<String> groups = new ArrayList<>(java.util.Collections.nCopies(
                slots.size(), RecipeResourceResolver.VANILLA_INPUT_GROUP));
        List<Integer> unnamed = new ArrayList<>();
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

        List<Integer> columns = unnamed.stream().map(index -> slots.get(index).x())
                .filter(java.util.Objects::nonNull).distinct().sorted().toList();
        if (columns.size() < 2) return List.copyOf(groups);
        for (int index : unnamed)
        {
            Integer x = slots.get(index).x();
            if (x == null) continue;
            int column = columns.indexOf(x);
            groups.set(index, columns.size() == 2 ? (column == 0 ? "input_left" : "input_right")
                    : columns.size() == 3 ? switch (column)
                    {
                        case 0 -> "input_left";
                        case 1 -> "input_center";
                        default -> "input_right";
                    } : "input_column_" + (column + 1));
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

    public record Slot(String rawName, Set<String> ingredientTypes, Integer x)
    {
        public Slot
        {
            ingredientTypes = ingredientTypes == null ? Set.of() : Set.copyOf(ingredientTypes);
        }
    }
}
