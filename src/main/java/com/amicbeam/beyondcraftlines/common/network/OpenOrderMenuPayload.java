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
            ResourceLocation requestedRecipe = payload.recipeId().isBlank()
                    ? null : ResourceLocation.tryParse(payload.recipeId());
            ResourceLocation requestedType = payload.jeiRecipeType().isBlank()
                    ? null : ResourceLocation.tryParse(payload.jeiRecipeType());
            if (target == null || target.isEmpty()
                    || (!payload.jeiRecipeType().isBlank() && requestedType == null)
                    || (!payload.recipeId().isBlank() && requestedRecipe == null))
            {
                player.displayClientMessage(Component.translatable("error.beyond_craftlines.invalid_order_target"), false);
                return;
            }
            var level = player.level();
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
                    if (requestedRecipe == null || !holder.id().equals(requestedRecipe))
                        throw new IllegalArgumentException("virtual recipe id mismatch");
                }
                catch (IllegalArgumentException exception)
                {
                    player.displayClientMessage(Component.translatable(
                            "error.beyond_craftlines.invalid_order_target"), false);
                    return;
                }
            }
            var candidates = requestedRecipe == null
                    ? java.util.List.<net.minecraft.world.item.crafting.RecipeHolder<?>>of()
                    : level.getRecipeManager().byKey(requestedRecipe)
                    .or(() -> com.amicbeam.beyondcraftlines.common.crafting
                            .VirtualProvisionerRecipeRegistry.find(requestedRecipe)).stream().toList();
            if (requestedRecipe != null && CraftlinesConfig.DEBUG_RECIPE_TYPE_MAPPINGS.get())
                showRecipeDebug(player, payload, candidates);
            DimensionsNet network = player.containerMenu instanceof DimensionsNetMenu dimensionsMenu
                    && dimensionsMenu.storage instanceof com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage storage
                    ? storage.getNet() : DimensionsNet.getPrimaryNetFromPlayer(player);
            if (network == null)
            {
                player.displayClientMessage(Component.translatable("error.beyond_craftlines.network_required"), false);
                return;
            }
            int networkId = network.getId();
            var availableFamilies = DeviceBindingRegistry.availableFamilies(player.getServer(), networkId);
            if (requestedRecipe != null && candidates.isEmpty())
            {
                reject(player, payload, "not_loaded", "error.beyond_craftlines.order_recipe_not_loaded",
                        payload.recipeId());
                return;
            }
            var supported = candidates.stream().filter(RecipePlanningService::supported).toList();
            if (requestedRecipe != null && supported.isEmpty())
            {
                reject(player, payload, "unsupported_structure",
                        "error.beyond_craftlines.order_recipe_unsupported_structure", payload.recipeId());
                return;
            }
            var familyAvailable = supported.stream().filter(holder -> {
                String family = RecipePlanningService.family(holder);
                return "crafting".equals(family) || availableFamilies.contains(family);
            }).toList();
            if (requestedRecipe != null && familyAvailable.isEmpty())
            {
                String family = RecipePlanningService.family(supported.getFirst());
                reject(player, payload, "family_unavailable",
                        "error.beyond_craftlines.order_recipe_family_unavailable", family);
                return;
            }
            var recipe = familyAvailable.stream().filter(holder -> RecipeOutputResolver
                    .outputs(holder.value(), level.registryAccess()).stream()
                    .anyMatch(output -> target.isSame(output.key()))).findFirst().orElse(null);
            // The JEI category id is presentation metadata, not an execution capability. A single
            // server RecipeType may be split across multiple JEI subcategories whose ids cannot be
            // inferred generically. The recipe id, actual server family, network binding and selected
            // output above are the authoritative checks.
            if (requestedRecipe != null && recipe == null)
            {
                reject(player, payload, "output_mismatch",
                        "error.beyond_craftlines.order_recipe_output_mismatch", payload.recipeId());
                return;
            }
            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new CraftlineOrderMenu(id, inventory, networkId, target,
                            recipe == null ? null : recipe.id(),
                            requestedRecipe != null, availableFamilies),
                    Component.translatable("menu.beyond_craftlines.order")), buffer -> {
                        buffer.writeVarInt(networkId);
                        IStackKey.STREAM_CODEC.encode(buffer, target);
                        buffer.writeUtf(recipe == null ? "" : recipe.id().toString());
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
        player.displayClientMessage(Component.translatable(
                "message.beyond_craftlines.debug_order_recipe", payload.recipeId(),
                payload.jeiRecipeType(), actual), false);
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

    public record VirtualInput(java.util.List<KeyAmount> candidates)
    {
        public VirtualInput { candidates = java.util.List.copyOf(candidates); }
    }
}
