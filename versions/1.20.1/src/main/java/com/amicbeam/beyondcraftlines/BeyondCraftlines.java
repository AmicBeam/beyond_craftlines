package com.amicbeam.beyondcraftlines;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlocks;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesCreativeTab;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.network.CraftlinesNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BeyondCraftlines.MOD_ID)
public final class BeyondCraftlines {
    public static final String MOD_ID = "beyond_craftlines";

    public BeyondCraftlines() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        CraftlinesConfig.register();
        CraftlinesBlocks.register(modBus);
        CraftlinesBlockEntities.register(modBus);
        CraftlinesItems.register(modBus);
        CraftlinesMenus.register(modBus);
        CraftlinesCreativeTab.register(modBus);
        CraftlinesNetwork.register();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
                com.amicbeam.beyondcraftlines.client.ForgeClientBootstrap.register(modBus));
    }
}
