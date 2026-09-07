package com.amicbeam.beyondcraftlines.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client controls shared by the JEI, EMI, and Beyond Dimensions order entry points. */
public final class CraftlinesKeyMappings
{
    public static final KeyMapping ORDER_HOVERED_RESOURCE = new KeyMapping(
            "key.beyond_craftlines.order_hovered_resource",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.category.beyond_craftlines.controls");

    private CraftlinesKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event)
    {
        event.register(ORDER_HOVERED_RESOURCE);
    }
}
