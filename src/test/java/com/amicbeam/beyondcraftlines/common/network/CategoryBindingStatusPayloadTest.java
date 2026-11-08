package com.amicbeam.beyondcraftlines.common.network;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CategoryBindingStatusPayloadTest
{
    @Test void requestAndResponsePreserveTheQueryIdentityAndBindingEvidence()
    {
        var request = new RequestCategoryBindingPayload(4, 18L, "test:altar");
        var reply = new CategoryBindingStatusPayload(4, 18L, "test:altar", 6, 2, "test:machine @ test:world 1, 2, 3");
        var buffer = Unpooled.buffer();
        try
        {
            RequestCategoryBindingPayload.STREAM_CODEC.encode(buffer, request);
            CategoryBindingStatusPayload.STREAM_CODEC.encode(buffer, reply);
            assertEquals(request, RequestCategoryBindingPayload.STREAM_CODEC.decode(buffer));
            assertEquals(reply, CategoryBindingStatusPayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
            assertTrue(reply.networkAvailable());
            assertTrue(reply.manager());
            assertFalse(reply.available(), "Recorded bindings alone do not prove an available executor");
        }
        finally { buffer.release(); }
    }

    @Test void staleMenuNonceOrCategoryCannotBeAppliedToAnotherQuery()
    {
        var reply = new CategoryBindingStatusPayload(4, 18L, "test:altar", 7, 1, "");
        assertTrue(reply.available());
        assertTrue(reply.matchesRequest(4, 18L, "test:altar"));
        assertFalse(reply.matchesRequest(5, 18L, "test:altar"));
        assertFalse(reply.matchesRequest(4, 19L, "test:altar"));
        assertFalse(reply.matchesRequest(4, 18L, "test:press"));
        assertFalse(new CategoryBindingStatusPayload(4, 18L, "test:altar", 0, 0, "").networkAvailable());
    }
}
