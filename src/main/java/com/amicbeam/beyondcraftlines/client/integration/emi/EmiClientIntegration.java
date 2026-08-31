package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Direct EMI access isolated behind {@link EmiOptionalIntegration}. */
public final class EmiClientIntegration
{
    private static final EmiStack ICON = EmiStack.of(new ItemStack(
            com.amicbeam.beyondcraftlines.common.init.CraftlinesItems.NETWORK_LINKER.get()));
    private static volatile Object metadataManager;
    private static volatile Map<net.minecraft.world.item.Item, Set<ResourceLocation>> typesByWorkstation
            = Map.of();
    private static volatile Map<ResourceLocation, Component> titlesByType = Map.of();
    private static volatile Field currentPageField;

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

    /** Draws recipe actions independently of EMI's production-disabled recipe decorators. */
    public static void renderRecipeOrderButtons(Screen screen, GuiGraphics graphics,
                                                int mouseX, int mouseY, float partialTick)
    {
        for (RecipeButton button : recipeButtons(screen))
        {
            boolean available = CraftlinesJeiPlugin.canOrderFromRecipeViewer();
            boolean hovered = button.contains(mouseX, mouseY);
            int fill = !available ? 0xFF4A4A4A : hovered ? 0xFF9A9A9A : 0xFF747474;
            graphics.fill(button.x(), button.y(), button.x() + 18, button.y() + 18, 0xFF101010);
            graphics.fill(button.x() + 1, button.y() + 1,
                    button.x() + 17, button.y() + 17, fill);
            graphics.fill(button.x() + 2, button.y() + 2,
                    button.x() + 17, button.y() + 3, 0xFFC8C8C8);
            graphics.fill(button.x() + 2, button.y() + 2,
                    button.x() + 3, button.y() + 17, 0xFFC8C8C8);
            ICON.render(graphics, button.x() + 1, button.y() + 1, partialTick);
            if (hovered)
                graphics.renderTooltip(Minecraft.getInstance().font,
                        Component.translatable("gui.beyond_craftlines.order_from_jei"),
                        mouseX, mouseY);
        }
    }

    public static boolean orderRecipeButtonUnderMouse(Screen screen, double mouseX, double mouseY)
    {
        if (!CraftlinesJeiPlugin.canOrderFromRecipeViewer()) return false;
        for (RecipeButton button : recipeButtons(screen))
            if (button.contains(mouseX, mouseY))
            {
                CraftlinesJeiPlugin.orderPreferredTarget(
                        button.target().output(), button.target().recipe());
                return true;
            }
        return false;
    }

    private static java.util.List<RecipeButton> recipeButtons(Screen screen)
    {
        if (!(screen instanceof RecipeScreen)) return java.util.List.of();
        try
        {
            Field field = currentPageField;
            if (field == null)
            {
                field = RecipeScreen.class.getDeclaredField("currentPage");
                field.setAccessible(true);
                currentPageField = field;
            }
            Object value = field.get(screen);
            if (!(value instanceof java.util.List<?> groups)) return java.util.List.of();
            java.util.ArrayList<RecipeButton> buttons = new java.util.ArrayList<>();
            for (Object valueGroup : groups)
            {
                if (!(valueGroup instanceof WidgetGroup group)) continue;
                RecipeTarget target = recipeTarget(group.recipe);
                if (target == null) continue;
                // EMI's native TREE/DEFAULT/FILL actions occupy the first right-hand column.
                buttons.add(new RecipeButton(group.x() + group.getWidth() + 19,
                        group.y(), target));
            }
            return buttons;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
        { return java.util.List.of(); }
    }

    private static @Nullable RecipeTarget recipeTarget(EmiRecipe recipe)
    {
        ResourceLocation rawId = recipe.getId();
        var backing = recipe.getBackingRecipe();
        ResourceLocation recipeId = backing != null || EmiRecipeId.isWrappedJei(rawId)
                ? EmiRecipeId.normalize(rawId) : null;
        if (recipeId == null) return null;
        for (EmiStack output : recipe.getOutputs())
        {
            ItemStack stack = output.getItemStack();
            if (stack.isEmpty()) continue;
            var amount = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                    .fromStack(stack);
            if (amount != null) return new RecipeTarget(amount.key(), recipeId);
        }
        return null;
    }

    private record RecipeTarget(IStackKey<?> output, ResourceLocation recipe) {}
    private record RecipeButton(int x, int y, RecipeTarget target)
    {
        private boolean contains(double mouseX, double mouseY)
        { return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18; }
    }

    public static @Nullable ResourceLocation preferredRecipe(IStackKey<?> target)
    {
        if (!(target instanceof ItemStackKey item)) return null;
        EmiIngredient ingredient = EmiStack.of(item.getReadOnlyStack().copyWithCount(1));
        try
        {
            Class<?> bom = Class.forName("dev.emi.emi.bom.BoM", false,
                    EmiClientIntegration.class.getClassLoader());
            Method method = bom.getMethod("getRecipe", EmiIngredient.class);
            Object value = method.invoke(null, ingredient);
            return value instanceof EmiRecipe recipe ? EmiRecipeId.normalize(recipe.getId()) : null;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }
}
