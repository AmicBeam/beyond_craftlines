package com.amicbeam.beyondcraftlines.common.event;

import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "beyond_craftlines")
public final class CraftlinesEvents
{
    private CraftlinesEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        var server = event.getServer();
        com.amicbeam.beyondcraftlines.common.structure.TrialSessionTickService.tick(server);
        com.amicbeam.beyondcraftlines.common.runtime.ProductionQueueService.tick(server);
        for (var session : com.amicbeam.beyondcraftlines.common.structure.SandboxSessionSavedData
                .get(server).all())
        {
            if (com.amicbeam.beyondcraftlines.common.structure.SandboxPasteRuntimeService
                    .tick(server, session.id()))
            {
                var player = server.getPlayerList().getPlayer(session.owner());
                if (player != null && com.amicbeam.beyondcraftlines.common.structure.SandboxPlayerStateSavedData
                        .get(server).get(player.getUUID()) == null)
                    com.amicbeam.beyondcraftlines.common.structure.SandboxExitService.enter(
                            server, player, session);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            com.amicbeam.beyondcraftlines.common.structure.SandboxRuntimeService.tickPlayer(player);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if (!event.getPlayer().level().isClientSide() && event.getPlayer().getServer() != null)
        {
            DeviceBindingRegistry.removeAt(event.getPlayer().getServer(),
                    event.getPlayer().level().dimension(), event.getPos());
        }
    }
}
