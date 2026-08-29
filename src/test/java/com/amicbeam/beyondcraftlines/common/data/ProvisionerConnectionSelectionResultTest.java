package com.amicbeam.beyondcraftlines.common.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProvisionerConnectionSelectionResultTest
{
    @Test void missingRecipesUseTheirOwnActionableMessage()
    {
        assertEquals("error.beyond_craftlines.provisioner_connection_no_recipes",
                ProvisionerConnectionSelectionResult.NO_ENABLED_RECIPE_TYPES.messageKey());
    }

    @Test void invalidNetworkKeepsTheNetworkPermissionMessage()
    {
        assertEquals("error.beyond_craftlines.provisioner_selection_failed",
                ProvisionerConnectionSelectionResult.INVALID_NETWORK.messageKey());
    }
}
