package com.amicbeam.beyondcraftlines.common.event;

import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

@EventBusSubscriber(modid = "beyond_craftlines")
public final class CraftlinesEvents
{
    private CraftlinesEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        var server = event.getServer();
        com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderService.tick(server);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if (!event.getPlayer().level().isClientSide() && event.getPlayer().getServer() != null)
        {
            DeviceBindingRegistry.removeAt(event.getPlayer().getServer(),
                    event.getPlayer().level().dimension(), event.getPos());
            BindingVisualsPayload.broadcast((net.minecraft.server.level.ServerLevel) event.getPlayer().level());
        }
    }

    @SubscribeEvent
    public static void onNetedBlockBound(com.wintercogs.beyonddimensions.api.event.dimensionnet.NetedBlockEvent.Bound event)
    { com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry.onBound(event); }

    @SubscribeEvent
    public static void onNetedBlockUnbound(com.wintercogs.beyonddimensions.api.event.dimensionnet.NetedBlockEvent.Unbound event)
    { com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry.onUnbound(event); }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event)
    { com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry.onChunkLoad(event); }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event)
    { com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry.onChunkUnload(event); }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    { com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry.onLevelUnload(event); }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event)
    { com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.clearRecipeCache(); }
}
