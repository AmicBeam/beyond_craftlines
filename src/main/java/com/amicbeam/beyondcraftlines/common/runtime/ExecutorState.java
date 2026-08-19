package com.amicbeam.beyondcraftlines.common.runtime;

public record ExecutorState(Status status, long startedAt, long finishAt, String blueprintHash) {
    public enum Status { IDLE, RUNNING, PAUSED, ERROR }
    public static ExecutorState idle() { return new ExecutorState(Status.IDLE, 0, 0, ""); }
}
