package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.resources.Identifier;
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
        LinkedHashMap<IStackKey<?>, MutableNode> nodes = new LinkedHashMap<>();
        for (RecipePlan.Step step : theoretical.steps())
        {
            IStackKey<?> outputKey = step.outputKey();
            long output = SaturatingLongMath.multiply(step.outputPerCraft(), step.crafts());
            produced.merge(outputKey, output, SaturatingLongMath::add);
            MutableNode node = nodes.computeIfAbsent(outputKey, ignored -> new MutableNode(step.recipe()));
            node.produced = SaturatingLongMath.add(node.produced, output);
            node.crafts = SaturatingLongMath.add(node.crafts, step.crafts());
            for (RecipePlan.Material input : step.inputs())
                needed.merge(input.key(), input.amount(), SaturatingLongMath::add);
        }

        LinkedHashMap<IStackKey<?>, Long> leftovers = new LinkedHashMap<>();
        produced.forEach((item, amount) -> {
            long surplus = amount - Math.min(amount, needed.getOrDefault(item, 0L));
            if (surplus > 0) leftovers.put(item, surplus);
        });
        addCraftingRemainders(level, theoretical.steps(), leftovers);

        List<Node> nodeList = new ArrayList<>();
        nodes.forEach((item, node) -> nodeList.add(new Node(item, node.recipe,
                needed.getOrDefault(item, node.produced), node.produced, node.crafts)));
        totalCost.forEach((item, amount) -> {
            if (!nodes.containsKey(item)) nodeList.add(new Node(item, null, amount, 0, 0));
        });
        return new Summary(sorted(totalCost), sorted(extraction), sorted(leftovers), List.copyOf(nodeList));
    }

    private static void addCraftingRemainders(ServerLevel level, List<RecipePlan.Step> steps,
                                               Map<IStackKey<?>, Long> leftovers)
    {
        for (RecipePlan.Step step : steps)
        {
            var holder = level.recipeAccess().byKey(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.RECIPE, step.recipe())).orElse(null);
            if (holder == null) continue;
            for (ItemStack remainder : SimulatedCrafting.previewRemainders(
                    holder, level, step.ingredientSelections()))
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

    private static ItemStackKey itemKey(Identifier item)
    { return new ItemStackKey(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(item))); }

    public record Summary(Map<IStackKey<?>, Long> totalCost, Map<IStackKey<?>, Long> extraction,
                          Map<IStackKey<?>, Long> leftovers, List<Node> nodes) {}
    public record Node(IStackKey<?> key, Identifier recipe, long needed, long produced, long crafts) {}

    private static final class MutableNode
    {
        private final Identifier recipe;
        private long produced;
        private long crafts;
        private MutableNode(Identifier recipe) { this.recipe = recipe; }
    }
}
