package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.List;

/** Explicit proof that a native machine recipe has fixed item inputs and one guaranteed item output. */
public record NativeRecipeFallbackPolicy(String inputs, String output, String probability,
                                         List<String> emptySections)
{
    private static final NativeRecipeFallbackPolicy NONE = new NativeRecipeFallbackPolicy("", "", "", List.of());

    public static NativeRecipeFallbackPolicy parse(JsonObject object)
    {
        if (object == null) return NONE;
        var empty = object.getAsJsonArray("empty_sections");
        if (empty == null || empty.size() > 8) throw new IllegalArgumentException("invalid native fallback sections");
        return new NativeRecipeFallbackPolicy(member(object.get("required_inputs").getAsString()),
                member(object.get("single_output").getAsString()), member(object.get("probability").getAsString()),
                empty.asList().stream().map(value -> member(value.getAsString())).toList());
    }

    private static String member(String value)
    {
        if (!value.matches("[A-Za-z_$][A-Za-z0-9_$]{0,127}"))
            throw new IllegalArgumentException("invalid native fallback member");
        return value;
    }

    public boolean active() { return !inputs.isEmpty(); }

    public boolean matches(Object recipe)
    {
        if (!active()) return false;
        Object input = RecipeReflection.readPublicMember(recipe, inputs);
        Object result = RecipeReflection.readPublicMember(recipe, output);
        if (!(input instanceof Collection<?> values) || values.isEmpty() || values.size() > VirtualRecipeLimits.INPUTS
                || !(result instanceof Collection<?> outputs) || outputs.size() != 1) return false;
        for (String section : emptySections)
            if (!(RecipeReflection.readPublicMember(recipe, section) instanceof Collection<?> empty) || !empty.isEmpty())
                return false;
        return values.stream().allMatch(this::guaranteed) && outputs.stream().allMatch(this::guaranteed);
    }

    private boolean guaranteed(Object value)
    {
        Object chance = RecipeReflection.readPublicMember(value, probability);
        return chance instanceof Number number && number.doubleValue() == 1.0;
    }
}
