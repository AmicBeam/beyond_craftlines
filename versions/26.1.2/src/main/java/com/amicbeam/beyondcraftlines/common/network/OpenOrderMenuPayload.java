package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
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
import net.minecraft.resources.Identifier;
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
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "open_order_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOrderMenuPayload> STREAM_CODEC = StreamCodec.of(
            OpenOrderMenuPayload::encode, OpenOrderMenuPayload::decode);

    public OpenOrderMenuPayload(IStackKey<?> target, String recipeId, String jeiRecipeType)
    { this(target, recipeId, jeiRecipeType, java.util.List.of(), 0); }

    public OpenOrderMenuPayload { virtualInputs = java.util.List.copyOf(virtualInputs); }

    private static void encode(RegistryFriendlyByteBuf buffer, OpenOrderMenuPayload payload)
    {
        IStackKey.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUtf(payload.recipeId(), 256);
        buffer.writeUtf(payload.jeiRecipeType(), 256);
        buffer.writeVarInt(payload.virtualInputs().size());
        for (VirtualInput input : payload.virtualInputs())
        {
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
            int candidates = buffer.readVarInt();
            if (candidates < 1 || candidates > 64)
                throw new IllegalArgumentException("invalid virtual candidate count");
            java.util.List<KeyAmount> values = new java.util.ArrayList<>();
            for (int candidate = 0; candidate < candidates; candidate++)
                values.add(new KeyAmount(IStackKey.STREAM_CODEC.decode(buffer), buffer.readVarLong()));
            inputs.add(new VirtualInput(values));
        }
        return new OpenOrderMenuPayload(target, recipe, type, inputs, buffer.readVarLong());
    }

    public static void handle(OpenOrderMenuPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            IStackKey<?> target = payload.target();
            Identifier requestedRecipe = payload.recipeId().isBlank()
                    ? null : Identifier.tryParse(payload.recipeId());
            Identifier requestedType = payload.jeiRecipeType().isBlank()
                    ? null : Identifier.tryParse(payload.jeiRecipeType());
            if (target == null || target.isEmpty()
                    || (!payload.jeiRecipeType().isBlank() && requestedType == null)
                    || (!payload.recipeId().isBlank() && requestedRecipe == null))
            {
                player.sendSystemMessage(Component.translatable("error.beyond_craftlines.invalid_order_target"));
                return;
            }
            if (!payload.virtualInputs().isEmpty())
            {
                try
                {
                    if (requestedType == null || !com.amicbeam.beyondcraftlines.common.crafting
                            .VanillaProvisionerRecipeTypes.isJeiOnly(requestedType))
                        throw new IllegalArgumentException("invalid virtual recipe category");
                    var holder = com.amicbeam.beyondcraftlines.common.crafting
                            .VirtualProvisionerRecipeRegistry.register(requestedType.toString(), target,
                            payload.virtualOutputAmount(), payload.virtualInputs().stream()
                                    .map(VirtualInput::candidates).toList());
                    if (requestedRecipe == null || !holder.id().identifier().equals(requestedRecipe))
                        throw new IllegalArgumentException("virtual recipe id mismatch");
                }
                catch (IllegalArgumentException exception)
                {
                    player.sendSystemMessage(Component.translatable(
                            "error.beyond_craftlines.invalid_order_target"));
                    return;
                }
            }
            var candidates = requestedRecipe == null
                    ? java.util.List.<net.minecraft.world.item.crafting.RecipeHolder<?>>of()
                    : player.level().recipeAccess().byKey(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.RECIPE, requestedRecipe))
                    .or(() -> com.amicbeam.beyondcraftlines.common.crafting
                            .VirtualProvisionerRecipeRegistry.find(requestedRecipe)).stream().toList();
            if (requestedRecipe != null && CraftlinesConfig.DEBUG_RECIPE_TYPE_MAPPINGS.get())
                showRecipeDebug(player, payload, candidates);
            DimensionsNet network = player.containerMenu instanceof DimensionsNetMenu dimensionsMenu
                    ? DimensionsNet.getAllNetFromPlayer(player).stream()
                            .filter(net -> net.getUnifiedStorage() == dimensionsMenu.storage).findFirst().orElse(null)
                    : DimensionsNet.getPrimaryNetFromPlayer(player);
            if (network == null)
            {
                player.sendSystemMessage(Component.translatable("error.beyond_craftlines.network_required"));
                return;
            }
            int networkId = network.getId();
            var availableFamilies = DeviceBindingRegistry.availableFamilies(player.level().getServer(), networkId);
            var recipe = candidates.stream()
                    .filter(RecipePlanningService::supported)
                    .filter(holder -> "crafting".equals(RecipePlanningService.family(holder))
                            || availableFamilies.contains(RecipePlanningService.family(holder)))
                    .filter(holder -> RecipeOutputResolver.outputs(holder.value(), player.level())
                            .stream().anyMatch(output -> target.isSame(output.key())))
                    .findFirst().orElse(null);
            // The JEI category id is presentation metadata, not an execution capability. A single
            // server RecipeType may be split across multiple JEI subcategories whose ids cannot be
            // inferred generically. The recipe id, actual server family, network binding and selected
            // output above are the authoritative checks.
            if (requestedRecipe != null && recipe == null)
            {
                player.sendSystemMessage(Component.translatable(
                        "error.beyond_craftlines.invalid_order_recipe"));
                return;
            }
            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new CraftlineOrderMenu(id, inventory, networkId, target,
                            recipe == null ? null : recipe.id().identifier(),
                            requestedRecipe != null, availableFamilies),
                    Component.translatable("menu.beyond_craftlines.order")), buffer -> {
                        buffer.writeVarInt(networkId);
                        IStackKey.STREAM_CODEC.encode(buffer, target);
                        buffer.writeUtf(recipe == null ? "" : recipe.id().identifier().toString());
                        buffer.writeBoolean(requestedRecipe != null);
                        buffer.writeVarInt(availableFamilies.size());
                        availableFamilies.stream().sorted().forEach(buffer::writeUtf);
                        buffer.writeBoolean(false);
                        buffer.writeBoolean(false);
                        buffer.writeVarLong(1);
                        buffer.writeUtf("network", 16);
                    });
        });
    }

    private static void showRecipeDebug(ServerPlayer player, OpenOrderMenuPayload payload,
                                        java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> candidates)
    {
        String families = candidates.stream().map(RecipePlanningService::family)
                .filter(java.util.Objects::nonNull).filter(family -> !family.isBlank())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(", "));
        Component actual = families.isEmpty()
                ? Component.translatable("message.beyond_craftlines.debug_recipe_type_not_found")
                : Component.literal(families);
        player.sendSystemMessage(Component.translatable(
                "message.beyond_craftlines.debug_order_recipe", payload.recipeId(),
                payload.jeiRecipeType(), actual));
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record VirtualInput(java.util.List<KeyAmount> candidates)
    {
        public VirtualInput { candidates = java.util.List.copyOf(candidates); }
    }
}
