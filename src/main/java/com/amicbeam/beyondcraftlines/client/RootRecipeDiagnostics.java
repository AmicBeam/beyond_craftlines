package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex;
import com.amicbeam.beyondcraftlines.common.crafting.*;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.menu.RecipeIndexVisibility;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/** On-demand client evidence. Reads captured data, never assembles or changes a recipe/selection. */
public final class RootRecipeDiagnostics
{
    private RootRecipeDiagnostics() {}
    public record Report(List<Component> lines, String profile)
    {
        public String text()
        { return String.join("\n", lines.stream().map(Component::getString).toList()); }
    }

    public static Report capture(CraftlineOrderMenu menu, IStackKey<?> root, RecipeHolder<?> holder,
                                 ClientRecipePlanner.Catalog catalog, long amount, String outcome,
                                 String previewError, String loading, boolean snapshotValid,
                                 IStackKey<?> sample, String problem, String itemId, String expected)
    {
        List<Component> lines = new ArrayList<>();
        String profile = "";
        add(lines, "problem_" + problem);
        add(lines, "scope");
        IStackKey<?> target;
        try { target = diagnosticTarget(sample == null ? menu.initialTarget() : sample, itemId); }
        catch (IllegalArgumentException exception)
        {
            add(lines, "invalid_item", itemId);
            return new Report(List.copyOf(lines), "");
        }
        if (target != menu.initialTarget() && target != sample) add(lines, "plain_item");
        lines.add(Component.literal("Minecraft: " + SharedConstants.getCurrentVersion().getName()));
        lines.add(Component.literal("Beyond: Craftlines=" + modVersion("beyond_craftlines")
                + "; BD=" + modVersion("beyonddimensions") + "; JEI=" + modVersion("jei")
                + "; EMI=" + modVersion("emi")));
        lines.add(Component.literal("target: " + OrderDiagnostics.resource(target) + " x" + amount));
        lines.add(Component.literal("outcome: " + outcome + "; snapshotValid=" + snapshotValid));
        if (!previewError.isBlank()) lines.add(Component.literal("preview: " + previewError));
        if (!loading.isBlank()) lines.add(Component.literal("loading: " + loading));
        try
        {
            boolean ready = catalog != null && menu.recipeIndexComplete()
                    && JeiCatalystIndex.recipeTypesReady(menu.availableFamilies());
            add(lines, ready ? "index_ready" : "pending");
            add(lines, root == null ? "root_unknown" : StackKeyMatch.exact(root, target) ? "root_ok" : "root_mismatch");
            if (!menu.initialError().isBlank()) lines.add(Component.literal("menu: " + menu.initialError()));

            lines.add(Component.literal("JEI runtime=" + JeiCatalystIndex.diagnosticRuntimeAvailable()
                    + "; categories=" + JeiCatalystIndex.recipeTypes().size()
                    + "; captured=" + JeiCatalystIndex.completedRecipeTypes(menu.availableFamilies())
                    + "/" + JeiCatalystIndex.totalRecipeTypes(menu.availableFamilies())));
            if (!JeiCatalystIndex.diagnosticRuntimeAvailable()) add(lines, "jei_unavailable");
            var known = JeiCatalystIndex.recipeTypes().stream().map(Object::toString)
                    .collect(java.util.stream.Collectors.toSet());
            var absent = RootDiagnosticRules.missingCategories(menu.availableFamilies(), known,
                    RecipeIoProfileRegistry.nativeFallbackFamilies());
            if (!absent.isEmpty()) add(lines, "categories_missing", absent.toString());
            var failures = JeiCatalystIndex.diagnosticFailures();
            if (!failures.isEmpty()) add(lines, "capture_failures", new java.util.TreeMap<>(failures).toString());

            String id = holder == null ? "<none>" : holder.id().toString();
            lines.add(Component.literal("root recipe: " + id));
            if (menu.initialRecipe() != null)
                lines.add(Component.literal("entry recipe: " + menu.initialRecipe() + "; pinned=" + menu.initialRecipePinned()));
            var outputs = holder == null || catalog == null ? List.<com.wintercogs.beyonddimensions.api.storage.key.KeyAmount>of()
                    : catalog.diagnosticOutputs(id, target);
            boolean exact = outputs.stream().anyMatch(output -> StackKeyMatch.exact(target, output.key()));
            String status = RootDiagnosticRules.outputStatus(ready, holder != null, outputs.size(), exact);
            List<Component> findings = new ArrayList<>();
            appendExpectation(findings, menu, holder, catalog, target, problem, expected, ready);
            add(findings, status);
            lines.addAll(1, findings);
            if (holder != null)
            {
                String family = RecipePlanningService.family(holder);
                boolean virtual = VirtualProvisionerRecipeRegistry.descriptor(holder.value()) != null;
                lines.add(Component.literal("family: " + family + "; source=" + (virtual ? "JEI/EMI layout" : "native")));
                lines.add(Component.literal("recipe class: " + holder.value().getClass().getName()));
                if (!RecipeIndexVisibility.includes(family, menu.availableFamilies())) add(lines, "family_disabled");
                if (!virtual && !RecipePlanningService.isNativeExecutable(holder)) add(lines, "not_executable");
                lines.add(Component.literal("input groups: " + new java.util.TreeSet<>(JeiInputGroupRegistry.groups(family))));
                var descriptor = VirtualProvisionerRecipeRegistry.descriptor(holder.value());
                if (descriptor != null)
                {
                    lines.add(Component.literal("root slots: " + descriptor.inputs().size() + "; groups="
                            + descriptor.inputs().stream().map(VirtualProvisionerRecipeRegistry.InputSlot::inputGroup).distinct().sorted().toList()));
                    if (descriptor.inputs().stream().allMatch(slot -> slot.inputGroup().equals("ingredients")))
                        add(lines, "generic_groups");
                }
                var profiles = RecipeIoProfileRegistry.diagnosticProfiles(holder.value(), id);
                add(lines, "profiles", profiles.size());
                profiles.stream().limit(8).map(Component::literal).forEach(lines::add);
                boolean expectedRoot = expected.isBlank() || (problem.equals("category")
                        ? canonicalFamily(family).equals(canonicalFamily(expected)) : id.equals(expected));
                if (!expectedRoot || outputs.isEmpty()) add(lines, "profile_not_target");
                else if (!virtual) profile = template(holder, id, family);
                else add(lines, "virtual_profile");
            }
            for (var output : outputs.stream().limit(4).toList())
            {
                lines.add(Component.literal("declared: " + OrderDiagnostics.resource(output.key()) + " x" + output.amount()));
                if (!StackKeyMatch.exact(target, output.key()))
                {
                    appendDifferences(lines, target, output.key());
                }
            }
            if (holder == null && ready)
            {
                var candidates = menu.recipeCandidatesForResource(target);
                add(lines, "candidates", candidates.size());
                for (var candidate : candidates.stream().limit(4).toList())
                {
                    String candidateId = candidate.id().toString();
                    lines.add(Component.literal("candidate: " + candidateId));
                    var output = catalog.candidateOutput(candidateId, target);
                    if (output != null && !StackKeyMatch.exact(target, output))
                        appendDifferences(lines, target, output);
                }
            }
            add(lines, "dynamic");
            add(lines, "feedback");
            if (!profile.isBlank()) add(lines, "profile_help");
        }
        catch (RuntimeException | LinkageError exception)
        {
            add(lines, "incomplete", exception.getClass().getName());
            add(lines, "feedback");
        }
        return new Report(List.copyOf(lines), profile);
    }

    public static String initialItem(IStackKey<?> target)
    {
        return target instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey item
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getSource()).toString() : "";
    }

    private static IStackKey<?> diagnosticTarget(IStackKey<?> initial, String itemId)
    {
        if (itemId.isBlank() || itemId.equals(initialItem(initial))) return initial;
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))
            throw new IllegalArgumentException("invalid item");
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        var stack = new net.minecraft.world.item.ItemStack(item);
        if (stack.isEmpty()) throw new IllegalArgumentException("empty item");
        return new com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey(stack);
    }

    private static String canonicalFamily(String family)
    {
        return "minecraft:crafting".equals(family) ? "crafting"
                : VanillaProvisionerRecipeTypes.runtimeFamily(family);
    }

    private static void appendExpectation(List<Component> lines, CraftlineOrderMenu menu,
                                           RecipeHolder<?> holder, ClientRecipePlanner.Catalog catalog,
                                           IStackKey<?> target, String problem, String expected, boolean ready)
    {
        if (expected.isBlank())
        {
            add(lines, "expectation_empty");
            return;
        }
        lines.add(Component.literal("expected " + (problem.equals("category") ? "category: " : "recipe: ") + expected));
        var recipes = catalog == null ? List.<ClientRecipePlanner.Recipe>of() : catalog.diagnosticRecipes(target);
        if (problem.equals("category"))
        {
            String wanted = canonicalFamily(expected);
            boolean known = JeiCatalystIndex.recipeTypes().stream().anyMatch(type -> canonicalFamily(type.toString()).equals(wanted));
            boolean enabled = RecipeIndexVisibility.includes(wanted, menu.availableFamilies());
            var candidates = recipes.stream().filter(recipe -> canonicalFamily(recipe.family()).equals(wanted)).toList();
            add(lines, RootDiagnosticRules.expectedCategoryStatus(ready, known, enabled, candidates.size()));
            lines.add(Component.literal("expected category captured outputs=" + candidates.size()
                    + "; exact=" + candidates.stream().filter(recipe -> StackKeyMatch.exact(target, recipe.output())).count()));
            candidates.stream().map(recipe -> recipe.id().toString()).distinct().sorted().limit(8)
                    .forEach(id -> lines.add(Component.literal("expected category candidate: " + id)));
            if (holder != null && !canonicalFamily(RecipePlanningService.family(holder)).equals(wanted))
                add(lines, "root_category_diff", RecipePlanningService.family(holder), expected);
            else if (holder != null) add(lines, "root_category_ok");
            if (!candidates.isEmpty() && candidates.stream().noneMatch(recipe -> StackKeyMatch.exact(target, recipe.output())))
                add(lines, "expected_components");
        }
        else
        {
            boolean captured = recipes.stream().anyMatch(recipe -> recipe.id().toString().equals(expected));
            if (!ready) add(lines, "pending");
            else if (!captured) add(lines, "expected_recipe_missing");
            if (holder == null || !holder.id().toString().equals(expected))
                add(lines, "root_recipe_diff", holder == null ? "<none>" : holder.id().toString(), expected);
            else add(lines, "root_recipe_ok");
            // For component questions, compare the requested recipe even when root selected another recipe.
            if (problem.equals("components") && catalog != null && captured)
                for (var output : catalog.diagnosticOutputs(expected, target).stream().limit(4).toList())
                    if (!StackKeyMatch.exact(target, output.key())) appendDifferences(lines, target, output.key());
        }
    }

    private static void appendDifferences(List<Component> lines, IStackKey<?> target, IStackKey<?> output)
    {
        var left = ComponentRecipeCandidates.diagnosticFields(target);
        var right = ComponentRecipeCandidates.diagnosticFields(output);
        add(lines, "diff_limit");
        var differences = RootDiagnosticRules.differences(left, right);
        differences.stream().map(Component::literal).forEach(lines::add);
        if (differences.isEmpty()) add(lines, "diff_unavailable");
    }

    private static String template(RecipeHolder<?> holder, String id, String family)
    {
        JsonObject json = new JsonObject();
        // Scope the draft by ID prefix and class; the prefix can also match sibling IDs.
        JsonArray ids = new JsonArray(); ids.add(id); json.add("recipe_id_prefixes", ids);
        if (family.contains(":")) json.addProperty("recipe_type", family);
        JsonArray classes = new JsonArray(); classes.add(holder.value().getClass().getName());
        json.add("recipe_classes", classes);
        json.addProperty("include_defaults", true);
        json.add("input_fields", new JsonArray());
        json.add("output_fields", new JsonArray());
        return new GsonBuilder().setPrettyPrinting().create().toJson(json);
    }

    private static void add(List<Component> lines, String key, Object... args)
    { lines.add(Component.translatable("gui.beyond_craftlines.diagnostics." + key, args)); }

    private static String modVersion(String id)
    {
        return net.neoforged.fml.ModList.get().getModContainerById(id)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("<absent>");
    }
}
