package com.amicbeam.beyondcraftlines.compat.protocol;

import net.minecraft.resources.ResourceLocation;

/** Source-compatible payload identity for the Forge 1.20.1 SimpleChannel bridge. */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
}
