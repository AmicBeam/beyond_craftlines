package com.amicbeam.beyondcraftlines.common.localization;

import java.util.List;

/** Stable, language-neutral encoding for persisted order status details. */
public final class OrderStatusMessage
{
    private static final String PREFIX = "@beyond_craftlines:";
    private static final char ARGUMENT_SEPARATOR = '\u001F';
    private static final String TRANSLATION_PREFIX = "gui.beyond_craftlines.order_message.";

    private OrderStatusMessage() {}

    public static String encode(String id, Object... arguments)
    {
        StringBuilder encoded = new StringBuilder(PREFIX).append(id);
        for (Object argument : arguments)
            encoded.append(ARGUMENT_SEPARATOR).append(String.valueOf(argument)
                    .replace(ARGUMENT_SEPARATOR, ' '));
        return encoded.toString();
    }

    public static Decoded decode(String stored)
    {
        if (stored == null || stored.isBlank()) return Decoded.EMPTY;
        if (stored.startsWith(PREFIX))
        {
            String[] parts = stored.substring(PREFIX.length()).split(
                    String.valueOf(ARGUMENT_SEPARATOR), -1);
            if (parts.length > 0 && !parts[0].isBlank())
                return new Decoded(TRANSLATION_PREFIX + parts[0],
                        List.of(parts).subList(1, parts.length));
        }
        return decodeLegacy(stored);
    }

    public static boolean hasId(String stored, String id)
    { return decode(stored).translationKey().equals(TRANSLATION_PREFIX + id); }

    private static Decoded decodeLegacy(String stored)
    {
        Decoded exact = switch (stored)
        {
            case "cancelled by owner" -> decoded("cancelled_by_owner");
            case "execution failed" -> decoded("execution_failed");
            case "waiting for network order transaction" -> decoded("waiting_network_transaction");
            case "network unavailable" -> decoded("network_unavailable");
            case "waiting for virtual crafting node interval" -> decoded("virtual_crafting_interval");
            case "provisioner is waiting for an earlier output" -> decoded("provisioner_waiting_earlier");
            case "waiting for matching provisioner inputs" -> decoded("matching_provisioner_inputs");
            case "provisioner delivery rolled back" -> decoded("provisioner_delivery_rolled_back");
            case "bound machine is busy" -> decoded("bound_machine_busy");
            case "generic machine automation does not support output items that are also inputs" ->
                    decoded("shared_input_output_unsupported");
            case "blocking mode: target machine still contains a recipe input" ->
                    decoded("blocking_machine_input");
            case "bound machine reserved; preparing inputs" -> decoded("bound_machine_preparing");
            case "waiting for pre-existing bound machine byproducts to clear" ->
                    decoded("bound_machine_byproducts_clear");
            case "BD network furnace is busy" -> decoded("native_furnace_busy");
            case "blocking mode: BD network furnace still contains a recipe input" ->
                    decoded("blocking_native_furnace_input");
            case "waiting for pre-existing BD network furnace output to clear" ->
                    decoded("native_furnace_output_clear");
            case "BD network furnace reserved; preparing inputs" -> decoded("native_furnace_preparing");
            case "bound machine was removed or changed" -> decoded("bound_machine_removed");
            case "feeding bound machine inputs" -> decoded("feeding_bound_machine");
            case "provisioner was removed or unbound" -> decoded("provisioner_removed");
            case "provisioner recipe assignment changed" -> decoded("provisioner_assignment_changed");
            case "BD network furnace was removed or unbound" -> decoded("native_furnace_removed");
            case "BD network furnace type changed" -> decoded("native_furnace_type_changed");
            case "feeding BD network furnace inputs" -> decoded("feeding_native_furnace");
            case "invalid crafting batch size" -> decoded("crafting_invalid_batch");
            case "waiting for matching crafting ingredients" -> decoded("crafting_waiting_inputs");
            case "recipe produced an unexpected item" -> decoded("crafting_unexpected_output");
            case "network has no room for crafting output or remaining items" ->
                    decoded("crafting_network_full");
            case "crafting ingredients changed before execution" -> decoded("crafting_inputs_changed");
            case "crafting transaction rolled back because network capacity changed" ->
                    decoded("crafting_rolled_back");
            default -> null;
        };
        if (exact != null) return exact;
        if (stored.startsWith("execution failed; waiting to return reserved materials:"))
            return decoded("execution_failed_returning");
        if (stored.startsWith("waiting to return reserved materials:"))
            return decoded("waiting_return_reserved");
        if (stored.startsWith("BD network furnace unavailable for "))
            return decoded("native_furnace_unavailable", after(stored,
                    "BD network furnace unavailable for "));
        if (stored.startsWith("bound machine unavailable for "))
            return decoded("bound_machine_unavailable", after(stored, "bound machine unavailable for "));
        if (stored.startsWith("provisioner has no room for "))
            return decoded("provisioner_no_room", after(stored, "provisioner has no room for "));
        if (stored.startsWith("provisioner inputs delivered; waiting for network output "))
            return decodedProgress("provisioner_waiting_output", stored,
                    "provisioner inputs delivered; waiting for network output ");
        if (stored.startsWith("machine processing; returned "))
            return decodedProgress("machine_processing", stored, "machine processing; returned ");
        if (stored.startsWith("waiting for provisioner output in network "))
            return decodedProgress("provisioner_waiting_output", stored,
                    "waiting for provisioner output in network ");
        if (stored.startsWith("waiting for "))
            return decoded("waiting_resource", after(stored, "waiting for "));
        if (stored.startsWith("BD network furnace processing; returned "))
            return decodedProgress("native_furnace_processing", stored,
                    "BD network furnace processing; returned ");
        if (stored.startsWith("crafting recipe is no longer available: "))
            return decoded("crafting_recipe_unavailable", after(stored,
                    "crafting recipe is no longer available: "));
        if (stored.startsWith("recipe simulation failed:")) return decoded("crafting_simulation_failed");
        return decoded("unknown");
    }

    private static String after(String value, String prefix)
    { return value.substring(prefix.length()); }

    private static Decoded decodedProgress(String id, String value, String prefix)
    {
        String progress = after(value, prefix);
        int slash = progress.indexOf('/');
        return slash < 0 ? decoded(id, progress, "?")
                : decoded(id, progress.substring(0, slash), progress.substring(slash + 1));
    }

    private static Decoded decoded(String id, String... arguments)
    { return new Decoded(TRANSLATION_PREFIX + id, List.of(arguments)); }

    public record Decoded(String translationKey, List<String> arguments)
    {
        private static final Decoded EMPTY = new Decoded("", List.of());

        public Decoded
        { arguments = List.copyOf(arguments); }

        public boolean isEmpty() { return translationKey.isEmpty(); }
    }
}
