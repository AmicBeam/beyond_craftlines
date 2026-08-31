package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/** Direct EMI access isolated behind {@link EmiOptionalIntegration}. */
public final class EmiClientIntegration
{
    private EmiClientIntegration() {}

    public static boolean orderIngredientUnderMouse()
    {
        var interaction = EmiApi.getHoveredStack(false);
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
