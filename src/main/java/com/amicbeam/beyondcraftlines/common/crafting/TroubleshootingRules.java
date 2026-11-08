package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.common.localization.OrderStatusMessage;
import com.amicbeam.beyondcraftlines.common.network.OrderMenuError;

/** Evidence-based advice IDs. Never infer machine internals from a wait status. */
public final class TroubleshootingRules
{
    private TroubleshootingRules() {}

    public static String submission(PlanningOutcome outcome, String error, boolean ready, boolean loading)
    {
        var decoded = OrderMenuError.decode(error);
        String key = decoded == null ? "" : decoded.translationKey().replace("error.beyond_craftlines.", "");
        if (!key.isEmpty()) return switch (key)
        {
            case "order_recipe_family_unavailable", "invalid_order_recipe" -> "enable_type";
            case "order_recipe_not_loaded", "order_recipe_unsupported_structure" -> "compatibility";
            case "order_recipe_output_mismatch" -> "output_mismatch";
            case "no_permission", "network_required", "network_not_found", "planning_network_unavailable" -> "network";
            case "order_limit", "order_submit_cooldown" -> "queue_limit";
            case "order_network_capacity" -> "network_space";
            case "planning_stale" -> "stale";
            case "planning_snapshot_sequence", "planning_preview_sequence",
                 "planning_protocol_invalid", "planning_target_unavailable" -> "refresh";
            case "planning_recipe_invalid" -> "compatibility";
            case "planning_node_budget", "planning_time_budget", "planning_server_budget",
                 "planning_upload_limit", "planning_budget_exhausted" -> "budget";
            case "planning_no_recipe" -> "no_recipe";
            case "planning_cycle_only" -> "cycle";
            case "planning_materials_changed", "planning_ingredient_invalid" -> "materials";
            case "order_inventory_unsupported" -> "delivery";
            default -> "unknown";
        };
        if (loading) return "loading";
        if (ready) return "ready";
        return switch (outcome)
        {
            case SEARCHING -> "loading";
            case MISSING_INPUTS -> "materials";
            case NO_RECIPE -> "no_recipe";
            case CYCLE -> "cycle";
            case BUDGET_EXHAUSTED -> "budget";
            case STALE -> "stale";
            default -> "unknown";
        };
    }

    public static String execution(String status, String stored)
    {
        if (status.equals("COMPLETE")) return "complete";
        if (status.equals("CANCELLED")) return "cancelled";
        String key = OrderStatusMessage.decode(stored).translationKey()
                .replace("gui.beyond_craftlines.order_message.", "");
        return switch (key)
        {
            case "resource_filter_blocked" -> "filter";
            case "network_unavailable", "waiting_network" -> "network";
            case "waiting_owner_online", "waiting_inventory_space", "waiting_dashboard_container",
                 "waiting_dashboard_container_space", "inventory_delivery_unsupported" -> "delivery";
            case "waiting_final_output" -> "final_output";
            case "dynamic_output_requires_manual" -> "dynamic";
            case "bound_machine_unavailable" -> "enable_type";
            case "native_furnace_unavailable" -> "furnace";
            case "provisioner_group_unassigned", "input_group_unassigned" -> "assign_group";
            case "provisioner_no_room", "provisioner_delivery_rolled_back" -> "provisioner_space";
            case "crafting_network_full", "crafting_rolled_back", "waiting_return_reserved",
                 "execution_failed_returning" -> "network_space";
            case "blocking_machine_input", "blocking_native_furnace_input" -> "blocking";
            case "native_furnace_output_clear", "bound_machine_byproducts_clear" -> "clear_output";
            case "bound_machine_removed", "provisioner_removed", "native_furnace_removed",
                 "native_furnace_type_changed", "provisioner_assignment_changed" -> "restore_binding";
            case "matching_provisioner_inputs", "matching_machine_inputs", "waiting_resource",
                 "crafting_waiting_inputs", "crafting_inputs_changed" -> "materials";
            case "provisioner_waiting_output" -> "provisioner_output";
            case "machine_processing", "native_furnace_processing" -> "machine_output";
            case "bound_machine_busy", "native_furnace_busy", "waiting_network_transaction",
                 "waiting_dependencies", "provisioner_waiting_earlier" -> "dependencies";
            case "shared_input_output_unsupported" -> "shared_io";
            case "crafting_recipe_unavailable", "crafting_invalid_batch", "crafting_simulation_failed" -> "compatibility";
            case "crafting_unexpected_output" -> "output_mismatch";
            case "virtual_crafting_interval", "bound_machine_preparing", "native_furnace_preparing",
                 "feeding_bound_machine", "feeding_native_furnace" -> "working";
            default -> status.equals("QUEUED") && key.isEmpty() ? "dependencies" : "unknown";
        };
    }
}
