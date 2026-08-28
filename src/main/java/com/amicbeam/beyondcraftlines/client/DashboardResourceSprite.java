package com.amicbeam.beyondcraftlines.client;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

import java.lang.reflect.Method;

/** Resolves BD fluid and optional chemical keys to the block-atlas sprite used by their GUI renderer. */
final class DashboardResourceSprite
{
    private DashboardResourceSprite() {}

    static Icon resolve(IStackKey<?> key)
    {
        if (key instanceof FluidStackKey fluidKey)
        {
            var stack = fluidKey.getRenderStack();
            if (stack.isEmpty()) return null;
            var extensions = IClientFluidTypeExtensions.of(stack.getFluid());
            ResourceLocation texture = extensions.getStillTexture(stack);
            return texture == null ? null : icon(texture, extensions.getTintColor(stack));
        }
        return chemical(key);
    }

    private static Icon chemical(IStackKey<?> key)
    {
        try
        {
            Object stack = key.getRenderStack();
            if (stack == null || booleanValue(stack, "isEmpty")) return null;
            Object chemical = invokeFirst(stack, "getChemical", "getType");
            if (chemical == null) return null;
            Object rawTexture = invokeFirst(chemical, "getIcon");
            Object rawTint = invokeFirst(chemical, "getTint");
            if (!(rawTexture instanceof ResourceLocation texture) || !(rawTint instanceof Number tint))
                return null;
            return icon(texture, tint.intValue());
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
        { return null; }
    }

    private static Icon icon(ResourceLocation texture, int tint)
    {
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        return sprite == null ? null : new Icon(sprite, tint);
    }

    private static boolean booleanValue(Object owner, String method)
            throws ReflectiveOperationException
    {
        Object value = owner.getClass().getMethod(method).invoke(owner);
        return value instanceof Boolean result && result;
    }

    private static Object invokeFirst(Object owner, String... names)
            throws ReflectiveOperationException
    {
        ReflectiveOperationException failure = null;
        for (String name : names)
            try
            {
                Method method = owner.getClass().getMethod(name);
                return method.invoke(owner);
            }
            catch (NoSuchMethodException exception) { failure = exception; }
        if (failure != null) throw failure;
        return null;
    }

    record Icon(TextureAtlasSprite sprite, int tint) {}
}
