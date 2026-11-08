package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.amicbeam.beyondcraftlines.common.menu.RecipeIndexVisibility;
import com.amicbeam.beyondcraftlines.common.crafting.VanillaProvisionerRecipeTypes;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RequestCategoryBindingPayload(int menuId, long nonce, String category) implements CustomPacketPayload
{
    public static final Type<RequestCategoryBindingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "request_category_binding"));
    public static final StreamCodec<ByteBuf, RequestCategoryBindingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequestCategoryBindingPayload::menuId,
            ByteBufCodecs.VAR_LONG, RequestCategoryBindingPayload::nonce,
            ByteBufCodecs.stringUtf8(256), RequestCategoryBindingPayload::category,
            RequestCategoryBindingPayload::new);
    public static void handle(RequestCategoryBindingPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                    || menu.containerId != payload.menuId() || !menu.canAccessNetwork(player)
                    || ResourceLocation.tryParse(payload.category()) == null) return;
            var network = DimensionsNet.getNetFromId(menu.networkId());
            if (network == null)
            {
                PacketDistributor.sendToPlayer(player, new CategoryBindingStatusPayload(
                        menu.containerId, payload.nonce(), payload.category(), 0, 0, ""));
                return;
            }
            String family = VanillaProvisionerRecipeTypes.runtimeFamily(payload.category());
            if (family.equals("minecraft:crafting")) family = "crafting";
            final String wanted = family;
            boolean available = RecipeIndexVisibility.includes(wanted,
                    DeviceBindingRegistry.availableFamilies(player.getServer(), menu.networkId()));
            // Inspect this network's saved binding index and known endpoints only, never a world scan.
            var matches = BindingSavedData.get(player.getServer()).forNetwork(menu.networkId()).stream()
                    .filter(record -> com.amicbeam.beyondcraftlines.common.crafting.RootDiagnosticRules.matchesBinding(
                            payload.category(), record.recipeFamilies(), record.jeiRecipeTypes().stream()
                                    .map(Object::toString).collect(java.util.stream.Collectors.toSet())))
                    .toList();
            StringBuilder endpoints = new StringBuilder();
            for (var record : matches.stream().limit(8).toList())
            {
                boolean provisioner = record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING;
                var dimension = provisioner && record.provisionerDimension() != null
                        ? record.provisionerDimension() : record.dimension();
                var position = provisioner && record.provisionerPosition() != null
                        ? record.provisionerPosition() : record.position();
                endpoints.append(provisioner ? "[provisioner] " : "[machine] ")
                        .append(record.lastBlockId()).append(" @ ").append(dimension.location())
                        .append(" ").append(position.toShortString()).append("\n");
            }
            String text = endpoints.substring(0, Math.min(2048, endpoints.length()));
            int flags = 4 | (available ? 1 : 0) | (network.isManager(player) ? 2 : 0);
            PacketDistributor.sendToPlayer(player, new CategoryBindingStatusPayload(
                    menu.containerId, payload.nonce(), payload.category(), flags, matches.size(), text));
        });
    }
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
