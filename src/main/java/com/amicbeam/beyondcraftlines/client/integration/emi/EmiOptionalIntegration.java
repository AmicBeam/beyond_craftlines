package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/** Keeps EMI optional: no EMI class is linked until its mod is confirmed loaded. */
public final class EmiOptionalIntegration
{
    private static final String CLIENT =
            "com.amicbeam.beyondcraftlines.client.integration.emi.EmiClientIntegration";
    private static volatile boolean initialized;
    private static Method orderHovered;
    private static Method preferredRecipe;

    private EmiOptionalIntegration() {}

    public static boolean orderIngredientUnderMouse()
    {
        initialize();
        if (orderHovered == null) return false;
        try { return Boolean.TRUE.equals(orderHovered.invoke(null)); }
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

    private static synchronized void initialize()
    {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("emi")) return;
        try
        {
            Class<?> type = Class.forName(CLIENT, false, EmiOptionalIntegration.class.getClassLoader());
            orderHovered = type.getMethod("orderIngredientUnderMouse");
            preferredRecipe = type.getMethod("preferredRecipe", IStackKey.class);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
        {
            orderHovered = null;
            preferredRecipe = null;
        }
    }
}
