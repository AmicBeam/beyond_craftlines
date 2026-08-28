package com.amicbeam.beyondcraftlines.compat;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.amicbeam.beyondcraftlines.compat.protocol.StreamCodec;

public final class ForgePayloadCodecs {
    public static final StreamCodec<ByteBuf, UUID> UUID = StreamCodec.of(
            (buffer, value) -> friendly(buffer).writeUUID(value),
            buffer -> friendly(buffer).readUUID());
    public static final StreamCodec<ByteBuf, IStackKey<?>> STACK_KEY = StreamCodec.of(
            (buffer, key) -> IStackKey.serializeCommon(friendly(buffer), key),
            buffer -> IStackKey.deserializeCommon(friendly(buffer)));
    public static final StreamCodec<ByteBuf, BlockPos> BLOCK_POS = StreamCodec.of(
            (buffer, position) -> friendly(buffer).writeBlockPos(position),
            buffer -> friendly(buffer).readBlockPos());

    private ForgePayloadCodecs() {}

    private static FriendlyByteBuf friendly(ByteBuf buffer) {
        return buffer instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(buffer);
    }
}
