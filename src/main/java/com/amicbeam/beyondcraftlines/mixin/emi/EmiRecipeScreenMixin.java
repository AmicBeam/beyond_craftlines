package com.amicbeam.beyondcraftlines.mixin.emi;

import com.amicbeam.beyondcraftlines.client.integration.emi.EmiClientIntegration;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** Adds a CraftFlow-style production button directly to EMI's recipe screen. */
@Pseudo
@Mixin(targets = "dev.emi.emi.screen.RecipeScreen", remap = false)
public abstract class EmiRecipeScreenMixin
{
    @Shadow(remap = false) private List<WidgetGroup> currentPage;
    @Shadow(remap = false) int backgroundWidth;
    @Shadow(remap = false) int x;

    @Unique private static final int beyondCraftlines$buttonWidth = 13;
    @Unique private static final int beyondCraftlines$buttonHeight = 13;
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void beyondCraftlines$renderButtons(GuiGraphics graphics, int mouseX, int mouseY,
                                                 float partialTick, CallbackInfo callback)
    {
        Screen screen = (Screen) (Object) this;
        EmiClientIntegration.beginRecipeButtonFrame(screen);
        if (currentPage == null) return;
        for (WidgetGroup group : currentPage)
        {
            if (group == null || !EmiClientIntegration.hasRecipeOrderTarget(group.recipe)) continue;
            int[] position = beyondCraftlines$findButtonPosition(group);
            int buttonX = position[0];
            int buttonY = position[1];
            EmiClientIntegration.renderRecipeButton(screen, graphics, buttonX, buttonY,
                    group.recipe, mouseX, mouseY, partialTick);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyondCraftlines$clickButton(double mouseX, double mouseY, int button,
                                               CallbackInfoReturnable<Boolean> callback)
    {
        if (button == 0 && EmiClientIntegration.orderRecipeButtonUnderMouse(
                (Screen) (Object) this, mouseX, mouseY)) callback.setReturnValue(true);
    }

    @Unique
    private int[] beyondCraftlines$findButtonPosition(WidgetGroup group)
    {
        TreeSet<Integer> columns = new TreeSet<>();
        TreeSet<Integer> rows = new TreeSet<>(Comparator.reverseOrder());
        for (Widget widget : group.widgets)
        {
            Bounds bounds = widget.getBounds();
            if (bounds.x() >= group.getWidth())
            {
                columns.add(bounds.x());
                rows.add(bounds.y());
            }
        }
        for (int localX : columns)
            for (int localY : rows)
                if (!beyondCraftlines$occupied(group, localX, localY))
                    return new int[]{group.x() + localX, group.y() + localY};
        int localX = columns.isEmpty() ? group.getWidth() + 5 : columns.last() + 14;
        int localY = rows.isEmpty() ? Math.max(0,
                group.getHeight() - beyondCraftlines$buttonHeight) : rows.first();
        int buttonX = group.x() + localX;
        int panelRight = x + backgroundWidth - 3;
        if (buttonX + beyondCraftlines$buttonWidth > panelRight
                && !beyondCraftlines$hasRightSideWidgets(group))
            buttonX = panelRight - beyondCraftlines$buttonWidth;
        return new int[]{buttonX, group.y() + localY};
    }

    @Unique
    private static boolean beyondCraftlines$occupied(WidgetGroup group, int x, int y)
    {
        for (Widget widget : group.widgets)
        {
            Bounds bounds = widget.getBounds();
            if (x < bounds.x() + bounds.width()
                    && x + beyondCraftlines$buttonWidth > bounds.x()
                    && y < bounds.y() + bounds.height()
                    && y + beyondCraftlines$buttonHeight > bounds.y()) return true;
        }
        return false;
    }

    @Unique
    private static boolean beyondCraftlines$hasRightSideWidgets(WidgetGroup group)
    {
        for (Widget widget : group.widgets)
            if (widget.getBounds().x() >= group.getWidth()) return true;
        return false;
    }

}
