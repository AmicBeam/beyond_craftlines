package com.amicbeam.beyondcraftlines.compat;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import net.minecraft.server.level.ServerPlayer;

public final class BeyondDimensionsCompat {
    private BeyondDimensionsCompat() {}
    public static DimensionsNet networkFor(ServerPlayer player, UnifiedStorage storage) {
        return DimensionsNet.getNetFromPlayer(player);
    }
}
