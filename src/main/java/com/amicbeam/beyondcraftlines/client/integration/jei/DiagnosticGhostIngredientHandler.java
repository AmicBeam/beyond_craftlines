package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.client.CraftlineOrderScreen;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.List;

/** Generic JEI ghost target: BD's stack-key registry decides item/fluid/chemical support. */
public final class DiagnosticGhostIngredientHandler implements IGhostIngredientHandler<CraftlineOrderScreen>
{
    @Override public <I> List<Target<I>> getTargetsTyped(CraftlineOrderScreen screen,
                                                         ITypedIngredient<I> ingredient,
                                                         boolean doStart)
    {
        if (!screen.acceptsDiagnosticTarget()) return List.of();
        var converted = RecipeResourceResolver.fromStack(ingredient.getIngredient());
        if (converted == null || converted.isEmpty()) return List.of();
        return List.of(new Target<>()
        {
            @Override public net.minecraft.client.renderer.Rect2i getArea()
            { return screen.diagnosticTargetArea(); }

            @Override public void accept(I ignored)
            { screen.setDiagnosticSample(converted.key()); }
        });
    }

    @Override public void onComplete() {}
    @Override public boolean shouldHighlightTargets() { return true; }
}
