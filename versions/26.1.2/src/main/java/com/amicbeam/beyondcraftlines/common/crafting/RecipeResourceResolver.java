package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final int MAX_RESOLUTION_KEYS = 4_096;
    private static final BoundedIdentityCache<IStackKey<?>,String> RESOLUTION_KEYS=new BoundedIdentityCache<>(MAX_RESOLUTION_KEYS);

    private RecipeResourceResolver() {}

    public static List<ResourceIngredient> ingredients(Recipe<?> recipe)
    { return CACHE.computeIfAbsent(recipe, RecipeResourceResolver::resolveSafely); }

    public static Set<String> inputGroups(Recipe<?> recipe)
    {
        return ingredients(recipe).stream().map(ResourceIngredient::inputGroup)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static List<ResourceIngredient> ingredientsForOutput(Recipe<?> recipe, IStackKey<?> output)
    {
        if (VirtualProvisionerRecipeRegistry.descriptor(recipe) != null) return ingredients(recipe);
        List<String> inputMethods = directionalInputMethodsForStackType(recipe, output);
        if (inputMethods.isEmpty()) inputMethods = directionalInputMethods(recipe,
                raw -> matchesOutputDirection(output, raw));
        return inputMethods.isEmpty() ? ingredients(recipe) : resolveSafely(recipe, inputMethods, false);
    }

    private static List<String> directionalInputMethodsForStackType(Recipe<?> recipe, IStackKey<?> output)
    {
        String id = output.getTypeId().toString();
        String path = output.getTypeId().getPath();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        RecipeIoProfileRegistry.directionRules(recipe).stream().filter(rule -> rule.matchesClass(recipe)
                && rule.matchesStackType(id, path)).forEach(rule -> result.addAll(rule.inputFields()));
        return List.copyOf(result);
    }

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
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (RecipeIoProfileRegistry.DirectionRule rule : RecipeIoProfileRegistry.directionRules(recipe))
            if (rule.matchesClass(recipe) && !rule.outputFields().isEmpty()
                    && RecipeOutputResolver.reflectiveOutputValues(recipe, rule.outputFields().stream().toList())
                    .stream().anyMatch(selectedOutput)) result.addAll(rule.inputFields());
        return List.copyOf(result);
    }

    public static void clearCache()
    {
        CACHE.clear();
        clearResolutionKeyCache();
    }

    public static void clearResolutionKeyCache(){RESOLUTION_KEYS.clear();}

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

    public static String resolutionKey(IStackKey<?> key){return RESOLUTION_KEYS.computeIfAbsent(key,RecipeResourceResolver::encodeResolutionKey);}

    static String uncachedResolutionKey(IStackKey<?> key)
    { return encodeResolutionKey(key); }

    private static String encodeResolutionKey(IStackKey<?> key)
    {
        String serialized;
        try
        {
            serialized = key.serializeNBT(com.wintercogs.beyonddimensions.util.RegistryAccessResolver.resolve())
                    .toString();
        }
        catch (LinkageError | RuntimeException ignored)
        { serialized = key.getSource() + "|" + key.hashCode(); }
        return identityKey(key.getTypeId().toString(), key.getModId(), key.getSource(), serialized);
    }

    static String identityKey(String type, String mod, Object source, String serialized)
    { return type + "|" + mod + "|" + source + "|" + sha256(serialized); }

    private static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException impossible)
        { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }

    private static List<ResourceIngredient> resolve(Recipe<?> recipe)
    {
        var virtual = VirtualProvisionerRecipeRegistry.descriptor(recipe);
        if (virtual != null)
        {
            List<ResourceIngredient> result = new ArrayList<>();
            for (int slot = 0; slot < virtual.inputs().size(); slot++)
            {
                var input = virtual.inputs().get(slot);
                result.add(new ResourceIngredient(slot, input.candidates(), null, input.inputGroup()));
            }
            return List.copyOf(result);
        }
        return resolve(recipe, RecipeIoProfileRegistry.inputMembers(recipe), true);
    }

    private static List<ResourceIngredient> resolveSafely(Recipe<?> recipe)
    {
        try
        { return resolve(recipe); }
        catch (LinkageError | RuntimeException ignored)
        { return List.of(); }
    }

    private static List<ResourceIngredient> resolveSafely(Recipe<?> recipe, List<String> inputMethods,
                                                          boolean includeVanillaIngredients)
    {
        try
        { return resolve(recipe, inputMethods, includeVanillaIngredients); }
        catch (LinkageError | RuntimeException ignored)
        { return List.of(); }
    }

    private static List<ResourceIngredient> resolve(Recipe<?> recipe, List<String> inputMethods,
                                                    boolean includeVanillaIngredients)
    {
        List<ResourceIngredient> result = new ArrayList<>();
        int slot = 0;
        if (includeVanillaIngredients)
            for (Ingredient ingredient : RecipeIngredientResolver.vanillaIngredients(recipe))
            {
                List<KeyAmount> candidates = new ArrayList<>();
                for (ItemStack stack : ingredient.items().map(ItemStack::new).toList())
                    if (!stack.isEmpty()) candidates.add(new KeyAmount(
                            new ItemStackKey(stack.copyWithCount(1)), Math.max(1, stack.getCount())));
                if (!candidates.isEmpty()) result.add(new ResourceIngredient(
                        slot, candidates, ingredient, VANILLA_INPUT_GROUP));
                slot++;
            }

        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> canonicalSignatures = new java.util.HashSet<>();
        result.forEach(value -> canonicalSignatures.add(signature(value.candidates())));

        // Fill slots omitted by placementInfo() from public third-party input accessors.
        // Numeric energy metadata is deliberately not among the accessor names.
        for (String methodName : inputMethods)
        {
            RecipeIoProfileRegistry.InputCountSemantics countSemantics =
                    RecipeIoProfileRegistry.inputCountSemantics(recipe, methodName);
            boolean distinctInput = RecipeIoProfileRegistry.distinctInputMember(recipe, methodName);
            Object rawInput = RecipeReflection.readPublicMember(recipe, methodName);
            for (Object input : CountedInputReflection.flatten(recipe, rawInput))
            {
                if (input == null || seen.contains(input)) continue;
                CountedInputReflection.Value reflected = CountedInputReflection.read(recipe, input);
                Object ingredientSource = reflected == null ? input : reflected.ingredient();
                long multiplier = SaturatingLongMath.multiply(
                        reflected == null ? 1 : reflected.count(),
                        CountedInputReflection.recipeInputMultiplier(recipe, methodName));

                if (ingredientSource instanceof Ingredient ingredient)
                {
                    // Custom ingredients may keep vanilla storage empty while overriding
                    // getItems() with their real candidates. Trust those candidates directly.
                    List<KeyAmount> candidates = itemCandidates(ingredient, multiplier, countSemantics);
                    if (candidates.isEmpty()) continue;
                    seen.add(input);
                    if (shouldSkipCanonicalInput(distinctInput, canonicalSignatures,
                            signature(candidates))) continue;
                    result.add(new ResourceIngredient(slot++, candidates, ingredient,
                            CountedInputReflection.inputGroup(methodName)));
                    continue;
                }

                List<?> values = CountedInputReflection.representationValues(recipe, ingredientSource);
                if (values.isEmpty()) continue;
                LinkedHashMap<IStackKey<?>, KeyAmount> candidates = new LinkedHashMap<>();
                for (Object value : values)
                {
                    KeyAmount converted = fromStack(value);
                    if (converted == null) continue;
                    long amount = interpretedInputAmount(converted.amount(), multiplier, countSemantics);
                    if (amount > 0) candidates.putIfAbsent(converted.key(),
                            new KeyAmount(converted.key(), amount));
                }
                if (candidates.isEmpty()) continue;
                seen.add(input);
                if (shouldSkipCanonicalInput(distinctInput, canonicalSignatures,
                        signature(List.copyOf(candidates.values())))) continue;
                result.add(new ResourceIngredient(slot++, List.copyOf(candidates.values()), null,
                        CountedInputReflection.inputGroup(methodName)));
            }
        }
        return List.copyOf(result);
    }

    static boolean shouldSkipCanonicalInput(boolean distinctInput, Set<String> canonicalSignatures,
                                            String candidateSignature)
    { return !distinctInput && canonicalSignatures.contains(candidateSignature); }

    static long interpretedInputAmount(long representedAmount, long wrapperMultiplier,
                                       RecipeIoProfileRegistry.InputCountSemantics semantics)
    {
        if (semantics == RecipeIoProfileRegistry.InputCountSemantics.BATCH_LIMIT) return 1;
        return SaturatingLongMath.multiply(Math.max(1, representedAmount), Math.max(1, wrapperMultiplier));
    }

    private static List<KeyAmount> itemCandidates(Ingredient ingredient, long count,
                                                   RecipeIoProfileRegistry.InputCountSemantics semantics)
    {
        List<KeyAmount> candidates = new ArrayList<>();
        for (ItemStack stack : ingredient.items().map(ItemStack::new).toList())
            if (!stack.isEmpty()) candidates.add(new KeyAmount(
                    new ItemStackKey(stack.copyWithCount(1)),
                    interpretedInputAmount(stack.getCount(), count, semantics)));
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
