package com.amicbeam.beyondcraftlines.mixin.emi;

import com.amicbeam.beyondcraftlines.client.integration.emi.EmiClientIntegration;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
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
    @Unique private List<RecipeButton> beyondCraftlines$buttons;

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void beyondCraftlines$renderButtons(GuiGraphics graphics, int mouseX, int mouseY,
                                                 float partialTick, CallbackInfo callback)
    {
        if (beyondCraftlines$buttons == null) beyondCraftlines$buttons = new ArrayList<>();
        else beyondCraftlines$buttons.clear();
        if (currentPage == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        for (WidgetGroup group : currentPage)
        {
            if (group == null || !EmiClientIntegration.hasRecipeOrderTarget(group.recipe)) continue;
            int[] position = beyondCraftlines$findButtonPosition(group);
            int buttonX = position[0];
            int buttonY = position[1];
            boolean hovered = mouseX >= buttonX && mouseX < buttonX + beyondCraftlines$buttonWidth
                    && mouseY >= buttonY && mouseY < buttonY + beyondCraftlines$buttonHeight;
            graphics.fill(buttonX, buttonY,
                    buttonX + beyondCraftlines$buttonWidth, buttonY + beyondCraftlines$buttonHeight,
                    hovered ? 0xFF409CFF : 0xFF0A84FF);
            graphics.fill(buttonX + 1, buttonY + 1,
                    buttonX + beyondCraftlines$buttonWidth - 1,
                    buttonY + beyondCraftlines$buttonHeight - 1,
                    hovered ? 0xFF1B7FCE : 0xFF075A9D);
            graphics.drawCenteredString(minecraft.font, "+",
                    buttonX + beyondCraftlines$buttonWidth / 2, buttonY + 2, 0xFFFFFFFF);
            beyondCraftlines$buttons.add(new RecipeButton(
                    buttonX, buttonY, group.recipe));
            if (hovered) graphics.renderTooltip(minecraft.font,
                    Component.translatable("gui.beyond_craftlines.order_from_jei"), mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyondCraftlines$clickButton(double mouseX, double mouseY, int button,
                                               CallbackInfoReturnable<Boolean> callback)
    {
        if (button != 0 || beyondCraftlines$buttons == null) return;
        for (RecipeButton recipeButton : beyondCraftlines$buttons)
            if (recipeButton.contains(mouseX, mouseY)
                    && EmiClientIntegration.orderRecipe(recipeButton.recipe()))
            {
                callback.setReturnValue(true);
                return;
            }
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

    private record RecipeButton(int x, int y, EmiRecipe recipe)
    {
        private boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= x && mouseX < x + beyondCraftlines$buttonWidth
                    && mouseY >= y && mouseY < y + beyondCraftlines$buttonHeight;
        }
    }
}
