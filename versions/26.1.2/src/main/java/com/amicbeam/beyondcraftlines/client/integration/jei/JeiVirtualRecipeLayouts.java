package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.JeiSlotGroupResolver;
import com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.amicbeam.beyondcraftlines.common.crafting.VirtualInputSemantics;
import com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/** Captures bounded, execution-ready virtual recipes directly from rendered JEI layouts. */
public final class JeiVirtualRecipeLayouts
{
    private JeiVirtualRecipeLayouts() {}
    public static Captured capture(Identifier type, IRecipeLayoutDrawable<?> layout)
    { return captures(type, layout).stream().findFirst().orElse(null); }

    public static Captured capture(Identifier type, IRecipeLayoutDrawable<?> layout,
                                   com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> target)
    {
        List<Captured> captured = captures(type, layout);
        return captured.stream().filter(value -> com.amicbeam.beyondcraftlines.common.crafting
                        .StackKeyMatch.exact(target, value.output().key())).findFirst()
                .orElse(captured.isEmpty() ? null : captured.getFirst());
    }

    public static List<Captured> captures(Identifier type, IRecipeLayoutDrawable<?> layout)
    {
        List<Captured> structured = structuredCaptures(type, layout.getRecipe());
        if (!structured.isEmpty()) return structured;
        KeyAmount output = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> slot.getAllIngredients())
                .map(typed -> RecipeResourceResolver.fromStack(typed.getIngredient()))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (output == null) return List.of();
        output = new KeyAmount(output.key(), com.amicbeam.beyondcraftlines.common.crafting
                .VanillaRecipeBatching.outputAmount(type, output.amount()));
        List<SlotCapture> slots = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT)
                .stream().map(slot -> captureSlot(slot, layout.getRecipe(), false))
                .filter(java.util.Objects::nonNull).limit(32).toList();
        List<SlotCapture> catalysts = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.CRAFTING_STATION)
                .stream().map(slot -> captureSlot(slot, layout.getRecipe(), true))
                .filter(java.util.Objects::nonNull).limit(Math.max(0, 32 - slots.size())).toList();
        List<String> profileGroups = JeiInputGroupProfileRegistry.resolve(
                type.toString(), layout.getRecipe(), slots.size());
        List<String> groups = profileGroups.isEmpty() ? JeiSlotGroupResolver.resolve(slots.stream()
                .map(SlotCapture::groupSlot).toList()) : profileGroups;
        List<OpenOrderMenuPayload.VirtualInput> inputs = new java.util.ArrayList<>();
        for (int index = 0; index < slots.size(); index++)
        {
            SlotCapture slot = slots.get(index);
            String group = slot.semanticGroup().isBlank() ? groups.get(index) : slot.semanticGroup();
            inputs.add(new OpenOrderMenuPayload.VirtualInput(group, slot.candidates(), slot.use()));
        }
        for (SlotCapture slot : catalysts)
        {
            String group = slot.semanticGroup().isBlank()
                    ? JeiSlotGroupResolver.resolve(List.of(slot.groupSlot())).getFirst() : slot.semanticGroup();
            inputs.add(new OpenOrderMenuPayload.VirtualInput(group, slot.candidates(), slot.use()));
        }
        return inputs.isEmpty() ? List.of() : List.of(new Captured(type, output, List.copyOf(inputs)));
    }

    private static List<Captured> structuredCaptures(Identifier type, Object displayedRecipe)
    {
        Recipe<?> recipe = displayedRecipe instanceof RecipeHolder<?> holder ? holder.value()
                : displayedRecipe instanceof Recipe<?> value ? value : null;
        if (recipe == null || !com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry
                .requiresStructuredJeiCapture(recipe)) return List.of();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return List.of();
        List<OpenOrderMenuPayload.VirtualInput> inputs = com.amicbeam.beyondcraftlines.common.crafting
                .RecipeResourceResolver.ingredients(recipe).stream().map(input ->
                        new OpenOrderMenuPayload.VirtualInput(input.inputGroup(), input.candidates(),
                                com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse.CONSUMED))
                .limit(32).toList();
        if (inputs.isEmpty()) return List.of();
        List<Captured> captured = new ArrayList<>();
        com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver
                .outputs(recipe, level).stream().limit(8)
                .forEach(output -> captured.add(new Captured(type, output, inputs)));
        return List.copyOf(captured);
    }

    private static SlotCapture captureSlot(mezz.jei.api.gui.ingredient.IRecipeSlotView slot,
                                           Object displayedRecipe, boolean catalyst)
    {
        List<KeyAmount> candidates = slot.getAllIngredients().map(typed -> RecipeResourceResolver.fromStack(typed.getIngredient()))
                .filter(java.util.Objects::nonNull).distinct().limit(64).toList();
        if (candidates.isEmpty()) return null;
        var groupSlot = new JeiSlotGroupResolver.Slot(slot.getSlotName().orElse(""),
                slot.getAllIngredients().map(typed -> String.valueOf(typed.getType().getUid()))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        var semantics = VirtualInputSemantics.decide(displayedRecipe, candidates, catalyst);
        if(!semantics.included())return null;
        return new SlotCapture(groupSlot, candidates, semantics.inputGroup(), semantics.use());
    }

    public static net.minecraft.world.item.crafting.RecipeHolder<?> register(Captured captured)
    {
        String family = com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRecipeFamilies
                .executionFamily(captured.type().toString());
        return VirtualProvisionerRecipeRegistry.register(family, captured.output().key(),
                captured.output().amount(), captured.inputs().stream().map(input ->
                        new VirtualProvisionerRecipeRegistry.InputSlot(
                                input.inputGroup(), input.candidates(), input.use())).toList());
    }
    public record Captured(Identifier type, KeyAmount output,
                           List<OpenOrderMenuPayload.VirtualInput> inputs) {}
    private record SlotCapture(JeiSlotGroupResolver.Slot groupSlot, List<KeyAmount> candidates,
                               String semanticGroup,
                               com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse use) {}
}
