package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin;
import com.amicbeam.beyondcraftlines.client.integration.emi.EmiOptionalIntegration;
import net.minecraft.client.gui.screens.Screen;

public final class GhostResourceSelection
{
    private GhostResourceSelection() {}
    public static boolean click(Screen screen, double x, double y, int button)
    {
        if (button != 2 || !(screen instanceof DashboardConfigScreen)
                && !(screen instanceof ProvisionerConfigScreen config && config.isFilterEditing())) return false;
        var key = EmiOptionalIntegration.ingredientUnderMouseKey(x, y);
        if (key == null || key.isEmpty()) key = CraftlinesJeiPlugin.ingredientUnderMouseKey();
        if (key == null || key.isEmpty()) return false;
        if (screen instanceof DashboardConfigScreen dashboard) dashboard.setGhostTarget(key);
        else ((ProvisionerConfigScreen) screen).addFilterEntry(key);
        return true;
    }
}
