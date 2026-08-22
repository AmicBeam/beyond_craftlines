package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

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
    public static final String VANILLA_INPUT_GROUP = "ingredients";
    private static final Map<Recipe<?>, List<ResourceIngredient>> CACHE =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private RecipeResourceResolver() {}

    public static List<ResourceIngredient> ingredients(Recipe<?> recipe)
    { return CACHE.computeIfAbsent(recipe, RecipeResourceResolver::resolve); }

    public static Set<String> inputGroups(Recipe<?> recipe)
    {
        return ingredients(recipe).stream().map(ResourceIngredient::inputGroup)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static List<ResourceIngredient> ingredientsForOutput(Recipe<?> recipe, IStackKey<?> output)
    {
        List<String> inputMethods = specializedDirectionalInputMethods(recipe, output);
        if (inputMethods.isEmpty()) inputMethods = directionalInputMethods(recipe,
                raw -> matchesOutputDirection(output, raw));
        return inputMethods.isEmpty() ? ingredients(recipe) : resolve(recipe, inputMethods, false);
    }

    private static List<String> specializedDirectionalInputMethods(Recipe<?> recipe, IStackKey<?> output)
    {
        String stackTypePath = output.getTypeId().getPath();
        for (Class<?> type = recipe.getClass(); type != null; type = type.getSuperclass())
        {
            List<String> methods = mekanismInputMethods(type.getName(), stackTypePath);
            if (!methods.isEmpty()) return methods;
        }
        return List.of();
    }

    static List<String> mekanismInputMethods(String recipeClassName, String stackTypePath)
    {
        if ("mekanism.api.recipes.ItemStackChemicalToObjectRecipe".equals(recipeClassName)
                || "mekanism.api.recipes.chemical.ItemStackChemicalToItemStackRecipe".equals(recipeClassName)
                || "mekanism.api.recipes.ItemStackGasToItemStackRecipe".equals(recipeClassName))
            return List.of("getItemInput", "getChemicalInput");
        if (!"mekanism.api.recipes.RotaryRecipe".equals(recipeClassName)) return List.of();
        if ("stack_type/fluid".equals(stackTypePath))
            return List.of("getGasInput", "getChemicalInput");
        if ("stack_type/chemical".equals(stackTypePath)
                || stackTypePath.startsWith("stack_type/chemicals/"))
            return List.of("getFluidInput");
        return List.of();
    }

    static List<String> mekanismRotaryInputMethods(String recipeClassName, String stackTypePath)
    { return mekanismInputMethods(recipeClassName, stackTypePath); }

    static boolean matchesOutputDirection(IStackKey<?> selectedOutput, Object rawOutput)
    {
        KeyAmount converted = fromStack(rawOutput);
        if (converted == null) return false;
        IStackKey<?> candidate = converted.key();
        // Prefer exact resource semantics. The type fallback is intentional: some
        // external keys retain registry-holder identity, so a key reconstructed from
        // the network may not compare equal to the same locally enumerated resource.
        // Directional output groups (for example chemical versus fluid) still have
        // distinct stack type IDs and therefore remain unambiguous.
        return selectedOutput.isSame(candidate) || candidate.isSame(selectedOutput)
                || selectedOutput.getTypeId().equals(candidate.getTypeId());
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
            for (Ingredient ingredient : recipe.getIngredients())
            {
                List<KeyAmount> candidates = new ArrayList<>();
                for (ItemStack stack : ingredient.getItems())
                    if (!stack.isEmpty()) candidates.add(new KeyAmount(
                            new ItemStackKey(stack.copyWithCount(1)), Math.max(1, stack.getCount())));
                if (!candidates.isEmpty()) result.add(new ResourceIngredient(
                        slot, candidates, ingredient, VANILLA_INPUT_GROUP));
                slot++;
            }

        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> canonicalSignatures = new java.util.HashSet<>();
        result.forEach(value -> canonicalSignatures.add(signature(value.candidates())));

        // Some data-driven machine recipes expose their item inputs as records such as
        // CountedIngredient(Ingredient ingredient, int count, ...), while intentionally
        // leaving Recipe#getIngredients() empty. Read that common shape without linking
        // Craftlines to the owning mod.
        for (String methodName : inputMethods)
        {
            Object rawInput = RecipeReflection.readPublicMember(recipe, methodName);
            for (Object input : CountedInputReflection.flatten(rawInput))
            {
                if (input == null || seen.contains(input)) continue;
                CountedInputReflection.Value reflected = CountedInputReflection.read(input);
                Object ingredientSource = reflected == null ? input : reflected.ingredient();
                long multiplier = SaturatingLongMath.multiply(
                        reflected == null ? 1 : reflected.count(),
                        CountedInputReflection.recipeInputMultiplier(recipe, methodName));

                if (ingredientSource instanceof Ingredient ingredient)
                {
                    if (ingredient.isEmpty()) continue;
                    List<KeyAmount> candidates = itemCandidates(ingredient, multiplier);
                    if (candidates.isEmpty()) continue;
                    seen.add(input);
                    // Recipe#getIngredients() is authoritative when it already exposes this
                    // slot. Reflection only fills inputs omitted by the vanilla recipe API.
                    if (canonicalSignatures.contains(signature(candidates))) continue;
                    result.add(new ResourceIngredient(slot++, candidates, ingredient,
                            CountedInputReflection.inputGroup(methodName)));
                    continue;
                }

                List<?> values = CountedInputReflection.representationValues(ingredientSource);
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
                // Distinct but equal inputs remain distinct slots. Identity de-duplication
                // above only removes aliases that expose the same input object twice.
                if (canonicalSignatures.contains(signature(List.copyOf(candidates.values())))) continue;
                result.add(new ResourceIngredient(slot++, List.copyOf(candidates.values()), null,
                        CountedInputReflection.inputGroup(methodName)));
            }
        }
        return List.copyOf(result);
    }

    private static List<KeyAmount> itemCandidates(Ingredient ingredient, long count)
    {
        List<KeyAmount> candidates = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems())
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

    public record ResourceIngredient(int slot, List<KeyAmount> candidates, Ingredient itemIngredient,
                                     String inputGroup)
    {
        public ResourceIngredient
        {
            if (slot < 0 || candidates == null || candidates.isEmpty()
                    || inputGroup == null || inputGroup.isBlank())
                throw new IllegalArgumentException("invalid resource ingredient");
            candidates = List.copyOf(candidates);
        }
        public boolean isItem() { return itemIngredient != null; }
        public boolean hasOnlyItemCandidates()
        { return candidates.stream().allMatch(value -> value.key() instanceof ItemStackKey); }
    }
}
