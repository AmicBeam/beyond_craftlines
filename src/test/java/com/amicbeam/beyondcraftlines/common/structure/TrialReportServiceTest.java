package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TrialReportServiceTest
{
    @Test
    void rejectsNullTrialInputWithoutMinecraftObjects()
    {
        BlueprintRecord record = new BlueprintRecord(UUID.randomUUID(), UUID.randomUUID(), "test", null,
                BlueprintRecord.State.DRAFT);
        try
        {
            TrialReportService.compile(record, null);
        }
        catch (IllegalArgumentException exception)
        {
            assertEquals("trial input is required", exception.getMessage());
        }
    }
}
