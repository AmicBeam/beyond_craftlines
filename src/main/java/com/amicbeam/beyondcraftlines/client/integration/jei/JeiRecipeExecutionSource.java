package com.amicbeam.beyondcraftlines.client.integration.jei;

/** Chooses recipes that must retain their authoritative server identity for execution. */
final class JeiRecipeExecutionSource
{
    private JeiRecipeExecutionSource() {}

    static boolean usesServerRecipe(String family)
    { return usesServerRecipe(family, false); }

    static boolean usesServerRecipe(String family, boolean nativeFallbackAllowed)
    { return nativeFallbackAllowed || com.amicbeam.beyondcraftlines.common.crafting.VanillaProvisionerRecipeTypes
            .isPotentialNetworkExecutable(family); }

    static boolean usesServerRecipe(net.minecraft.resources.ResourceLocation category, Object displayedRecipe)
    {
        var recipe = unwrap(displayedRecipe);
        if (recipe == null) return false;
        String family = com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.family(recipe.getType());
        return usesServerRecipe(category.toString(), family,
                com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry.allowsNativeFallback(recipe),
                needsCraftingCapture(category, displayedRecipe));
    }

    static boolean usesServerRecipe(String category, String family, boolean nativeFallbackAllowed, boolean capturedCrafting)
    {
        String categoryFamily = "minecraft:crafting".equals(category) ? "crafting"
                : com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRecipeFamilies.executionFamily(category);
        return nativeFallbackAllowed || family.equals(categoryFamily) && !capturedCrafting && usesServerRecipe(family);
    }

    static boolean needsCraftingCapture(net.minecraft.resources.ResourceLocation category, Object displayedRecipe)
    {
        var recipe = unwrap(displayedRecipe);
        if (!"minecraft:crafting".equals(category.toString())
                || !(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe)
                || recipe instanceof net.minecraft.world.item.crafting.FireworkRocketRecipe) return false;
        if (com.amicbeam.beyondcraftlines.common.crafting.CapturedCraftingSource.requiresCapture(recipe)) return true;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null && com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver.outputs(recipe, level.registryAccess()).isEmpty();
    }

    private static net.minecraft.world.item.crafting.Recipe<?> unwrap(Object displayedRecipe)
    { return displayedRecipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder ? holder.value()
            : displayedRecipe instanceof net.minecraft.world.item.crafting.Recipe<?> value ? value : null; }

    static boolean usesServerRecipe(Object displayedRecipe)
    {
        net.minecraft.world.item.crafting.Recipe<?> recipe = displayedRecipe instanceof
                net.minecraft.world.item.crafting.RecipeHolder<?> holder ? holder.value()
                : displayedRecipe instanceof net.minecraft.world.item.crafting.Recipe<?> value ? value : null;
        return recipe != null && usesServerRecipe(com.amicbeam.beyondcraftlines.common.crafting
                .RecipePlanningService.family(recipe.getType()),
                com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry.allowsNativeFallback(recipe));
    }
}
