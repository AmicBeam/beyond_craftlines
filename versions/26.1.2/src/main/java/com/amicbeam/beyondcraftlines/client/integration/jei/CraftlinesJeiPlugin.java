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
    private static PendingOrder pendingOrder;

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
                Identifier recipeType = recipeLayoutDrawable.getRecipeCategory()
                        .getRecipeType().getUid();
                Identifier craftingRecipe = serverCraftingRecipeId(recipeLayoutDrawable);
                if (craftingRecipe != null)
                {
                    var output = findOutput(recipeLayoutDrawable);
                    return output == null ? null : new OrderButtonController(
                            output.key(), craftingRecipe, recipeType, java.util.List.of(),
                            output.amount(), scaledIcon);
                }
                var captured = JeiVirtualRecipeLayouts.capture(recipeType, recipeLayoutDrawable);
                if (captured == null) return null;
                Identifier recipe = JeiVirtualRecipeLayouts.register(captured).id().identifier();
                return new OrderButtonController(
                        captured.output().key(), recipe, recipeType, captured.inputs(),
                        captured.output().amount(), scaledIcon);
            }
        });
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){registration.addGhostIngredientHandler(com.amicbeam.beyondcraftlines.client.DashboardConfigScreen.class,new DashboardGhostIngredientHandler());}

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
        pendingOrder = null;
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
        pendingOrder = null;
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
        if (current == null || orderButtonAvailability() != NetworkAvailability.AVAILABLE) return false;
        IStackKey<?> target = ingredientUnderMouse(current);
        if (target == null || target.isEmpty()) return false;
        queueOrder(new OpenOrderMenuPayload(target, "", ""));
        return true;
    }

    public static void orderTarget(IStackKey<?> target)
    { queueOrder(new OpenOrderMenuPayload(target, "", "")); }

    /** Advances the target-driven JEI queue once per rendered client frame. */
    public static void clientFrame()
    {
        JeiCatalystIndex.tick();
        if (pendingOrder != null && JeiCatalystIndex.idle())
        {
            ClientPacketDistributor.sendToServer(pendingOrder.payload());
            pendingOrder = null;
        }
    }

    private static void queueOrder(OpenOrderMenuPayload payload)
    {
        if (runtime == null)
        {
            ClientPacketDistributor.sendToServer(payload);
            return;
        }
        pendingOrder = new PendingOrder(payload);
        JeiCatalystIndex.requestRecipesFor(payload.target());
        clientFrame();
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

    private static <T> @Nullable Identifier findRecipeId(IRecipeLayoutDrawable<T> layout)
    {
        Object displayedRecipe = layout.getRecipe();
        Identifier intrinsic = intrinsicRecipeId(displayedRecipe);
        return intrinsic != null ? intrinsic : layout.getRecipeCategory().getIdentifier(layout.getRecipe());
    }

    /** Real crafting recipes must keep their server id so SimulatedCrafting can execute them. */
    private static <T> @Nullable Identifier serverCraftingRecipeId(IRecipeLayoutDrawable<T> layout)
    {
        Object displayed = layout.getRecipe();
        return JeiRecipeExecutionSource.usesServerRecipe(displayed)
                ? findRecipeId(layout) : null;
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
                if (value instanceof net.minecraft.resources.ResourceKey<?> key) return key.identifier();
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
                                         Identifier recipeType,
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
            if (orderButtonAvailability() != NetworkAvailability.AVAILABLE) return false;
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

    private record PendingOrder(OpenOrderMenuPayload payload) {}

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
