package com.amicbeam.beyondcraftlines.common.data;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeTypeCycle;
import com.amicbeam.beyondcraftlines.common.runtime.BoundMachineAutomation;
import com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

public final class DeviceBindingRegistry
{
    private static final Map<UUID, ProvisionerSelection> PROVISIONER_SELECTIONS = new ConcurrentHashMap<>();
    private DeviceBindingRegistry() {}

    public static Optional<BindingRecord> find(BindingSavedData data, ResourceKey<Level> dimension, BlockPos position)
    { return Optional.ofNullable(data.at(dimension, position)); }

    public static boolean unbind(Player player, BlockPos position)
    {
        if (player.level().isClientSide() || player.getServer() == null) return false;
        BindingSavedData data = BindingSavedData.get(player.getServer());
        BindingRecord record = data.at(player.level().dimension(), position);
        if (record == null) return false;
        DimensionsNet network = DimensionsNet.getNetFromId(record.networkId());
        return network != null && network.isManager(player) && data.remove(player.level().dimension(), position);
    }

    public static BindAttempt bindMachine(Player player, BlockPos position,
                                          Set<ResourceLocation> jeiTypes)
    { return bindMachine(player, position, Direction.UP, jeiTypes); }

    public static BindAttempt bindMachine(Player player, BlockPos position, Direction clickedFace,
                                          Set<ResourceLocation> jeiTypes)
    { return bindMachine(player, position, clickedFace, jeiTypes,
            CraftlineProvisionerBlockEntity.ConnectionRole.SUPPLY); }

    public static BindAttempt bindMachine(Player player, BlockPos position, Direction clickedFace,
                                          Set<ResourceLocation> jeiTypes,
                                          CraftlineProvisionerBlockEntity.ConnectionRole connectionRole)
    {
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level))
            return BindAttempt.failure(BindFailure.INVALID_TARGET);
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null || !network.isManager(player))
            return BindAttempt.failure(BindFailure.NO_NETWORK_PERMISSION);
        if (!level.isLoaded(position)) return BindAttempt.failure(BindFailure.INVALID_TARGET);
        BindingSavedData data = BindingSavedData.get(player.getServer());
        ProvisionerSelection connectionSelection = validSelection(player.getServer(), player.getUUID(),
                network.getId(), SelectionMode.DEVICE_CONNECTION);
        if (connectionSelection != null)
            return toggleProvisionerConnection(player, level, position, clickedFace,
                    connectionSelection, connectionRole);
        BindingRecord existing = data.at(level.dimension(), position);
        if (existing != null && existing.networkId() != network.getId())
            return BindAttempt.failure(BindFailure.NO_NETWORK_PERMISSION);
        var state = level.getBlockState(position);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        ProvisionerSelection selection = validSelection(player.getServer(), player.getUUID(), network.getId(),
                SelectionMode.RECIPE_SCAN);
        if (!DeviceType.isBindableMachine(blockId.toString())
                || (selection == null && level.getBlockEntity(position) == null))
            return BindAttempt.failure(BindFailure.INVALID_TARGET);
        Set<ResourceLocation> acceptedTypes = selection == null
                ? com.amicbeam.beyondcraftlines.common.crafting.VanillaProvisionerRecipeTypes
                .directBindable(jeiTypes)
                : Set.copyOf(jeiTypes);
        if (acceptedTypes.isEmpty() || acceptedTypes.size() != jeiTypes.size())
            return BindAttempt.failure(BindFailure.UNSUPPORTED_RECIPE_TYPE);
        if (selection == null && jeiTypes.stream().anyMatch(com.amicbeam.beyondcraftlines.common.crafting
                .VanillaProvisionerRecipeTypes::isProvisionerOnly))
            return BindAttempt.failure(BindFailure.UNSUPPORTED_CAPABILITY);
        DeviceType deviceType = selection == null ? DeviceType.EXTERNAL_RECIPE_MACHINE
                : DeviceType.PROVISIONER_RECIPE_BINDING;
        if (deviceType == DeviceType.EXTERNAL_RECIPE_MACHINE
                && !BoundMachineAutomation.isAutomatable(level, position))
            return BindAttempt.failure(BindFailure.UNSUPPORTED_CAPABILITY);
        if (selection == null)
        {
            Set<String> currentTypes = existing == null ? Set.of() : existing.jeiRecipeTypes().stream()
                    .map(Object::toString).collect(java.util.stream.Collectors.toSet());
            String selectedName = RecipeTypeCycle.next(acceptedTypes.stream()
                    .map(Object::toString).toList(), currentTypes);
            ResourceLocation selectedType = acceptedTypes.stream()
                    .filter(type -> type.toString().equals(selectedName)).findFirst().orElseThrow();
            Set<String> selectedFamilies = Set.of(selectedType.toString());
            BindingRecord record = new BindingRecord(existing == null ? UUID.randomUUID() : existing.id(),
                    existing == null ? player.getUUID() : existing.owner(), network.getId(),
                    level.dimension(), position, deviceType, Set.of(selectedType), selectedFamilies, Map.of(), blockId,
                    null, null, existing == null ? "" : existing.nickname(),
                    existing != null && existing.favorite(),
                    existing == null ? 0 : existing.priority(),
                    existing == null ? level.getGameTime() : existing.boundGameTime());
            data.add(record);
            return BindAttempt.success(new BindResult(
                    deviceType, Set.of(selectedType), selectedFamilies, false, null));
        }
        ServerLevel provisionerLevel = player.getServer().getLevel(selection.dimension());
        if (provisionerLevel == null || !(provisionerLevel.getBlockEntity(selection.position())
                instanceof CraftlineProvisionerBlockEntity provisioner))
            return BindAttempt.failure(BindFailure.INVALID_TARGET);
        ItemStack targetIcon = state.getBlock().getCloneItemStack(state,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false),
                level, position, player);
        if (targetIcon.isEmpty()) targetIcon = new ItemStack(state.getBlock());
        provisioner.addRecipeCandidates(acceptedTypes, blockId, targetIcon);
        Set<ResourceLocation> candidates = provisioner.recipeCandidates();
        boolean autoSelected = candidates.size() == 1
                && data.recipeTypesForProvisioner(selection.dimension(), selection.position()).isEmpty()
                && configureProvisioner(player, provisionerLevel, selection.position(), provisioner, candidates);
        PROVISIONER_SELECTIONS.remove(player.getUUID());
        return BindAttempt.success(new BindResult(
                deviceType, acceptedTypes, com.amicbeam.beyondcraftlines.common.crafting
                .VanillaProvisionerRecipeTypes.provisionerFamilies(acceptedTypes, Set.of()),
                autoSelected, null));
    }

    private static BindAttempt toggleProvisionerConnection(Player player, ServerLevel targetLevel,
                                                           BlockPos target, Direction face,
                                                           ProvisionerSelection selection,
                                                           CraftlineProvisionerBlockEntity.ConnectionRole role)
    {
        ServerLevel provisionerLevel = player.getServer().getLevel(selection.dimension());
        if (provisionerLevel == null || !(provisionerLevel.getBlockEntity(selection.position())
                instanceof CraftlineProvisionerBlockEntity provisioner))
            return BindAttempt.failure(BindFailure.INVALID_TARGET);
        if (targetLevel.dimension().equals(selection.dimension()) && target.equals(selection.position()))
            return BindAttempt.failure(BindFailure.INVALID_TARGET);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(targetLevel.getBlockState(target).getBlock());
        if (!DeviceType.isBindableMachine(blockId.toString())
                || targetLevel.getBlockEntity(target) == null
                || !BoundMachineAutomation.isAutomatable(targetLevel, target, face))
            return BindAttempt.failure(BindFailure.UNSUPPORTED_CAPABILITY);
        var edit = provisioner.toggleWirelessConnection(targetLevel.dimension(), target, face, blockId, role);
        if (edit == CraftlineProvisionerBlockEntity.ConnectionEdit.LIMIT_REACHED)
            return BindAttempt.failure(BindFailure.CONNECTION_LIMIT);
        return BindAttempt.success(new BindResult(DeviceType.PROVISIONER_RECIPE_BINDING,
                Set.of(), Set.of(), false, edit));
    }

    public static boolean configureProvisioner(Player player, BlockPos position,
                                               Set<ResourceLocation> selectedTypes)
    { return configureProvisioner(player, position, selectedTypes, Map.of()); }

    public static boolean configureProvisioner(Player player, BlockPos position,
                                               Set<ResourceLocation> selectedTypes,
                                               Map<ResourceLocation, Set<String>> selectedGroups)
    { return configureProvisioner(player, position, selectedTypes, selectedGroups, 0); }

    public static boolean configureProvisioner(Player player, BlockPos position,
                                               Set<ResourceLocation> selectedTypes,
                                               Map<ResourceLocation, Set<String>> selectedGroups,
                                               int priority)
    { return configureProvisioner(player, position, selectedTypes, selectedGroups, priority, false); }

    public static boolean configureProvisioner(Player player, BlockPos position,
                                               Set<ResourceLocation> selectedTypes,
                                               Map<ResourceLocation, Set<String>> selectedGroups,
                                               int priority, boolean allowManualSelection)
    {
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity provisioner))
            return false;
        return configureProvisioner(player, level, position, provisioner, selectedTypes, selectedGroups,
                priority, allowManualSelection);
    }

    public static boolean configureBoundMachine(Player player, BlockPos position,
                                                Set<ResourceLocation> selectedTypes)
    { return configureBoundMachine(player, position, selectedTypes, Map.of()); }

    public static boolean configureBoundMachine(Player player, BlockPos position,
                                                Set<ResourceLocation> selectedTypes,
                                                Map<ResourceLocation, Set<String>> selectedGroups)
    { return configureBoundMachine(player, position, selectedTypes, selectedGroups, 0); }

    public static boolean configureBoundMachine(Player player, BlockPos position,
                                                Set<ResourceLocation> selectedTypes,
                                                Map<ResourceLocation, Set<String>> selectedGroups,
                                                int priority)
    {
        if (selectedTypes.stream().anyMatch(com.amicbeam.beyondcraftlines.common.crafting
                .VanillaProvisionerRecipeTypes::isProvisionerOnly)) return false;
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)
                || !level.isLoaded(position)) return false;
        BindingSavedData data = BindingSavedData.get(player.getServer());
        BindingRecord existing = data.at(level.dimension(), position);
        if (existing == null || existing.deviceType() != DeviceType.EXTERNAL_RECIPE_MACHINE) return false;
        DimensionsNet network = DimensionsNet.getNetFromId(existing.networkId());
        if (network == null || !network.isManager(player)) return false;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock());
        if (!blockId.equals(existing.lastBlockId()) || !BoundMachineAutomation.isAutomatable(level, position))
            return false;

        Set<ResourceLocation> acceptedTypes = com.amicbeam.beyondcraftlines.common.crafting
                .VanillaProvisionerRecipeTypes.directBindable(selectedTypes);
        if (acceptedTypes.size() != selectedTypes.size()) return false;
        Map<ResourceLocation, Set<String>> availableGroups = inputGroupsByJeiType(level, selectedTypes);
        if (!selectedGroups.keySet().stream().allMatch(selectedTypes::contains)) return false;
        LinkedHashMap<String, Set<String>> groupsByFamily = new LinkedHashMap<>();
        for (ResourceLocation type : selectedTypes)
        {
            Set<String> available = availableGroups.getOrDefault(type, Set.of());
            Set<String> chosen = selectedGroups.getOrDefault(type, Set.of());
            if (!available.containsAll(chosen)) return false;
            for (String family : Set.of(type.toString()))
                groupsByFamily.merge(family, com.amicbeam.beyondcraftlines.common.crafting
                        .ProvisionerInputGroupSelection.accepted(available, chosen),
                        DeviceBindingRegistry::mergeInputGroups);
        }
        data.add(new BindingRecord(existing.id(), existing.owner(), existing.networkId(),
                existing.dimension(), existing.position(), existing.deviceType(),
                acceptedTypes, Set.copyOf(groupsByFamily.keySet()), Map.copyOf(groupsByFamily), existing.lastBlockId(),
                existing.provisionerDimension(), existing.provisionerPosition(), existing.nickname(),
                existing.favorite(), priority, existing.boundGameTime()));
        return true;
    }

    private static boolean configureProvisioner(Player player, ServerLevel level, BlockPos position,
                                                CraftlineProvisionerBlockEntity provisioner,
                                                Set<ResourceLocation> selectedTypes)
    { return configureProvisioner(player, level, position, provisioner, selectedTypes, Map.of(), 0, false); }

    private static boolean configureProvisioner(Player player, ServerLevel level, BlockPos position,
                                                CraftlineProvisionerBlockEntity provisioner,
                                                Set<ResourceLocation> selectedTypes,
                                                Map<ResourceLocation, Set<String>> selectedGroups,
                                                int priority)
    { return configureProvisioner(player, level, position, provisioner, selectedTypes,
            selectedGroups, priority, false); }

    private static boolean configureProvisioner(Player player, ServerLevel level, BlockPos position,
                                                CraftlineProvisionerBlockEntity provisioner,
                                                Set<ResourceLocation> selectedTypes,
                                                Map<ResourceLocation, Set<String>> selectedGroups,
                                                int priority, boolean allowManualSelection)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(provisioner.getNetId());
        if (network == null || !network.isManager(player)) return false;
        boolean manualSelection = allowManualSelection && provisioner.recipeCandidates().isEmpty()
                && selectedTypes.size() == 1;
        if (!manualSelection && !provisioner.recipeCandidates().containsAll(selectedTypes)) return false;

        BindingSavedData data = BindingSavedData.get(player.getServer());
        if (selectedTypes.isEmpty())
        {
            provisioner.clearWirelessConnections();
            data.removeForProvisioner(level.dimension(), position);
            return true;
        }
        Set<ResourceLocation> acceptedTypes = Set.copyOf(selectedTypes);
        if (acceptedTypes.size() != selectedTypes.size()) return false;
        java.util.LinkedHashSet<String> provisionerFamilies = acceptedTypes.stream()
                .map(ResourceLocation::toString).collect(java.util.stream.Collectors.toCollection(
                        java.util.LinkedHashSet::new));
        Map<ResourceLocation, Set<String>> availableGroups = inputGroupsByJeiType(level, selectedTypes);
        if (!selectedGroups.keySet().stream().allMatch(selectedTypes::contains)) return false;
        LinkedHashMap<String, Set<String>> groupsByFamily = new LinkedHashMap<>();
        for (ResourceLocation type : selectedTypes)
        {
            Set<String> available = availableGroups.getOrDefault(type, Set.of());
            Set<String> chosen = selectedGroups.getOrDefault(type, Set.of());
            if (!available.containsAll(chosen)) return false;
            Set<String> typeFamilies = Set.of(type.toString());
            for (String family : typeFamilies)
            {
                Set<String> accepted = com.amicbeam.beyondcraftlines.common.crafting
                        .ProvisionerInputGroupSelection.accepted(available, chosen);
                groupsByFamily.merge(family, accepted, DeviceBindingRegistry::mergeInputGroups);
            }
        }
        BindingRecord existing = data.at(level.dimension(), position);
        ResourceLocation provisionerId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock());
        BindingRecord replacement = new BindingRecord(
                existing == null ? UUID.randomUUID() : existing.id(), player.getUUID(), network.getId(),
                level.dimension(), position, DeviceType.PROVISIONER_RECIPE_BINDING,
                acceptedTypes, Set.copyOf(provisionerFamilies), Map.copyOf(groupsByFamily), provisionerId,
                level.dimension(), position, existing == null ? "" : existing.nickname(),
                existing != null && existing.favorite(),
                priority,
                existing == null ? level.getGameTime() : existing.boundGameTime());
        data.replaceProvisionerBinding(level.dimension(), position, replacement);
        if (manualSelection) provisioner.addRecipeCandidates(acceptedTypes);
        return true;
    }

    public static boolean clearSelectedProvisionerRecipes(Player player)
    {
        if (player.getServer() == null) return false;
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null || !network.isManager(player)) return false;
        ProvisionerSelection selection = validSelection(player.getServer(), player.getUUID(), network.getId(),
                SelectionMode.RECIPE_SCAN);
        if (selection == null) return false;
        PROVISIONER_SELECTIONS.remove(player.getUUID());
        ServerLevel level = player.getServer().getLevel(selection.dimension());
        boolean clearedCandidates = false;
        if (level != null && level.getBlockEntity(selection.position())
                instanceof CraftlineProvisionerBlockEntity provisioner)
        {
            clearedCandidates = !provisioner.recipeCandidates().isEmpty();
            provisioner.clearRecipeCandidates();
            provisioner.clearWirelessConnections();
        }
        BindingSavedData data = BindingSavedData.get(player.getServer());
        return data.removeForProvisioner(selection.dimension(), selection.position()) || clearedCandidates;
    }

    public static boolean configurePriority(Player player, BlockPos position, int priority,
                                            boolean boundMachineConfiguration)
    {
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)
                || !level.isLoaded(position)) return false;
        BindingSavedData data = BindingSavedData.get(player.getServer());
        BindingRecord existing = data.at(level.dimension(), position);
        DeviceType expected = boundMachineConfiguration ? DeviceType.EXTERNAL_RECIPE_MACHINE
                : DeviceType.PROVISIONER_RECIPE_BINDING;
        if (existing == null || existing.deviceType() != expected) return false;
        DimensionsNet network = DimensionsNet.getNetFromId(existing.networkId());
        if (network == null || !network.isManager(player)) return false;
        if (boundMachineConfiguration)
        {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock());
            if (!blockId.equals(existing.lastBlockId()) || !BoundMachineAutomation.isAutomatable(level, position))
                return false;
        }
        else if (!(level.getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity provisioner)
                || provisioner.getNetId() != existing.networkId()) return false;
        data.add(new BindingRecord(existing.id(), existing.owner(), existing.networkId(), existing.dimension(),
                existing.position(), existing.deviceType(), existing.jeiRecipeTypes(), existing.recipeFamilies(),
                existing.provisionerInputGroups(), existing.lastBlockId(), existing.provisionerDimension(),
                existing.provisionerPosition(), existing.nickname(), existing.favorite(), priority,
                existing.boundGameTime()));
        return true;
    }

    public static boolean selectProvisioner(Player player, BlockPos position)
    {
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity provisioner))
            return false;
        DimensionsNet network = DimensionsNet.getNetFromId(provisioner.getNetId());
        if (network == null || !network.isManager(player)) return false;
        PROVISIONER_SELECTIONS.put(player.getUUID(), new ProvisionerSelection(
                level.dimension(), position.immutable(), network.getId(), SelectionMode.RECIPE_SCAN));
        return true;
    }

    public static boolean selectProvisionerConnections(Player player, BlockPos position)
    {
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity provisioner))
            return false;
        DimensionsNet network = DimensionsNet.getNetFromId(provisioner.getNetId());
        if (network == null || !network.isManager(player)
                || BindingSavedData.get(player.getServer())
                .recipeTypesForProvisioner(level.dimension(), position).isEmpty()) return false;
        PROVISIONER_SELECTIONS.put(player.getUUID(), new ProvisionerSelection(
                level.dimension(), position.immutable(), network.getId(), SelectionMode.DEVICE_CONNECTION));
        return true;
    }

    public static boolean hasProvisionerConnectionSelection(Player player)
    {
        if (player.getServer() == null) return false;
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        return network != null && validSelection(player.getServer(), player.getUUID(), network.getId(),
                SelectionMode.DEVICE_CONNECTION) != null;
    }

    public static boolean hasProvisionerRecipeSelection(Player player)
    {
        if (player.getServer() == null) return false;
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        return network != null && validSelection(player.getServer(), player.getUUID(), network.getId(),
                SelectionMode.RECIPE_SCAN) != null;
    }

    public static Optional<ConnectionSelection> connectionSelection(Player player)
    {
        if (player.getServer() == null) return Optional.empty();
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null) return Optional.empty();
        ProvisionerSelection selection = validSelection(player.getServer(), player.getUUID(), network.getId(),
                SelectionMode.DEVICE_CONNECTION);
        if (selection == null) return Optional.empty();
        ServerLevel level = player.getServer().getLevel(selection.dimension());
        if (level == null || !(level.getBlockEntity(selection.position())
                instanceof CraftlineProvisionerBlockEntity provisioner)) return Optional.empty();
        return Optional.of(new ConnectionSelection(selection.dimension(), selection.position(), provisioner));
    }

    public static SelectionClearResult useLinkerInAir(Player player)
    {
        ProvisionerSelection selected = PROVISIONER_SELECTIONS.get(player.getUUID());
        if (selected != null && selected.mode() == SelectionMode.DEVICE_CONNECTION)
        {
            PROVISIONER_SELECTIONS.remove(player.getUUID());
            return SelectionClearResult.CONNECTION_MODE_CLEARED;
        }
        return clearSelectedProvisionerRecipes(player)
                ? SelectionClearResult.RECIPES_CLEARED : SelectionClearResult.NOTHING;
    }

    public static Optional<BoundMachine> machineFor(MinecraftServer server, int networkId, String family)
    {
        return BindingSavedData.get(server).forNetwork(networkId).stream()
                .filter(record -> record.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE)
                .filter(record -> record.recipeFamilies().contains(family))
                .map(record -> validMachine(server, record)).flatMap(Optional::stream).findFirst();
    }

    public static List<BoundMachine> machinesFor(MinecraftServer server, int networkId,
                                                 String family, String inputGroup)
    {
        return BindingSavedData.get(server).forNetwork(networkId).stream()
                .filter(record -> record.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE)
                .filter(record -> record.recipeFamilies().contains(family))
                .filter(record -> record.acceptsInputGroup(family, inputGroup))
                .map(record -> validMachine(server, record)).flatMap(Optional::stream)
                .sorted(java.util.Comparator
                        .comparingInt((BoundMachine machine) -> machine.binding()
                                .inputGroupRoutingPriority(family, inputGroup))
                        .thenComparing(Comparator.comparingInt(
                                (BoundMachine machine) -> machine.binding().priority()).reversed())
                        .thenComparing(machine -> machine.binding().dimension().location().toString())
                        .thenComparingLong(machine -> machine.binding().position().asLong()))
                .toList();
    }

    public static Set<String> availableFamilies(MinecraftServer server, int networkId)
    {
        HashSet<String> result = new HashSet<>(NativeFurnaceRegistry.availableFamilies(server, networkId));
        for (BindingRecord record : BindingSavedData.get(server).forNetwork(networkId))
            if (record.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                    && validMachine(server, record).isPresent()) result.addAll(record.recipeFamilies());
            else if (record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING
                    && validProvisionerTarget(server, record).isPresent())
                record.recipeFamilies().stream().filter(record::acceptsAnyInputGroup).forEach(result::add);
        return Set.copyOf(result);
    }

    public static boolean supportsJeiType(MinecraftServer server, int networkId,
                                          ResourceLocation jeiType, String family)
    {
        for (BindingRecord record : BindingSavedData.get(server).forNetwork(networkId))
            if (record.jeiRecipeTypes().contains(jeiType) && record.recipeFamilies().contains(family)
                    && ((record.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                    && validMachine(server, record).isPresent())
                    || (record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING
                    && record.acceptsAnyInputGroup(family)
                    && validProvisionerTarget(server, record).isPresent()))) return true;
        return false;
    }

    public static Optional<ProvisionerTarget> provisionerFor(MinecraftServer server, int networkId, String family)
    {
        return BindingSavedData.get(server).forNetwork(networkId).stream()
                .filter(record -> record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING)
                .filter(record -> record.recipeFamilies().contains(family))
                .filter(record -> record.acceptsAnyInputGroup(family))
                .map(record -> validProvisionerTarget(server, record)).flatMap(Optional::stream).findFirst();
    }

    public static List<ProvisionerTarget> provisionersFor(MinecraftServer server, int networkId,
                                                          String family, String inputGroup)
    {
        return BindingSavedData.get(server).forNetwork(networkId).stream()
                .filter(record -> record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING)
                .filter(record -> record.recipeFamilies().contains(family))
                .filter(record -> record.acceptsInputGroup(family, inputGroup))
                .map(record -> validProvisionerTarget(server, record)).flatMap(Optional::stream)
                .sorted(java.util.Comparator
                        .comparingInt((ProvisionerTarget target) -> com.amicbeam.beyondcraftlines.common.crafting
                                .ProvisionerInputGroupSelection.routingPriority(
                                        target.binding().provisionerInputGroups().getOrDefault(family, Set.of()),
                                        inputGroup))
                        .thenComparing(Comparator.comparingInt(
                                (ProvisionerTarget target) -> target.binding().priority()).reversed())
                        .thenComparing(target -> target.binding().dimension().location().toString()
                                + "|" + target.binding().position().asLong())).toList();
    }

    public static Map<ResourceLocation, Set<String>> inputGroupsByJeiType(ServerLevel level,
                                                                          Set<ResourceLocation> jeiTypes)
    {
        Map<String, Set<String>> byFamily = new HashMap<>();
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.recipes().forEach(holder -> {
            String family = RecipePlanningService.family(holder);
            if (jeiTypes.stream().noneMatch(type -> type.toString().equals(family))) return;
            Set<String> groups = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                    .inputGroups(holder.value());
            byFamily.merge(family, groups, DeviceBindingRegistry::mergeInputGroups);
        });
        LinkedHashMap<ResourceLocation, Set<String>> result = new LinkedHashMap<>();
        for (ResourceLocation type : jeiTypes)
        {
            Set<String> groups = new HashSet<>();
            groups.addAll(byFamily.getOrDefault(type.toString(), Set.of()));
            result.put(type, Set.copyOf(groups));
        }
        return Map.copyOf(result);
    }

    public static Map<ResourceLocation, Set<String>> selectedGroupsByJeiType(
            ServerLevel level, Set<ResourceLocation> jeiTypes, Map<String, Set<String>> stored)
    {
        LinkedHashMap<ResourceLocation, Set<String>> result = new LinkedHashMap<>();
        for (ResourceLocation type : jeiTypes)
        {
            HashSet<String> groups = new HashSet<>();
            Set<String> values = stored.getOrDefault(type.toString(), Set.of());
            if (!values.contains(BindingRecord.ALL_INPUT_GROUPS)) groups.addAll(values);
            result.put(type, Set.copyOf(groups));
        }
        return Map.copyOf(result);
    }

    private static Set<String> mergeInputGroups(Set<String> left, Set<String> right)
    {
        if (left.contains(BindingRecord.ALL_INPUT_GROUPS) || right.contains(BindingRecord.ALL_INPUT_GROUPS))
            return Set.of(BindingRecord.ALL_INPUT_GROUPS);
        HashSet<String> merged = new HashSet<>(left);
        merged.addAll(right);
        return Set.copyOf(merged);
    }

    private static Optional<BoundMachine> validMachine(MinecraftServer server, BindingRecord record)
    {
        ServerLevel level = server.getLevel(record.dimension());
        if (level == null || !level.isLoaded(record.position())
                || !BuiltInRegistries.BLOCK.getKey(level.getBlockState(record.position()).getBlock())
                .equals(record.lastBlockId())
                || !BoundMachineAutomation.isAutomatable(level, record.position())) return Optional.empty();
        return Optional.of(new BoundMachine(record, level));
    }

    private static Optional<ProvisionerTarget> validProvisionerTarget(MinecraftServer server, BindingRecord record)
    {
        ResourceKey<Level> provisionerDimension = record.provisionerDimension() == null
                ? record.dimension() : record.provisionerDimension();
        BlockPos provisionerPosition = record.provisionerPosition() == null
                ? record.position() : record.provisionerPosition();
        ServerLevel provisionerLevel = server.getLevel(provisionerDimension);
        if (provisionerLevel == null || !provisionerLevel.isLoaded(provisionerPosition)
                || !(provisionerLevel.getBlockEntity(provisionerPosition)
                instanceof CraftlineProvisionerBlockEntity provisioner)
                || provisioner.getNetId() != record.networkId()) return Optional.empty();
        return Optional.of(new ProvisionerTarget(record, provisioner));
    }

    private static ProvisionerSelection validSelection(MinecraftServer server, UUID player, int networkId,
                                                       SelectionMode mode)
    {
        ProvisionerSelection selection = PROVISIONER_SELECTIONS.get(player);
        if (selection == null || selection.networkId() != networkId || selection.mode() != mode) return null;
        ServerLevel level = server.getLevel(selection.dimension());
        if (level == null || !level.isLoaded(selection.position())
                || !(level.getBlockEntity(selection.position()) instanceof CraftlineProvisionerBlockEntity provisioner)
                || provisioner.getNetId() != networkId) return null;
        if (mode == SelectionMode.DEVICE_CONNECTION && BindingSavedData.get(server)
                .recipeTypesForProvisioner(selection.dimension(), selection.position()).isEmpty()) return null;
        return selection;
    }

    public record BoundMachine(BindingRecord binding, ServerLevel level) {}
    public record ProvisionerTarget(BindingRecord binding, CraftlineProvisionerBlockEntity provisioner) {}
    public record ConnectionSelection(ResourceKey<Level> dimension, BlockPos position,
                                      CraftlineProvisionerBlockEntity provisioner) {}
    public record BindResult(DeviceType deviceType, Set<ResourceLocation> jeiRecipeTypes,
                             Set<String> recipeFamilies, boolean autoSelected,
                             CraftlineProvisionerBlockEntity.ConnectionEdit connectionEdit) {}
    public record BindAttempt(BindResult result, BindFailure failure)
    {
        public static BindAttempt success(BindResult result) { return new BindAttempt(result, null); }
        public static BindAttempt failure(BindFailure failure) { return new BindAttempt(null, failure); }
        public boolean isSuccess() { return result != null; }
    }
    public enum BindFailure
    {
        NO_NETWORK_PERMISSION("error.beyond_craftlines.machine_binding_no_network_permission"),
        UNSUPPORTED_RECIPE_TYPE("error.beyond_craftlines.machine_recipe_type_unknown"),
        UNSUPPORTED_CAPABILITY("error.beyond_craftlines.machine_capability_unsupported"),
        CONNECTION_LIMIT("error.beyond_craftlines.provisioner_connection_limit"),
        INVALID_TARGET("error.beyond_craftlines.machine_binding_invalid_target");

        private final String messageKey;
        BindFailure(String messageKey) { this.messageKey = messageKey; }
        public String messageKey() { return messageKey; }
    }
    public enum SelectionClearResult { RECIPES_CLEARED, CONNECTION_MODE_CLEARED, NOTHING }
    private enum SelectionMode { RECIPE_SCAN, DEVICE_CONNECTION }
    private record ProvisionerSelection(ResourceKey<Level> dimension, BlockPos position, int networkId,
                                        SelectionMode mode) {}

    public static void removeAt(MinecraftServer server, ResourceKey<Level> dimension, BlockPos position)
    {
        BindingSavedData data = BindingSavedData.get(server);
        BindingRecord record = data.at(dimension, position);
        if (record != null && record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING
                && record.provisionerDimension() != null && record.provisionerPosition() != null
                && (!dimension.equals(record.provisionerDimension())
                || !position.equals(record.provisionerPosition())))
        {
            ServerLevel provisionerLevel = server.getLevel(record.provisionerDimension());
            if (provisionerLevel != null && provisionerLevel.isLoaded(record.provisionerPosition())
                    && provisionerLevel.getBlockEntity(record.provisionerPosition())
                    instanceof CraftlineProvisionerBlockEntity)
            {
                Set<ResourceLocation> recipeTypes = data.recipeTypesForProvisioner(
                        record.provisionerDimension(), record.provisionerPosition());
                Set<String> recipeFamilies = data.recipeFamiliesForProvisioner(
                        record.provisionerDimension(), record.provisionerPosition());
                ResourceLocation provisionerId = BuiltInRegistries.BLOCK.getKey(
                        provisionerLevel.getBlockState(record.provisionerPosition()).getBlock());
                data.replaceProvisionerBinding(record.provisionerDimension(), record.provisionerPosition(),
                        new BindingRecord(record.id(), record.owner(), record.networkId(),
                                record.provisionerDimension(), record.provisionerPosition(), record.deviceType(),
                                recipeTypes, recipeFamilies, record.provisionerInputGroups(), provisionerId,
                                record.provisionerDimension(), record.provisionerPosition(), record.nickname(),
                                record.favorite(), record.priority(), record.boundGameTime()));
            }
        }
        else data.remove(dimension, position);
        data.removeForProvisioner(dimension, position);
    }
}
