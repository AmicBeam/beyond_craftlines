package com.amicbeam.beyondcraftlines.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

/** Owns the immediate buffer for camera-relative binding overlays. */
final class BindingVisualBuffers
{
    private static ByteBufferBuilder memory;
    private static MultiBufferSource.BufferSource buffers;

    private BindingVisualBuffers() {}

    static MultiBufferSource.BufferSource get()
    {
        if (buffers == null)
        {
            memory = new ByteBufferBuilder(4096);
            buffers = MultiBufferSource.immediate(memory);
        }
        return buffers;
    }

    static void release()
    {
        if (memory != null)
        {
            memory.close();
            memory = null;
            buffers = null;
        }
    }
}
