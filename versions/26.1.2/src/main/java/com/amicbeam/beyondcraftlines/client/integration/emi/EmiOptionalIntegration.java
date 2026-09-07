package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/** EMI has no 26.1.2 artifact yet; keep the shared call sites explicitly inert. */
public final class EmiOptionalIntegration
{
    private EmiOptionalIntegration() {}
    public static boolean orderIngredientUnderMouse(double mouseX, double mouseY) { return false; }
    public static @Nullable Identifier preferredRecipe(IStackKey<?> target) { return null; }
    public static Set<Identifier> recipeTypesFor(net.minecraft.world.item.ItemStack workstation)
    { return Set.of(); }
    public static Optional<net.minecraft.network.chat.Component> recipeTypeTitle(Identifier type)
    { return Optional.empty(); }
    public static Set<Identifier> recipeTypes() { return Set.of(); }
}
