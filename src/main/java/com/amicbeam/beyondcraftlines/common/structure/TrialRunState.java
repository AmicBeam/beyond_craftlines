package com.amicbeam.beyondcraftlines.common.structure;

public record TrialRunState(Status status, long startedAt, long finishAt, String failureReason)
{
    public enum Status { READY, RUNNING, COMPLETE, FAILED }

    public static TrialRunState ready()
    {
        return new TrialRunState(Status.READY, 0, 0, "");
    }

    public static TrialRunState start(long now, long duration)
    {
        if (duration < 1) throw new IllegalArgumentException("duration must be positive");
        return new TrialRunState(Status.RUNNING, now, now + duration, "");
    }

    public TrialRunState finish(long now)
    {
        if (status != Status.RUNNING || now < finishAt) return this;
        return new TrialRunState(Status.COMPLETE, startedAt, finishAt, "");
    }

    public TrialRunState fail(String reason)
    {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("failure reason is required");
        return new TrialRunState(Status.FAILED, startedAt, finishAt, reason);
    }
}
