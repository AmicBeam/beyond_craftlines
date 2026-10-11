package com.amicbeam.beyondcraftlines.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RequestOrderStatusPayloadTest
{
    @Test
    void pageAndSessionSurviveTheRequestCodec()
    {
        RequestOrderStatusPayload request = new RequestOrderStatusPayload(42, UUID.randomUUID(), 3);
        ByteBuf buffer = Unpooled.buffer();
        try
        {
            RequestOrderStatusPayload.STREAM_CODEC.encode(buffer, request);
            assertEquals(request, RequestOrderStatusPayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        }
        finally { buffer.release(); }
    }
}
