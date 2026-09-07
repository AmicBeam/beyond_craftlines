package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded runtime recipes uploaded from JEI for vanilla categories with no server RecipeHolder. */
public final class VirtualProvisionerRecipeRegistry
{
    private static final int MAX_RECIPES = 16_384;
    private static final Map<Identifier, RecipeHolder<?>> RECIPES = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75F, true)
            {
                @Override protected boolean removeEldestEntry(Map.Entry<Identifier, RecipeHolder<?>> eldest)
                { return size() > MAX_RECIPES; }
            });
    private static final Map<Recipe<?>, Descriptor> DESCRIPTORS = Collections.synchronizedMap(
            new java.util.WeakHashMap<>());
    private static final java.util.concurrent.atomic.AtomicLong REVISION =
            new java.util.concurrent.atomic.AtomicLong();

    private VirtualProvisionerRecipeRegistry() {}

    public static RecipeHolder<?> register(String family, IStackKey<?> output, long outputAmount,
                                           List<InputSlot> inputs)
    {
        return register(family, output, outputAmount, inputs, List.of());
    }

    public static RecipeHolder<?> register(String family, IStackKey<?> output, long outputAmount,
                                           List<InputSlot> inputs, List<KeyAmount> byproducts)
    {
        return register(family, output, outputAmount, inputs, byproducts, byproducts);
    }

    public static RecipeHolder<?> register(String family, IStackKey<?> output, long outputAmount,
                                           List<InputSlot> inputs, List<KeyAmount> byproducts,
                                           List<KeyAmount> guaranteedByproducts)
    {
        return register(new Descriptor(family, output, outputAmount, inputs, byproducts, guaranteedByproducts));
    }

    public static RecipeHolder<?> register(Descriptor descriptor)
    {
        Identifier id = descriptor.id();
        RecipeHolder<?> existing = RECIPES.get(id);
        if (existing != null) return existing;
        Recipe<?> recipe = proxy(descriptor);
        RecipeHolder<?> holder = new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe);
        RECIPES.put(id, holder);
        DESCRIPTORS.put(recipe, descriptor);
        REVISION.incrementAndGet();
        return holder;
    }

    public static Optional<RecipeHolder<?>> find(Identifier id)
    { return Optional.ofNullable(RECIPES.get(id)); }

    public static List<RecipeHolder<?>> recipes()
    { synchronized (RECIPES) { return List.copyOf(RECIPES.values()); } }

    public static Descriptor descriptor(Recipe<?> recipe)
    { return DESCRIPTORS.get(recipe); }

    public static long revision() { return REVISION.get(); }

    public static void clear()
    {
        RECIPES.clear();
        DESCRIPTORS.clear();
        REVISION.incrementAndGet();
    }

    @SuppressWarnings("unchecked")
    private static Recipe<?> proxy(Descriptor descriptor)
    {
        return (Recipe<?>) Proxy.newProxyInstance(Recipe.class.getClassLoader(), new Class<?>[]{Recipe.class},
                (proxy, method, arguments) -> switch (method.getName())
                {
                    case "getIngredients" -> NonNullList.<Ingredient>create();
                    case "getResultItem", "assemble" -> outputStack(descriptor);
                    case "isSpecial" -> true;
                    case "getGroup" -> "";
                    case "toString" -> "VirtualProvisionerRecipe[" + descriptor.id() + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static ItemStack outputStack(Descriptor descriptor)
    {
        Object stack = descriptor.output().getReadOnlyStack();
        if (!(stack instanceof ItemStack item)) return ItemStack.EMPTY;
        return item.copyWithCount((int) Math.min(item.getMaxStackSize(), descriptor.outputAmount()));
    }

    private static Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive())
        {
            if (type.isEnum())
            {
                Object[] constants = type.getEnumConstants();
                return constants == null || constants.length == 0 ? null : constants[0];
            }
            return null;
        }
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    public record InputSlot(String inputGroup, List<KeyAmount> candidates, VirtualInputUse use)
    {
        public InputSlot(String inputGroup, List<KeyAmount> candidates)
        { this(inputGroup, candidates, VirtualInputUse.CONSUMED); }
        public InputSlot
        {
            if (!JeiSlotInputGroup.isValid(inputGroup) || candidates == null || candidates.isEmpty()
                    || candidates.size() > VirtualRecipeLimits.CANDIDATES || candidates.stream().anyMatch(value -> value == null
                    || value.isEmpty() || value.amount() < 1) || use == null)
                throw new IllegalArgumentException("invalid virtual provisioner ingredient");
            candidates = List.copyOf(candidates);
        }
    }

    public record Descriptor(String family, IStackKey<?> output, long outputAmount,
                             List<InputSlot> inputs, List<KeyAmount> byproducts, List<KeyAmount> guaranteedByproducts)
    {
        public Descriptor(String family, IStackKey<?> output, long outputAmount, List<InputSlot> inputs)
        { this(family, output, outputAmount, inputs, List.of(), List.of()); }

        public Descriptor
        {
            if (Identifier.tryParse(family) == null || family.length() > 256
                    || output == null || output.isEmpty() || outputAmount < 1
                    || inputs == null || inputs.isEmpty() || inputs.size() > VirtualRecipeLimits.INPUTS)
                throw new IllegalArgumentException("invalid virtual provisioner recipe");
            inputs = List.copyOf(inputs);
            VirtualRecipeLimits.requireInputs(inputs.size(), inputs.stream().mapToLong(slot -> slot.candidates().size()).sum());
            if (byproducts == null || byproducts.size() >= VirtualRecipeLimits.OUTPUTS
                    || byproducts.stream().anyMatch(value -> value == null || value.isEmpty()
                    || value.amount() < 1 || StackKeyMatch.exact(output, value.key())))
                throw new IllegalArgumentException("invalid virtual recipe byproducts");
            byproducts = List.copyOf(byproducts);
            guaranteedByproducts = List.copyOf(guaranteedByproducts);
            if (byproducts.stream().map(value -> RecipeResourceResolver.resolutionKey(value.key())).distinct().count()
                    != byproducts.size() || guaranteedByproducts.stream()
                    .map(value -> RecipeResourceResolver.resolutionKey(value.key())).distinct().count() != guaranteedByproducts.size())
                throw new IllegalArgumentException("duplicate virtual recipe byproduct");
            for (KeyAmount guaranteed : guaranteedByproducts)
                if (byproducts.stream().noneMatch(actual -> StackKeyMatch.exact(actual.key(), guaranteed.key())
                        && actual.amount() == guaranteed.amount()))
                    throw new IllegalArgumentException("guaranteed output is not a declared byproduct");
        }

        public Identifier id()
        {
            StringBuilder canonical = new StringBuilder(family).append('|')
                    .append(RecipeResourceResolver.resolutionKey(output)).append('@').append(outputAmount);
            for (InputSlot slot : inputs)
            {
                canonical.append('|').append(slot.inputGroup()).append(':')
                        .append(slot.use().kind()).append('@').append(slot.use().damagePerCraft()).append(':');
                slot.candidates().stream().map(value -> RecipeResourceResolver.resolutionKey(value.key()) + '@' + value.amount())
                        .sorted().forEach(value -> canonical.append(value).append(','));
            }
            byproducts.stream().map(value -> RecipeResourceResolver.resolutionKey(value.key()) + '@' + value.amount())
                    .sorted().forEach(value -> canonical.append("|byproduct:").append(value));
            guaranteedByproducts.stream().map(value -> RecipeResourceResolver.resolutionKey(value.key()) + '@' + value.amount())
                    .sorted().forEach(value -> canonical.append("|guaranteed:").append(value));
            UUID uuid = UUID.nameUUIDFromBytes(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return Identifier.fromNamespaceAndPath("beyond_craftlines", "jei_virtual/" + uuid);
        }
    }
}
