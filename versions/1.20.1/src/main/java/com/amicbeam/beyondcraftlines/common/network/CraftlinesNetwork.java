package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import com.amicbeam.beyondcraftlines.compat.protocol.IPayloadContext;
import com.amicbeam.beyondcraftlines.compat.protocol.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CraftlinesNetwork {
    private static final String PROTOCOL = "12";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BeyondCraftlines.MOD_ID, "main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static int discriminator;
    private static boolean registered;

    private CraftlinesNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        server(OpenOrderMenuPayload.class, OpenOrderMenuPayload.STREAM_CODEC, OpenOrderMenuPayload::handle);
        server(OpenOrderStatusMenuPayload.class, OpenOrderStatusMenuPayload.STREAM_CODEC, OpenOrderStatusMenuPayload::handle);
        server(SubmitOrderPayload.class, SubmitOrderPayload.STREAM_CODEC, SubmitOrderPayload::handle);
        server(RequestPlanningSnapshotPayload.class, RequestPlanningSnapshotPayload.STREAM_CODEC, RequestPlanningSnapshotPayload::handle);
        server(RequestPlannerPreferencesPayload.class, RequestPlannerPreferencesPayload.STREAM_CODEC, RequestPlannerPreferencesPayload::handle);
        server(SavePlannerPreferencePayload.class, SavePlannerPreferencePayload.STREAM_CODEC, SavePlannerPreferencePayload::handle);
        server(PlanProposalUploadPayload.class, PlanProposalUploadPayload.STREAM_CODEC, PlanProposalUploadPayload::handle);
        server(RequestOrderStatusPayload.class, RequestOrderStatusPayload.STREAM_CODEC, RequestOrderStatusPayload::handle);
        server(RequestNetworkAmountPayload.class, RequestNetworkAmountPayload.STREAM_CODEC, RequestNetworkAmountPayload::handle);
        server(CancelOrderPayload.class, CancelOrderPayload.STREAM_CODEC, CancelOrderPayload::handle);
        server(BindMachinePayload.class, BindMachinePayload.STREAM_CODEC, BindMachinePayload::handle);
        server(ConfigureProvisionerPayload.class, ConfigureProvisionerPayload.STREAM_CODEC, ConfigureProvisionerPayload::handle);
        server(ReturnProvisionerContentPayload.class, ReturnProvisionerContentPayload.STREAM_CODEC,
                ReturnProvisionerContentPayload::handle);
        server(RequestBindingVisualsPayload.class, RequestBindingVisualsPayload.STREAM_CODEC, RequestBindingVisualsPayload::handle);
        server(RequestJeiNetworkAvailabilityPayload.class, RequestJeiNetworkAvailabilityPayload.STREAM_CODEC,
                RequestJeiNetworkAvailabilityPayload::handle);
        client(OrderStatusPayload.class, OrderStatusPayload.STREAM_CODEC, OrderStatusPayload::handle);
        client(NetworkAmountPayload.class, NetworkAmountPayload.STREAM_CODEC, NetworkAmountPayload::handle);
        client(PlanPreviewPayload.class, PlanPreviewPayload.STREAM_CODEC, PlanPreviewPayload::handle);
        client(PlanningSnapshotPayload.class, PlanningSnapshotPayload.STREAM_CODEC, PlanningSnapshotPayload::handle);
        client(PlannerPreferencesPayload.class, PlannerPreferencesPayload.STREAM_CODEC, PlannerPreferencesPayload::handle);
        client(BindingVisualsPayload.class, BindingVisualsPayload.STREAM_CODEC, BindingVisualsPayload::handle);
        client(BindMachineFeedbackPayload.class, BindMachineFeedbackPayload.STREAM_CODEC,
                BindMachineFeedbackPayload::handle);
        client(JeiNetworkAvailabilityPayload.class, JeiNetworkAvailabilityPayload.STREAM_CODEC,
                JeiNetworkAvailabilityPayload::handle);
    }

    public static void sendToServer(Object payload) { CHANNEL.sendToServer(payload); }
    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
    public static void sendToPlayersInDimension(ServerLevel level, Object payload) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), payload);
    }

    private static <T> void server(Class<T> type, StreamCodec<?, T> codec, BiConsumer<T, IPayloadContext> handler) {
        message(type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }
    private static <T> void client(Class<T> type, StreamCodec<?, T> codec, BiConsumer<T, IPayloadContext> handler) {
        message(type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> void message(Class<T> type, StreamCodec<?, T> codec,
                                    BiConsumer<T, IPayloadContext> handler, NetworkDirection direction) {
        StreamCodec raw = codec;
        CHANNEL.registerMessage(discriminator++, type,
                (payload, buffer) -> raw.encode(buffer, payload),
                buffer -> (T) raw.decode(buffer),
                (payload, context) -> handle(payload, context, handler), java.util.Optional.of(direction));
    }

    private static <T> void handle(T payload, Supplier<NetworkEvent.Context> contextSupplier,
                                   BiConsumer<T, IPayloadContext> handler) {
        NetworkEvent.Context context = contextSupplier.get();
        handler.accept(payload, new ForgeContext(context));
        context.setPacketHandled(true);
    }

    private record ForgeContext(NetworkEvent.Context delegate) implements IPayloadContext {
        @Override public Player player() { return delegate.getSender(); }
        @Override public void enqueueWork(Runnable work) { delegate.enqueueWork(work); }
        @Override public void reply(Object payload) {
            ServerPlayer sender = delegate.getSender();
            if (sender != null) sendToPlayer(sender, payload); else sendToServer(payload);
        }
    }
}
