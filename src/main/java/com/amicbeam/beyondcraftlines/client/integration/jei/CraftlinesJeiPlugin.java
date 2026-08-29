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
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Adds the Craftlines action beside recipes, matching JEI's native transfer/bookmark buttons. */
@JeiPlugin
public final class CraftlinesJeiPlugin implements IModPlugin
{
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            BeyondCraftlines.MOD_ID, "jei_plugin");
    private static volatile IJeiRuntime runtime;
    private static volatile NetworkAvailability networkAvailability = NetworkAvailability.UNKNOWN;
    private static volatile long nextNetworkCheckNanos;

    @Override
    public ResourceLocation getPluginUid()
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
                ResourceLocation recipeType = recipeLayoutDrawable.getRecipeCategory()
                        .getRecipeType().getUid();
                ResourceLocation craftingRecipe = serverCraftingRecipeId(recipeLayoutDrawable);
                if (craftingRecipe != null)
                {
                    var output = findOutput(recipeLayoutDrawable);
                    return output == null ? null : new OrderButtonController(
                            output.key(), craftingRecipe, recipeType, java.util.List.of(),
                            output.amount(), scaledIcon);
                }
                var captured = JeiVirtualRecipeLayouts.capture(recipeType, recipeLayoutDrawable);
                if (captured == null) return null;
                ResourceLocation recipe = JeiVirtualRecipeLayouts.register(captured).id();
                return new OrderButtonController(
                        captured.output().key(), recipe, recipeType, captured.inputs(),
                        captured.output().amount(), scaledIcon);
            }
        });
    }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration)
    {
        registration.addGhostIngredientHandler(
                com.amicbeam.beyondcraftlines.client.DashboardConfigScreen.class,
                new DashboardGhostIngredientHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime)
    {
        runtime = jeiRuntime;
        JeiNetworkAvailabilityPayload.clientReceiver = payload -> {
            networkAvailability = payload.available()
                    ? NetworkAvailability.AVAILABLE : NetworkAvailability.UNAVAILABLE;
            JeiCatalystIndex.prewarmRecipeTypes(payload.recipeTypes());
        };
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

    /** Opens the Craftlines order screen for the JEI ingredient currently under the mouse. */
    public static boolean orderIngredientUnderMouse()
    {
        IJeiRuntime current = runtime;
        if (current == null) return false;
        IStackKey<?> target = ingredientUnderMouse(current);
        if (target == null || target.isEmpty()) return false;
        queueOrder(new OpenOrderMenuPayload(target, "", ""));
        return true;
    }

    public static void orderTarget(IStackKey<?> target)
    { queueOrder(new OpenOrderMenuPayload(target, "", "")); }

    /** Advances the target-driven JEI queue once per rendered client frame. */
    public static void clientFrame()
    { JeiCatalystIndex.tick(); }

    private static void queueOrder(OpenOrderMenuPayload payload)
    {
        if (runtime == null)
        {
            PacketDistributor.sendToServer(payload);
            return;
        }
        if (!payload.jeiRecipeType().isBlank())
            JeiCatalystIndex.prewarmRecipeTypes(java.util.List.of(payload.jeiRecipeType()));
        JeiCatalystIndex.requestRecipesFor(payload.target());
        PacketDistributor.sendToServer(payload);
    }

    private static @Nullable IStackKey<?> ingredientUnderMouse(IJeiRuntime current)
    {
        ItemStack recipeStack = current.getRecipesGui()
                .getIngredientUnderMouse(mezz.jei.api.constants.VanillaTypes.ITEM_STACK)
                .orElse(ItemStack.EMPTY);
        if (!recipeStack.isEmpty()) return orderTarget(recipeStack);

        var ingredient = current.getIngredientListOverlay().getIngredientUnderMouse()
                .or(() -> current.getBookmarkOverlay().getIngredientUnderMouse());
        return ingredient.map(mezz.jei.api.ingredients.ITypedIngredient::getIngredient)
                .map(CraftlinesJeiPlugin::orderTarget).orElse(null);
    }

    public static @Nullable IStackKey<?> ingredientUnderMouseKey()
    {
        IJeiRuntime current = runtime;
        return current == null ? null : ingredientUnderMouse(current);
    }

    private static @Nullable IStackKey<?> orderTarget(Object ingredient)
    {
        var amount = RecipeResourceResolver.fromStack(ingredient);
        return amount == null ? null : amount.key();
    }

    private static @Nullable com.wintercogs.beyonddimensions.api.storage.key.KeyAmount findOutput(
            IRecipeLayoutDrawable<?> layout)
    {
        return layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> slot.getAllIngredients())
                .map(typed -> RecipeResourceResolver.fromStack(typed.getIngredient()))
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    private static java.util.List<OpenOrderMenuPayload.VirtualInput> virtualInputs(
            IRecipeLayoutDrawable<?> layout)
    {
        return layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT).stream()
                .map(slot -> new OpenOrderMenuPayload.VirtualInput(
                        com.amicbeam.beyondcraftlines.common.crafting.JeiSlotInputGroup.fromSlotName(
                                slot.getSlotName().orElse("")),
                        slot.getAllIngredients().map(typed -> RecipeResourceResolver.fromStack(
                                        typed.getIngredient())).filter(java.util.Objects::nonNull)
                                .distinct().limit(64).toList()))
                .filter(input -> !input.candidates().isEmpty()).limit(32).toList();
    }

    private static <T> @Nullable ResourceLocation findRecipeId(IRecipeLayoutDrawable<T> layout)
    {
        Object displayedRecipe = layout.getRecipe();
        ResourceLocation intrinsic = intrinsicRecipeId(displayedRecipe);
        return intrinsic != null ? intrinsic : layout.getRecipeCategory().getRegistryName(layout.getRecipe());
    }

    /** Real crafting recipes must keep their server id so SimulatedCrafting can execute them. */
    private static <T> @Nullable ResourceLocation serverCraftingRecipeId(IRecipeLayoutDrawable<T> layout)
    {
        Object displayed = layout.getRecipe();
        return JeiRecipeExecutionSource.usesServerRecipe(displayed)
                ? findRecipeId(layout) : null;
    }

    /**
     * JEI categories may assign a presentation or bookmark id that is not the id used by the
     * server RecipeManager. Prefer an identity carried by the displayed recipe/holder itself and
     * keep the category id only as a fallback for JEI-native recipes.
     */
    private static @Nullable ResourceLocation intrinsicRecipeId(Object recipe)
    {
        if (recipe == null) return null;
        for (String accessor : java.util.List.of("id", "getId"))
            try
            {
                var method = recipe.getClass().getMethod(accessor);
                if (method.getParameterCount() != 0) continue;
                Object value = method.invoke(recipe);
                if (value instanceof ResourceLocation id) return id;
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
        if (runtime == null || Minecraft.getInstance().player == null) return;
        long now = System.nanoTime();
        if (now >= nextNetworkCheckNanos)
        {
            nextNetworkCheckNanos = now + 2_000_000_000L;
            PacketDistributor.sendToServer(new RequestJeiNetworkAvailabilityPayload());
        }
    }

    private record OrderButtonController(IStackKey<?> target, ResourceLocation recipe,
                                         ResourceLocation recipeType,
                                         java.util.List<OpenOrderMenuPayload.VirtualInput> virtualInputs,
                                         long virtualOutputAmount, IDrawable icon)
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
            // JEI first simulates input routing and then performs the click. Visibility and activity are
            // already refreshed every tick; re-running the asynchronous network check here can make the
            // real click lose a race with its own availability response on Forge 1.20.1.
            if (!input.isSimulate())
            {
                queueOrder(new OpenOrderMenuPayload(target, recipe.toString(), recipeType.toString(),
                        virtualInputs, virtualOutputAmount));
            }
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
        public void draw(net.minecraft.client.gui.GuiGraphics graphics, int xOffset, int yOffset)
        {
            float scale = Math.min((float) width / delegate.getWidth(),
                    (float) height / delegate.getHeight());
            graphics.pose().pushPose();
            graphics.pose().translate(xOffset, yOffset, 0);
            graphics.pose().scale(scale, scale, 1);
            delegate.draw(graphics, 0, 0);
            graphics.pose().popPose();
        }
    }
}
