package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.amicbeam.beyondcraftlines.client.ClientPlannerPreferences;
import com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Direct EMI access isolated behind {@link EmiOptionalIntegration}. */
public final class EmiClientIntegration
{
    private static final EmiStack ICON = EmiStack.of(new ItemStack(CraftlinesItems.NETWORK_LINKER.get()));
    private static volatile Object metadataManager;
    private static volatile Map<net.minecraft.world.item.Item, Set<ResourceLocation>> typesByWorkstation
            = Map.of();
    private static volatile Map<ResourceLocation, Component> titlesByType = Map.of();

    private EmiClientIntegration() {}

    public static boolean orderIngredientUnderMouse(double mouseX, double mouseY)
    {
        var interaction = EmiApi.getHoveredStack(
                (int) Math.floor(mouseX), (int) Math.floor(mouseY), false);
        if (interaction == null || interaction.isEmpty() || !interaction.isClickable()) return false;
        for (EmiStack value : interaction.getStack().getEmiStacks())
        {
            ItemStack stack = value.getItemStack();
            if (stack.isEmpty()) continue;
            CraftlinesJeiPlugin.orderTarget(new ItemStackKey(stack.copyWithCount(1)));
            return true;
        }
        return false;
    }

    /** EMI finalizes JEMI categories after JEI's runtime callback, so read workstation metadata lazily. */
    public static Set<ResourceLocation> recipeTypesFor(ItemStack workstation)
    {
        if (workstation.isEmpty()) return Set.of();
        refreshMetadata();
        return typesByWorkstation.getOrDefault(workstation.getItem(), Set.of());
    }

    public static Optional<Component> recipeTypeTitle(ResourceLocation type)
    {
        refreshMetadata();
        return Optional.ofNullable(titlesByType.get(type));
    }

    public static Set<ResourceLocation> recipeTypes()
    {
        refreshMetadata();
        return titlesByType.keySet();
    }

    private static void refreshMetadata()
    {
        var manager = EmiApi.getRecipeManager();
        if (manager == metadataManager) return;
        boolean refreshed = false;
        synchronized (EmiClientIntegration.class)
        {
            if (manager == metadataManager) return;
            Map<net.minecraft.world.item.Item, LinkedHashSet<ResourceLocation>> workstations = new HashMap<>();
            Map<ResourceLocation, Component> titles = new HashMap<>();
            for (var category : manager.getCategories())
            {
                ResourceLocation type = category.getId();
                if (type == null) continue;
                titles.put(type, category.getName());
                for (EmiIngredient ingredient : manager.getWorkstations(category))
                    for (EmiStack value : ingredient.getEmiStacks())
                    {
                        ItemStack stack = value.getItemStack();
                        if (!stack.isEmpty()) workstations.computeIfAbsent(stack.getItem(), ignored ->
                                new LinkedHashSet<>()).add(type);
                    }
            }
            Map<net.minecraft.world.item.Item, Set<ResourceLocation>> frozen = new HashMap<>();
            workstations.forEach((item, types) -> frozen.put(item, Set.copyOf(types)));
            typesByWorkstation = Map.copyOf(frozen);
            titlesByType = Map.copyOf(titles);
            metadataManager = manager;
            refreshed = true;
        }
        // Craftlines' JEI callback runs before JEMI finishes importing categories. Rebuild once
        // against the finalized runtime so semantic input groups (the provisioner sublabels)
        // are materialized for the same category ids exposed by EMI.
        if (refreshed)
            com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex.refresh();
    }

    public static @Nullable ResourceLocation preferredRecipe(IStackKey<?> target)
    {
        EmiStack output = itemStack(target);
        if (output == null) return null;
        EmiRecipe recipe = BoM.getRecipe(output);
        return recipe == null ? null : EmiRecipeId.normalize(recipe.getId());
    }

    public static boolean setPreferredRecipe(IStackKey<?> target, ResourceLocation recipeId)
    {
        EmiStack output = itemStack(target);
        if (output == null || recipeId == null) return false;
        EmiRecipe recipe = findRecipe(output, recipeId);
        if (recipe == null) return false;
        BoM.addRecipe(output, recipe);
        return true;
    }

    public static boolean clearPreferredRecipe(IStackKey<?> target, ResourceLocation expectedRecipe)
    {
        EmiStack output = itemStack(target);
        if (output == null || expectedRecipe == null) return false;
        EmiRecipe current = BoM.getRecipe(output);
        if (current == null || !EmiRecipeId.matches(current.getId(), expectedRecipe)) return false;
        BoM.removeRecipe(output, current);
        return true;
    }

    /** Called by the BoM mixin after EMI changes one of its own defaults. */
    public static void syncPreferenceFromEmi(EmiIngredient output, @Nullable EmiRecipe removedRecipe)
    {
        try
        {
            if (output == null || output.isEmpty()) return;
            EmiRecipe current = BoM.getRecipe(output);
            ResourceLocation currentId = current == null ? null : EmiRecipeId.normalize(current.getId());
            ResourceLocation removedId = removedRecipe == null
                    ? null : EmiRecipeId.normalize(removedRecipe.getId());
            for (EmiStack value : output.getEmiStacks())
            {
                ItemStack stack = value.getItemStack();
                if (stack.isEmpty()) continue;
                ItemStackKey key = new ItemStackKey(stack.copyWithCount(1));
                String token = RecipeResourceResolver.resolutionKey(key);
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                ClientPlannerPreferences.Snapshot snapshot = ClientPlannerPreferences.load();
                ResourceLocation saved = snapshot.recipes().get(token);
                if (currentId != null)
                {
                    if (!currentId.equals(saved)) ClientPlannerPreferences.setRecipe(token, currentId);
                }
                else if (removedId != null && removedId.equals(saved))
                {
                    ResourceLocation legacy = snapshot.recipes().get(itemId.toString());
                    ClientPlannerPreferences.clearRecipe(token,
                            removedId.equals(legacy) ? itemId : null);
                }
            }
        }
        catch (RuntimeException | LinkageError ignored) {}
    }

    /** Imports user-added preferences loaded from EMI's persistent data before any new UI click occurs. */
    public static void syncAddedPreferencesFromEmi()
    {
        try
        {
            for (var entry : BoM.addedRecipes.entrySet())
                if (entry.getValue() != null && entry.getValue().getId() != null)
                    syncPreferenceFromEmi(entry.getKey(), null);
        }
        catch (RuntimeException | LinkageError ignored) {}
    }

    public static boolean hasRecipeOrderTarget(EmiRecipe recipe)
    {
        try { return recipeTarget(recipe) != null; }
        catch (RuntimeException | LinkageError ignored) { return false; }
    }

    /** Adds a normal EMI widget; RecipeDisplayMixin only supplies the production-visible hook. */
    public static void addRecipeOrderButton(EmiRecipe recipe, WidgetGroup widgets, int x)
    {
        RecipeTarget target;
        try { target = recipeTarget(recipe); }
        catch (RuntimeException | LinkageError ignored) { return; }
        if (target == null || widgets == null) return;
        int y = Math.max(0, widgets.getHeight() - 18);
        widgets.addButton(x, y, 18, 18, 0, 0,
                CraftlinesJeiPlugin::canOrderFromRecipeViewer,
                (mouseX, mouseY, button) -> {
                    if (button == 0 && CraftlinesJeiPlugin.canOrderFromRecipeViewer())
                        CraftlinesJeiPlugin.orderPreferredTarget(target.output(), target.recipe());
                });
        widgets.addDrawable(x + 1, y + 1, 16, 16,
                (graphics, mouseX, mouseY, delta) -> ICON.render(graphics, 0, 0, delta));
        widgets.addTooltip(List.of(ClientTooltipComponent.create(Component.translatable(
                "gui.beyond_craftlines.order_from_jei").getVisualOrderText())), x, y, 18, 18);
    }

    private static @Nullable EmiStack itemStack(IStackKey<?> target)
    {
        if (!(target instanceof ItemStackKey item) || target.isEmpty()) return null;
        return EmiStack.of(item.getReadOnlyStack().copyWithCount(1));
    }

    private static @Nullable EmiRecipe findRecipe(EmiStack output, ResourceLocation recipeId)
    {
        var emiManager = EmiApi.getRecipeManager();
        for (EmiRecipe recipe : emiManager.getRecipesByOutput(output))
            if (EmiRecipeId.matches(recipe.getId(), recipeId) && recipeMatchesOutput(recipe, output))
                return recipe;
        EmiRecipe direct = emiManager.getRecipe(recipeId);
        if (direct != null && recipeMatchesOutput(direct, output)
                && EmiRecipeId.matches(direct.getId(), recipeId)) return direct;
        for (EmiRecipe recipe : emiManager.getRecipes())
            if (EmiRecipeId.matches(recipe.getId(), recipeId) && recipeMatchesOutput(recipe, output))
                return recipe;
        return null;
    }

    private static boolean recipeMatchesOutput(EmiRecipe recipe, EmiStack requested)
    {
        ItemStack stack = requested.getItemStack();
        if (stack.isEmpty()) return false;
        ItemStackKey key = new ItemStackKey(stack.copyWithCount(1));
        for (EmiStack output : recipe.getOutputs())
        {
            ItemStack candidate = output.getItemStack();
            if (!candidate.isEmpty() && StackKeyMatch.exact(
                    key, new ItemStackKey(candidate.copyWithCount(1)))) return true;
        }
        return false;
    }

    private static @Nullable RecipeTarget recipeTarget(EmiRecipe recipe)
    {
        if (recipe == null) return null;
        ResourceLocation rawId = recipe.getId();
        var backing = recipe.getBackingRecipe();
        ResourceLocation recipeId = backing != null || EmiRecipeId.isWrappedJei(rawId)
                ? EmiRecipeId.normalize(rawId) : null;
        if (recipeId == null) return null;
        for (EmiStack output : recipe.getOutputs())
        {
            ItemStack stack = output.getItemStack();
            if (stack.isEmpty()) continue;
            var amount = RecipeResourceResolver.fromStack(stack);
            if (amount != null) return new RecipeTarget(amount.key(), recipeId);
        }
        return null;
    }

    private record RecipeTarget(IStackKey<?> output, ResourceLocation recipe) {}
}
