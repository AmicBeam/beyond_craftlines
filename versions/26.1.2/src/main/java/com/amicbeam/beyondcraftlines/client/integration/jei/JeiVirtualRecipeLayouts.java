package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.JeiSlotGroupResolver;
import com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry;
import com.amicbeam.beyondcraftlines.common.crafting.JeiInputSemantics;
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
        List<SlotCapture> slots = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT)
                .stream().map(slot -> captureSlot(slot, false, layout.getRecipe()))
                .filter(java.util.Objects::nonNull).limit(32).toList();
        List<SlotCapture> catalysts = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.CRAFTING_STATION)
                .stream().map(slot -> captureSlot(slot, true, layout.getRecipe()))
                .filter(java.util.Objects::nonNull).limit(Math.max(0, 32 - slots.size())).toList();
        List<String> profileGroups = JeiInputGroupProfileRegistry.resolve(
                type.toString(), layout.getRecipe(), slots.size());
        List<String> groups = profileGroups.isEmpty() ? JeiSlotGroupResolver.resolve(slots.stream()
                .map(SlotCapture::groupSlot).toList()) : profileGroups;
        List<OpenOrderMenuPayload.VirtualInput> inputs = new java.util.ArrayList<>();
        for (int index = 0; index < slots.size(); index++)
            inputs.add(new OpenOrderMenuPayload.VirtualInput(groups.get(index),
                    slots.get(index).candidates(), slots.get(index).reusable()));
        for (SlotCapture catalyst : catalysts)
            inputs.add(new OpenOrderMenuPayload.VirtualInput(
                    JeiSlotGroupResolver.resolve(List.of(catalyst.groupSlot())).getFirst(),
                    catalyst.candidates(), catalyst.reusable()));
        return inputs.isEmpty() ? null : new Captured(type, output, List.copyOf(inputs));
    }

    private static SlotCapture captureSlot(mezz.jei.api.gui.ingredient.IRecipeSlotView slot,
                                           boolean catalyst, Object displayedRecipe)
    {
        List<KeyAmount> candidates = slot.getAllIngredients().map(typed ->
                        RecipeResourceResolver.fromStack(typed.getIngredient()))
                .filter(java.util.Objects::nonNull).distinct().limit(64).toList();
        if (candidates.isEmpty()) return null;
        var groupSlot = new JeiSlotGroupResolver.Slot(
                                slot.getSlotName().orElse(""), slot.getAllIngredients()
                                .map(typed -> String.valueOf(typed.getType().getUid()))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        boolean reusable = catalyst ? JeiInputSemantics.reusableCatalyst(displayedRecipe, candidates)
                : JeiInputSemantics.reusableInput(candidates);
        return new SlotCapture(groupSlot, candidates, reusable);
    }

    public static net.minecraft.world.item.crafting.RecipeHolder<?> register(Captured captured)
    {
        String family = com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRecipeFamilies
                .executionFamily(captured.type().toString());
        return VirtualProvisionerRecipeRegistry.register(family, captured.output().key(),
                captured.output().amount(), captured.inputs().stream().map(input ->
                        new VirtualProvisionerRecipeRegistry.InputSlot(
                                input.inputGroup(), input.candidates(), input.reusable())).toList());
    }
    public record Captured(Identifier type, KeyAmount output,
                           List<OpenOrderMenuPayload.VirtualInput> inputs) {}
    private record SlotCapture(JeiSlotGroupResolver.Slot groupSlot, List<KeyAmount> candidates,
                               boolean reusable) {}
}
