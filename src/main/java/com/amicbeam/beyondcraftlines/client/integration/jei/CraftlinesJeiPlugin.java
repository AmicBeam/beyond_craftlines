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
        orderTarget(target);
        return true;
    }

    public static void orderTarget(IStackKey<?> target)
    {
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} client order-key open page target={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
        queueOrder(new OpenOrderMenuPayload(target, "", ""));
    }

    public static @Nullable OpenOrderMenuPayload resolveOrderTarget(IStackKey<?> target)
    { return focusedOrderPayload(target); }

    public static @Nullable OpenOrderMenuPayload resolveOrderTarget(IStackKey<?> target,
                                                                     ResourceLocation preferredRecipe)
    { return focusedOrderPayload(target, preferredRecipe); }

    public static void openResolvedOrder(OpenOrderMenuPayload payload)
    { if (payload != null) queueOrder(payload); }

    public static boolean canOrderFromRecipeViewer()
    { return orderButtonAvailability() != NetworkAvailability.UNAVAILABLE; }

    public static void orderPreferredTarget(IStackKey<?> target, ResourceLocation preferredRecipe)
    {
        OpenOrderMenuPayload payload = focusedOrderPayload(target, preferredRecipe);
        if (payload != null) queueOrder(payload);
        else if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(
                    "error.beyond_craftlines.middle_click_recipe_not_found"), true);
    }

    private static @Nullable OpenOrderMenuPayload focusedOrderPayload(IStackKey<?> target)
    { return focusedOrderPayload(target, null); }

    private static @Nullable OpenOrderMenuPayload focusedOrderPayload(
            IStackKey<?> target, @Nullable ResourceLocation preferredRecipe)
    {
        IJeiRuntime current = runtime;
        if (current == null || target == null || target.isEmpty())
        {
            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                    "{} client JEI search unavailable runtime={} target={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    current != null, com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
            return null;
        }
        var typed = current.getIngredientManager().createTypedIngredient(target.getReadOnlyStack(), false);
        if (typed.isEmpty())
        {
            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                    "{} client JEI typed ingredient missing target={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
            return null;
        }
        var focusFactory = current.getJeiHelpers().getFocusFactory();
        var focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, typed.get());
        java.util.List<mezz.jei.api.recipe.IFocus<?>> focuses = java.util.List.of(focus);
        var focusGroup = focusFactory.createFocusGroup(focuses);
        var categories = current.getRecipeManager().createRecipeCategoryLookup()
                .limitFocus(focuses).includeHidden().get().toList();
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} client JEI focused search target={} categories={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target), categories.size());
        for (var category : categories)
        {
            OpenOrderMenuPayload payload = focusedOrderPayload(
                    current, target, category, focuses, focusGroup, true, preferredRecipe);
            if (payload != null) return payload;
        }
        // Dynamic recipes may expose only a base getResultItem while JEI's focused layout
        // represents the concrete assemble() result. The focus is authoritative here; preserve
        // the clicked target components instead of falling back to every same-item recipe.
        for (var category : categories)
        {
            OpenOrderMenuPayload payload = focusedOrderPayload(
                    current, target, category, focuses, focusGroup, false, preferredRecipe);
            if (payload != null) return payload;
        }
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                "{} client JEI focused search found no recipe target={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
        return null;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable OpenOrderMenuPayload focusedOrderPayload(
            IJeiRuntime current, IStackKey<?> target,
            mezz.jei.api.recipe.category.IRecipeCategory<?> rawCategory,
            java.util.List<mezz.jei.api.recipe.IFocus<?>> focuses,
            mezz.jei.api.recipe.IFocusGroup focusGroup, boolean exactOnly,
            @Nullable ResourceLocation preferredRecipe)
    {
        var category = (mezz.jei.api.recipe.category.IRecipeCategory<Object>) rawCategory;
        var recipes = current.getRecipeManager().createRecipeLookup(category.getRecipeType())
                .limitFocus(focuses).includeHidden().get().toList();
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} client JEI category={} recipes={} exactPass={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                category.getRecipeType().getUid(), recipes.size(), exactOnly);
        for (Object recipe : recipes)
        {
            var layout = current.getRecipeManager().createRecipeLayoutDrawable(
                    category, recipe, focusGroup).orElse(null);
            if (layout == null) continue;
            ResourceLocation displayedRecipeId = findRecipeId(layout);
            if (preferredRecipe != null && !preferredRecipe.equals(displayedRecipeId)) continue;
            var captured = JeiVirtualRecipeLayouts.capture(category.getRecipeType().getUid(), layout);
            if (captured == null) continue;
            boolean exact = com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                    .exact(target, captured.output().key());
            if (exactOnly != exact) continue;
            ResourceLocation serverRecipe = serverCraftingRecipeId(layout);
            if (serverRecipe != null)
            {
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                        "{} client JEI match serverRecipe={} exact={} target={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        serverRecipe, exact,
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
                return new OpenOrderMenuPayload(target, serverRecipe.toString(),
                        captured.type().toString(), java.util.List.of(), captured.output().amount());
            }
            var focusedCapture = exact ? captured : new JeiVirtualRecipeLayouts.Captured(
                    captured.type(), new com.wintercogs.beyonddimensions.api.storage.key.KeyAmount(
                    target, captured.output().amount()), captured.inputs());
            ResourceLocation virtualRecipe = JeiVirtualRecipeLayouts.register(focusedCapture).id();
            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                    "{} client JEI match virtualRecipe={} exact={} inputs={} target={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    virtualRecipe, exact, captured.inputs().size(),
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
            return new OpenOrderMenuPayload(target, virtualRecipe.toString(), captured.type().toString(),
                    captured.inputs(), captured.output().amount());
        }
        return null;
    }

    /** Advances the target-driven JEI queue once per rendered client frame. */
    public static void clientFrame()
    { JeiCatalystIndex.tick(); }

    private static void queueOrder(OpenOrderMenuPayload payload)
    {
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} client send open-order recipe={} type={} virtualInputs={} target={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                payload.recipeId(), payload.jeiRecipeType(), payload.virtualInputs().size(),
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(payload.target()));
        if (runtime == null)
        {
            PacketDistributor.sendToServer(payload);
            return;
        }
        boolean initialPageOpen = payload.recipeId().isBlank()
                && payload.jeiRecipeType().isBlank() && payload.virtualInputs().isEmpty();
        // Opening the empty order page is latency-sensitive. Its screen starts the budgeted JEI
        // warmup itself, so doing it here only stalls the click before any UI can be rendered.
        if (!initialPageOpen)
        {
            if (!payload.jeiRecipeType().isBlank())
                JeiCatalystIndex.prewarmRecipeTypes(java.util.List.of(payload.jeiRecipeType()));
            JeiCatalystIndex.requestRecipesFor(payload.target());
        }
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
