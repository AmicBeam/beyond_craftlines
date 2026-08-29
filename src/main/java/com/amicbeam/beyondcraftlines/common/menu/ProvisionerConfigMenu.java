package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
    private final Set<ResourceLocation> candidates;
    private final Set<ResourceLocation> selected;
    private final Map<ResourceLocation, Set<String>> availableGroups;
    private final Map<ResourceLocation, Set<String>> selectedGroups;
    private final ContainerData provisionerData;
    private final boolean boundMachineConfiguration;
    private final int priority;
    private final boolean debugRecipeTypeMappings;
    private final Set<String> manualLoadedFamilies;
    private final Map<String, Set<String>> manualRecipeAliases;

    public ProvisionerConfigMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, readOptions(data), new SimpleContainerData(4));
    }

    private ProvisionerConfigMenu(int id, Inventory inventory, Options options, ContainerData provisionerData)
    { this(id, inventory, options.position(), options.candidates(), options.selected(),
            options.availableGroups(), options.selectedGroups(), provisionerData,
            options.boundMachineConfiguration(), options.priority(), options.debugRecipeTypeMappings(),
            options.manualLoadedFamilies(), options.manualRecipeAliases()); }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                 Map<ResourceLocation, Set<String>> availableGroups,
                                 Map<ResourceLocation, Set<String>> selectedGroups,
                                 int priority,
                                 CraftlineProvisionerBlockEntity provisioner)
    {
        this(id, inventory, position, candidates, selected, availableGroups, selectedGroups,
                new ContainerData()
                {
                    @Override public int get(int index)
                    {
                        if (index == 0) return !provisioner.isEmpty() ? 1 : 0;
                        if (index == 1) return provisioner.supplyConnectionCount();
                        if (index == 2) return provisioner.extractConnectionCount();
                        return index == 3 ? provisioner.deliveryStrategy().id() : 0;
                    }
                    @Override public void set(int index, int value) {}
                    @Override public int getCount() { return 4; }
                }, false, priority);
    }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<ResourceLocation> candidates, Set<ResourceLocation> selected)
    { this(id, inventory, position, candidates, selected, Map.of()); }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                 Map<ResourceLocation, Set<String>> availableGroups)
    {
        this(id, inventory, position, candidates, selected, availableGroups, Map.of(),
                new SimpleContainerData(4), true, 0);
    }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                 Map<ResourceLocation, Set<String>> availableGroups,
                                 Map<ResourceLocation, Set<String>> selectedGroups)
    { this(id, inventory, position, candidates, selected, availableGroups, selectedGroups, 0); }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                 Map<ResourceLocation, Set<String>> availableGroups,
                                 Map<ResourceLocation, Set<String>> selectedGroups,
                                 int priority)
    {
        this(id, inventory, position, candidates, selected, availableGroups, selectedGroups,
                new SimpleContainerData(4), true, priority);
    }

    private ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                  Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                  Map<ResourceLocation, Set<String>> availableGroups,
                                  Map<ResourceLocation, Set<String>> selectedGroups,
                                  ContainerData provisionerData, boolean boundMachineConfiguration, int priority)
    {
        this(id, inventory, position, candidates, selected, availableGroups, selectedGroups,
                provisionerData, boundMachineConfiguration, priority, false, Set.of(), Map.of());
    }

    private ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                  Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                  Map<ResourceLocation, Set<String>> availableGroups,
                                  Map<ResourceLocation, Set<String>> selectedGroups,
                                  ContainerData provisionerData, boolean boundMachineConfiguration, int priority,
                                  boolean debugRecipeTypeMappings, Set<String> manualLoadedFamilies,
                                  Map<String, Set<String>> manualRecipeAliases)
    {
        super(CraftlinesMenus.PROVISIONER.get(), id);
        this.position = position.immutable();
        this.candidates = Set.copyOf(candidates);
        this.selected = Set.copyOf(selected);
        this.availableGroups = copyGroups(availableGroups);
        this.selectedGroups = copyGroups(selectedGroups);
        this.provisionerData = provisionerData;
        this.boundMachineConfiguration = boundMachineConfiguration;
        this.priority = priority;
        this.debugRecipeTypeMappings = debugRecipeTypeMappings;
        this.manualLoadedFamilies = Set.copyOf(manualLoadedFamilies);
        this.manualRecipeAliases = copyStringGroups(manualRecipeAliases);
        addDataSlots(provisionerData);
    }

    public BlockPos position() { return position; }
    public Set<ResourceLocation> candidates() { return candidates; }
    public Set<ResourceLocation> selected() { return selected; }
    public Map<ResourceLocation, Set<String>> availableGroups() { return availableGroups; }
    public Map<ResourceLocation, Set<String>> selectedGroups() { return selectedGroups; }
    public boolean hasResources() { return provisionerData.get(0) != 0; }
    public int connectedDeviceCount()
    { return supplyConnectionCount() + extractConnectionCount(); }
    public int supplyConnectionCount()
    { return provisionerData.getCount() > 1 ? provisionerData.get(1) : 0; }
    public int extractConnectionCount()
    { return provisionerData.getCount() > 2 ? provisionerData.get(2) : 0; }
    public com.amicbeam.beyondcraftlines.common.runtime.ProvisionerDeliveryStrategy deliveryStrategy()
    { return com.amicbeam.beyondcraftlines.common.runtime.ProvisionerDeliveryStrategy.fromId(
            provisionerData.getCount() > 3 ? provisionerData.get(3) : 0); }
    public boolean isBoundMachineConfiguration() { return boundMachineConfiguration; }
    public boolean allowsManualRecipeSelection()
    { return com.amicbeam.beyondcraftlines.common.crafting.ManualRecipeSelectionPolicy
            .isManualMode(boundMachineConfiguration, candidates, selected); }
    public boolean acceptsRecipeSelection(Set<ResourceLocation> requested)
    { return com.amicbeam.beyondcraftlines.common.crafting.ManualRecipeSelectionPolicy
            .accepts(boundMachineConfiguration, candidates, selected, requested); }
    public int priority() { return priority; }
    public boolean debugRecipeTypeMappings() { return debugRecipeTypeMappings; }
    public Set<String> manualLoadedFamilies() { return manualLoadedFamilies; }
    public Map<String, Set<String>> manualRecipeAliases() { return manualRecipeAliases; }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                    Map<ResourceLocation, Set<String>> availableGroups,
                                    Map<ResourceLocation, Set<String>> selectedGroups)
    { writeOptions(buffer, position, candidates, selected, availableGroups, selectedGroups, false, 0); }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                    Map<ResourceLocation, Set<String>> availableGroups,
                                    Map<ResourceLocation, Set<String>> selectedGroups, int priority)
    { writeOptions(buffer, position, candidates, selected, availableGroups, selectedGroups, false, priority); }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                    Map<ResourceLocation, Set<String>> availableGroups,
                                    Map<ResourceLocation, Set<String>> selectedGroups,
                                    boolean boundMachineConfiguration)
    { writeOptions(buffer, position, candidates, selected, availableGroups, selectedGroups,
            boundMachineConfiguration, 0); }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                    Map<ResourceLocation, Set<String>> availableGroups,
                                    Map<ResourceLocation, Set<String>> selectedGroups,
                                    boolean boundMachineConfiguration, int priority)
    {
        writeOptions(buffer, position, candidates, selected, availableGroups, selectedGroups,
                boundMachineConfiguration, priority, false, Set.of(), Map.of());
    }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<ResourceLocation> candidates, Set<ResourceLocation> selected,
                                    Map<ResourceLocation, Set<String>> availableGroups,
                                    Map<ResourceLocation, Set<String>> selectedGroups,
                                    boolean boundMachineConfiguration, int priority,
                                    boolean debugRecipeTypeMappings, Set<String> manualLoadedFamilies,
                                    Map<String, Set<String>> manualRecipeAliases)
    {
        buffer.writeBlockPos(position);
        buffer.writeBoolean(boundMachineConfiguration);
        buffer.writeInt(priority);
        var sorted = candidates.stream().sorted(Comparator.comparing(ResourceLocation::toString)).limit(32).toList();
        buffer.writeVarInt(sorted.size());
        for (ResourceLocation type : sorted)
        {
            buffer.writeUtf(type.toString());
            buffer.writeBoolean(selected.contains(type));
            writeGroups(buffer, availableGroups.getOrDefault(type, Set.of()));
            writeGroups(buffer, selectedGroups.getOrDefault(type, Set.of()));
        }
        buffer.writeBoolean(debugRecipeTypeMappings);
        var families = manualLoadedFamilies.stream().filter(ProvisionerConfigMenu::validFamily)
                .sorted().limit(2048).toList();
        buffer.writeVarInt(families.size());
        families.forEach(family -> buffer.writeUtf(family, 256));
        Set<String> sentFamilies = Set.copyOf(families);
        var aliases = manualRecipeAliases.entrySet().stream()
                .filter(entry -> validFamily(entry.getKey()))
                .filter(entry -> entry.getValue().stream().anyMatch(sentFamilies::contains))
                .sorted(Map.Entry.comparingByKey()).limit(512).toList();
        buffer.writeVarInt(aliases.size());
        for (Map.Entry<String, Set<String>> alias : aliases)
        {
            buffer.writeUtf(alias.getKey(), 256);
            var targets = alias.getValue().stream().filter(sentFamilies::contains)
                    .filter(ProvisionerConfigMenu::validFamily).sorted().limit(32).toList();
            buffer.writeVarInt(targets.size());
            targets.forEach(target -> buffer.writeUtf(target, 256));
        }
    }

    private static Options readOptions(FriendlyByteBuf data)
    {
        BlockPos position = data.readBlockPos();
        boolean boundMachineConfiguration = data.readBoolean();
        int priority = data.readInt();
        int count = Math.min(32, Math.max(0, data.readVarInt()));
        LinkedHashSet<ResourceLocation> candidates = new LinkedHashSet<>();
        LinkedHashSet<ResourceLocation> selected = new LinkedHashSet<>();
        Map<ResourceLocation, Set<String>> availableGroups = new HashMap<>();
        Map<ResourceLocation, Set<String>> selectedGroups = new HashMap<>();
        for (int i = 0; i < count; i++)
        {
            ResourceLocation type = ResourceLocation.tryParse(data.readUtf(256));
            boolean enabled = data.readBoolean();
            Set<String> available = readGroups(data);
            Set<String> groups = readGroups(data);
            if (type == null) continue;
            candidates.add(type);
            if (enabled) selected.add(type);
            availableGroups.put(type, available);
            selectedGroups.put(type, groups);
        }
        boolean debugRecipeTypeMappings = data.readBoolean();
        int familyCount = Math.min(2048, Math.max(0, data.readVarInt()));
        LinkedHashSet<String> manualLoadedFamilies = new LinkedHashSet<>();
        for (int i = 0; i < familyCount; i++)
        {
            String family = data.readUtf(256);
            if (validFamily(family)) manualLoadedFamilies.add(family);
        }
        int aliasCount = Math.min(512, Math.max(0, data.readVarInt()));
        Map<String, Set<String>> manualRecipeAliases = new HashMap<>();
        for (int i = 0; i < aliasCount; i++)
        {
            String alias = data.readUtf(256);
            int targetCount = Math.min(32, Math.max(0, data.readVarInt()));
            LinkedHashSet<String> targets = new LinkedHashSet<>();
            for (int target = 0; target < targetCount; target++)
            {
                String family = data.readUtf(256);
                if (validFamily(family)) targets.add(family);
            }
            if (validFamily(alias) && !targets.isEmpty()) manualRecipeAliases.put(alias, Set.copyOf(targets));
        }
        return new Options(position, Set.copyOf(candidates), Set.copyOf(selected),
                copyGroups(availableGroups), copyGroups(selectedGroups), boundMachineConfiguration, priority,
                debugRecipeTypeMappings, Set.copyOf(manualLoadedFamilies),
                copyStringGroups(manualRecipeAliases));
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

    private static boolean validFamily(String family)
    { return family != null && !family.isBlank() && family.length() <= 256; }

    private static Map<ResourceLocation, Set<String>> copyGroups(Map<ResourceLocation, Set<String>> source)
    {
        HashMap<ResourceLocation, Set<String>> result = new HashMap<>();
        source.forEach((type, groups) -> result.put(type, Set.copyOf(groups)));
        return Map.copyOf(result);
    }

    private static Map<String, Set<String>> copyStringGroups(Map<String, Set<String>> source)
    {
        HashMap<String, Set<String>> result = new HashMap<>();
        source.forEach((type, groups) -> result.put(type, Set.copyOf(groups)));
        return Map.copyOf(result);
    }

    private record Options(BlockPos position, Set<ResourceLocation> candidates,
                           Set<ResourceLocation> selected,
                           Map<ResourceLocation, Set<String>> availableGroups,
                           Map<ResourceLocation, Set<String>> selectedGroups,
                           boolean boundMachineConfiguration, int priority,
                           boolean debugRecipeTypeMappings, Set<String> manualLoadedFamilies,
                           Map<String, Set<String>> manualRecipeAliases) {}

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(Player player)
    {
        if (player.blockPosition().distSqr(position) > 64) return false;
        if (!boundMachineConfiguration)
            return player.level().getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity;
        if (player.level().isClientSide()) return !player.level().getBlockState(position).isAir();
        if (player.getServer() == null) return false;
        var binding = BindingSavedData.get(player.getServer()).at(player.level().dimension(), position);
        return binding != null && binding.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                && BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(position).getBlock())
                .equals(binding.lastBlockId());
    }
}
