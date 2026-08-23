package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload;
import com.amicbeam.beyondcraftlines.common.network.JeiNetworkAvailabilityPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestJeiNetworkAvailabilityPayload;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Adds the Craftlines action beside recipes, matching JEI's native transfer/bookmark buttons. */
@JeiPlugin
public final class CraftlinesJeiPlugin implements IModPlugin
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(
            BeyondCraftlines.MOD_ID, "jei_plugin");
    private static volatile IJeiRuntime runtime;
    private static volatile NetworkAvailability networkAvailability = NetworkAvailability.UNKNOWN;
    private static volatile long nextNetworkCheckNanos;

    @Override
    public Identifier getPluginUid()
    {
        return UID;
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration)
    {
        IDrawable icon = registration.getJeiHelpers().getGuiHelper()
                .createDrawableItemLike(CraftlinesItems.NETWORK_LINKER.get());
        IDrawable scaledIcon = new ScaledDrawable(icon, 12, 12);
        registration.addRecipeButtonFactory(new IRecipeButtonControllerFactory()
        {
            @Override
            public <T> @Nullable IIconButtonController createButtonController(
                    IRecipeLayoutDrawable<T> recipeLayoutDrawable)
            {
                IStackKey<?> target = findOutput(recipeLayoutDrawable);
                Identifier recipe = findRecipeId(recipeLayoutDrawable);
                Identifier recipeType = recipeLayoutDrawable.getRecipeCategory()
                        .getRecipeType().getUid();
                return target == null || recipe == null ? null
                        : new OrderButtonController(target, recipe, recipeType, scaledIcon);
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime)
    {
        runtime = jeiRuntime;
        JeiNetworkAvailabilityPayload.clientReceiver = available -> networkAvailability = available
                ? NetworkAvailability.AVAILABLE : NetworkAvailability.UNAVAILABLE;
        networkAvailability = NetworkAvailability.UNKNOWN;
        nextNetworkCheckNanos = 0L;
        requestNetworkAvailability();
        JeiCatalystIndex.rebuild(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable()
    {
        runtime = null;
        networkAvailability = NetworkAvailability.UNKNOWN;
        JeiCatalystIndex.clear();
    }

    public static void onLoggingIn()
    {
        networkAvailability = NetworkAvailability.UNKNOWN;
        nextNetworkCheckNanos = 0L;
        Minecraft.getInstance().execute(CraftlinesJeiPlugin::requestNetworkAvailability);
    }

    public static void onLoggingOut()
    {
        networkAvailability = NetworkAvailability.UNKNOWN;
        nextNetworkCheckNanos = 0L;
    }

    public static boolean showRecipesFor(ItemStack stack)
    {
        IJeiRuntime current = runtime;
        if (current == null || stack.isEmpty()) return false;
        current.getRecipesGui().show(current.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
        return true;
    }

    public static boolean showRecipesFor(IStackKey<?> key)
    {
        IJeiRuntime current = runtime;
        if (current == null || key == null || key.isEmpty()) return false;
        Object stack = key.getReadOnlyStack();
        var typed = current.getIngredientManager().createTypedIngredient(stack, false);
        if (typed.isEmpty()) return false;
        current.getRecipesGui().show(current.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT, typed.get()));
        return true;
    }

    private static @Nullable IStackKey<?> findOutput(IRecipeLayoutDrawable<?> layout)
    {
        return layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> slot.getAllIngredients())
                .map(typed -> RecipeResourceResolver.fromStack(typed.getIngredient()))
                .filter(java.util.Objects::nonNull)
                .map(com.wintercogs.beyonddimensions.api.storage.key.KeyAmount::key)
                .findFirst().orElse(null);
    }

    private static <T> @Nullable Identifier findRecipeId(IRecipeLayoutDrawable<T> layout)
    {
        Object displayedRecipe = layout.getRecipe();
        Identifier intrinsic = intrinsicRecipeId(displayedRecipe);
        return intrinsic != null ? intrinsic : layout.getRecipeCategory().getIdentifier(layout.getRecipe());
    }

    /** Prefer the server recipe identity over a JEI-only presentation or bookmark identity. */
    private static @Nullable Identifier intrinsicRecipeId(Object recipe)
    {
        if (recipe == null) return null;
        for (String accessor : java.util.List.of("id", "getId"))
            try
            {
                var method = recipe.getClass().getMethod(accessor);
                if (method.getParameterCount() != 0) continue;
                Object value = method.invoke(recipe);
                if (value instanceof Identifier id) return id;
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        return null;
    }

    private static boolean hasDimensionsNetContext()
    {
        var player = Minecraft.getInstance().player;
        return player != null && player.containerMenu instanceof DimensionsNetMenu;
    }

    private static NetworkAvailability orderButtonAvailability()
    {
        if (hasDimensionsNetContext()) return NetworkAvailability.AVAILABLE;
        if (!CraftlinesConfig.SHOW_JEI_ORDER_BUTTON_EVERYWHERE.get()
                || Minecraft.getInstance().player == null) return NetworkAvailability.UNAVAILABLE;
        requestNetworkAvailability();
        return networkAvailability;
    }

    private static void requestNetworkAvailability()
    {
        if (runtime == null || !CraftlinesConfig.SHOW_JEI_ORDER_BUTTON_EVERYWHERE.get()
                || Minecraft.getInstance().player == null) return;
        long now = System.nanoTime();
        if (now >= nextNetworkCheckNanos)
        {
            nextNetworkCheckNanos = now + 2_000_000_000L;
            ClientPacketDistributor.sendToServer(new RequestJeiNetworkAvailabilityPayload());
        }
    }

    private record OrderButtonController(IStackKey<?> target, Identifier recipe,
                                         Identifier recipeType, IDrawable icon)
            implements IIconButtonController
    {
        @Override
        public void initState(IButtonState state)
        {
            state.setIcon(icon);
            updateState(state);
        }

        @Override
        public void updateState(IButtonState state)
        {
            NetworkAvailability availability = orderButtonAvailability();
            state.setVisible(availability != NetworkAvailability.UNAVAILABLE);
            state.setActive(availability == NetworkAvailability.AVAILABLE);
        }

        @Override
        public boolean onPress(IJeiUserInput input)
        {
            if (orderButtonAvailability() != NetworkAvailability.AVAILABLE) return false;
            if (!input.isSimulate())
                ClientPacketDistributor.sendToServer(new OpenOrderMenuPayload(
                        target, recipe.toString(), recipeType.toString()));
            return true;
        }

        @Override
        public void getTooltips(ITooltipBuilder tooltip)
        {
            tooltip.add(Component.translatable("gui.beyond_craftlines.order_from_jei"));
        }
    }

    private enum NetworkAvailability
    {
        UNKNOWN,
        AVAILABLE,
        UNAVAILABLE
    }

    private record ScaledDrawable(IDrawable delegate, int width, int height) implements IDrawable
    {
        @Override
        public int getWidth()
        {
            return width;
        }

        @Override
        public int getHeight()
        {
            return height;
        }

        @Override
        public void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int xOffset, int yOffset)
        {
            float scale = Math.min((float) width / delegate.getWidth(),
                    (float) height / delegate.getHeight());
            graphics.pose().pushMatrix();
            graphics.pose().translate(xOffset, yOffset);
            graphics.pose().scale(scale, scale);
            delegate.draw(graphics, 0, 0);
            graphics.pose().popMatrix();
        }
    }
}
