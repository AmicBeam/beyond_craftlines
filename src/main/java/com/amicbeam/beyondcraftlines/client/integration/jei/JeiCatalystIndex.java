package com.amicbeam.beyondcraftlines.client.integration.jei;

import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Client-only snapshot of the machine relationships JEI actually displays. */
public final class JeiCatalystIndex
{
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static volatile Map<Item, Set<ResourceLocation>> TYPES_BY_CATALYST = Map.of();
    private static volatile Map<ResourceLocation, Component> TITLES_BY_TYPE = Map.of();
    private static volatile Map<ResourceLocation, Set<String>> INPUT_GROUPS_BY_TYPE = Map.of();
    private static volatile IJeiRuntime runtime;

    private JeiCatalystIndex() {}

    public static void rebuild(IJeiRuntime runtime)
    {
        JeiCatalystIndex.runtime = runtime;
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        Map<Item, LinkedHashSet<ResourceLocation>> building = new HashMap<>();
        Map<ResourceLocation, Component> titles = new HashMap<>();
        Map<ResourceLocation, Set<String>> inputGroups = new HashMap<>();
        int[] remainingLayouts = {16_384};
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().includeHidden().get().forEach(category -> {
            try
            {
                var recipeType = category.getRecipeType();
                ResourceLocation typeId = recipeType.getUid();
                titles.put(typeId, category.getTitle());
                manager.createRecipeCatalystLookup(recipeType).includeHidden().getItemStack()
                        .filter(stack -> !stack.isEmpty())
                        .map(ItemStack::getItem)
                        .forEach(item -> building.computeIfAbsent(item, ignored ->
                                new LinkedHashSet<>()).add(typeId));
                inputGroups.put(typeId, collectInputGroups(runtime, category, remainingLayouts));
            }
            catch (RuntimeException | LinkageError exception)
            {
                LOGGER.warn("Unable to index JEI recipe category {}",
                        category.getClass().getName(), exception);
            }
        });
        Map<Item, Set<ResourceLocation>> frozen = new HashMap<>();
        building.forEach((item, types) -> frozen.put(item, Set.copyOf(types)));
        TYPES_BY_CATALYST = Map.copyOf(frozen);
        TITLES_BY_TYPE = Map.copyOf(titles);
        INPUT_GROUPS_BY_TYPE = Map.copyOf(inputGroups);
    }

    public static void refresh()
    {
        IJeiRuntime current = runtime;
        if (current != null) rebuild(current);
    }

    public static Set<ResourceLocation> recipeTypesFor(ItemStack catalyst)
    {
        if (catalyst.isEmpty()) return Set.of();
        Item item = catalyst.getItem();
        Set<ResourceLocation> types = TYPES_BY_CATALYST.get(item);
        if (types != null) return types;

        // A recipe reload can complete after JEI first publishes its runtime. Rebuild once at the
        // point of use so machine binding and provisioner scans do not remain stuck with that stale snapshot.
        refresh();
        return TYPES_BY_CATALYST.getOrDefault(item, Set.of());
    }

    public static Optional<Component> recipeTypeTitle(ResourceLocation type)
    {
        return Optional.ofNullable(TITLES_BY_TYPE.get(type));
    }

    /** All recipe categories currently exposed by JEI, for the provisioner's manual fallback. */
    public static Set<ResourceLocation> recipeTypes()
    {
        return TITLES_BY_TYPE.keySet();
    }

    public static Map<ResourceLocation, Set<String>> inputGroupsFor(Set<ResourceLocation> types)
    {
        Map<ResourceLocation, Set<String>> result = new HashMap<>();
        types.forEach(type -> result.put(type, INPUT_GROUPS_BY_TYPE.getOrDefault(type, Set.of())));
        return Map.copyOf(result);
    }

    /** Uses the synced client recipe manager to mirror the server's representative-hint validation. */
    public static Set<ResourceLocation> recipeTypes(Set<String> loadedFamilies,
                                                    Map<String, Set<String>> aliases,
                                                    boolean debugMappings)
    {
        if (TITLES_BY_TYPE.isEmpty()) refresh();
        return recipeTypes();
    }

    public static void clear()
    {
        runtime = null;
        TYPES_BY_CATALYST = Map.of();
        TITLES_BY_TYPE = Map.of();
        INPUT_GROUPS_BY_TYPE = Map.of();
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
    }

    private static <T> Set<String> collectInputGroups(IJeiRuntime runtime,
                                                       mezz.jei.api.recipe.category.IRecipeCategory<T> category,
                                                       int[] remainingLayouts)
    {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        var manager = runtime.getRecipeManager();
        var focuses = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        if (remainingLayouts[0] <= 0) return Set.of();
        manager.createRecipeLookup(category.getRecipeType()).includeHidden().get()
                .limit(remainingLayouts[0]).forEach(recipe ->
                manager.createRecipeLayoutDrawable(category, recipe, focuses).ifPresent(layout -> {
                    var captured = JeiVirtualRecipeLayouts.capture(category.getRecipeType().getUid(), layout);
                    if (captured == null) return;
                    JeiVirtualRecipeLayouts.register(captured);
                    remainingLayouts[0]--;
                    captured.inputs().forEach(input -> groups.add(input.inputGroup()));
                }));
        return Set.copyOf(groups);
    }
}
