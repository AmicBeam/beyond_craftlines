package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class CraftlineStatusMenu extends AbstractContainerMenu
{
    private final Player player;
    private final int networkId;

    public CraftlineStatusMenu(int id, Inventory inventory, FriendlyByteBuf data)
    { this(id, inventory, data.readVarInt()); }

    public CraftlineStatusMenu(int id, Inventory inventory, int networkId)
    {
        super(CraftlinesMenus.STATUS.get(), id);
        this.player = inventory.player;
        this.networkId = networkId;
    }

    public int networkId() { return networkId; }

    public boolean canAccessNetwork(Player player)
    {
        if (player != this.player) return false;
        if (player.level().isClientSide()) return true;
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        return network != null && (network.isOwner(player) || network.isManager(player)
                || network.getPlayers().contains(player.getUUID()));
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return canAccessNetwork(player); }
}
