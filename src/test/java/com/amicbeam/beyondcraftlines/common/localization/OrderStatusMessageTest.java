package com.amicbeam.beyondcraftlines.common.localization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrderStatusMessageTest
{
    @Test
    void encodedMessageKeepsOnlyStableCodeAndArguments()
    {
        var decoded = OrderStatusMessage.decode(OrderStatusMessage.encode(
                "machine_processing", 3, 10));
        assertEquals("gui.beyond_craftlines.order_message.machine_processing", decoded.translationKey());
        assertEquals(List.of("3", "10"), decoded.arguments());
    }

    @Test
    void legacyCancelledMessageRemainsLocalizable()
    {
        var decoded = OrderStatusMessage.decode("cancelled by owner");
        assertEquals("gui.beyond_craftlines.order_message.cancelled_by_owner", decoded.translationKey());
        assertEquals(List.of(), decoded.arguments());
        assertTrue(OrderStatusMessage.hasId("execution failed; waiting to return reserved materials: old error",
                "execution_failed_returning"));
    }

    @Test
    void legacyProgressMessageExtractsArguments()
    {
        var decoded = OrderStatusMessage.decode("machine processing; returned 4/12");
        assertEquals("gui.beyond_craftlines.order_message.machine_processing", decoded.translationKey());
        assertEquals(List.of("4", "12"), decoded.arguments());

        var provisioner = OrderStatusMessage.decode("waiting for provisioner output in network 2/8");
        assertEquals("gui.beyond_craftlines.order_message.provisioner_waiting_output",
                provisioner.translationKey());
        assertEquals(List.of("2", "8"), provisioner.arguments());
    }

    @Test
    void unknownLegacyExceptionDoesNotReachThePlayer()
    {
        var decoded = OrderStatusMessage.decode("third-party implementation detail");
        assertEquals("gui.beyond_craftlines.order_message.unknown", decoded.translationKey());
        assertEquals(List.of(), decoded.arguments());
    }
}
