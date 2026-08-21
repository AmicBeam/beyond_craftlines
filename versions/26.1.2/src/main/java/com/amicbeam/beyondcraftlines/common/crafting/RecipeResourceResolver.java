package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Resolves item and non-item recipe inputs into Beyond Dimensions' native resource keys. */
public final class RecipeResourceResolver
{
    private static final Map<Recipe<?>, List<ResourceIngredient>> CACHE =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private RecipeResourceResolver() {}

    public static List<ResourceIngredient> ingredients(Recipe<?> recipe)
    { return CACHE.computeIfAbsent(recipe, RecipeResourceResolver::resolve); }

    public static List<ResourceIngredient> ingredientsForOutput(Recipe<?> recipe, IStackKey<?> output)
    {
        List<String> inputMethods = directionalInputMethods(recipe, raw -> {
            KeyAmount converted = fromStack(raw);
            return converted != null && output.isSame(converted.key());
        });
        return inputMethods.isEmpty() ? ingredients(recipe) : resolve(recipe, inputMethods, false);
    }

    static List<String> directionalInputMethods(Object recipe, Predicate<Object> selectedOutput)
    {
        if (RecipeOutputResolver.reflectiveOutputValues(recipe,
                List.of("getGasOutputDefinition", "getChemicalOutputDefinition"))
                .stream().anyMatch(selectedOutput)) return List.of("getFluidInput");
        if (RecipeOutputResolver.reflectiveOutputValues(recipe, List.of("getFluidOutputDefinition"))
                .stream().anyMatch(selectedOutput)) return List.of("getGasInput", "getChemicalInput");
        return List.of();
    }

    public static void clearCache() { CACHE.clear(); }

    public static KeyAmount fromStack(Object stack)
    {
        if (stack == null) return null;
        for (IStackKey<?> prototype : StackKeyRegistry.getAllTypes())
        {
            try
            {
                KeyAmount value = prototype.fromStackObject(stack);
                if (value != null && !value.isEmpty() && value.amount() > 0) return value;
            }
            catch (LinkageError | RuntimeException ignored) {}
        }
        return null;
    }

    public static String sortKey(IStackKey<?> key)
    { return key.getTypeId() + "|" + key.getModId() + "|" + key.getSource(); }

    private static List<ResourceIngredient> resolve(Recipe<?> recipe)
    { return resolve(recipe, CountedInputReflection.INPUT_METHODS, true); }

    private static List<ResourceIngredient> resolve(Recipe<?> recipe, List<String> inputMethods,
                                                    boolean includeVanillaIngredients)
    {
        List<ResourceIngredient> result = new ArrayList<>();
        int slot = 0;
        if (includeVanillaIngredients)
            for (Ingredient ingredient : recipe.placementInfo().ingredients())
            {
                List<KeyAmount> candidates = new ArrayList<>();
                for (ItemStack stack : ingredient.items().map(ItemStack::new).toList())
                    if (!stack.isEmpty()) candidates.add(new KeyAmount(
                            new ItemStackKey(stack.copyWithCount(1)), Math.max(1, stack.getCount())));
                if (!candidates.isEmpty()) result.add(new ResourceIngredient(slot, candidates, ingredient));
                slot++;
            }

        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> canonicalSignatures = new java.util.HashSet<>();
        result.forEach(value -> canonicalSignatures.add(signature(value.candidates())));

        // Fill slots omitted by placementInfo() from public third-party input accessors.
        // Numeric energy metadata is deliberately not among the accessor names.
        for (String methodName : inputMethods)
        {
            Object rawInput = invokeNoArgs(recipe, methodName);
            for (Object input : CountedInputReflection.flatten(rawInput))
            {
                if (input == null || seen.contains(input)) continue;
                CountedInputReflection.Value reflected = CountedInputReflection.read(input);
                Object ingredientSource = reflected == null ? input : reflected.ingredient();
                long multiplier = reflected == null ? 1 : reflected.count();

                if (ingredientSource instanceof Ingredient ingredient)
                {
                    if (ingredient.isEmpty()) continue;
                    List<KeyAmount> candidates = itemCandidates(ingredient, multiplier);
                    if (candidates.isEmpty()) continue;
                    seen.add(input);
                    if (canonicalSignatures.contains(signature(candidates))) continue;
                    result.add(new ResourceIngredient(slot++, candidates, ingredient));
                    continue;
                }

                Object representations = invokeNoArgs(ingredientSource, "getRepresentations");
                List<?> values = CountedInputReflection.flatten(representations);
                if (values.isEmpty()) continue;
                LinkedHashMap<IStackKey<?>, KeyAmount> candidates = new LinkedHashMap<>();
                for (Object value : values)
                {
                    KeyAmount converted = fromStack(value);
                    if (converted == null) continue;
                    long amount = SaturatingLongMath.multiply(converted.amount(), multiplier);
                    if (amount > 0) candidates.putIfAbsent(converted.key(),
                            new KeyAmount(converted.key(), amount));
                }
                if (candidates.isEmpty()) continue;
                seen.add(input);
                if (canonicalSignatures.contains(signature(List.copyOf(candidates.values())))) continue;
                result.add(new ResourceIngredient(slot++, List.copyOf(candidates.values()), null));
            }
        }
        return List.copyOf(result);
    }

    private static List<KeyAmount> itemCandidates(Ingredient ingredient, long count)
    {
        List<KeyAmount> candidates = new ArrayList<>();
        for (ItemStack stack : ingredient.items().map(ItemStack::new).toList())
            if (!stack.isEmpty()) candidates.add(new KeyAmount(
                    new ItemStackKey(stack.copyWithCount(1)),
                    SaturatingLongMath.multiply(Math.max(1, stack.getCount()), count)));
        return List.copyOf(candidates);
    }

    private static String signature(List<KeyAmount> candidates)
    {
        return candidates.stream().map(value -> sortKey(value.key()) + "@" + value.amount())
                .sorted().collect(java.util.stream.Collectors.joining(","));
    }

    private static Object invokeNoArgs(Object target, String name)
    {
        try
        {
            Method method = target.getClass().getMethod(name);
            if (method.getParameterCount() != 0) return null;
            if (!method.canAccess(target) && !method.trySetAccessible()) return null;
            return method.invoke(target);
        }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    public record ResourceIngredient(int slot, List<KeyAmount> candidates, Ingredient itemIngredient)
    {
        public ResourceIngredient
        {
            if (slot < 0 || candidates == null || candidates.isEmpty())
                throw new IllegalArgumentException("invalid resource ingredient");
            candidates = List.copyOf(candidates);
        }
        public boolean isItem() { return itemIngredient != null; }
        public boolean hasOnlyItemCandidates()
        { return candidates.stream().allMatch(value -> value.key() instanceof ItemStackKey); }
    }
}
