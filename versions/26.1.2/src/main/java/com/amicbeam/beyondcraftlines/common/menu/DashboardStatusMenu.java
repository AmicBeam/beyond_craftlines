package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class DashboardStatusMenu extends AbstractContainerMenu
{
    private final Player player;
    private final int networkId;
    public DashboardStatusMenu(int id, Inventory inventory, FriendlyByteBuf data)
    { this(id, inventory, data.readVarInt()); }
    public DashboardStatusMenu(int id, Inventory inventory, int networkId)
    { super(CraftlinesMenus.DASHBOARD_STATUS.get(), id); this.player = inventory.player; this.networkId = networkId; }
    public int networkId() { return networkId; }
    public boolean canAccessNetwork(Player value)
    {
        if (value != player) return false;
        if (value.level().isClientSide()) return true;
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        return network != null && (network.isOwner(value) || network.isManager(value)
                || network.getPlayers().contains(value.getUUID()));
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return canAccessNetwork(player); }
}
