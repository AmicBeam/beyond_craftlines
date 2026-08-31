package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Direct EMI access isolated behind {@link EmiOptionalIntegration}. */
public final class EmiClientIntegration
{
    private static volatile Object metadataManager;
    private static volatile Map<net.minecraft.world.item.Item, Set<ResourceLocation>> typesByWorkstation
            = Map.of();
    private static volatile Map<ResourceLocation, Component> titlesByType = Map.of();

    private EmiClientIntegration() {}

    public static boolean orderIngredientUnderMouse(double mouseX, double mouseY)
    {
        var interaction = EmiApi.getHoveredStack(
                (int) Math.floor(mouseX), (int) Math.floor(mouseY), false);
        if (interaction == null || interaction.isEmpty() || !interaction.isClickable()) return false;
        for (EmiStack value : interaction.getStack().getEmiStacks())
        {
            ItemStack stack = value.getItemStack();
            if (stack.isEmpty()) continue;
            CraftlinesJeiPlugin.orderTarget(new ItemStackKey(stack.copyWithCount(1)));
            return true;
        }
        return false;
    }

    /** EMI finalizes JEMI categories after JEI's runtime callback, so read workstation metadata lazily. */
    public static Set<ResourceLocation> recipeTypesFor(ItemStack workstation)
    {
        if (workstation.isEmpty()) return Set.of();
        refreshMetadata();
        return typesByWorkstation.getOrDefault(workstation.getItem(), Set.of());
    }

    public static Optional<Component> recipeTypeTitle(ResourceLocation type)
    {
        refreshMetadata();
        return Optional.ofNullable(titlesByType.get(type));
    }

    public static Set<ResourceLocation> recipeTypes()
    {
        refreshMetadata();
        return titlesByType.keySet();
    }

    private static void refreshMetadata()
    {
        var manager = EmiApi.getRecipeManager();
        if (manager == metadataManager) return;
        synchronized (EmiClientIntegration.class)
        {
            if (manager == metadataManager) return;
            Map<net.minecraft.world.item.Item, LinkedHashSet<ResourceLocation>> workstations = new HashMap<>();
            Map<ResourceLocation, Component> titles = new HashMap<>();
            for (var category : manager.getCategories())
            {
                ResourceLocation type = category.getId();
                if (type == null) continue;
                titles.put(type, category.getName());
                for (EmiIngredient ingredient : manager.getWorkstations(category))
                    for (EmiStack value : ingredient.getEmiStacks())
                    {
                        ItemStack stack = value.getItemStack();
                        if (!stack.isEmpty()) workstations.computeIfAbsent(stack.getItem(), ignored ->
                                new LinkedHashSet<>()).add(type);
                    }
            }
            Map<net.minecraft.world.item.Item, Set<ResourceLocation>> frozen = new HashMap<>();
            workstations.forEach((item, types) -> frozen.put(item, Set.copyOf(types)));
            typesByWorkstation = Map.copyOf(frozen);
            titlesByType = Map.copyOf(titles);
            metadataManager = manager;
        }
    }

    public static @Nullable ResourceLocation preferredRecipe(IStackKey<?> target)
    {
        if (!(target instanceof ItemStackKey item)) return null;
        EmiIngredient ingredient = EmiStack.of(item.getReadOnlyStack().copyWithCount(1));
        try
        {
            Class<?> bom = Class.forName("dev.emi.emi.bom.BoM", false,
                    EmiClientIntegration.class.getClassLoader());
            Method method = bom.getMethod("getRecipe", EmiIngredient.class);
            Object value = method.invoke(null, ingredient);
            return value instanceof EmiRecipe recipe ? EmiRecipeId.normalize(recipe.getId()) : null;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }
}
