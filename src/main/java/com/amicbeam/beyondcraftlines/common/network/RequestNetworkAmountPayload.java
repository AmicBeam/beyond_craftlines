package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
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

public record RequestNetworkAmountPayload(String itemId,String matchRule) implements CustomPacketPayload
{
    public RequestNetworkAmountPayload(String itemId){this(itemId,"strict");}
    public static final Type<RequestNetworkAmountPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "request_network_amount"));
    public static final StreamCodec<ByteBuf, RequestNetworkAmountPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), RequestNetworkAmountPayload::itemId,
            ByteBufCodecs.stringUtf8(16_384),RequestNetworkAmountPayload::matchRule,
            RequestNetworkAmountPayload::new);

    public static void handle(RequestNetworkAmountPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                    || !menu.canAccessNetwork(player)) return;
            if (!menu.targetToken().equals(payload.itemId())) return;
            DimensionsNet network = DimensionsNet.getNetFromId(menu.networkId());
            if (network == null) return;
            long total = 0;
            var rule=com.amicbeam.beyondcraftlines.common.crafting.ResourceMatchRule.decode(payload.matchRule());
            for (var stored : network.getUnifiedStorage().getStorage())
            {
                if(!rule.matches(menu.initialTarget(),stored.key()))continue;
                long amount = Math.max(0, stored.amount());
                total = Long.MAX_VALUE - total < amount ? Long.MAX_VALUE : total + amount;
            }
            PacketDistributor.sendToPlayer(player, new NetworkAmountPayload(payload.itemId(), total));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
