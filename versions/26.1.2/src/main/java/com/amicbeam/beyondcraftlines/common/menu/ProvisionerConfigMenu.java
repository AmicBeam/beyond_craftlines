package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ProvisionerConfigMenu extends AbstractContainerMenu
{
    private final BlockPos position;
    private final Set<Identifier> candidates;
    private final Set<Identifier> selected;
    private final Map<Identifier, Set<String>> availableGroups;
    private final Map<Identifier, Set<String>> selectedGroups;
    private final ContainerData provisionerData;

    public ProvisionerConfigMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, readOptions(data), new SimpleContainerData(1));
    }

    private ProvisionerConfigMenu(int id, Inventory inventory, Options options, ContainerData provisionerData)
    { this(id, inventory, options.position(), options.candidates(), options.selected(),
            options.availableGroups(), options.selectedGroups(), provisionerData); }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<Identifier> candidates, Set<Identifier> selected,
                                 Map<Identifier, Set<String>> availableGroups,
                                 Map<Identifier, Set<String>> selectedGroups,
                                 CraftlineProvisionerBlockEntity provisioner)
    {
        this(id, inventory, position, candidates, selected, availableGroups, selectedGroups,
                new ContainerData()
                {
                    @Override public int get(int index)
                    { return index == 0 && !provisioner.isEmpty() ? 1 : 0; }
                    @Override public void set(int index, int value) {}
                    @Override public int getCount() { return 1; }
                });
    }

    private ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                  Set<Identifier> candidates, Set<Identifier> selected,
                                  Map<Identifier, Set<String>> availableGroups,
                                  Map<Identifier, Set<String>> selectedGroups,
                                  ContainerData provisionerData)
    {
        super(CraftlinesMenus.PROVISIONER.get(), id);
        this.position = position.immutable();
        this.candidates = Set.copyOf(candidates);
        this.selected = Set.copyOf(selected);
        this.availableGroups = copyGroups(availableGroups);
        this.selectedGroups = copyGroups(selectedGroups);
        this.provisionerData = provisionerData;
        addDataSlots(provisionerData);
    }

    public BlockPos position() { return position; }
    public Set<Identifier> candidates() { return candidates; }
    public Set<Identifier> selected() { return selected; }
    public Map<Identifier, Set<String>> availableGroups() { return availableGroups; }
    public Map<Identifier, Set<String>> selectedGroups() { return selectedGroups; }
    public boolean hasResources() { return provisionerData.get(0) != 0; }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<Identifier> candidates, Set<Identifier> selected,
                                    Map<Identifier, Set<String>> availableGroups,
                                    Map<Identifier, Set<String>> selectedGroups)
    {
        buffer.writeBlockPos(position);
        var sorted = candidates.stream().sorted(Comparator.comparing(Identifier::toString)).limit(32).toList();
        buffer.writeVarInt(sorted.size());
        for (Identifier type : sorted)
        {
            buffer.writeUtf(type.toString());
            buffer.writeBoolean(selected.contains(type));
            writeGroups(buffer, availableGroups.getOrDefault(type, Set.of()));
            writeGroups(buffer, selectedGroups.getOrDefault(type, Set.of()));
        }
    }

    private static Options readOptions(FriendlyByteBuf data)
    {
        BlockPos position = data.readBlockPos();
        int count = Math.min(32, Math.max(0, data.readVarInt()));
        LinkedHashSet<Identifier> candidates = new LinkedHashSet<>();
        LinkedHashSet<Identifier> selected = new LinkedHashSet<>();
        Map<Identifier, Set<String>> availableGroups = new HashMap<>();
        Map<Identifier, Set<String>> selectedGroups = new HashMap<>();
        for (int i = 0; i < count; i++)
        {
            Identifier type = Identifier.tryParse(data.readUtf(256));
            boolean enabled = data.readBoolean();
            Set<String> available = readGroups(data);
            Set<String> groups = readGroups(data);
            if (type == null) continue;
            candidates.add(type);
            if (enabled) selected.add(type);
            availableGroups.put(type, available);
            selectedGroups.put(type, groups);
        }
        return new Options(position, Set.copyOf(candidates), Set.copyOf(selected),
                copyGroups(availableGroups), copyGroups(selectedGroups));
    }

    private static void writeGroups(FriendlyByteBuf buffer, Set<String> groups)
    {
        var sorted = groups.stream().filter(ProvisionerConfigMenu::validGroup).sorted().limit(16).toList();
        buffer.writeVarInt(sorted.size());
        sorted.forEach(group -> buffer.writeUtf(group, 64));
    }

    private static Set<String> readGroups(FriendlyByteBuf data)
    {
        int count = Math.min(16, Math.max(0, data.readVarInt()));
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int i = 0; i < count; i++)
        {
            String group = data.readUtf(64);
            if (validGroup(group)) result.add(group);
        }
        return Set.copyOf(result);
    }

    private static boolean validGroup(String group)
    { return group != null && !group.isBlank() && group.length() <= 64; }

    private static Map<Identifier, Set<String>> copyGroups(Map<Identifier, Set<String>> source)
    {
        HashMap<Identifier, Set<String>> result = new HashMap<>();
        source.forEach((type, groups) -> result.put(type, Set.copyOf(groups)));
        return Map.copyOf(result);
    }

    private record Options(BlockPos position, Set<Identifier> candidates, Set<Identifier> selected,
                           Map<Identifier, Set<String>> availableGroups,
                           Map<Identifier, Set<String>> selectedGroups) {}

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(Player player)
    {
        return player.blockPosition().distSqr(position) <= 64
                && player.level().getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity;
    }
}
