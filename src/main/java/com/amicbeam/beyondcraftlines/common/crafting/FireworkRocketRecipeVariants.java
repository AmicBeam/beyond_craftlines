package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-derived ordinary rockets. No uploaded output template is ever executed. */
public final class FireworkRocketRecipeVariants
{
    private static final Map<RecipeHolder<?>, List<RecipeHolder<?>>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private FireworkRocketRecipeVariants() {}
    public static void clear() { CACHE.clear(); }

    public static List<RecipeHolder<?>> planningRecipes(Level level)
    {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : RecipePlanningService.allRecipes(level))
        {
            if (holder.value() instanceof FireworkRocketRecipe)
                result.addAll(variants(holder, level));
            else result.add(holder);
        }
        return List.copyOf(result);
    }

    public static List<RecipeHolder<?>> variants(RecipeHolder<?> holder, Level level)
    {
        if (!(holder.value() instanceof FireworkRocketRecipe source)) return List.of();
        return CACHE.computeIfAbsent(holder, ignored -> {
            List<RecipeHolder<?>> result = new ArrayList<>();
            for (int flight = 1; flight <= 3; flight++)
            {
                List<ItemStack> samples = samples(flight);
                CraftingInput input = CraftingInput.of(3, 3, samples);
                if (!source.matches(input, level)) continue;
                ItemStack output = source.assemble(input, level.registryAccess());
                if (output.isEmpty() || !output.is(Items.FIREWORK_ROCKET)) continue;
                ResourceLocation id = variantId(holder.id().toString(), flight);
                result.add(new RecipeHolder<>(id, new FireworkRocketCraftingVariant(source, output, flight)));
            }
            return List.copyOf(result);
        });
    }

    public static List<ItemStack> samples(int flight)
    {
        if (flight < 1 || flight > 3) throw new IllegalArgumentException("invalid rocket duration");
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        items.set(0, new ItemStack(Items.PAPER));
        for (int slot = 1; slot <= flight; slot++) items.set(slot, new ItemStack(Items.GUNPOWDER));
        return items;
    }

    public static ResourceLocation variantId(String source, int flight)
    {
        String identity = "firework-v1|" + source + "|" + flight;
        return ResourceLocation.fromNamespaceAndPath("beyond_craftlines", "crafting_variant/firework/"
                + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)));
    }

    public static Optional<RecipeHolder<?>> find(Level level, ResourceLocation id)
    {
        if (!id.getNamespace().equals("beyond_craftlines") || !id.getPath().startsWith("crafting_variant/firework/"))
            return Optional.empty();
        return RecipePlanningService.allRecipes(level).stream()
                .flatMap(holder -> variants(holder, level).stream()).filter(holder -> holder.id().equals(id)).findFirst();
    }

    /** Keep ordinary recipes on their existing path, including SlashBlade's dynamic-output profile. */
    public static ResourceLocation targetRecipeId(Level level, ResourceLocation sourceId, IStackKey<?> target)
    {
        if (sourceId == null) return null;
        if (!(target instanceof ItemStackKey item) || !item.getReadOnlyStack().is(Items.FIREWORK_ROCKET)) return sourceId;
        for (RecipeHolder<?> source : RecipePlanningService.allRecipes(level))
            if (source.id().equals(sourceId) && source.value() instanceof FireworkRocketRecipe)
                return variants(source, level).stream().filter(holder -> StackKeyMatch.exact(target,
                        new ItemStackKey(holder.value().getResultItem(level.registryAccess()))))
                        .map(RecipeHolder::id).findFirst().orElse(sourceId);
        return sourceId;
    }
}
