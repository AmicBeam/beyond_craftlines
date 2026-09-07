package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.common.crafting.VirtualRecipeLimits;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;

/** Complete additional outputs, shared by both virtual recipe upload paths. */
final class VirtualRecipeOutputs
{
    private VirtualRecipeOutputs() {}
    static void write(RegistryFriendlyByteBuf buffer, List<KeyAmount> outputs)
    {
        buffer.writeVarInt(outputs.size());
        for (KeyAmount output : outputs)
        {
            IStackKey.STREAM_CODEC.encode(buffer, output.key());
            buffer.writeVarLong(output.amount());
        }
    }
    static List<KeyAmount> read(RegistryFriendlyByteBuf buffer)
    {
        int count = buffer.readVarInt();
        if (count < 0 || count >= VirtualRecipeLimits.OUTPUTS)
            throw new IllegalArgumentException("invalid virtual output count");
        List<KeyAmount> outputs = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            KeyAmount output = new KeyAmount(IStackKey.STREAM_CODEC.decode(buffer), buffer.readVarLong());
            if (output.isEmpty() || output.amount() < 1) throw new IllegalArgumentException("invalid virtual output");
            outputs.add(output);
        }
        return List.copyOf(outputs);
    }
}
