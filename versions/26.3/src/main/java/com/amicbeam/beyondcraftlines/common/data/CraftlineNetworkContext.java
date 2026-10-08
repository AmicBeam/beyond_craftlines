package com.amicbeam.beyondcraftlines.common.data;

import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineStatusMenu;
import com.amicbeam.beyondcraftlines.common.menu.DashboardStatusMenu;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Server-authoritative network selection, shared by orders and recipe-viewer availability. */
public final class CraftlineNetworkContext
{
    private CraftlineNetworkContext() {}

    public static DimensionsNet resolve(ServerPlayer player)
    {
        AbstractContainerMenu menu = player.containerMenu;
        return NetworkSelectionPolicy.select(hasNetworkContext(menu), () -> menuNetwork(player, menu),
                () -> rememberedNetwork(player), () -> DimensionsNet.getPrimaryNetFromPlayer(player),
                network -> accessible(player, network));
    }

    /** Only actual menu use updates memory; background availability polls never overwrite it. */
    public static void rememberMenu(ServerPlayer player, AbstractContainerMenu menu)
    {
        DimensionsNet network = menuNetwork(player, menu);
        if (network != null && accessible(player, network))
            BindingSavedData.get(player.level().getServer()).rememberNetwork(player.getUUID(), network.getId());
    }

    private static boolean hasNetworkContext(AbstractContainerMenu menu)
    {
        return menu instanceof DimensionsNetMenu || menu instanceof CraftlineOrderMenu
                || menu instanceof CraftlineStatusMenu || menu instanceof DashboardStatusMenu;
    }

    private static DimensionsNet menuNetwork(ServerPlayer player, AbstractContainerMenu menu)
    {
        if (menu instanceof DimensionsNetMenu dimensionsMenu)
            return dimensionsMenu.storage instanceof UnifiedStorage storage ? storage.getNet() : null;
        if (menu instanceof CraftlineOrderMenu orderMenu)
            return orderMenu.canAccessNetwork(player) ? DimensionsNet.getNetFromId(orderMenu.networkId()) : null;
        if (menu instanceof CraftlineStatusMenu statusMenu)
            return DimensionsNet.getNetFromId(statusMenu.networkId());
        if (menu instanceof DashboardStatusMenu dashboardMenu)
            return DimensionsNet.getNetFromId(dashboardMenu.networkId());
        return null;
    }

    private static DimensionsNet rememberedNetwork(ServerPlayer player)
    {
        BindingSavedData data = BindingSavedData.get(player.level().getServer());
        Integer id = data.lastUsedNetwork(player.getUUID());
        if (id == null) return null;
        DimensionsNet network = DimensionsNet.getNetFromId(id);
        if (network != null && accessible(player, network)) return network;
        data.forgetNetwork(player.getUUID());
        return null;
    }

    private static boolean accessible(ServerPlayer player, DimensionsNet network)
    {
        return network.isOwner(player) || network.isManager(player)
                || network.getPlayers().contains(player.getUUID());
    }
}
