package com.amicbeam.beyondcraftlines.common.data;

import com.amicbeam.beyondcraftlines.common.crafting.JeiRecipeFamilyRegistry;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.runtime.BoundMachineAutomation;
import com.amicbeam.beyondcraftlines.common.runtime.NativeFurnaceRegistry;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DeviceBindingRegistry
{
    private static final Map<UUID, ProvisionerSelection> PROVISIONER_SELECTIONS = new ConcurrentHashMap<>();
    private DeviceBindingRegistry() {}

    public static Optional<BindingRecord> find(BindingSavedData data, ResourceKey<Level> dimension, BlockPos position)
    { return Optional.ofNullable(data.at(dimension, position)); }

    public static boolean unbind(Player player, BlockPos position)
    {
        if (player.level().isClientSide() || player.level().getServer() == null) return false;
        BindingSavedData data = BindingSavedData.get(player.level().getServer());
        BindingRecord record = data.at(player.level().dimension(), position);
        if (record == null) return false;
        DimensionsNet network = DimensionsNet.getNetFromId(record.networkId());
        return network != null && network.isManager(player) && data.remove(player.level().dimension(), position);
    }

    public static Optional<BindResult> bindMachine(Player player, BlockPos position,
                                                   Set<Identifier> jeiTypes)
    {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) return Optional.empty();
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null || !network.isManager(player) || !level.isLoaded(position)) return Optional.empty();
        BindingSavedData data = BindingSavedData.get(level.getServer());
        BindingRecord existing = data.at(level.dimension(), position);
        if (existing != null && existing.networkId() != network.getId()) return Optional.empty();
        var state = level.getBlockState(position);
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!DeviceType.isThirdPartyMachine(blockId.toString()) || level.getBlockEntity(position) == null)
            return Optional.empty();
        Set<String> loadedFamilies = level.recipeAccess().getRecipes().stream()
                .map(RecipePlanningService::family).collect(java.util.stream.Collectors.toSet());
        var resolved = JeiRecipeFamilyRegistry.resolve(jeiTypes, loadedFamilies);
        if (resolved.isEmpty()) return Optional.empty();
        ProvisionerSelection selection = validSelection(level.getServer(), player.getUUID(), network.getId());
        DeviceType deviceType = selection == null ? DeviceType.EXTERNAL_RECIPE_MACHINE
                : DeviceType.PROVISIONER_RECIPE_BINDING;
        if (deviceType == DeviceType.EXTERNAL_RECIPE_MACHINE
                && !BoundMachineAutomation.isAutomatable(level, position)) return Optional.empty();
        if (selection == null)
        {
            BindingRecord record = new BindingRecord(UUID.randomUUID(), player.getUUID(), network.getId(),
                    level.dimension(), position, deviceType, resolved.jeiTypes(), resolved.families(), blockId,
                    null, null, "", false, level.getGameTime());
            data.add(record);
            return Optional.of(new BindResult(deviceType, resolved.families(), false));
        }
        ServerLevel provisionerLevel = level.getServer().getLevel(selection.dimension());
        if (provisionerLevel == null || !(provisionerLevel.getBlockEntity(selection.position())
                instanceof CraftlineProvisionerBlockEntity provisioner)) return Optional.empty();
        ItemStack targetIcon = new ItemStack(state.getBlock());
        provisioner.addRecipeCandidates(resolved.jeiTypes(), blockId, targetIcon);
        Set<Identifier> candidates = provisioner.recipeCandidates();
        boolean autoSelected = candidates.size() == 1
                && data.recipeTypesForProvisioner(selection.dimension(), selection.position()).isEmpty()
                && configureProvisioner(player, provisionerLevel, selection.position(), provisioner, candidates);
        PROVISIONER_SELECTIONS.remove(player.getUUID());
        return Optional.of(new BindResult(deviceType, resolved.families(), autoSelected));
    }

    public static boolean configureProvisioner(Player player, BlockPos position,
                                               Set<Identifier> selectedTypes)
    {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null
                || !(level.getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity provisioner))
            return false;
        return configureProvisioner(player, level, position, provisioner, selectedTypes);
    }

    private static boolean configureProvisioner(Player player, ServerLevel level, BlockPos position,
                                                CraftlineProvisionerBlockEntity provisioner,
                                                Set<Identifier> selectedTypes)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(provisioner.getNetId());
        if (network == null || !network.isManager(player)) return false;
        if (!provisioner.recipeCandidates().containsAll(selectedTypes)) return false;

        BindingSavedData data = BindingSavedData.get(level.getServer());
        if (selectedTypes.isEmpty())
        {
            data.removeForProvisioner(level.dimension(), position);
            return true;
        }
        Set<String> loadedFamilies = level.recipeAccess().getRecipes().stream()
                .map(RecipePlanningService::family).collect(java.util.stream.Collectors.toSet());
        var resolved = JeiRecipeFamilyRegistry.resolve(selectedTypes, loadedFamilies);
        if (resolved.isEmpty() || resolved.jeiTypes().size() != selectedTypes.size()) return false;
        BindingRecord existing = data.at(level.dimension(), position);
        Identifier provisionerId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock());
        BindingRecord replacement = new BindingRecord(
                existing == null ? UUID.randomUUID() : existing.id(), player.getUUID(), network.getId(),
                level.dimension(), position, DeviceType.PROVISIONER_RECIPE_BINDING,
                resolved.jeiTypes(), resolved.families(), provisionerId,
                level.dimension(), position, existing == null ? "" : existing.nickname(),
                existing != null && existing.favorite(),
                existing == null ? level.getGameTime() : existing.boundGameTime());
        data.replaceProvisionerBinding(level.dimension(), position, replacement);
        return true;
    }

    public static boolean clearSelectedProvisionerRecipes(Player player)
    {
        if (player.level().getServer() == null) return false;
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null || !network.isManager(player)) return false;
        ProvisionerSelection selection = validSelection(player.level().getServer(), player.getUUID(), network.getId());
        if (selection == null) return false;
        PROVISIONER_SELECTIONS.remove(player.getUUID());
        ServerLevel level = player.level().getServer().getLevel(selection.dimension());
        boolean clearedCandidates = false;
        if (level != null && level.getBlockEntity(selection.position())
                instanceof CraftlineProvisionerBlockEntity provisioner)
        {
            clearedCandidates = !provisioner.recipeCandidates().isEmpty();
            provisioner.clearRecipeCandidates();
        }
        BindingSavedData data = BindingSavedData.get(player.level().getServer());
        return data.removeForProvisioner(selection.dimension(), selection.position()) || clearedCandidates;
    }

    public static boolean selectProvisioner(Player player, BlockPos position)
    {
        if (player.level().getServer() == null || !(player.level() instanceof ServerLevel level)
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

    public static Set<String> availableFamilies(MinecraftServer server, int networkId)
    {
        HashSet<String> result = new HashSet<>(NativeFurnaceRegistry.availableFamilies(server, networkId));
        for (BindingRecord record : BindingSavedData.get(server).forNetwork(networkId))
            if ((record.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                    && validMachine(server, record).isPresent())
                    || (record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING
                    && validProvisionerTarget(server, record).isPresent())) result.addAll(record.recipeFamilies());
        return Set.copyOf(result);
    }

    public static Optional<ProvisionerTarget> provisionerFor(MinecraftServer server, int networkId, String family)
    {
        return BindingSavedData.get(server).forNetwork(networkId).stream()
                .filter(record -> record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING)
                .filter(record -> record.recipeFamilies().contains(family))
                .map(record -> validProvisionerTarget(server, record)).flatMap(Optional::stream).findFirst();
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
    public record BindResult(DeviceType deviceType, Set<String> recipeFamilies, boolean autoSelected) {}
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
                Set<Identifier> recipeTypes = data.recipeTypesForProvisioner(
                        record.provisionerDimension(), record.provisionerPosition());
                Set<String> recipeFamilies = data.recipeFamiliesForProvisioner(
                        record.provisionerDimension(), record.provisionerPosition());
                Identifier provisionerId = BuiltInRegistries.BLOCK.getKey(
                        provisionerLevel.getBlockState(record.provisionerPosition()).getBlock());
                data.replaceProvisionerBinding(record.provisionerDimension(), record.provisionerPosition(),
                        new BindingRecord(record.id(), record.owner(), record.networkId(),
                                record.provisionerDimension(), record.provisionerPosition(), record.deviceType(),
                                recipeTypes, recipeFamilies, provisionerId,
                                record.provisionerDimension(), record.provisionerPosition(), record.nickname(),
                                record.favorite(), record.boundGameTime()));
            }
        }
        else data.remove(dimension, position);
        data.removeForProvisioner(dimension, position);
    }
}
