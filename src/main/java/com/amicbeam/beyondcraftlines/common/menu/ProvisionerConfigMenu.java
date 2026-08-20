package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
    private final Set<ResourceLocation> candidates;
    private final Set<ResourceLocation> selected;

    public ProvisionerConfigMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, data.readBlockPos(), readTypes(data), readTypes(data));
    }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<ResourceLocation> candidates, Set<ResourceLocation> selected)
    {
        super(CraftlinesMenus.PROVISIONER.get(), id);
        this.position = position.immutable();
        this.candidates = Set.copyOf(candidates);
        this.selected = Set.copyOf(selected);
    }

    public BlockPos position() { return position; }
    public Set<ResourceLocation> candidates() { return candidates; }
    public Set<ResourceLocation> selected() { return selected; }

    public static void writeTypes(FriendlyByteBuf buffer, Set<ResourceLocation> types)
    {
        var sorted = types.stream().sorted(Comparator.comparing(ResourceLocation::toString)).limit(32).toList();
        buffer.writeVarInt(sorted.size());
        sorted.forEach(type -> buffer.writeUtf(type.toString()));
    }

    private static Set<ResourceLocation> readTypes(FriendlyByteBuf data)
    {
        int count = Math.min(32, Math.max(0, data.readVarInt()));
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        for (int i = 0; i < count; i++)
        {
            ResourceLocation type = ResourceLocation.tryParse(data.readUtf(256));
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
