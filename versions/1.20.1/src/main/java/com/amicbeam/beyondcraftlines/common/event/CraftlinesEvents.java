package com.amicbeam.beyondcraftlines.common.event;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload;
import com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderService;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "beyond_craftlines")
public final class CraftlinesEvents {
    private static net.minecraft.server.MinecraftServer recipeAliasServer;
    private CraftlinesEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu.tickServerRecipeIndex(event.getServer());
            NativeFurnaceRegistry.tick(event.getServer());
            RecipeOrderService.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("craftlines")
                .requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("rebuild_recipe_index")
                        .executes(context -> {
                            var server = context.getSource().getServer();
                            com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu
                                    .rebuildServerRecipeIndex(server);
                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                                    "command.beyond_craftlines.recipe_index_rebuild_started"), true);
                            return 1;
                        })));
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level)
            NativeFurnaceRegistry.onBlockPlaced(level, event.getPos());
    }

    @SubscribeEvent
    public static void onNetedBlockBound(
            com.wintercogs.beyonddimensions.api.event.dimensionnet.NetedBlockEvent.Bound event) {
        NativeFurnaceRegistry.onBound(event);
    }

    @SubscribeEvent
    public static void onNetedBlockUnbound(
            com.wintercogs.beyonddimensions.api.event.dimensionnet.NetedBlockEvent.Unbound event) {
        NativeFurnaceRegistry.onUnbound(event);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.getPlayer().level().isClientSide() && event.getPlayer().getServer() != null) {
            var binding = BindingSavedData.get(event.getPlayer().getServer()).at(
                    event.getPlayer().level().dimension(), event.getPos());
            if (binding != null && binding.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                    && (event.getPlayer().getMainHandItem().is(CraftlinesItems.NETWORK_LINKER.get())
                    || event.getPlayer().getOffhandItem().is(CraftlinesItems.NETWORK_LINKER.get()))) {
                event.setCanceled(true);
                return;
            }
            if (event.isCanceled()) return;
            DeviceBindingRegistry.removeAt(event.getPlayer().getServer(), event.getPlayer().level().dimension(), event.getPos());
            BindingVisualsPayload.broadcast((ServerLevel) event.getPlayer().level());
        }
    }

    @SubscribeEvent public static void onChunkLoad(ChunkEvent.Load event) { NativeFurnaceRegistry.onChunkLoad(event); }
    @SubscribeEvent public static void onChunkUnload(ChunkEvent.Unload event) { NativeFurnaceRegistry.onChunkUnload(event); }
    @SubscribeEvent public static void onLevelUnload(LevelEvent.Unload event) { NativeFurnaceRegistry.onLevelUnload(event); }
    @SubscribeEvent public static void onDatapackSync(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        if (event.getPlayer() == null) {
            if (recipeAliasServer == server)
                com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu
                        .invalidatePersistedServerIndex(server);
            RecipePlanningService.clearRecipeCache();
        }
        if (event.getPlayer() == null || recipeAliasServer != server) {
            com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry.reload(
                    server.getResourceManager());
            com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
            com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupRegistry.clear();
            recipeAliasServer = server;
        }
        var profiles = com.amicbeam.beyondcraftlines.common.network.RecipeIoProfilePayload.snapshot();
        if (event.getPlayer() != null) {
            com.amicbeam.beyondcraftlines.common.network.CraftlinesNetwork
                    .sendToPlayer(event.getPlayer(), profiles);
        } else server.getPlayerList().getPlayers().forEach(player -> {
            com.amicbeam.beyondcraftlines.common.network.CraftlinesNetwork.sendToPlayer(player, profiles);
        });
    }
}
