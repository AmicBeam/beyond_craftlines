package com.amicbeam.beyondcraftlines.client;

/** Decides whether a linker attack opens configuration without trusting a not-yet-synced client cache. */
public final class LinkerAttackPolicy
{
    private LinkerAttackPolicy() {}

    public static Action decide(boolean provisioner, boolean knownBound, boolean snapshotReady)
    {
        if (provisioner || knownBound) return Action.OPEN_AND_CANCEL_ATTACK;
        return snapshotReady ? Action.IGNORE : Action.VERIFY_WITH_SERVER;
    }

    public enum Action { OPEN_AND_CANCEL_ATTACK, VERIFY_WITH_SERVER, IGNORE }
}
