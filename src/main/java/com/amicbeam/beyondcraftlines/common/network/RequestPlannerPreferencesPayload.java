package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.PlannerPreferences;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RequestPlannerPreferencesPayload() implements CustomPacketPayload
{
    public static final Type<RequestPlannerPreferencesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "request_planner_preferences"));
    public static final StreamCodec<ByteBuf, RequestPlannerPreferencesPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestPlannerPreferencesPayload());

    public static void handle(RequestPlannerPreferencesPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                    || !menu.canAccessNetwork(player)) return;
            PacketDistributor.sendToPlayer(player, PlannerPreferencesPayload.from(PlannerPreferences.read(player)));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
