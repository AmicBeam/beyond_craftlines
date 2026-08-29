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
    private static final java.util.concurrent.atomic.AtomicLong GENERATION =
            new java.util.concurrent.atomic.AtomicLong();
    private static final Map<Identifier, RecipeHolder<?>> RECIPES = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75F, true)
            {
                @Override protected boolean removeEldestEntry(Map.Entry<Identifier, RecipeHolder<?>> eldest)
                { return size() > MAX_RECIPES; }
            });
    private static final Map<Recipe<?>, Descriptor> DESCRIPTORS = Collections.synchronizedMap(
            new java.util.WeakHashMap<>());

    private VirtualProvisionerRecipeRegistry() {}

    public static RecipeHolder<?> register(String family, IStackKey<?> output, long outputAmount,
                                           List<InputSlot> inputs)
    {
        Descriptor descriptor = new Descriptor(family, output, outputAmount, inputs);
        Identifier id = descriptor.id();
        RecipeHolder<?> existing = RECIPES.get(id);
        if (existing != null) return existing;
        Recipe<?> recipe = proxy(descriptor);
        RecipeHolder<?> holder = new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe);
        RECIPES.put(id, holder);
        DESCRIPTORS.put(recipe, descriptor);
        GENERATION.incrementAndGet();
        return holder;
    }

    public static Optional<RecipeHolder<?>> find(Identifier id)
    { return Optional.ofNullable(RECIPES.get(id)); }

    public static List<RecipeHolder<?>> recipes()
    { synchronized (RECIPES) { return List.copyOf(RECIPES.values()); } }

    public static Descriptor descriptor(Recipe<?> recipe)
    { return DESCRIPTORS.get(recipe); }

    /** Changes whenever the client-visible virtual recipe catalog may need a new index. */
    public static long generation() { return GENERATION.get(); }

    public static void clear()
    {
        RECIPES.clear();
        DESCRIPTORS.clear();
        GENERATION.incrementAndGet();
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

    public record InputSlot(String inputGroup, List<KeyAmount> candidates)
    {
        public InputSlot
        {
            if (!JeiSlotInputGroup.isValid(inputGroup) || candidates == null || candidates.isEmpty()
                    || candidates.size() > 64 || candidates.stream().anyMatch(value -> value == null
                    || value.isEmpty() || value.amount() < 1))
                throw new IllegalArgumentException("invalid virtual provisioner ingredient");
            candidates = List.copyOf(candidates);
        }
    }

    public record Descriptor(String family, IStackKey<?> output, long outputAmount,
                             List<InputSlot> inputs)
    {
        public Descriptor
        {
            if (Identifier.tryParse(family) == null || family.length() > 256
                    || output == null || output.isEmpty() || outputAmount < 1
                    || inputs == null || inputs.isEmpty() || inputs.size() > 32)
                throw new IllegalArgumentException("invalid virtual provisioner recipe");
            inputs = List.copyOf(inputs);
        }

        public Identifier id()
        {
            StringBuilder canonical = new StringBuilder(family).append('|')
                    .append(RecipeResourceResolver.sortKey(output)).append('@').append(outputAmount);
            for (InputSlot slot : inputs)
            {
                canonical.append('|').append(slot.inputGroup()).append(':');
                slot.candidates().stream().map(value -> RecipeResourceResolver.sortKey(value.key()) + '@' + value.amount())
                        .sorted().forEach(value -> canonical.append(value).append(','));
            }
            UUID uuid = UUID.nameUUIDFromBytes(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return Identifier.fromNamespaceAndPath("beyond_craftlines", "jei_virtual/" + uuid);
        }
    }
}
