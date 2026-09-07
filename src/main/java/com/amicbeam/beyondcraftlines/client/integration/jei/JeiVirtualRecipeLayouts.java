package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.*;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Captures complete recipe variants; unsupported slots never become free inputs. */
public final class JeiVirtualRecipeLayouts
{
    private static final java.util.Set<String> WARNED = new java.util.LinkedHashSet<>();
    private JeiVirtualRecipeLayouts() {}
    public static void resetDiagnostics() { synchronized (WARNED) { WARNED.clear(); } }


    public static Captured capture(ResourceLocation type, IRecipeLayoutDrawable<?> layout)
    { return captures(type, layout).stream().findFirst().orElse(null); }

    public static Captured capture(ResourceLocation type, IRecipeLayoutDrawable<?> layout,
                                   com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> target)
    {
        List<Captured> captures = captures(type, layout);
        Captured exact = captures.stream().filter(value -> StackKeyMatch.exact(target, value.output().key()))
                .findFirst().orElse(null);
        if (exact != null) return exact;
        var source = CraftlinesJeiPlugin.findRecipeId(layout);
        if (source == null || !JeiRecipeExecutionSource.usesServerRecipe(layout.getRecipe())) return null;
        return captures.stream().filter(value -> RecipeIoProfileRegistry.outputMatches(
                source.toString(), target, value.output().key())).findFirst().orElse(null);
    }

    public static List<Captured> captures(ResourceLocation type, IRecipeLayoutDrawable<?> layout)
    {
        try
        {
            var uncertain = RecipeOutputProbabilities.uncertain(layout.getRecipe());
            var ignored = RecipePresentationOutputs.ignored(layout.getRecipe());
            return captureComplete(type, layout).stream()
                    .filter(captured -> ignored.stream().noneMatch(key -> StackKeyMatch.exact(key, captured.output().key())))
                    .map(captured -> new Captured(captured.type(), captured.output(), captured.inputs(), captured.byproducts().stream()
                            .filter(output -> ignored.stream().noneMatch(key -> StackKeyMatch.exact(key, output.key()))).toList()))
                    .filter(captured -> uncertain.stream()
                    .noneMatch(key -> StackKeyMatch.exact(key, captured.output().key())))
                    .map(captured -> new Captured(captured.type(), captured.output(), captured.inputs(), captured.byproducts(),
                            captured.byproducts().stream().filter(output -> uncertain.stream()
                                    .noneMatch(key -> StackKeyMatch.exact(key, output.key()))).toList())).toList();
        }
        catch (IllegalArgumentException exception)
        {
            String warning = type + "|" + layout.getRecipe().getClass().getName() + "|" + exception.getMessage();
            synchronized (WARNED)
            {
                if (WARNED.size() < 128 && WARNED.add(warning))
                    org.slf4j.LoggerFactory.getLogger(JeiVirtualRecipeLayouts.class).warn(
                            "Unsupported JEI recipe category={} recipeClass={} reason={}", type,
                            layout.getRecipe().getClass().getName(), exception.getMessage());
            }
            return List.of();
        }
    }

    private static List<Captured> captureComplete(ResourceLocation type, IRecipeLayoutDrawable<?> layout)
    {
        List<Captured> structured = structuredCaptures(type, layout.getRecipe());
        if (!structured.isEmpty()) return structured;
        List<IRecipeSlotView> views = layout.getRecipeSlotsView().getSlotViews();
        List<List<KeyAmount>> raw = new ArrayList<>();
        for (IRecipeSlotView slot : views)
        {
            if (slot.getRole() == RecipeIngredientRole.RENDER_ONLY) { raw.add(List.of()); continue; }
            List<KeyAmount> values = new ArrayList<>();
            slot.getAllIngredients().limit(VirtualRecipeLimits.CANDIDATES + 1L).forEach(typed -> {
                KeyAmount value = RecipeResourceResolver.fromStack(typed.getIngredient());
                if (value == null || value.isEmpty() || value.amount() < 1)
                    throw new IllegalArgumentException("unrecognized JEI resource type " + typed.getType().getUid());
                values.add(value);
            });
            if (values.size() > VirtualRecipeLimits.CANDIDATES)
                throw new IllegalArgumentException("JEI slot exceeds candidate budget");
            raw.add(List.copyOf(values));
        }
        long inputCandidates = 0;
        int inputCount = 0;
        for (int i = 0; i < views.size(); i++)
            if (views.get(i).getRole() == RecipeIngredientRole.INPUT && !raw.get(i).isEmpty())
            { inputCount++; inputCandidates += raw.get(i).size(); }
        if (inputCount > 0) VirtualRecipeLimits.requireInputs(inputCount, inputCandidates);
        List<Captured> captured = new ArrayList<>();
        for (List<List<KeyAmount>> variant : LinkedSlotVariants.expand(raw, JeiLayoutRelations.links(layout), 256))
        {
            List<SlotCapture> inputSlots = new ArrayList<>();
            List<SlotCapture> catalysts = new ArrayList<>();
            List<KeyAmount> outputs = new ArrayList<>();
            for (int i = 0; i < views.size(); i++)
            {
                IRecipeSlotView slot = views.get(i);
                List<KeyAmount> candidates = variant.get(i).stream().distinct().toList();
                if (candidates.isEmpty()) continue;
                if (slot.getRole() == RecipeIngredientRole.OUTPUT)
                {
                    if (candidates.size() != 1)
                        throw new IllegalArgumentException("output alternatives require an explicit JEI focus link");
                    KeyAmount output = candidates.getFirst();
                    outputs.add(new KeyAmount(output.key(), VanillaRecipeBatching.outputAmount(type, output.amount())));
                }
                else if (slot.getRole() == RecipeIngredientRole.INPUT)
                    inputSlots.add(captureSlot(slot, layout.getRecipe(), candidates, false));
                else if (slot.getRole() == RecipeIngredientRole.CATALYST)
                {
                    SlotCapture catalyst = captureSlot(slot, layout.getRecipe(), candidates, true);
                    if (catalyst != null) catalysts.add(catalyst);
                }
            }
            List<String> profile = JeiInputGroupProfileRegistry.resolve(type.toString(), layout.getRecipe(), inputSlots.size());
            List<String> groups = profile.isEmpty() ? JeiSlotGroupResolver.resolve(inputSlots.stream()
                    .map(SlotCapture::groupSlot).toList()) : profile;
            List<OpenOrderMenuPayload.VirtualInput> inputs = new ArrayList<>();
            for (int i = 0; i < inputSlots.size(); i++)
            {
                SlotCapture slot = inputSlots.get(i);
                String group = slot.semanticGroup().isBlank() ? groups.get(i) : slot.semanticGroup();
                inputs.add(new OpenOrderMenuPayload.VirtualInput(group, slot.candidates(), slot.use()));
            }
            for (SlotCapture slot : catalysts)
                inputs.add(new OpenOrderMenuPayload.VirtualInput(slot.semanticGroup().isBlank()
                        ? JeiSlotGroupResolver.resolve(List.of(slot.groupSlot())).getFirst() : slot.semanticGroup(),
                        slot.candidates(), slot.use()));
            captured.addAll(withOutputs(type, outputs, inputs));
            if (captured.size() > 256) throw new IllegalArgumentException("JEI recipe exceeds variant budget");
        }
        return List.copyOf(captured);
    }

    private static List<Captured> withOutputs(ResourceLocation type, List<KeyAmount> outputs,
                                              List<OpenOrderMenuPayload.VirtualInput> inputs)
    {
        if (inputs.isEmpty() || outputs.isEmpty()) return List.of();
        VirtualRecipeLimits.requireInputs(inputs.size(), inputs.stream().mapToLong(slot -> slot.candidates().size()).sum());
        var amounts = new LinkedHashMap<com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>, Long>();
        for (KeyAmount output : outputs) amounts.merge(output.key(), output.amount(), SaturatingLongMath::add);
        if (amounts.size() > VirtualRecipeLimits.OUTPUTS) throw new IllegalArgumentException("too many JEI outputs");
        List<KeyAmount> merged = amounts.entrySet().stream().map(entry -> new KeyAmount(entry.getKey(), entry.getValue())).toList();
        return merged.stream().map(output -> new Captured(type, output, List.copyOf(inputs), merged.stream()
                .filter(other -> !StackKeyMatch.exact(output.key(), other.key())).toList())).toList();
    }

    private static List<Captured> structuredCaptures(ResourceLocation type, Object displayedRecipe)
    {
        Recipe<?> recipe = displayedRecipe instanceof RecipeHolder<?> holder ? holder.value()
                : displayedRecipe instanceof Recipe<?> value ? value : null;
        if (recipe == null || !RecipeIoProfileRegistry.requiresStructuredJeiCapture(recipe)) return List.of();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return List.of();
        List<OpenOrderMenuPayload.VirtualInput> inputs = RecipeResourceResolver.ingredients(recipe).stream()
                .map(input -> new OpenOrderMenuPayload.VirtualInput(input.inputGroup(), input.candidates(), VirtualInputUse.CONSUMED))
                .toList();
        return withOutputs(type, RecipeOutputResolver.outputs(recipe, level.registryAccess()), inputs);
    }

    private static SlotCapture captureSlot(IRecipeSlotView slot, Object recipe, List<KeyAmount> candidates, boolean catalyst)
    {
        var semantics = VirtualInputSemantics.decide(recipe, candidates, catalyst);
        if (!semantics.included()) return null;
        var group = new JeiSlotGroupResolver.Slot(slot.getSlotName().orElse(""), slot.getAllIngredients()
                .map(typed -> String.valueOf(typed.getType().getUid())).collect(java.util.stream.Collectors.toSet()));
        return new SlotCapture(group, candidates, semantics.inputGroup(), semantics.use());
    }

    public static RecipeHolder<?> register(Captured captured)
    {
        String family = com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRecipeFamilies
                .executionFamily(captured.type().toString());
        return VirtualProvisionerRecipeRegistry.register(family, captured.output().key(), captured.output().amount(),
                captured.inputs().stream().map(input -> new VirtualProvisionerRecipeRegistry.InputSlot(
                        input.inputGroup(), input.candidates(), input.use())).toList(), captured.byproducts(), captured.guaranteedByproducts());
    }

    public record Captured(ResourceLocation type, KeyAmount output,
                           List<OpenOrderMenuPayload.VirtualInput> inputs, List<KeyAmount> byproducts,
                           List<KeyAmount> guaranteedByproducts)
    {
        public Captured(ResourceLocation type, KeyAmount output, List<OpenOrderMenuPayload.VirtualInput> inputs,
                        List<KeyAmount> byproducts)
        { this(type, output, inputs, byproducts, byproducts); }
        public Captured(ResourceLocation type, KeyAmount output, List<OpenOrderMenuPayload.VirtualInput> inputs)
        { this(type, output, inputs, List.of()); }
    }
    private record SlotCapture(JeiSlotGroupResolver.Slot groupSlot, List<KeyAmount> candidates,
                               String semanticGroup, VirtualInputUse use) {}
}
