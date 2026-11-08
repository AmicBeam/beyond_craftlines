package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.common.localization.OrderStatusMessage;
import com.amicbeam.beyondcraftlines.common.network.OrderMenuError;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TroubleshootingRulesTest
{
    @Test void executionUsesStableReasonWithArgumentsAndLegacyProgress()
    {
        assertEquals("assign_group", reason("input_group_unassigned", "tools"));
        assertEquals("provisioner_space", reason("provisioner_no_room", "mod:fluid"));
        assertEquals("filter", reason("resource_filter_blocked"));
        assertEquals("blocking", reason("blocking_machine_input"));
        assertEquals("machine_output", TroubleshootingRules.execution("RUNNING", "machine processing; returned 2/5"));
        assertEquals("provisioner_output", reason("provisioner_waiting_output", 2, 5));
    }

    @Test void waitingForOutputDoesNotInventMachineFailure()
    {
        assertNotEquals("enable_type", reason("machine_processing", 0, 5));
        assertNotEquals("enable_type", reason("provisioner_waiting_output", 0, 5));
        assertEquals("unknown", TroubleshootingRules.execution("RUNNING", ""));
        assertEquals("unknown", reason("future_reason"));
    }

    @Test void terminalStateDoesNotRecommendResumingOldWait()
    {
        assertEquals("complete", TroubleshootingRules.execution("COMPLETE", OrderStatusMessage.encode("waiting_resource", "iron")));
        assertEquals("cancelled", TroubleshootingRules.execution("CANCELLED", OrderStatusMessage.encode("machine_processing", 0, 1)));
    }

    @Test void deliveryAndDynamicOutputHaveDifferentSolutions()
    {
        for (String id : new String[]{"waiting_inventory_space", "waiting_owner_online",
                "waiting_dashboard_container", "waiting_dashboard_container_space", "inventory_delivery_unsupported"})
            assertEquals("delivery", reason(id), id);
        assertEquals("dynamic", reason("dynamic_output_requires_manual"));
        assertEquals("final_output", reason("waiting_final_output"));
        assertEquals("network_space", reason("execution_failed_returning"));
        assertEquals("restore_binding", reason("provisioner_assignment_changed"));
    }

    @Test void specificServerErrorWinsOverPreviewReadiness()
    {
        assertEquals("enable_type", TroubleshootingRules.submission(PlanningOutcome.READY,
                OrderMenuError.translated("error.beyond_craftlines.order_recipe_family_unavailable", "mod:altar"), true, false));
        assertEquals("output_mismatch", TroubleshootingRules.submission(PlanningOutcome.RUNTIME_UNAVAILABLE,
                OrderMenuError.translated("error.beyond_craftlines.order_recipe_output_mismatch", "mod:recipe"), false, false));
        assertEquals("unknown", TroubleshootingRules.submission(PlanningOutcome.READY,
                OrderMenuError.translated("error.beyond_craftlines.future_error"), true, false));
    }

    @Test void previewProtocolAndBudgetErrorsGiveConcreteRecovery()
    {
        for (String key : new String[]{"planning_snapshot_sequence", "planning_preview_sequence", "planning_protocol_invalid"})
            assertEquals("refresh", TroubleshootingRules.submission(PlanningOutcome.RUNTIME_UNAVAILABLE,
                    OrderMenuError.translated("error.beyond_craftlines." + key), false, false));
        assertEquals("budget", TroubleshootingRules.submission(PlanningOutcome.RUNTIME_UNAVAILABLE,
                OrderMenuError.translated("error.beyond_craftlines.planning_upload_limit"), false, false));
    }

    @Test void unfinishedPreviewDoesNotProveRecipeMissing()
    {
        assertEquals("loading", TroubleshootingRules.submission(PlanningOutcome.NO_RECIPE, "", false, true));
        assertEquals("loading", TroubleshootingRules.submission(PlanningOutcome.READY, "", true, true));
        assertEquals("materials", TroubleshootingRules.submission(PlanningOutcome.MISSING_INPUTS, "", false, false));
        assertEquals("cycle", TroubleshootingRules.submission(PlanningOutcome.CYCLE, "", false, false));
        assertEquals("budget", TroubleshootingRules.submission(PlanningOutcome.BUDGET_EXHAUSTED, "", false, false));
        assertEquals("stale", TroubleshootingRules.submission(PlanningOutcome.STALE, "", false, false));
        assertEquals("ready", TroubleshootingRules.submission(PlanningOutcome.READY, "", true, false));
        assertEquals("unknown", TroubleshootingRules.submission(PlanningOutcome.READY, "", false, false));
    }

    private static String reason(String id, Object... args)
    { return TroubleshootingRules.execution("PAUSED", OrderStatusMessage.encode(id, args)); }
}
