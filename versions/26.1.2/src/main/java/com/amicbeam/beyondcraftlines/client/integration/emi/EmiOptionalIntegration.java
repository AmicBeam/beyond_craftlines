package com.amicbeam.beyondcraftlines.client.integration.emi;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** EMI has no 26.1.2 artifact yet; keep the shared call sites explicitly inert. */
public final class EmiOptionalIntegration
{
    private EmiOptionalIntegration() {}
    public static boolean orderIngredientUnderMouse() { return false; }
    public static @Nullable Identifier preferredRecipe(IStackKey<?> target) { return null; }
}
