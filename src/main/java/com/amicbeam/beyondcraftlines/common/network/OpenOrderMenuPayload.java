package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OpenOrderMenuPayload(IStackKey<?> target, String recipeId, String jeiRecipeType,
                                   java.util.List<VirtualInput> virtualInputs, long virtualOutputAmount)
        implements CustomPacketPayload
{
    public static final Type<OpenOrderMenuPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "open_order_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOrderMenuPayload> STREAM_CODEC = StreamCodec.of(
            OpenOrderMenuPayload::encode, OpenOrderMenuPayload::decode);

    public OpenOrderMenuPayload(IStackKey<?> target, String recipeId, String jeiRecipeType)
    { this(target, recipeId, jeiRecipeType, java.util.List.of(), 0); }

    public OpenOrderMenuPayload
    {
        virtualInputs = java.util.List.copyOf(virtualInputs);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, OpenOrderMenuPayload payload)
    {
        IStackKey.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUtf(payload.recipeId(), 256);
        buffer.writeUtf(payload.jeiRecipeType(), 256);
        buffer.writeVarInt(payload.virtualInputs().size());
        for (VirtualInput input : payload.virtualInputs())
        {
            buffer.writeUtf(input.inputGroup(), 64);
            buffer.writeVarInt(input.use().kind().ordinal());
            buffer.writeVarInt(input.use().damagePerCraft());
            buffer.writeVarInt(input.candidates().size());
            for (KeyAmount candidate : input.candidates())
            {
                IStackKey.STREAM_CODEC.encode(buffer, candidate.key());
                buffer.writeVarLong(candidate.amount());
            }
        }
        buffer.writeVarLong(payload.virtualOutputAmount());
    }

    private static OpenOrderMenuPayload decode(RegistryFriendlyByteBuf buffer)
    {
        IStackKey<?> target = IStackKey.STREAM_CODEC.decode(buffer);
        String recipe = buffer.readUtf(256);
        String type = buffer.readUtf(256);
        int slots = buffer.readVarInt();
        if (slots < 0 || slots > 32) throw new IllegalArgumentException("invalid virtual input count");
        java.util.List<VirtualInput> inputs = new java.util.ArrayList<>();
        for (int slot = 0; slot < slots; slot++)
        {
            String inputGroup = buffer.readUtf(64);
            int useKind = buffer.readVarInt();
            int damagePerCraft = buffer.readVarInt();
            var kinds = com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse.Kind.values();
            if (useKind < 0 || useKind >= kinds.length) throw new IllegalArgumentException("invalid virtual input use");
            var use = new com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse(
                    kinds[useKind], damagePerCraft);
            int candidates = buffer.readVarInt();
            if (candidates < 1 || candidates > 64)
                throw new IllegalArgumentException("invalid virtual candidate count");
            java.util.List<KeyAmount> values = new java.util.ArrayList<>();
            for (int candidate = 0; candidate < candidates; candidate++)
                values.add(new KeyAmount(IStackKey.STREAM_CODEC.decode(buffer), buffer.readVarLong()));
            inputs.add(new VirtualInput(inputGroup, values, use));
        }
        return new OpenOrderMenuPayload(target, recipe, type, inputs, buffer.readVarLong());
    }

    public static void handle(OpenOrderMenuPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            IStackKey<?> target = payload.target();
            ResourceLocation requestedRecipe = payload.recipeId().isBlank()
                    ? null : ResourceLocation.tryParse(payload.recipeId());
            ResourceLocation requestedType = payload.jeiRecipeType().isBlank()
                    ? null : ResourceLocation.tryParse(payload.jeiRecipeType());
            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                    "{} server open-order request player={} recipe={} type={} virtualInputs={} target={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    player.getGameProfile().getName(), requestedRecipe, requestedType,
                    payload.virtualInputs().size(),
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
            if (target == null || target.isEmpty())
            {
                player.displayClientMessage(Component.translatable("error.beyond_craftlines.invalid_order_target"), false);
                return;
            }
            boolean malformedRequest = !payload.jeiRecipeType().isBlank() && requestedType == null
                    || !payload.recipeId().isBlank() && requestedRecipe == null;
            var level = player.level();
            DimensionsNet network;
            if (player.containerMenu instanceof CraftlineOrderMenu orderMenu)
                network = orderMenu.canAccessNetwork(player)
                        ? DimensionsNet.getNetFromId(orderMenu.networkId()) : null;
            else network = player.containerMenu instanceof DimensionsNetMenu dimensionsMenu
                    && dimensionsMenu.storage instanceof com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage storage
                    ? storage.getNet() : DimensionsNet.getPrimaryNetFromPlayer(player);
            if (network == null)
            {
                openOrderMenu(player, -1, target, null, false, java.util.Set.of(), "network unavailable");
                return;
            }
            int networkId = network.getId();
            var availableFamilies = DeviceBindingRegistry.availableFamilies(player.getServer(), networkId);
            if (malformedRequest)
            {
                openOrderMenu(player, networkId, target, null, false, availableFamilies,
                        OrderMenuError.translated("error.beyond_craftlines.invalid_order_target"));
                return;
            }
            if (!payload.virtualInputs().isEmpty())
            {
                String executionFamily = requestedType == null ? ""
                        : com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRecipeFamilies
                        .executionFamily(requestedType.toString());
                try
                {
                    boolean nativeFurnaceAvailable = requestedType != null
                            && com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRecipeFamilies
                            .isAvailable(requestedType.toString(), availableFamilies);
                    if (requestedType == null || (!nativeFurnaceAvailable
                            && !DeviceBindingRegistry.supportsJeiType(
                            player.getServer(), networkId, requestedType, executionFamily)))
                        throw new IllegalArgumentException("invalid virtual recipe category");
                    if (!com.amicbeam.beyondcraftlines.common.crafting.VanillaRecipeBatching
                            .validUploadedOutputAmount(executionFamily, payload.virtualOutputAmount()))
                        throw new IllegalArgumentException("invalid virtual recipe output amount");
                    var holder = com.amicbeam.beyondcraftlines.common.crafting
                            .VirtualProvisionerRecipeRegistry.register(executionFamily, target,
                            payload.virtualOutputAmount(), payload.virtualInputs().stream().map(input ->
                                    new com.amicbeam.beyondcraftlines.common.crafting
                                            .VirtualProvisionerRecipeRegistry.InputSlot(
                                            input.inputGroup(), input.candidates(), input.use())).toList());
                    if (requestedRecipe == null || !holder.id().equals(requestedRecipe))
                        throw new IllegalArgumentException("virtual recipe id mismatch");
                }
                catch (IllegalArgumentException exception)
                {
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                            "{} server virtual recipe rejected recipe={} type={} target={} error={}",
                            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                            requestedRecipe, requestedType,
                            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target),
                            exception.toString(), exception);
                    String error = "invalid virtual recipe category".equals(exception.getMessage())
                            ? OrderMenuError.translated("error.beyond_craftlines.invalid_order_category",
                            String.valueOf(requestedType), executionFamily)
                            : OrderMenuError.translated("error.beyond_craftlines.invalid_order_target");
                    openOrderMenu(player, networkId, target, null, false, availableFamilies, error);
                    return;
                }
            }
            var candidates = requestedRecipe == null
                    ? java.util.List.<net.minecraft.world.item.crafting.RecipeHolder<?>>of()
                    : level.getRecipeManager().byKey(requestedRecipe)
                    .filter(holder -> com.amicbeam.beyondcraftlines.common.crafting
                            .VanillaProvisionerRecipeTypes.isPotentialNetworkExecutable(
                                    RecipePlanningService.family(holder)))
                    .or(() -> com.amicbeam.beyondcraftlines.common.crafting
                            .VirtualProvisionerRecipeRegistry.find(requestedRecipe)).stream().toList();
            if (requestedRecipe != null && candidates.isEmpty())
            {
                openOrderMenu(player, networkId, target, null, false, availableFamilies,
                        "selected recipe is unavailable");
                return;
            }
            var supported = candidates.stream().filter(RecipePlanningService::supported).toList();
            if (requestedRecipe != null && supported.isEmpty())
            {
                openOrderMenu(player, networkId, target, null, false, availableFamilies,
                        "recipe resolution failed");
                return;
            }
            var familyAvailable = supported.stream().filter(holder -> {
                String family = RecipePlanningService.family(holder);
                return "crafting".equals(family) || availableFamilies.contains(family);
            }).toList();
            if (requestedRecipe != null && familyAvailable.isEmpty())
            {
                String family = RecipePlanningService.family(supported.getFirst());
                openOrderMenu(player, networkId, target, null, false, availableFamilies,
                        "target is unavailable");
                return;
            }
            var recipe = familyAvailable.stream().filter(holder -> com.amicbeam.beyondcraftlines.common.crafting
                    .ExplicitRootOutputMatch.matches(target,
                            RecipeOutputResolver.outputs(holder.value(), level.registryAccess())))
                    .findFirst().orElse(null);
            // The JEI category id is presentation metadata, not an execution capability. A single
            // server RecipeType may be split across multiple JEI subcategories whose ids cannot be
            // inferred generically. The recipe id, actual server family, network binding and selected
            // output above are the authoritative checks.
            if (requestedRecipe != null && recipe == null)
            {
                openOrderMenu(player, networkId, target, null, false, availableFamilies,
                        "target is unavailable");
                return;
            }
            openOrderMenu(player, networkId, target, recipe == null ? null : recipe.id(),
                    requestedRecipe != null, availableFamilies, "");
        });
    }

    private static void openOrderMenu(ServerPlayer player, int networkId, IStackKey<?> target,
                                      ResourceLocation recipe, boolean pinned,
                                      java.util.Set<String> families, String initialError)
    {
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} server open menu network={} recipe={} pinned={} families={} error={} target={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                networkId, recipe, pinned, families.size(), initialError,
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new CraftlineOrderMenu(id, inventory, networkId, target, recipe, pinned, families),
                Component.translatable("menu.beyond_craftlines.order")), buffer -> {
            buffer.writeVarInt(networkId);
            IStackKey.STREAM_CODEC.encode(buffer, target);
            buffer.writeUtf(recipe == null ? "" : recipe.toString());
            buffer.writeBoolean(pinned);
            buffer.writeVarInt(families.size());
            families.stream().sorted().forEach(buffer::writeUtf);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeVarLong(1);
            buffer.writeUtf("network", 16);
            buffer.writeUtf(initialError == null ? "" : initialError, 512);
        });
    }

    private static void reject(ServerPlayer player, OpenOrderMenuPayload payload, String gate,
                               String translation, Object detail)
    {
        org.slf4j.LoggerFactory.getLogger(BeyondCraftlines.MOD_ID).warn(
                "Rejected JEI order at gate {}: recipe={}, jeiType={}, targetType={}", gate,
                payload.recipeId(), payload.jeiRecipeType(),
                payload.target() == null ? "null" : payload.target().getTypeId());
        player.displayClientMessage(Component.translatable(translation, detail), false);
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record VirtualInput(String inputGroup, java.util.List<KeyAmount> candidates,
                               com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse use)
    {
        public VirtualInput(String inputGroup, java.util.List<KeyAmount> candidates)
        { this(inputGroup, candidates, com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse.CONSUMED); }

        public VirtualInput
        {
            if (!com.amicbeam.beyondcraftlines.common.crafting.JeiSlotInputGroup.isValid(inputGroup))
                throw new IllegalArgumentException("invalid virtual input group");
            if (use == null) throw new IllegalArgumentException("invalid virtual input use");
            candidates = java.util.List.copyOf(candidates);
        }
    }
}
