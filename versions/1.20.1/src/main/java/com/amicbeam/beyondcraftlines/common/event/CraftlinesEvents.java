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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "beyond_craftlines")
public final class CraftlinesEvents {
    private CraftlinesEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) RecipeOrderService.tick(event.getServer());
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
    @SubscribeEvent public static void onDatapackSync(OnDatapackSyncEvent event) { RecipePlanningService.clearRecipeCache(); }
}
