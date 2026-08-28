package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.dashboard.DashboardRedstoneMode;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardStockMode;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class DashboardConfigMenu extends AbstractContainerMenu
{
    public static final int SAMPLE_SLOT = 0;
    public static final int SAMPLE_SLOT_X = 9;
    public static final int SAMPLE_SLOT_Y = 30;
    private static final int PLAYER_INVENTORY_Y = 143;
    private static final int HOTBAR_Y = 201;
    private final Player player;
    private final BlockPos position;
    private final SimpleContainer sampleContainer = new SimpleContainer(1);
    private final CraftlineDashboardBlockEntity dashboard;
    private IStackKey<?> target;
    private final long desired;
    private final DashboardStockMode stockMode;
    private final DashboardRedstoneMode redstoneMode;
    private final boolean blocking;
    private final boolean recipeConfigured;
    private final long observed;
    private final String error;

    public DashboardConfigMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, data.readBlockPos(), IStackKey.STREAM_CODEC.decode((RegistryFriendlyByteBuf) data),
                data.readVarLong(), DashboardStockMode.byId(data.readUtf(16)),
                DashboardRedstoneMode.byId(data.readUtf(16)), data.readBoolean(), data.readBoolean(),
                data.readVarLong(), data.readUtf(256), null);
    }

    public DashboardConfigMenu(int id, Inventory inventory, BlockPos position,
                               CraftlineDashboardBlockEntity dashboard)
    {
        this(id, inventory, position, dashboard.target(), dashboard.desiredAmount(), dashboard.stockMode(),
                dashboard.redstoneMode(), dashboard.recipe().blockingMode(), dashboard.recipe().configured(),
                dashboard.lastObserved(), dashboard.lastError(), dashboard);
    }

    private DashboardConfigMenu(int id, Inventory inventory, BlockPos position, IStackKey<?> target,
                                long desired, DashboardStockMode stockMode,
                                DashboardRedstoneMode redstoneMode, boolean blocking,
                                boolean recipeConfigured, long observed, String error,
                                CraftlineDashboardBlockEntity dashboard)
    {
        super(CraftlinesMenus.DASHBOARD.get(), id);
        this.player = inventory.player;
        this.position = position.immutable();
        this.dashboard = dashboard;
        this.target = com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey.EMPTY;
        this.desired = desired;
        this.stockMode = stockMode;
        this.redstoneMode = redstoneMode;
        this.blocking = blocking;
        this.recipeConfigured = recipeConfigured;
        this.observed = observed;
        this.error = error == null ? "" : error;
        addSlot(new Slot(sampleContainer, 0, SAMPLE_SLOT_X + 1, SAMPLE_SLOT_Y + 1)
        {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public int getMaxStackSize() { return 1; }
        });
        setGhostTarget(target);
        addPlayerInventory(inventory);
    }

    private void addPlayerInventory(Inventory inventory)
    {
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        7 + column * 18, PLAYER_INVENTORY_Y + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, 7 + column * 18, HOTBAR_Y));
    }

    public static void write(FriendlyByteBuf buffer, BlockPos position,
                             CraftlineDashboardBlockEntity dashboard)
    {
        buffer.writeBlockPos(position);
        IStackKey.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buffer, dashboard.target());
        buffer.writeVarLong(dashboard.desiredAmount());
        buffer.writeUtf(dashboard.stockMode().id(), 16);
        buffer.writeUtf(dashboard.redstoneMode().id(), 16);
        buffer.writeBoolean(dashboard.recipe().blockingMode());
        buffer.writeBoolean(dashboard.recipe().configured());
        buffer.writeVarLong(dashboard.lastObserved());
        buffer.writeUtf(dashboard.lastError(), 256);
    }

    public BlockPos position() { return position; }
    public IStackKey<?> target() { return target; }
    public void setGhostTarget(IStackKey<?> value)
    {
        target = value == null
                ? com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey.EMPTY : value;
        ItemStack display = target instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey item
                ? item.getReadOnlyStack().copyWithCount(1) : ItemStack.EMPTY;
        sampleContainer.setItem(0, display);
    }
    public long desired() { return desired; }
    public DashboardStockMode stockMode() { return stockMode; }
    public DashboardRedstoneMode redstoneMode() { return redstoneMode; }
    public boolean blocking() { return blocking; }
    public boolean recipeConfigured() { return recipeConfigured; }
    public long observed() { return observed; }
    public String error() { return error; }

    @Override public boolean stillValid(Player player)
    {
        return player == this.player && player.blockPosition().distSqr(position) <= 64
                && player.level().getBlockEntity(position) instanceof CraftlineDashboardBlockEntity;
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player)
    {
        if (slotId == SAMPLE_SLOT)
        {
            ItemStack carried = getCarried();
            setGhostTarget(carried.isEmpty()
                    ? com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey.EMPTY
                    : new com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey(
                    carried.copyWithCount(1)));
            persistGhost(player);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index)
    {
        if (index <= SAMPLE_SLOT || index >= slots.size()) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (!stack.isEmpty())
        {
            setGhostTarget(new com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey(
                    stack.copyWithCount(1)));
            persistGhost(player);
        }
        return ItemStack.EMPTY;
    }

    private void persistGhost(Player player)
    {
        if (dashboard != null && player instanceof ServerPlayer serverPlayer)
            dashboard.configure(serverPlayer, target, desired, stockMode, redstoneMode);
    }
}
