package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.JeiSlotInputGroup;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Captures bounded, execution-ready virtual recipes directly from rendered JEI layouts. */
public final class JeiVirtualRecipeLayouts
{
    private JeiVirtualRecipeLayouts() {}
    public static Captured capture(Identifier type, IRecipeLayoutDrawable<?> layout)
    {
        KeyAmount output = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> slot.getAllIngredients())
                .map(typed -> RecipeResourceResolver.fromStack(typed.getIngredient()))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (output == null) return null;
        List<OpenOrderMenuPayload.VirtualInput> inputs = layout.getRecipeSlotsView()
                .getSlotViews(RecipeIngredientRole.INPUT).stream()
                .map(slot -> new OpenOrderMenuPayload.VirtualInput(
                        JeiSlotInputGroup.fromSlotName(slot.getSlotName().orElse("")),
                        slot.getAllIngredients().map(typed -> RecipeResourceResolver.fromStack(
                                        typed.getIngredient())).filter(java.util.Objects::nonNull)
                                .distinct().limit(64).toList()))
                .filter(input -> !input.candidates().isEmpty()).limit(32).toList();
        return inputs.isEmpty() ? null : new Captured(type, output, inputs);
    }
    public static net.minecraft.world.item.crafting.RecipeHolder<?> register(Captured captured)
    {
        return VirtualProvisionerRecipeRegistry.register(captured.type().toString(), captured.output().key(),
                captured.output().amount(), captured.inputs().stream().map(input ->
                        new VirtualProvisionerRecipeRegistry.InputSlot(
                                input.inputGroup(), input.candidates())).toList());
    }
    public record Captured(Identifier type, KeyAmount output,
                           List<OpenOrderMenuPayload.VirtualInput> inputs) {}
}
