package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.JeiSlotGroupResolver;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Captures bounded, execution-ready virtual recipes directly from rendered JEI layouts. */
public final class JeiVirtualRecipeLayouts
{
    private JeiVirtualRecipeLayouts() {}

    public static Captured capture(ResourceLocation type, IRecipeLayoutDrawable<?> layout)
    {
        KeyAmount output = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> slot.getAllIngredients())
                .map(typed -> RecipeResourceResolver.fromStack(typed.getIngredient()))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (output == null) return null;
        List<SlotCapture> slots = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT)
                .stream().map(slot -> new SlotCapture(new JeiSlotGroupResolver.Slot(
                                slot.getSlotName().orElse(""), slot.getAllIngredients()
                                .map(typed -> String.valueOf(typed.getType().getUid()))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet()), slotX(slot)),
                        slot.getAllIngredients().map(typed -> RecipeResourceResolver.fromStack(
                                        typed.getIngredient())).filter(java.util.Objects::nonNull)
                                .distinct().limit(64).toList()))
                .filter(slot -> !slot.candidates().isEmpty()).limit(32).toList();
        List<String> groups = JeiSlotGroupResolver.resolve(slots.stream()
                .map(SlotCapture::groupSlot).toList());
        List<OpenOrderMenuPayload.VirtualInput> inputs = java.util.stream.IntStream.range(0, slots.size())
                .mapToObj(index -> new OpenOrderMenuPayload.VirtualInput(
                        groups.get(index), slots.get(index).candidates())).toList();
        return inputs.isEmpty() ? null : new Captured(type, output, inputs);
    }

    private static Integer slotX(mezz.jei.api.gui.ingredient.IRecipeSlotView slot)
    {
        return slot instanceof mezz.jei.api.gui.ingredient.IRecipeSlotDrawable drawable
                ? drawable.getAreaIncludingBackground().getX() : null;
    }

    public static net.minecraft.world.item.crafting.RecipeHolder<?> register(Captured captured)
    {
        return VirtualProvisionerRecipeRegistry.register(captured.type().toString(), captured.output().key(),
                captured.output().amount(), captured.inputs().stream().map(input ->
                        new VirtualProvisionerRecipeRegistry.InputSlot(
                                input.inputGroup(), input.candidates())).toList());
    }

    public record Captured(ResourceLocation type, KeyAmount output,
                           List<OpenOrderMenuPayload.VirtualInput> inputs) {}
    private record SlotCapture(JeiSlotGroupResolver.Slot groupSlot, List<KeyAmount> candidates) {}
}
