package com.amicbeam.beyondcraftlines;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlocks;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesCreativeTab;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(BeyondCraftlines.MOD_ID)
public final class BeyondCraftlines
{
    public static final String MOD_ID = "beyond_craftlines";

    public BeyondCraftlines(IEventBus modBus, ModContainer container)
    {
        CraftlinesConfig.register(container);
        CraftlinesBlocks.register(modBus);
        CraftlinesBlockEntities.register(modBus);
        CraftlinesItems.register(modBus);
        CraftlinesMenus.register(modBus);
        CraftlinesCreativeTab.register(modBus);
        modBus.addListener(CraftlineProvisionerBlockEntity::registerCapabilities);
    }
}
