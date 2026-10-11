package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.amicbeam.beyondcraftlines.client.DashboardConfigScreen;
import com.amicbeam.beyondcraftlines.client.ProvisionerConfigScreen;
import com.amicbeam.beyondcraftlines.common.data.ResourceFilter;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

@EmiEntrypoint
public final class CraftlinesEmiPlugin implements EmiPlugin
{
    @Override public void register(EmiRegistry registry)
    {
        registry.addDragDropHandler(ProvisionerConfigScreen.class, new EmiDragDropHandler<ProvisionerConfigScreen>() {
            @Override public boolean dropStack(ProvisionerConfigScreen screen, EmiIngredient ingredient, int x, int y)
            {
                var key = EmiClientIntegration.ghostKey(ingredient);
                if (!screen.isFilterEditing() || key == null || key.isEmpty()) return false;
                for (int slot = 0; slot < ResourceFilter.SIZE; slot++)
                    if (contains(screen.filterTargetArea(slot), x, y)) { screen.setFilterEntry(slot, key); return true; }
                return false;
            }
            @Override public void render(ProvisionerConfigScreen screen, EmiIngredient ingredient,
                    GuiGraphics graphics, int x, int y, float delta)
            {
                if (!screen.isFilterEditing() || EmiClientIntegration.ghostKey(ingredient) == null) return;
                for (int slot = 0; slot < ResourceFilter.SIZE; slot++) highlight(graphics, screen.filterTargetArea(slot));
            }
        });
        registry.addDragDropHandler(DashboardConfigScreen.class, new EmiDragDropHandler<DashboardConfigScreen>() {
            @Override public boolean dropStack(DashboardConfigScreen screen, EmiIngredient ingredient, int x, int y)
            {
                var key = EmiClientIntegration.ghostKey(ingredient);
                if (key == null || key.isEmpty() || !contains(screen.ghostTargetArea(), x, y)) return false;
                screen.setGhostTarget(key); return true;
            }
            @Override public void render(DashboardConfigScreen screen, EmiIngredient ingredient,
                    GuiGraphics graphics, int x, int y, float delta)
            { if (EmiClientIntegration.ghostKey(ingredient) != null) highlight(graphics, screen.ghostTargetArea()); }
        });
    }
    private static boolean contains(Rect2i area, int x, int y)
    { return x >= area.getX() && x < area.getX() + area.getWidth()
            && y >= area.getY() && y < area.getY() + area.getHeight(); }
    private static void highlight(GuiGraphics graphics, Rect2i area)
    { graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), 0x6622BB33); }
}
