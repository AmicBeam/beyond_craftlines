package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.client.ProvisionerConfigScreen;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.amicbeam.beyondcraftlines.common.data.ResourceFilter;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import java.util.ArrayList;
import java.util.List;

public final class ResourceFilterGhostIngredientHandler implements IGhostIngredientHandler<ProvisionerConfigScreen>
{
    @Override public <I> List<Target<I>> getTargetsTyped(ProvisionerConfigScreen screen,
            ITypedIngredient<I> ingredient, boolean start)
    {
        if (!screen.isFilterEditing()) return List.of();
        var value = RecipeResourceResolver.fromStack(ingredient.getIngredient());
        if (value == null || value.isEmpty()) return List.of();
        List<Target<I>> targets = new ArrayList<>();
        for (int slot = 0; slot < ResourceFilter.SIZE; slot++)
        {
            final int index = slot;
            targets.add(new Target<>() {
                @Override public net.minecraft.client.renderer.Rect2i getArea() { return screen.filterTargetArea(index); }
                @Override public void accept(I ignored) { screen.setFilterEntry(index, value.key()); }
            });
        }
        return targets;
    }
    @Override public void onComplete() {}
    @Override public boolean shouldHighlightTargets() { return true; }
}
