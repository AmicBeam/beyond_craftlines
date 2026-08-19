package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class TrialReportConfirmationService
{
    private TrialReportConfirmationService() {}

    public static CompiledBlueprint confirm(
            MinecraftServer server, UUID blueprintId, UUID owner, long now)
    {
        TrialSession session = TrialSessionSavedData.get(server).get(blueprintId);
        if (session == null || !session.owner().equals(owner)
                || session.state().status() != TrialRunState.Status.COMPLETE
                || session.observation() == null)
            throw new IllegalStateException("completed trial report is required");
        BlueprintRecord record = BlueprintLibrarySavedData.get(server).get(blueprintId)
                .orElseThrow(() -> new IllegalArgumentException("blueprint not found"));
        CompiledBlueprint compiled = TrialReportService.compile(record, session.observation());
        BlueprintLibrarySavedData.get(server).put(new BlueprintRecord(
                record.id(), record.owner(), record.name(), record.snapshot(),
                BlueprintRecord.State.COMPILED, compiled));
        TrialSessionSavedData.get(server).remove(blueprintId);
        return compiled;
    }
}
