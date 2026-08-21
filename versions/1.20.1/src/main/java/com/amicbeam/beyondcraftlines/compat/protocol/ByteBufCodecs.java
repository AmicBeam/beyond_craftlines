package com.amicbeam.beyondcraftlines.compat.protocol;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.function.IntFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public final class ByteBufCodecs {
    public static final StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.of(
            (b, v) -> friendly(b).writeVarInt(v), b -> friendly(b).readVarInt());
    public static final StreamCodec<ByteBuf, Long> VAR_LONG = StreamCodec.of(
            (b, v) -> friendly(b).writeVarLong(v), b -> friendly(b).readVarLong());
    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of(
            (b, v) -> friendly(b).writeBoolean(v), b -> friendly(b).readBoolean());
    public static final StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = StreamCodec.of(
            (b, v) -> friendly(b).writeNbt(v), b -> friendly(b).readNbt());

    private ByteBufCodecs() {}

    public static StreamCodec<ByteBuf, String> stringUtf8(int maxLength) {
        return StreamCodec.of((b, v) -> friendly(b).writeUtf(v, maxLength),
                b -> friendly(b).readUtf(maxLength));
    }

    public static <B extends ByteBuf, T, C extends Collection<T>> StreamCodec<B, C> collection(
            IntFunction<C> factory, StreamCodec<? super B, T> element, int maxSize) {
        return StreamCodec.of((buffer, values) -> {
            if (values.size() > maxSize) throw new IllegalArgumentException("collection exceeds " + maxSize);
            friendly(buffer).writeVarInt(values.size());
            for (T value : values) element.encode(buffer, value);
        }, buffer -> {
            int size = friendly(buffer).readVarInt();
            if (size < 0 || size > maxSize) throw new IllegalArgumentException("invalid collection size " + size);
            C result = factory.apply(size);
            for (int i = 0; i < size; i++) result.add(element.decode(buffer));
            return result;
        });
    }

    private static FriendlyByteBuf friendly(ByteBuf buffer) {
        return buffer instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(buffer);
    }
}
