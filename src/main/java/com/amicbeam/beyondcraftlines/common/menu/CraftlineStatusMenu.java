package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class CraftlineStatusMenu extends AbstractContainerMenu
{
    private final Player player;
    private final int networkId;
    private final InitialOrder initialOrder;

    public CraftlineStatusMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, data.readVarInt(), data.readBoolean() ? new InitialOrder(
                data.readUUID(), data.readUtf(256), IStackKey.STREAM_CODEC.decode(
                (RegistryFriendlyByteBuf) data), data.readVarLong(), data.readBoolean()) : null);
    }

    public CraftlineStatusMenu(int id, Inventory inventory, int networkId)
    { this(id, inventory, networkId, null); }

    public CraftlineStatusMenu(int id, Inventory inventory, int networkId, InitialOrder initialOrder)
    {
        super(CraftlinesMenus.STATUS.get(), id);
        this.player = inventory.player;
        this.networkId = networkId;
        this.initialOrder = initialOrder;
    }

    public int networkId() { return networkId; }
    public InitialOrder initialOrder() { return initialOrder; }

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

    public record InitialOrder(UUID id, String target, IStackKey<?> targetKey,
                               long requested, boolean blockingMode) {}
}
