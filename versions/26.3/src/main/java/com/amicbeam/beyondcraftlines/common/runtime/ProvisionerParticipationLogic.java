package com.amicbeam.beyondcraftlines.common.runtime;

/** Keeps mixed direct-machine/provisioner waits eligible for provisioner-side extraction. */
final class ProvisionerParticipationLogic
{
    private ProvisionerParticipationLogic() {}

    static boolean shouldActivate(boolean allProvisionerRoute, boolean endpointOccupied)
    {
        // allProvisionerRoute describes how the order service ticks the wait; endpoint occupancy
        // is the authoritative signal that this particular provisioner is participating.
        return endpointOccupied;
    }
}
