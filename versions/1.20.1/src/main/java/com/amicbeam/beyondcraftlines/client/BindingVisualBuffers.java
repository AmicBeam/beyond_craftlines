package com.amicbeam.beyondcraftlines.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

/** Owns the immediate buffer for camera-relative binding overlays. */
final class BindingVisualBuffers
{
    private static MultiBufferSource.BufferSource buffers;

    private BindingVisualBuffers() {}

    static MultiBufferSource.BufferSource get()
    {
        if (buffers == null)
            buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
        return buffers;
    }

    static void release()
    {
        // Forge's BufferBuilder has no close API. Retain the single reusable buffer
        // across worlds, as vanilla RenderBuffers does, rather than allocating again.
    }
}
