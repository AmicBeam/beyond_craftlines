package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

/** Keeps EMI optional: no EMI class is linked until its mod is confirmed loaded. */
public final class EmiOptionalIntegration
{
    private static final String CLIENT =
            "com.amicbeam.beyondcraftlines.client.integration.emi.EmiClientIntegration";
    private static volatile boolean initialized;
    private static Method orderHovered;
    private static Method preferredRecipe;
    private static Method recipeTypesFor;
    private static Method recipeTypeTitle;
    private static Method recipeTypes;
    private static Method setPreferredRecipe;
    private static Method clearPreferredRecipe;

    private EmiOptionalIntegration() {}

    public static boolean orderIngredientUnderMouse(double mouseX, double mouseY)
    {
        initialize();
        if (orderHovered == null) return false;
        try { return Boolean.TRUE.equals(orderHovered.invoke(null, mouseX, mouseY)); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return false; }
    }

    public static @Nullable ResourceLocation preferredRecipe(IStackKey<?> target)
    {
        initialize();
        if (preferredRecipe == null || target == null || target.isEmpty()) return null;
        try
        {
            Object value = preferredRecipe.invoke(null, target);
            return value instanceof ResourceLocation id ? id : null;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }

    public static boolean setPreferredRecipe(IStackKey<?> target, ResourceLocation recipe)
    {
        initialize();
        if (setPreferredRecipe == null || target == null || target.isEmpty() || recipe == null) return false;
        try { return Boolean.TRUE.equals(setPreferredRecipe.invoke(null, target, recipe)); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return false; }
    }

    public static boolean clearPreferredRecipe(IStackKey<?> target, ResourceLocation recipe)
    {
        initialize();
        if (clearPreferredRecipe == null || target == null || target.isEmpty() || recipe == null) return false;
        try { return Boolean.TRUE.equals(clearPreferredRecipe.invoke(null, target, recipe)); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return false; }
    }

    @SuppressWarnings("unchecked")
    public static Set<ResourceLocation> recipeTypesFor(net.minecraft.world.item.ItemStack workstation)
    {
        initialize();
        if (recipeTypesFor == null || workstation == null || workstation.isEmpty()) return Set.of();
        try
        {
            Object value = recipeTypesFor.invoke(null, workstation);
            return value instanceof Set<?> types ? (Set<ResourceLocation>) types : Set.of();
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return Set.of(); }
    }

    @SuppressWarnings("unchecked")
    public static Optional<net.minecraft.network.chat.Component> recipeTypeTitle(ResourceLocation type)
    {
        initialize();
        if (recipeTypeTitle == null || type == null) return Optional.empty();
        try
        {
            Object value = recipeTypeTitle.invoke(null, type);
            return value instanceof Optional<?> title
                    ? (Optional<net.minecraft.network.chat.Component>) title : Optional.empty();
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
        { return Optional.empty(); }
    }

    @SuppressWarnings("unchecked")
    public static Set<ResourceLocation> recipeTypes()
    {
        initialize();
        if (recipeTypes == null) return Set.of();
        try
        {
            Object value = recipeTypes.invoke(null);
            return value instanceof Set<?> types ? (Set<ResourceLocation>) types : Set.of();
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return Set.of(); }
    }

    private static synchronized void initialize()
    {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("emi")) return;
        try
        {
            Class<?> type = Class.forName(CLIENT, false, EmiOptionalIntegration.class.getClassLoader());
            orderHovered = type.getMethod("orderIngredientUnderMouse", double.class, double.class);
            preferredRecipe = type.getMethod("preferredRecipe", IStackKey.class);
            recipeTypesFor = type.getMethod("recipeTypesFor", net.minecraft.world.item.ItemStack.class);
            recipeTypeTitle = type.getMethod("recipeTypeTitle", ResourceLocation.class);
            recipeTypes = type.getMethod("recipeTypes");
            setPreferredRecipe = type.getMethod("setPreferredRecipe", IStackKey.class, ResourceLocation.class);
            clearPreferredRecipe = type.getMethod(
                    "clearPreferredRecipe", IStackKey.class, ResourceLocation.class);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
        {
            orderHovered = null;
            preferredRecipe = null;
            recipeTypesFor = null;
            recipeTypeTitle = null;
            recipeTypes = null;
            setPreferredRecipe = null;
            clearPreferredRecipe = null;
        }
    }
}
