package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ProvisionerConfigMenu extends AbstractContainerMenu
{
    private final BlockPos position;
    private final Set<Identifier> candidates;
    private final Set<Identifier> selected;

    public ProvisionerConfigMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, data.readBlockPos(), readTypes(data), readTypes(data));
    }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<Identifier> candidates, Set<Identifier> selected)
    {
        super(CraftlinesMenus.PROVISIONER.get(), id);
        this.position = position.immutable();
        this.candidates = Set.copyOf(candidates);
        this.selected = Set.copyOf(selected);
    }

    public BlockPos position() { return position; }
    public Set<Identifier> candidates() { return candidates; }
    public Set<Identifier> selected() { return selected; }

    public static void writeTypes(FriendlyByteBuf buffer, Set<Identifier> types)
    {
        var sorted = types.stream().sorted(Comparator.comparing(Identifier::toString)).limit(32).toList();
        buffer.writeVarInt(sorted.size());
        sorted.forEach(type -> buffer.writeUtf(type.toString()));
    }

    private static Set<Identifier> readTypes(FriendlyByteBuf data)
    {
        int count = Math.min(32, Math.max(0, data.readVarInt()));
        LinkedHashSet<Identifier> result = new LinkedHashSet<>();
        for (int i = 0; i < count; i++)
        {
            Identifier type = Identifier.tryParse(data.readUtf(256));
            if (type != null) result.add(type);
        }
        return Set.copyOf(result);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(Player player)
    {
        return player.blockPosition().distSqr(position) <= 64
                && player.level().getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity;
    }
}
