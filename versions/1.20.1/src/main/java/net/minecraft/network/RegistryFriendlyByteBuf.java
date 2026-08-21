package net.minecraft.network;

import io.netty.buffer.ByteBuf;

/** 1.20.1 transport view used by the shared 1.21 payload codecs. */
public final class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    public RegistryFriendlyByteBuf(ByteBuf source) {
        super(source);
    }
}
