package com.amicbeam.beyondcraftlines.client.integration.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

/** Adds Craftlines entry points to EMI while retaining JEI as the recipe execution backend. */
@EmiEntrypoint
public final class CraftlinesEmiPlugin implements EmiPlugin
{
    @Override
    public void register(EmiRegistry registry)
    {
        // Recipe decorators are disabled by default in production EMI. The client event bridge
        // renders the action directly on visible recipe cards so it is always available.
    }
}
