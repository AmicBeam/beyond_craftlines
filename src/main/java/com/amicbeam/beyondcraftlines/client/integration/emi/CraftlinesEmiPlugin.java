package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Adds Craftlines entry points to EMI while retaining JEI as the recipe execution backend. */
@EmiEntrypoint
public final class CraftlinesEmiPlugin implements EmiPlugin
{
    private static final EmiStack ICON = EmiStack.of(new ItemStack(CraftlinesItems.NETWORK_LINKER.get()));

    @Override
    public void register(EmiRegistry registry)
    {
        registry.addRecipeDecorator((recipe, widgets) -> {
            RecipeTarget target = target(recipe);
            if (target == null) return;
            int x = widgets.getWidth() + 5;
            int y = Math.max(0, widgets.getHeight() - 18);
            widgets.addButton(x, y, 18, 18, 0, 0,
                    CraftlinesJeiPlugin::canOrderFromRecipeViewer,
                    (mouseX, mouseY, button) -> {
                        if (button == 0 && CraftlinesJeiPlugin.canOrderFromRecipeViewer())
                            CraftlinesJeiPlugin.orderPreferredTarget(target.output(), target.recipe());
                    });
            widgets.addDrawable(x + 1, y + 1, 16, 16,
                    (graphics, mouseX, mouseY, delta) -> ICON.render(graphics, 0, 0, delta));
            widgets.addTooltip(List.of(ClientTooltipComponent.create(Component.translatable(
                    "gui.beyond_craftlines.order_from_jei").getVisualOrderText())), x, y, 18, 18);
        });
    }

    private static RecipeTarget target(EmiRecipe recipe)
    {
        ResourceLocation rawId = recipe.getId();
        var backing = recipe.getBackingRecipe();
        ResourceLocation recipeId = backing != null || EmiRecipeId.isWrappedJei(rawId)
                ? EmiRecipeId.normalize(rawId) : null;
        if (recipeId == null) return null;
        List<EmiStack> outputs = recipe.getOutputs();
        for (EmiStack output : outputs)
        {
            ItemStack stack = output.getItemStack();
            if (stack.isEmpty()) continue;
            var amount = RecipeResourceResolver.fromStack(stack);
            if (amount != null) return new RecipeTarget(amount.key(), recipeId);
        }
        return null;
    }

    private record RecipeTarget(IStackKey<?> output, ResourceLocation recipe) {}
}
