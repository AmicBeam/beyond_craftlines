package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CaptureServiceTest
{
    @Test
    void exposesCaptureVolumeLimit()
    {
        assertEquals(32768, CaptureService.MAX_VOLUME);
    }
}
