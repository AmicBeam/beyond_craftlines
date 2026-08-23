package com.amicbeam.beyondcraftlines.common.data;

import com.amicbeam.beyondcraftlines.common.crafting.JeiRecipeFamilyRegistry;
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
    {
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level))
            return BindAttempt.failure(BindFailure.INVALID_TARGET);
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null || !network.isManager(player))
            return BindAttempt.failure(BindFailure.NO_NETWORK_PERMISSION);
        if (!level.isLoaded(position)) return BindAttempt.failure(BindFailure.INVALID_TARGET);
        BindingSavedData data = BindingSavedData.get(player.getServer());
        BindingRecord existing = data.at(level.dimension(), position);
        if (existing != null && existing.networkId() != network.getId())
            return BindAttempt.failure(BindFailure.NO_NETWORK_PERMISSION);
        var state = level.getBlockState(position);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!DeviceType.isBindableMachine(blockId.toString()) || level.getBlockEntity(position) == null)
            return BindAttempt.failure(BindFailure.INVALID_TARGET);
        Set<String> loadedFamilies = level.getRecipeManager().getRecipes().stream()
                .map(RecipePlanningService::family).collect(java.util.stream.Collectors.toSet());
        var resolved = JeiRecipeFamilyRegistry.resolve(jeiTypes, loadedFamilies);
        if (resolved.isEmpty()) return BindAttempt.failure(BindFailure.UNSUPPORTED_RECIPE_TYPE);
        ProvisionerSelection selection = validSelection(player.getServer(), player.getUUID(), network.getId());
        DeviceType deviceType = selection == null ? DeviceType.EXTERNAL_RECIPE_MACHINE
                : DeviceType.PROVISIONER_RECIPE_BINDING;
        if (deviceType == DeviceType.EXTERNAL_RECIPE_MACHINE
                && !BoundMachineAutomation.isAutomatable(level, position))
            return BindAttempt.failure(BindFailure.UNSUPPORTED_CAPABILITY);
        if (selection == null)
        {
            Set<String> currentTypes = existing == null ? Set.of() : existing.jeiRecipeTypes().stream()
                    .map(Object::toString).collect(java.util.stream.Collectors.toSet());
            String selectedName = RecipeTypeCycle.next(resolved.jeiTypes().stream()
                    .map(Object::toString).toList(), currentTypes);
            ResourceLocation selectedType = resolved.jeiTypes().stream()
                    .filter(type -> type.toString().equals(selectedName)).findFirst().orElseThrow();
            var selected = JeiRecipeFamilyRegistry.resolve(Set.of(selectedType), loadedFamilies);
            BindingRecord record = new BindingRecord(existing == null ? UUID.randomUUID() : existing.id(),
                    existing == null ? player.getUUID() : existing.owner(), network.getId(),
                    level.dimension(), position, deviceType, selected.jeiTypes(), selected.families(), Map.of(), blockId,
                    null, null, existing == null ? "" : existing.nickname(),
                    existing != null && existing.favorite(),
                    existing == null ? level.getGameTime() : existing.boundGameTime());
            data.add(record);
            return BindAttempt.success(new BindResult(
                    deviceType, selected.jeiTypes(), selected.families(), false));
        }
        ServerLevel provisionerLevel = player.getServer().getLevel(selection.dimension());
        if (provisionerLevel == null || !(provisionerLevel.getBlockEntity(selection.position())
                instanceof CraftlineProvisionerBlockEntity provisioner))
            return BindAttempt.failure(BindFailure.INVALID_TARGET);
        ItemStack targetIcon = state.getBlock().getCloneItemStack(state,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false),
                level, position, player);
        if (targetIcon.isEmpty()) targetIcon = new ItemStack(state.getBlock());
        provisioner.addRecipeCandidates(resolved.jeiTypes(), blockId, targetIcon);
        Set<ResourceLocation> candidates = provisioner.recipeCandidates();
        boolean autoSelected = candidates.size() == 1
                && data.recipeTypesForProvisioner(selection.dimension(), selection.position()).isEmpty()
                && configureProvisioner(player, provisionerLevel, selection.position(), provisioner, candidates);
        PROVISIONER_SELECTIONS.remove(player.getUUID());
        return BindAttempt.success(new BindResult(
                deviceType, resolved.jeiTypes(), resolved.families(), autoSelected));
    }

    public static boolean configureProvisioner(Player player, BlockPos position,
                                               Set<ResourceLocation> selectedTypes)
    { return configureProvisioner(player, position, selectedTypes, Map.of()); }

    public static boolean configureProvisioner(Player player, BlockPos position,
                                               Set<ResourceLocation> selectedTypes,
                                               Map<ResourceLocation, Set<String>> selectedGroups)
    {
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity provisioner))
            return false;
        return configureProvisioner(player, level, position, provisioner, selectedTypes, selectedGroups);
    }

    public static boolean configureBoundMachine(Player player, BlockPos position,
                                                Set<ResourceLocation> selectedTypes)
    { return configureBoundMachine(player, position, selectedTypes, Map.of()); }

    public static boolean configureBoundMachine(Player player, BlockPos position,
                                                Set<ResourceLocation> selectedTypes,
                                                Map<ResourceLocation, Set<String>> selectedGroups)
    {
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

        Set<String> loadedFamilies = level.getRecipeManager().getRecipes().stream()
                .map(RecipePlanningService::family).collect(java.util.stream.Collectors.toSet());
        var resolved = JeiRecipeFamilyRegistry.resolve(selectedTypes, loadedFamilies);
        if ((!selectedTypes.isEmpty() && resolved.isEmpty())
                || resolved.jeiTypes().size() != selectedTypes.size()) return false;
        Map<ResourceLocation, Set<String>> availableGroups = inputGroupsByJeiType(level, selectedTypes);
        if (!selectedGroups.keySet().stream().allMatch(selectedTypes::contains)) return false;
        LinkedHashMap<String, Set<String>> groupsByFamily = new LinkedHashMap<>();
        for (ResourceLocation type : selectedTypes)
        {
            Set<String> available = availableGroups.getOrDefault(type, Set.of());
            Set<String> chosen = selectedGroups.getOrDefault(type, Set.of());
            if (!available.containsAll(chosen)) return false;
            var typeResolution = JeiRecipeFamilyRegistry.resolve(Set.of(type), loadedFamilies);
            for (String family : typeResolution.families())
                groupsByFamily.merge(family, com.amicbeam.beyondcraftlines.common.crafting
                        .ProvisionerInputGroupSelection.accepted(available, chosen),
                        DeviceBindingRegistry::mergeInputGroups);
        }
        data.add(new BindingRecord(existing.id(), existing.owner(), existing.networkId(),
                existing.dimension(), existing.position(), existing.deviceType(),
                resolved.jeiTypes(), resolved.families(), Map.copyOf(groupsByFamily), existing.lastBlockId(),
                existing.provisionerDimension(), existing.provisionerPosition(), existing.nickname(),
                existing.favorite(), existing.boundGameTime()));
        return true;
    }

    private static boolean configureProvisioner(Player player, ServerLevel level, BlockPos position,
                                                CraftlineProvisionerBlockEntity provisioner,
                                                Set<ResourceLocation> selectedTypes)
    { return configureProvisioner(player, level, position, provisioner, selectedTypes, Map.of()); }

    private static boolean configureProvisioner(Player player, ServerLevel level, BlockPos position,
                                                CraftlineProvisionerBlockEntity provisioner,
                                                Set<ResourceLocation> selectedTypes,
                                                Map<ResourceLocation, Set<String>> selectedGroups)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(provisioner.getNetId());
        if (network == null || !network.isManager(player)) return false;
        if (!provisioner.recipeCandidates().containsAll(selectedTypes)) return false;

        BindingSavedData data = BindingSavedData.get(player.getServer());
        if (selectedTypes.isEmpty())
        {
            data.removeForProvisioner(level.dimension(), position);
            return true;
        }
        Set<String> loadedFamilies = level.getRecipeManager().getRecipes().stream()
                .map(RecipePlanningService::family).collect(java.util.stream.Collectors.toSet());
        var resolved = JeiRecipeFamilyRegistry.resolve(selectedTypes, loadedFamilies);
        if (resolved.isEmpty() || resolved.jeiTypes().size() != selectedTypes.size()) return false;
        Map<ResourceLocation, Set<String>> availableGroups = inputGroupsByJeiType(level, selectedTypes);
        if (!selectedGroups.keySet().stream().allMatch(selectedTypes::contains)) return false;
        LinkedHashMap<String, Set<String>> groupsByFamily = new LinkedHashMap<>();
        for (ResourceLocation type : selectedTypes)
        {
            Set<String> available = availableGroups.getOrDefault(type, Set.of());
            Set<String> chosen = selectedGroups.getOrDefault(type, Set.of());
            if (!available.containsAll(chosen)) return false;
            var typeResolution = JeiRecipeFamilyRegistry.resolve(Set.of(type), loadedFamilies);
            for (String family : typeResolution.families())
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
                resolved.jeiTypes(), resolved.families(), Map.copyOf(groupsByFamily), provisionerId,
                level.dimension(), position, existing == null ? "" : existing.nickname(),
                existing != null && existing.favorite(),
                existing == null ? level.getGameTime() : existing.boundGameTime());
        data.replaceProvisionerBinding(level.dimension(), position, replacement);
        return true;
    }

    public static boolean clearSelectedProvisionerRecipes(Player player)
    {
        if (player.getServer() == null) return false;
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null || !network.isManager(player)) return false;
        ProvisionerSelection selection = validSelection(player.getServer(), player.getUUID(), network.getId());
        if (selection == null) return false;
        PROVISIONER_SELECTIONS.remove(player.getUUID());
        ServerLevel level = player.getServer().getLevel(selection.dimension());
        boolean clearedCandidates = false;
        if (level != null && level.getBlockEntity(selection.position())
                instanceof CraftlineProvisionerBlockEntity provisioner)
        {
            clearedCandidates = !provisioner.recipeCandidates().isEmpty();
            provisioner.clearRecipeCandidates();
        }
        BindingSavedData data = BindingSavedData.get(player.getServer());
        return data.removeForProvisioner(selection.dimension(), selection.position()) || clearedCandidates;
    }

    public static boolean selectProvisioner(Player player, BlockPos position)
    {
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity provisioner))
            return false;
        DimensionsNet network = DimensionsNet.getNetFromId(provisioner.getNetId());
        if (network == null || !network.isManager(player)) return false;
        PROVISIONER_SELECTIONS.put(player.getUUID(), new ProvisionerSelection(
                level.dimension(), position.immutable(), network.getId()));
        return true;
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
        boolean requestedTypeMatchesFamily = JeiRecipeFamilyRegistry.resolve(
                Set.of(jeiType), Set.of(family)).families().contains(family);
        if (requestedTypeMatchesFamily)
            for (BindingRecord record : BindingSavedData.get(server).forNetwork(networkId))
                if (record.recipeFamilies().contains(family)
                        && ((record.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                        && validMachine(server, record).isPresent())
                        || (record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING
                        && record.acceptsAnyInputGroup(family)
                        && validProvisionerTarget(server, record).isPresent()))) return true;
        Set<String> nativeFamilies = NativeFurnaceRegistry.availableFamilies(server, networkId);
        if ("crafting".equals(family)) nativeFamilies = Set.of("crafting");
        return JeiRecipeFamilyRegistry.resolve(Set.of(jeiType), nativeFamilies)
                .families().contains(family);
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
                        .thenComparing(target -> target.binding().dimension().location().toString()
                                + "|" + target.binding().position().asLong())).toList();
    }

    public static Map<ResourceLocation, Set<String>> inputGroupsByJeiType(ServerLevel level,
                                                                          Set<ResourceLocation> jeiTypes)
    {
        var recipes = level.getRecipeManager().getRecipes();
        Set<String> loadedFamilies = recipes.stream()
                .map(RecipePlanningService::family).collect(java.util.stream.Collectors.toSet());
        LinkedHashMap<ResourceLocation, Set<String>> familiesByType = new LinkedHashMap<>();
        Set<String> relevantFamilies = new HashSet<>();
        for (ResourceLocation type : jeiTypes)
        {
            Set<String> families = JeiRecipeFamilyRegistry.resolve(Set.of(type), loadedFamilies).families();
            familiesByType.put(type, families);
            relevantFamilies.addAll(families);
        }
        Map<String, Set<String>> byFamily = new HashMap<>();
        recipes.forEach(holder -> {
            String family = RecipePlanningService.family(holder);
            if (!relevantFamilies.contains(family)) return;
            Set<String> groups = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                    .inputGroups(holder.value());
            byFamily.merge(family, groups, DeviceBindingRegistry::mergeInputGroups);
        });
        LinkedHashMap<ResourceLocation, Set<String>> result = new LinkedHashMap<>();
        for (ResourceLocation type : jeiTypes)
        {
            Set<String> groups = new HashSet<>();
            familiesByType.getOrDefault(type, Set.of())
                    .forEach(family -> groups.addAll(byFamily.getOrDefault(family, Set.of())));
            result.put(type, Set.copyOf(groups));
        }
        return Map.copyOf(result);
    }

    public static Map<ResourceLocation, Set<String>> selectedGroupsByJeiType(
            ServerLevel level, Set<ResourceLocation> jeiTypes, Map<String, Set<String>> stored)
    {
        Set<String> loadedFamilies = level.getRecipeManager().getRecipes().stream()
                .map(RecipePlanningService::family).collect(java.util.stream.Collectors.toSet());
        LinkedHashMap<ResourceLocation, Set<String>> result = new LinkedHashMap<>();
        for (ResourceLocation type : jeiTypes)
        {
            HashSet<String> groups = new HashSet<>();
            JeiRecipeFamilyRegistry.resolve(Set.of(type), loadedFamilies).families().forEach(family -> {
                Set<String> values = stored.getOrDefault(family, Set.of());
                if (!values.contains(BindingRecord.ALL_INPUT_GROUPS)) groups.addAll(values);
            });
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

    private static ProvisionerSelection validSelection(MinecraftServer server, UUID player, int networkId)
    {
        ProvisionerSelection selection = PROVISIONER_SELECTIONS.get(player);
        if (selection == null || selection.networkId() != networkId) return null;
        ServerLevel level = server.getLevel(selection.dimension());
        if (level == null || !level.isLoaded(selection.position())
                || !(level.getBlockEntity(selection.position()) instanceof CraftlineProvisionerBlockEntity provisioner)
                || provisioner.getNetId() != networkId) return null;
        return selection;
    }

    public record BoundMachine(BindingRecord binding, ServerLevel level) {}
    public record ProvisionerTarget(BindingRecord binding, CraftlineProvisionerBlockEntity provisioner) {}
    public record BindResult(DeviceType deviceType, Set<ResourceLocation> jeiRecipeTypes,
                             Set<String> recipeFamilies, boolean autoSelected) {}
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
        INVALID_TARGET("error.beyond_craftlines.machine_binding_invalid_target");

        private final String messageKey;
        BindFailure(String messageKey) { this.messageKey = messageKey; }
        public String messageKey() { return messageKey; }
    }
    private record ProvisionerSelection(ResourceKey<Level> dimension, BlockPos position, int networkId) {}

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
                                record.favorite(), record.boundGameTime()));
            }
        }
        else data.remove(dimension, position);
        data.removeForProvisioner(dimension, position);
    }
}
