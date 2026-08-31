package com.amicbeam.beyondcraftlines.client.integration.emi;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Normalizes EMI's synthetic JEMI ids back to the wrapped JEI recipe id. */
final class EmiRecipeId
{
    private EmiRecipeId() {}

    static @Nullable ResourceLocation normalize(@Nullable ResourceLocation id)
    {
        if (id == null || !"jei".equals(id.getNamespace()) || !id.getPath().startsWith("/")) return id;
        String wrapped = id.getPath().substring(1);
        int separator = wrapped.indexOf('/');
        return separator <= 0 || separator == wrapped.length() - 1 ? null
                : ResourceLocation.tryParse(wrapped.substring(0, separator) + ":" + wrapped.substring(separator + 1));
    }
}
