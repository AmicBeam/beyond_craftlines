package com.amicbeam.beyondcraftlines.common.event;

import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "beyond_craftlines")
public final class CraftlinesEvents
{
    private static net.minecraft.server.MinecraftServer recipeAliasServer;
    private CraftlinesEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        var server = event.getServer();
        com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry.tick(server);
        com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderService.tick(server);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event)
    {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry
                    .onBlockPlaced(level, event.getPos());
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event)
    {
        if (!event.getPlayer().level().isClientSide() && event.getPlayer().level().getServer() != null)
        {
            var binding = BindingSavedData.get(event.getPlayer().level().getServer()).at(
                    event.getPlayer().level().dimension(), event.getPos());
            if (binding != null && binding.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                    && (event.getPlayer().getMainHandItem().is(CraftlinesItems.NETWORK_LINKER.get())
                    || event.getPlayer().getOffhandItem().is(CraftlinesItems.NETWORK_LINKER.get())))
            {
                event.setCanceled(true);
                return;
            }
            if (event.isCanceled()) return;
            DeviceBindingRegistry.removeAt(event.getPlayer().level().getServer(),
                    event.getPlayer().level().dimension(), event.getPos());
            BindingVisualsPayload.broadcast((net.minecraft.server.level.ServerLevel) event.getPlayer().level());
        }
    }

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
    {
        var server = event.getPlayerList().getServer();
        if (event.getPlayer() == null)
        {
            if (recipeAliasServer == server)
                com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu
                        .invalidatePersistedServerIndex(server);
            com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.clearRecipeCache();
        }
        if (event.getPlayer() == null || recipeAliasServer != server)
        {
            com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
            com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupRegistry.clear();
            recipeAliasServer = server;
        }
    }
}
