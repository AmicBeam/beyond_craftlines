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

/** Resolves item and non-item recipe inputs into Beyond Dimensions' native resource keys. */
public final class RecipeResourceResolver
{
    private static final List<String> INPUT_METHODS = List.of(
            "getInput", "getMainInput", "getExtraInput", "getItemInput",
            "getInputA", "getInputB", "getInputOne", "getInputTwo",
            "getLeftInput", "getRightInput", "getPrimaryInput", "getSecondaryInput",
            "getInputSolid", "getInputFluid", "getInputChemical",
            "getFluidInput", "getChemicalInput", "getGasInput", "getInputGas");
    private static final Map<Recipe<?>, List<ResourceIngredient>> CACHE =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private RecipeResourceResolver() {}

    public static List<ResourceIngredient> ingredients(Recipe<?> recipe)
    { return CACHE.computeIfAbsent(recipe, RecipeResourceResolver::resolve); }

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
    {
        List<ResourceIngredient> result = new ArrayList<>();
        int slot = 0;
        for (Ingredient ingredient : recipe.getIngredients())
        {
            List<KeyAmount> candidates = new ArrayList<>();
            for (ItemStack stack : ingredient.getItems())
                if (!stack.isEmpty()) candidates.add(new KeyAmount(
                        new ItemStackKey(stack.copyWithCount(1)), Math.max(1, stack.getCount())));
            if (!candidates.isEmpty()) result.add(new ResourceIngredient(slot, candidates, ingredient));
            slot++;
        }

        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> signatures = new java.util.HashSet<>();
        result.forEach(value -> signatures.add(signature(value.candidates())));

        // Some data-driven machine recipes expose their item inputs as records such as
        // CountedIngredient(Ingredient ingredient, int count, ...), while intentionally
        // leaving Recipe#getIngredients() empty. Read that common shape without linking
        // Craftlines to the owning mod.
        for (String methodName : CountedInputReflection.INPUT_METHODS)
        {
            Object rawInput = invokeNoArgs(recipe, methodName);
            for (Object input : CountedInputReflection.flatten(rawInput))
            {
                if (input == null || seen.contains(input)) continue;
                CountedInputReflection.Value reflected = CountedInputReflection.read(input);
                Ingredient ingredient = reflected != null && reflected.ingredient() instanceof Ingredient value
                        ? value : null;
                if (ingredient == null || ingredient.isEmpty()) continue;
                seen.add(input);
                long count = reflected.count();
                List<KeyAmount> candidates = new ArrayList<>();
                for (ItemStack stack : ingredient.getItems())
                    if (!stack.isEmpty()) candidates.add(new KeyAmount(
                            new ItemStackKey(stack.copyWithCount(1)), count));
                if (candidates.isEmpty()) continue;
                // Keep equal-looking inputs as distinct slots: two separate counted inputs
                // may intentionally request the same ingredient twice.
                signatures.add(signature(candidates));
                result.add(new ResourceIngredient(slot++, List.copyOf(candidates), ingredient));
            }
        }

        for (String methodName : INPUT_METHODS)
        {
            Object input = invokeNoArgs(recipe, methodName);
            if (input == null || !seen.add(input) || input instanceof Ingredient) continue;
            Object representations = invokeNoArgs(input, "getRepresentations");
            if (!(representations instanceof List<?> values)) continue;
            LinkedHashMap<IStackKey<?>, KeyAmount> candidates = new LinkedHashMap<>();
            for (Object value : values)
            {
                KeyAmount converted = fromStack(value);
                if (converted != null) candidates.putIfAbsent(converted.key(), converted);
            }
            if (candidates.isEmpty()) continue;
            String signature = signature(List.copyOf(candidates.values()));
            if (signatures.add(signature)) result.add(new ResourceIngredient(slot++,
                    List.copyOf(candidates.values()), null));
        }
        return List.copyOf(result);
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
