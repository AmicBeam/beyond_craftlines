package com.amicbeam.beyondcraftlines;

import com.amicbeam.beyondcraftlines.common.data.BlueprintComponents;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlocks;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(BeyondCraftlines.MOD_ID)
public final class BeyondCraftlines
{
    public static final String MOD_ID = "beyond_craftlines";

    public BeyondCraftlines(IEventBus modBus)
    {
        BlueprintComponents.register(modBus);
        CraftlinesBlocks.register(modBus);
        CraftlinesBlockEntities.register(modBus);
        CraftlinesItems.register(modBus);
    }
}
