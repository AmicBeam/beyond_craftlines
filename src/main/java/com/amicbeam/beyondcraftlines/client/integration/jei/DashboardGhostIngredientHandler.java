package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.client.DashboardConfigScreen;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.List;

/** Generic JEI ghost target: BD's stack-key registry decides item/fluid/chemical support. */
public final class DashboardGhostIngredientHandler implements IGhostIngredientHandler<DashboardConfigScreen>
{
    @Override public <I> List<Target<I>> getTargetsTyped(DashboardConfigScreen screen,
                                                         ITypedIngredient<I> ingredient,
                                                         boolean doStart)
    {
        var converted = RecipeResourceResolver.fromStack(ingredient.getIngredient());
        if (converted == null || converted.isEmpty()) return List.of();
        return List.of(new Target<>()
        {
            @Override public net.minecraft.client.renderer.Rect2i getArea()
            { return screen.ghostTargetArea(); }

            @Override public void accept(I ignored)
            { screen.setGhostTarget(converted.key()); }
        });
    }

    @Override public void onComplete() {}
    @Override public boolean shouldHighlightTargets() { return true; }
}
