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
        var registrar = event.registrar("24");
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
        registrar.playToServer(VirtualRecipeUploadPayload.TYPE, VirtualRecipeUploadPayload.STREAM_CODEC,
                VirtualRecipeUploadPayload::handle);
        registrar.playToServer(RequestOrderStatusPayload.TYPE, RequestOrderStatusPayload.STREAM_CODEC, RequestOrderStatusPayload::handle);
        registrar.playToServer(RequestNetworkAmountPayload.TYPE, RequestNetworkAmountPayload.STREAM_CODEC,
                RequestNetworkAmountPayload::handle);
        registrar.playToServer(CancelOrderPayload.TYPE, CancelOrderPayload.STREAM_CODEC, CancelOrderPayload::handle);
        registrar.playToServer(BindMachinePayload.TYPE, BindMachinePayload.STREAM_CODEC,
                BindMachinePayload::handle);
        registrar.playToServer(OpenBoundMachineConfigPayload.TYPE, OpenBoundMachineConfigPayload.STREAM_CODEC,
                OpenBoundMachineConfigPayload::handle);
        registrar.playToServer(ConfigureProvisionerPayload.TYPE, ConfigureProvisionerPayload.STREAM_CODEC,
                ConfigureProvisionerPayload::handle);
        registrar.playToServer(ConfigureBindingPriorityPayload.TYPE, ConfigureBindingPriorityPayload.STREAM_CODEC,
                ConfigureBindingPriorityPayload::handle);
        registrar.playToServer(ConfigureProvisionerDeliveryStrategyPayload.TYPE,
                ConfigureProvisionerDeliveryStrategyPayload.STREAM_CODEC,
                ConfigureProvisionerDeliveryStrategyPayload::handle);
        registrar.playToServer(ReturnProvisionerContentPayload.TYPE, ReturnProvisionerContentPayload.STREAM_CODEC,
                ReturnProvisionerContentPayload::handle);
        registrar.playToServer(ConfigureDashboardPayload.TYPE, ConfigureDashboardPayload.STREAM_CODEC,
                ConfigureDashboardPayload::handle);
        registrar.playToServer(OpenDashboardRecipePayload.TYPE, OpenDashboardRecipePayload.STREAM_CODEC,
                OpenDashboardRecipePayload::handle);
        registrar.playToServer(SaveDashboardRecipePayload.TYPE, SaveDashboardRecipePayload.STREAM_CODEC,
                SaveDashboardRecipePayload::handle);
        registrar.playToServer(OpenDashboardStatusMenuPayload.TYPE, OpenDashboardStatusMenuPayload.STREAM_CODEC,
                OpenDashboardStatusMenuPayload::handle);
        registrar.playToServer(RequestDashboardStatusPayload.TYPE, RequestDashboardStatusPayload.STREAM_CODEC,
                RequestDashboardStatusPayload::handle);
        registrar.playToServer(RequestBindingVisualsPayload.TYPE, RequestBindingVisualsPayload.STREAM_CODEC,
                RequestBindingVisualsPayload::handle);
        registrar.playToServer(RequestJeiNetworkAvailabilityPayload.TYPE,
                RequestJeiNetworkAvailabilityPayload.STREAM_CODEC, RequestJeiNetworkAvailabilityPayload::handle);
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
        registrar.playToClient(JeiNetworkAvailabilityPayload.TYPE, JeiNetworkAvailabilityPayload.STREAM_CODEC,
                JeiNetworkAvailabilityPayload::handle);
        registrar.playToClient(DashboardStatusPayload.TYPE, DashboardStatusPayload.STREAM_CODEC,
                DashboardStatusPayload::handle);
    }
}
