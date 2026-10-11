package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private com.amicbeam.beyondcraftlines.common.data.ResourceFilter resourceFilter =
            com.amicbeam.beyondcraftlines.common.data.ResourceFilter.EMPTY;
    public static final int OPEN_FILTER = 100;
    public static final int CLOSE_FILTER = 101;
    private boolean filterEditing;
    private final net.minecraft.world.SimpleContainer filterSamples = new net.minecraft.world.SimpleContainer(
            com.amicbeam.beyondcraftlines.common.data.ResourceFilter.SIZE);

    public boolean isFilterEditing() { return filterEditing; }
    public void setFilterEditing(boolean value) { filterEditing = value; }

    private void addFilterSlots(Inventory inventory)
    {
        int count = com.amicbeam.beyondcraftlines.common.data.ResourceFilter.SIZE;
        for (int index = 0; index < count; index++)
            addSlot(new net.minecraft.world.inventory.Slot(filterSamples, index,
                    58 + index % 9 * 18, 53 + index / 9 * 18) {
                @Override public boolean isActive() { return filterEditing; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public int getMaxStackSize() { return 1; }
            });
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new net.minecraft.world.inventory.Slot(inventory, column + row * 9 + 9,
                        58 + column * 18, 152 + row * 18) {
                    @Override public boolean isActive() { return filterEditing; }
                });
        for (int column = 0; column < 9; column++)
            addSlot(new net.minecraft.world.inventory.Slot(inventory, column, 58 + column * 18, 210) {
                @Override public boolean isActive() { return filterEditing; }
            });
    }

    @Override public boolean clickMenuButton(Player player, int action)
    {
        if (!stillValid(player)) return false;
        if (action != OPEN_FILTER && action != CLOSE_FILTER) return false;
        filterEditing = action == OPEN_FILTER;
        return true;
    }

    private void persistFilter(Player player)
    {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            com.amicbeam.beyondcraftlines.common.network.ConfigureResourceFilterPayload.apply(
                    serverPlayer, this, resourceFilter);
    }

    @Override public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput clickType, Player player)
    {
        int count = com.amicbeam.beyondcraftlines.common.data.ResourceFilter.SIZE;
        if (slotId >= 0 && slotId < count)
        {
            if (!filterEditing || !stillValid(player)) return;
            ItemStack carried = getCarried();
            var key = com.amicbeam.beyondcraftlines.common.crafting.ContainerResourceMarker.mark(carried, false);
            if (button == 1 && clickType == net.minecraft.world.inventory.ContainerInput.PICKUP && !carried.isEmpty())
            {
                var contents = com.amicbeam.beyondcraftlines.common.crafting.ContainerResourceMarker.contents(carried);
                if (!contents.isEmpty()) key = contents;
            }
            setResourceFilter(resourceFilter.withEntry(slotId, key));
            persistFilter(player);
            return;
        }
        if (slotId >= count && !filterEditing) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index)
    {
        int count = com.amicbeam.beyondcraftlines.common.data.ResourceFilter.SIZE;
        if (!filterEditing || index < count || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int target = 0;
        for (int slot = 0; slot < count; slot++)
            if (resourceFilter.entries().get(slot).isEmpty()) { target = slot; break; }
        setResourceFilter(resourceFilter.withEntry(target,
                com.amicbeam.beyondcraftlines.common.crafting.ContainerResourceMarker.mark(stack, false)));
        persistFilter(player);
        return ItemStack.EMPTY;
    }

    private final BlockPos position;
    private final Set<Identifier> candidates;
    private final Set<Identifier> selected;
    private final Map<Identifier, Set<String>> availableGroups;
    private final Map<Identifier, Set<String>> selectedGroups;
    private final ContainerData provisionerData;
    private final boolean boundMachineConfiguration;
    private final int priority;
    private final boolean debugRecipeTypeMappings;
    private final Set<String> manualLoadedFamilies;
    private final Map<String, Set<String>> manualRecipeAliases;

    public ProvisionerConfigMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, readOptions(data), new SimpleContainerData(4));
        setResourceFilter(com.amicbeam.beyondcraftlines.common.data.ResourceFilter.read(data));
    }

    private ProvisionerConfigMenu(int id, Inventory inventory, Options options, ContainerData provisionerData)
    { this(id, inventory, options.position(), options.candidates(), options.selected(),
            options.availableGroups(), options.selectedGroups(), provisionerData,
            options.boundMachineConfiguration(), options.priority(), options.debugRecipeTypeMappings(),
            options.manualLoadedFamilies(), options.manualRecipeAliases()); }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<Identifier> candidates, Set<Identifier> selected,
                                 Map<Identifier, Set<String>> availableGroups,
                                 Map<Identifier, Set<String>> selectedGroups,
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
                                 Set<Identifier> candidates, Set<Identifier> selected)
    { this(id, inventory, position, candidates, selected, Map.of()); }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<Identifier> candidates, Set<Identifier> selected,
                                 Map<Identifier, Set<String>> availableGroups)
    {
        this(id, inventory, position, candidates, selected, availableGroups, Map.of(),
                new SimpleContainerData(4), true, 0);
    }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<Identifier> candidates, Set<Identifier> selected,
                                 Map<Identifier, Set<String>> availableGroups,
                                 Map<Identifier, Set<String>> selectedGroups)
    { this(id, inventory, position, candidates, selected, availableGroups, selectedGroups, 0); }

    public ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                 Set<Identifier> candidates, Set<Identifier> selected,
                                 Map<Identifier, Set<String>> availableGroups,
                                 Map<Identifier, Set<String>> selectedGroups,
                                 int priority)
    {
        this(id, inventory, position, candidates, selected, availableGroups, selectedGroups,
                new SimpleContainerData(4), true, priority);
    }

    private ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                  Set<Identifier> candidates, Set<Identifier> selected,
                                  Map<Identifier, Set<String>> availableGroups,
                                  Map<Identifier, Set<String>> selectedGroups,
                                  ContainerData provisionerData, boolean boundMachineConfiguration, int priority)
    {
        this(id, inventory, position, candidates, selected, availableGroups, selectedGroups,
                provisionerData, boundMachineConfiguration, priority, false, Set.of(), Map.of());
    }

    private ProvisionerConfigMenu(int id, Inventory inventory, BlockPos position,
                                  Set<Identifier> candidates, Set<Identifier> selected,
                                  Map<Identifier, Set<String>> availableGroups,
                                  Map<Identifier, Set<String>> selectedGroups,
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
        addFilterSlots(inventory);
        if (inventory.player.level().getServer() != null)
            resourceFilter = BindingSavedData.get(inventory.player.level().getServer())
                    .resourceFilter(inventory.player.level().dimension(), position);
        setResourceFilter(resourceFilter);
    }

    public com.amicbeam.beyondcraftlines.common.data.ResourceFilter resourceFilter() { return resourceFilter; }
    public void setResourceFilter(com.amicbeam.beyondcraftlines.common.data.ResourceFilter value)
    {
        resourceFilter = value;
        for (int slot = 0; slot < value.entries().size(); slot++)
        {
            var key = value.entries().get(slot);
            filterSamples.setItem(slot, key instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey item
                    ? item.getReadOnlyStack().copyWithCount(1) : ItemStack.EMPTY);
        }
    }
    public BlockPos position() { return position; }
    public Set<Identifier> candidates() { return candidates; }
    public Set<Identifier> selected() { return selected; }
    public Map<Identifier, Set<String>> availableGroups() { return availableGroups; }
    public Map<Identifier, Set<String>> selectedGroups() { return selectedGroups; }
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
    public boolean acceptsRecipeSelection(Set<Identifier> requested)
    { return com.amicbeam.beyondcraftlines.common.crafting.ManualRecipeSelectionPolicy
            .accepts(boundMachineConfiguration, candidates, selected, requested); }
    public int priority() { return priority; }
    public boolean debugRecipeTypeMappings() { return debugRecipeTypeMappings; }
    public Set<String> manualLoadedFamilies() { return manualLoadedFamilies; }
    public Map<String, Set<String>> manualRecipeAliases() { return manualRecipeAliases; }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<Identifier> candidates, Set<Identifier> selected,
                                    Map<Identifier, Set<String>> availableGroups,
                                    Map<Identifier, Set<String>> selectedGroups)
    { writeOptions(buffer, position, candidates, selected, availableGroups, selectedGroups, false, 0); }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<Identifier> candidates, Set<Identifier> selected,
                                    Map<Identifier, Set<String>> availableGroups,
                                    Map<Identifier, Set<String>> selectedGroups, int priority)
    { writeOptions(buffer, position, candidates, selected, availableGroups, selectedGroups, false, priority); }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<Identifier> candidates, Set<Identifier> selected,
                                    Map<Identifier, Set<String>> availableGroups,
                                    Map<Identifier, Set<String>> selectedGroups,
                                    boolean boundMachineConfiguration)
    { writeOptions(buffer, position, candidates, selected, availableGroups, selectedGroups,
            boundMachineConfiguration, 0); }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<Identifier> candidates, Set<Identifier> selected,
                                    Map<Identifier, Set<String>> availableGroups,
                                    Map<Identifier, Set<String>> selectedGroups,
                                    boolean boundMachineConfiguration, int priority)
    {
        writeOptions(buffer, position, candidates, selected, availableGroups, selectedGroups,
                boundMachineConfiguration, priority, false, Set.of(), Map.of());
    }

    public static void writeOptions(FriendlyByteBuf buffer, BlockPos position,
                                    Set<Identifier> candidates, Set<Identifier> selected,
                                    Map<Identifier, Set<String>> availableGroups,
                                    Map<Identifier, Set<String>> selectedGroups,
                                    boolean boundMachineConfiguration, int priority,
                                    boolean debugRecipeTypeMappings, Set<String> manualLoadedFamilies,
                                    Map<String, Set<String>> manualRecipeAliases)
    {
        buffer.writeBlockPos(position);
        buffer.writeBoolean(boundMachineConfiguration);
        buffer.writeInt(priority);
        var sorted = candidates.stream().sorted(Comparator.comparing(Identifier::toString)).limit(32).toList();
        buffer.writeVarInt(sorted.size());
        for (Identifier type : sorted)
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

    private static Map<Identifier, Set<String>> copyGroups(Map<Identifier, Set<String>> source)
    {
        HashMap<Identifier, Set<String>> result = new HashMap<>();
        source.forEach((type, groups) -> result.put(type, Set.copyOf(groups)));
        return Map.copyOf(result);
    }

    private static Map<String, Set<String>> copyStringGroups(Map<String, Set<String>> source)
    {
        HashMap<String, Set<String>> result = new HashMap<>();
        source.forEach((type, groups) -> result.put(type, Set.copyOf(groups)));
        return Map.copyOf(result);
    }

    private record Options(BlockPos position, Set<Identifier> candidates, Set<Identifier> selected,
                           Map<Identifier, Set<String>> availableGroups,
                           Map<Identifier, Set<String>> selectedGroups,
                           boolean boundMachineConfiguration, int priority,
                           boolean debugRecipeTypeMappings, Set<String> manualLoadedFamilies,
                           Map<String, Set<String>> manualRecipeAliases) {}


    @Override public boolean stillValid(Player player)
    {
        if (player.blockPosition().distSqr(position) > 64) return false;
        if (!boundMachineConfiguration)
            return player.level().getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity;
        if (player.level().isClientSide()) return !player.level().getBlockState(position).isAir();
        if (player.level().getServer() == null) return false;
        var binding = BindingSavedData.get(player.level().getServer()).at(player.level().dimension(), position);
        return binding != null && binding.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                && BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(position).getBlock())
                .equals(binding.lastBlockId());
    }
}
