package com.amicbeam.beyondcraftlines.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SubmissionFailurePayloadTest
{
    @Test void feedbackPreservesMenuNonceAndStableErrorCode()
    {
        var failure = new SubmissionFailurePayload(7, 123L, "error.beyond_craftlines.planning_stale");
        ByteBuf buffer = Unpooled.buffer();
        try
        {
            SubmissionFailurePayload.STREAM_CODEC.encode(buffer, failure);
            assertEquals(failure, SubmissionFailurePayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        }
        finally { buffer.release(); }
    }

    @Test void rejectionClassificationDoesNotExposeExceptionDetails() throws ReflectiveOperationException
    {
        var method = SubmitOrderPayload.class.getDeclaredMethod("failureKey", String.class);
        method.setAccessible(true);
        assertEquals("error.beyond_craftlines.order_failed_generic", method.invoke(null, "unexpected private implementation details"));
        assertEquals("error.beyond_craftlines.planning_stale", method.invoke(null, "client plan is missing or expired; refresh the preview"));
        assertEquals("error.beyond_craftlines.order_limit", method.invoke(null, "too many active recipe orders"));
        assertEquals("error.beyond_craftlines.order_network_capacity", method.invoke(null, "network has no room to return reserved resource: example"));
    }
}
