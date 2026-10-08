package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client controls shared by the JEI and Beyond Dimensions order entry points. */
public final class CraftlinesKeyMappings
{
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "controls"));

    public static final KeyMapping ORDER_HOVERED_RESOURCE = new KeyMapping(
            "key.beyond_craftlines.order_hovered_resource",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY);

    private CraftlinesKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event)
    {
        event.registerCategory(CATEGORY);
        event.register(ORDER_HOVERED_RESOURCE);
    }
}
