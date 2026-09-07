package com.amicbeam.beyondcraftlines.client.integration.emi;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Normalizes EMI's synthetic JEMI ids back to the wrapped JEI recipe id. */
final class EmiRecipeId
{
    private EmiRecipeId() {}

    static boolean isWrappedJei(@Nullable ResourceLocation id)
    {
        return id != null && "jei".equals(id.getNamespace()) && id.getPath().startsWith("/");
    }

    static @Nullable ResourceLocation normalize(@Nullable ResourceLocation id)
    {
        if (!isWrappedJei(id)) return id;
        String wrapped = unwrapPath(id.getPath());
        return wrapped == null ? null : ResourceLocation.tryParse(wrapped);
    }

    static boolean matches(@Nullable ResourceLocation emiId, @Nullable ResourceLocation recipeId)
    { return java.util.Objects.equals(normalize(emiId), recipeId); }

    static @Nullable String unwrapPath(String path)
    {
        if (path == null || !path.startsWith("/")) return null;
        String wrapped = path.substring(1);
        int separator = wrapped.indexOf('/');
        return separator <= 0 || separator == wrapped.length() - 1 ? null
                : wrapped.substring(0, separator) + ":" + wrapped.substring(separator + 1);
    }
}
