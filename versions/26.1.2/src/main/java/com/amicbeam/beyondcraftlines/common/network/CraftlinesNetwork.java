package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = BeyondCraftlines.MOD_ID)
public final class CraftlinesNetwork
{
    private CraftlinesNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event)
    {
        var registrar = event.registrar("9");
        registrar.playToServer(OpenOrderMenuPayload.TYPE, OpenOrderMenuPayload.STREAM_CODEC, OpenOrderMenuPayload::handle);
        registrar.playToServer(OpenOrderStatusMenuPayload.TYPE, OpenOrderStatusMenuPayload.STREAM_CODEC,
                OpenOrderStatusMenuPayload::handle);
        registrar.playToServer(SubmitOrderPayload.TYPE, SubmitOrderPayload.STREAM_CODEC, SubmitOrderPayload::handle);
        registrar.playToServer(RequestPlanningSnapshotPayload.TYPE, RequestPlanningSnapshotPayload.STREAM_CODEC,
                RequestPlanningSnapshotPayload::handle);
        registrar.playToServer(RequestPlannerPreferencesPayload.TYPE, RequestPlannerPreferencesPayload.STREAM_CODEC,
                RequestPlannerPreferencesPayload::handle);
        registrar.playToServer(SavePlannerPreferencePayload.TYPE, SavePlannerPreferencePayload.STREAM_CODEC,
                SavePlannerPreferencePayload::handle);
        registrar.playToServer(PlanProposalUploadPayload.TYPE, PlanProposalUploadPayload.STREAM_CODEC,
                PlanProposalUploadPayload::handle);
        registrar.playToServer(RequestOrderStatusPayload.TYPE, RequestOrderStatusPayload.STREAM_CODEC, RequestOrderStatusPayload::handle);
        registrar.playToServer(RequestNetworkAmountPayload.TYPE, RequestNetworkAmountPayload.STREAM_CODEC,
                RequestNetworkAmountPayload::handle);
        registrar.playToServer(CancelOrderPayload.TYPE, CancelOrderPayload.STREAM_CODEC, CancelOrderPayload::handle);
        registrar.playToServer(BindMachinePayload.TYPE, BindMachinePayload.STREAM_CODEC,
                BindMachinePayload::handle);
        registrar.playToServer(ConfigureProvisionerPayload.TYPE, ConfigureProvisionerPayload.STREAM_CODEC,
                ConfigureProvisionerPayload::handle);
        registrar.playToServer(RequestBindingVisualsPayload.TYPE, RequestBindingVisualsPayload.STREAM_CODEC,
                RequestBindingVisualsPayload::handle);
        registrar.playToClient(OrderStatusPayload.TYPE, OrderStatusPayload.STREAM_CODEC, OrderStatusPayload::handle);
        registrar.playToClient(NetworkAmountPayload.TYPE, NetworkAmountPayload.STREAM_CODEC,
                NetworkAmountPayload::handle);
        registrar.playToClient(PlanPreviewPayload.TYPE, PlanPreviewPayload.STREAM_CODEC,
                PlanPreviewPayload::handle);
        registrar.playToClient(PlanningSnapshotPayload.TYPE, PlanningSnapshotPayload.STREAM_CODEC,
                PlanningSnapshotPayload::handle);
        registrar.playToClient(PlannerPreferencesPayload.TYPE, PlannerPreferencesPayload.STREAM_CODEC,
                PlannerPreferencesPayload::handle);
        registrar.playToClient(BindingVisualsPayload.TYPE, BindingVisualsPayload.STREAM_CODEC,
                BindingVisualsPayload::handle);
        registrar.playToClient(BindMachineFeedbackPayload.TYPE, BindMachineFeedbackPayload.STREAM_CODEC,
                BindMachineFeedbackPayload::handle);
    }
}
