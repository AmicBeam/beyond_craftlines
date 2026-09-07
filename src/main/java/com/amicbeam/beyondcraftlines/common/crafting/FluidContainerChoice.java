package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import net.minecraft.resources.ResourceLocation;

/** Encodes the fluid-proxy variant without losing the concrete container item used for simulation. */
public final class FluidContainerChoice
{
    private static final String PREFIX = "fluid_proxy/";
    private FluidContainerChoice() {}

    public static ResourceLocation proxy(ResourceLocation item)
    { return ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID,
            PREFIX + item.getNamespace() + "/" + item.getPath()); }

    public static boolean isProxy(ResourceLocation choice)
    { return choice != null && BeyondCraftlines.MOD_ID.equals(choice.getNamespace())
            && choice.getPath().startsWith(PREFIX); }

    public static boolean isProxy(String choice)
    { return choice != null && isProxy(ResourceLocation.tryParse(choice)); }

    public static ResourceLocation itemOrSelf(ResourceLocation choice)
    {
        if (!isProxy(choice)) return choice;
        String value = choice.getPath().substring(PREFIX.length());
        int separator = value.indexOf('/');
        ResourceLocation item = separator <= 0 || separator == value.length() - 1 ? null
                : ResourceLocation.tryParse(value.substring(0, separator) + ":" + value.substring(separator + 1));
        return item == null ? choice : item;
    }

    public static ResourceLocation itemOrNull(String choice)
    {
        ResourceLocation parsed = ResourceLocation.tryParse(choice);
        return parsed == null ? null : itemOrSelf(parsed);
    }
}
