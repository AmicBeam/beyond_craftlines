package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Explicit probability accessors preserve stochastic outputs without promising a fixed yield. */
public final class RecipeOutputProbabilities
{
    private RecipeOutputProbabilities() {}

    public static Set<IStackKey<?>> uncertain(Object displayedRecipe)
    {
        Object value = RecipeReflection.readPublicMember(displayedRecipe, "value");
        Object recipe = value == null ? displayedRecipe : value;
        Set<IStackKey<?>> uncertain = new LinkedHashSet<>();
        for (Rule rule : RecipeIoProfileRegistry.outputProbabilityRules(recipe))
        {
            Object raw = RecipeReflection.readPublicMember(recipe, rule.outputs());
            if (!(raw instanceof Iterable<?> outputs))
                throw new IllegalArgumentException("probability profile output collection is unavailable");
            int count = 0;
            for (Object output : outputs)
            {
                if (++count > VirtualRecipeLimits.OUTPUTS)
                    throw new IllegalArgumentException("probability output budget exceeded");
                Object chance = RecipeReflection.readPublicMember(output, rule.chance());
                var stack = RecipeResourceResolver.fromStack(RecipeReflection.readPublicMember(output, rule.stack()));
                if (!(chance instanceof Number number) || stack == null || stack.isEmpty())
                    throw new IllegalArgumentException("probability profile cannot resolve output and chance");
                if (!guaranteed(number.doubleValue(), rule.scale())) uncertain.add(stack.key());
            }
        }
        return Set.copyOf(uncertain);
    }

    static boolean guaranteed(double chance, double scale)
    {
        if (!Double.isFinite(chance) || !Double.isFinite(scale) || scale <= 0 || chance < 0 || chance > scale)
            throw new IllegalArgumentException("invalid output probability");
        return chance == scale;
    }

    static List<Rule> parse(JsonArray array)
    {
        if (array == null) return List.of();
        if (array.size() > 16) throw new IllegalArgumentException("too many probability rules");
        List<Rule> rules = new ArrayList<>();
        for (var entry : array)
        {
            JsonObject object = entry.getAsJsonObject();
            Rule rule = new Rule(object.get("outputs").getAsString(), object.get("stack").getAsString(),
                    object.get("chance").getAsString(), object.get("scale").getAsDouble());
            for (String member : List.of(rule.outputs(), rule.stack(), rule.chance()))
                if (!member.matches("[A-Za-z_$][A-Za-z0-9_$]{0,127}"))
                    throw new IllegalArgumentException("invalid probability accessor");
            guaranteed(rule.scale(), rule.scale());
            rules.add(rule);
        }
        return List.copyOf(rules);
    }

    public record Rule(String outputs, String stack, String chance, double scale) {}
}
