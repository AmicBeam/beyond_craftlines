package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class TrialSessionCodec
{
    private TrialSessionCodec() {}

    public static CompoundTag write(TrialSession session)
    {
        if (session == null) throw new IllegalArgumentException("session is required");
        CompoundTag tag = new CompoundTag();
        tag.putUUID("blueprint", session.blueprintId());
        tag.putUUID("owner", session.owner());
        tag.putString("status", session.state().status().name());
        tag.putLong("started", session.state().startedAt());
        tag.putLong("finish", session.state().finishAt());
        tag.putString("failure", session.state().failureReason());
        if (session.observation() != null) tag.put("observation", TrialReportCodec.write(session.observation()));
        return tag;
    }

    public static TrialSession read(CompoundTag tag)
    {
        if (tag == null || !tag.hasUUID("blueprint") || !tag.hasUUID("owner"))
            throw new IllegalArgumentException("invalid trial session");
        TrialRunState.Status status;
        try { status = TrialRunState.Status.valueOf(tag.getString("status")); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid trial status", exception); }
        TrialRunState state = new TrialRunState(status, tag.getLong("started"), tag.getLong("finish"), tag.getString("failure"));
        TrialObservation observation = tag.contains("observation")
                ? TrialReportCodec.read(tag.getCompound("observation")) : null;
        return new TrialSession(tag.getUUID("blueprint"), tag.getUUID("owner"), state, observation);
    }
}
