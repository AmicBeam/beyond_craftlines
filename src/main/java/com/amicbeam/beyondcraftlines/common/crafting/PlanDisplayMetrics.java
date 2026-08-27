package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** Server-authoritative quantities used only to explain a validated plan to the player. */
public final class PlanDisplayMetrics
{
    private PlanDisplayMetrics() {}

    public static Summary summarize(ServerLevel level, RecipePlan actual, RecipePlan theoretical)
    {
        LinkedHashMap<IStackKey<?>, Long> extraction = new LinkedHashMap<>();
        for (RecipePlan.ReservedMaterial material : actual.reserved())
            if (!actual.targetKey().isSame(material.key()))
                extraction.merge(material.key(), material.amount(), SaturatingLongMath::add);

        LinkedHashMap<IStackKey<?>, Long> totalCost = new LinkedHashMap<>();
        for (RecipePlan.Material material : theoretical.missing())
            totalCost.merge(material.key(), material.amount(), SaturatingLongMath::add);

        LinkedHashMap<IStackKey<?>, Long> needed = new LinkedHashMap<>();
        needed.put(theoretical.targetKey(), theoretical.requested());
        LinkedHashMap<IStackKey<?>, Long> produced = new LinkedHashMap<>();
        LinkedHashMap<IStackKey<?>, MutableNode> theoreticalNodes = new LinkedHashMap<>();
        for (RecipePlan.Step step : theoretical.steps())
        {
            IStackKey<?> outputKey = step.outputKey();
            long output = SaturatingLongMath.multiply(step.outputPerCraft(), step.crafts());
            produced.merge(outputKey, output, SaturatingLongMath::add);
            MutableNode node = theoreticalNodes.computeIfAbsent(outputKey, ignored -> new MutableNode(step.recipe()));
            node.produced = SaturatingLongMath.add(node.produced, output);
            node.crafts = SaturatingLongMath.add(node.crafts, step.crafts());
            for (RecipePlan.Material input : step.inputs())
                needed.merge(input.key(), input.amount(), SaturatingLongMath::add);
        }

        LinkedHashMap<IStackKey<?>, MutableNode> actualNodes = new LinkedHashMap<>();
        for (RecipePlan.Step step : actual.steps())
        {
            MutableNode node = actualNodes.computeIfAbsent(step.outputKey(), ignored -> new MutableNode(step.recipe()));
            node.produced = SaturatingLongMath.add(node.produced,
                    SaturatingLongMath.multiply(step.outputPerCraft(), step.crafts()));
            node.crafts = SaturatingLongMath.add(node.crafts, step.crafts());
        }

        LinkedHashMap<IStackKey<?>, Long> leftovers = new LinkedHashMap<>();
        produced.forEach((item, amount) -> {
            long surplus = amount - Math.min(amount, needed.getOrDefault(item, 0L));
            if (surplus > 0) leftovers.put(item, surplus);
        });
        addCraftingRemainders(level, theoretical.steps(), leftovers);

        List<Node> nodeList = new ArrayList<>();
        theoreticalNodes.forEach((item, theoreticalNode) -> {
            MutableNode actualNode = actualNodes.get(item);
            nodeList.add(new Node(item, actualNode == null ? theoreticalNode.recipe : actualNode.recipe,
                    needed.getOrDefault(item, theoreticalNode.produced),
                    actualNode == null ? 0 : actualNode.produced,
                    actualNode == null ? 0 : actualNode.crafts));
        });
        actualNodes.forEach((item, actualNode) -> {
            if (!theoreticalNodes.containsKey(item)) nodeList.add(new Node(item, actualNode.recipe,
                    needed.getOrDefault(item, actualNode.produced), actualNode.produced, actualNode.crafts));
        });
        totalCost.forEach((item, amount) -> {
            if (!theoreticalNodes.containsKey(item) && !actualNodes.containsKey(item))
                nodeList.add(new Node(item, null, amount, 0, 0));
        });
        return new Summary(sorted(totalCost), sorted(extraction), sorted(leftovers), List.copyOf(nodeList));
    }

    private static void addCraftingRemainders(ServerLevel level, List<RecipePlan.Step> steps,
                                               Map<IStackKey<?>, Long> leftovers)
    {
        for (RecipePlan.Step step : steps)
        {
            var holder = level.getRecipeManager().byKey(step.recipe()).orElse(null);
            if (holder == null) continue;
            for (ItemStack remainder : SimulatedCrafting.previewRemainders(
                    holder, level, step.ingredientSelections(), step.inputs()))
            {
                if (remainder.isEmpty()) continue;
                long amount = SaturatingLongMath.multiply(remainder.getCount(), step.crafts());
                leftovers.merge(new ItemStackKey(remainder.copyWithCount(1)), amount, SaturatingLongMath::add);
            }
        }
    }

    private static Map<IStackKey<?>, Long> sorted(Map<IStackKey<?>, Long> values)
    {
        LinkedHashMap<IStackKey<?>, Long> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey(
                java.util.Comparator.comparing(RecipeResourceResolver::sortKey)))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(result);
    }

    private static ItemStackKey itemKey(ResourceLocation item)
    { return new ItemStackKey(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item))); }

    public record Summary(Map<IStackKey<?>, Long> totalCost, Map<IStackKey<?>, Long> extraction,
                          Map<IStackKey<?>, Long> leftovers, List<Node> nodes) {}
    public record Node(IStackKey<?> key, ResourceLocation recipe, long needed, long produced, long crafts) {}

    private static final class MutableNode
    {
        private final ResourceLocation recipe;
        private long produced;
        private long crafts;
        private MutableNode(ResourceLocation recipe) { this.recipe = recipe; }
    }
}
