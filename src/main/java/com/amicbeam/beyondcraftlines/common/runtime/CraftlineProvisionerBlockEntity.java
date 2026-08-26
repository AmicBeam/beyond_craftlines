package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.util.CapCtx;
import com.wintercogs.beyonddimensions.api.util.USHandler;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.common.item.MatterCompressionBall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CraftlineProvisionerBlockEntity extends NetedBlockEntity
{
    public static final ModelProperty<ItemStack> TARGET_ITEM_ICON = new ModelProperty<>(stack -> !stack.isEmpty());
    private final ProvisionerStorage storage = new ProvisionerStorage(this::setChanged);
    private final LinkedHashSet<ResourceLocation> recipeCandidates = new LinkedHashSet<>();
    private final List<WirelessConnection> wirelessConnections = new ArrayList<>();
    private ResourceLocation targetBlockIcon;
    private ItemStack targetItemIcon = ItemStack.EMPTY;
    private int connectionCursor;
    private ProvisionerDeliveryStrategy deliveryStrategy = ProvisionerDeliveryStrategy.ROUND_ROBIN;

    public CraftlineProvisionerBlockEntity(BlockPos pos, BlockState state)
    {
        super(CraftlinesBlockEntities.CRAFTLINE_PROVISIONER.get(), pos, state);
    }

    public ProvisionerStorage storage() { return storage; }
    public boolean isEmpty() { return storage.isEmpty(); }
    public Set<ResourceLocation> recipeCandidates() { return Set.copyOf(recipeCandidates); }
    public ItemStack targetItemIcon() { return targetItemIcon; }
    public List<WirelessConnection> wirelessConnections() { return List.copyOf(wirelessConnections); }
    public int connectedDeviceCount()
    { return (int) wirelessConnections.stream().map(connection ->
            connection.dimension().location() + "|" + connection.position().asLong()).distinct().count(); }
    public int supplyConnectionCount() { return connectionCount(ConnectionRole.SUPPLY); }
    public int extractConnectionCount() { return connectionCount(ConnectionRole.EXTRACT); }
    public ProvisionerDeliveryStrategy deliveryStrategy() { return deliveryStrategy; }

    private int connectionCount(ConnectionRole role)
    { return (int) wirelessConnections.stream().filter(connection -> connection.role() == role).count(); }

    public void setDeliveryStrategy(ProvisionerDeliveryStrategy strategy)
    {
        if (strategy == null || strategy == deliveryStrategy) return;
        deliveryStrategy = strategy;
        connectionCursor = 0;
        syncChanged();
    }

    public ConnectionEdit toggleWirelessConnection(ResourceKey<net.minecraft.world.level.Level> dimension,
                                                   BlockPos position, Direction face,
                                                   ResourceLocation blockId, ConnectionRole role)
    {
        for (int i = 0; i < wirelessConnections.size(); i++)
        {
            WirelessConnection existing = wirelessConnections.get(i);
            if (!existing.dimension().equals(dimension) || !existing.position().equals(position)
                    || existing.role() != role) continue;
            if (existing.face() == face)
            {
                wirelessConnections.remove(i);
                connectionCursor = wirelessConnections.isEmpty() ? 0 : connectionCursor % wirelessConnections.size();
                syncChanged();
                return ConnectionEdit.REMOVED;
            }
            wirelessConnections.set(i, new WirelessConnection(
                    dimension, position.immutable(), face, blockId, role));
            syncChanged();
            return role == ConnectionRole.EXTRACT ? ConnectionEdit.EXTRACTING : ConnectionEdit.UPDATED;
        }
        boolean existingDevice = wirelessConnections.stream().anyMatch(connection ->
                connection.dimension().equals(dimension) && connection.position().equals(position));
        if (!existingDevice && connectedDeviceCount() >= CraftlinesConfig.MAX_PROVISIONER_CONNECTIONS.get())
            return ConnectionEdit.LIMIT_REACHED;
        wirelessConnections.add(new WirelessConnection(
                dimension, position.immutable(), face, blockId, role));
        syncChanged();
        return role == ConnectionRole.EXTRACT ? ConnectionEdit.EXTRACTING : ConnectionEdit.ADDED;
    }

    public boolean clearWirelessConnections()
    {
        if (wirelessConnections.isEmpty()) return false;
        wirelessConnections.clear();
        connectionCursor = 0;
        syncChanged();
        return true;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos position,
                                  BlockState state, CraftlineProvisionerBlockEntity provisioner)
    {
        if (!(level instanceof ServerLevel serverLevel) || provisioner.wirelessConnections.isEmpty()) return;
        if (!provisioner.storage.isEmpty() && provisioner.supplyConnectionCount() > 0)
            provisioner.dispatchWireless();
        if (provisioner.extractConnectionCount() > 0 && provisioner.participatesInActiveOrder(serverLevel))
            provisioner.extractWireless(serverLevel);
    }

    private void dispatchWireless()
    {
        if (!(level instanceof ServerLevel currentLevel) || wirelessConnections.isEmpty()) return;
        int size = wirelessConnections.size();
        for (KeyAmount staged : List.copyOf(storage.getStorage()))
        {
            if (staged.isEmpty()) continue;
            long remaining = staged.amount();
            for (int index : connectionOrder())
            {
                if (remaining <= 0) break;
                WirelessConnection connection = wirelessConnections.get(index);
                if (connection.role() != ConnectionRole.SUPPLY) continue;
                ServerLevel targetLevel = currentLevel.getServer().getLevel(connection.dimension());
                if (targetLevel == null || !targetLevel.isLoaded(connection.position())
                        || !BuiltInRegistries.BLOCK.getKey(targetLevel.getBlockState(connection.position()).getBlock())
                        .equals(connection.blockId())) continue;
                long capacity = BoundMachineAutomation.insertCapacity(targetLevel, connection.position(),
                        connection.face(), staged.key(), remaining);
                if (capacity <= 0) continue;
                KeyAmount taken = storage.extract(staged.key(), capacity, false, false);
                if (taken.isEmpty()) continue;
                long inserted = BoundMachineAutomation.insert(targetLevel, connection.position(),
                        connection.face(), taken.key(), taken.amount());
                if (inserted < taken.amount())
                    storage.insertFromOrder(taken.key(), taken.amount() - inserted, false);
                remaining -= inserted;
                if (inserted > 0 && deliveryStrategy == ProvisionerDeliveryStrategy.ROUND_ROBIN)
                    connectionCursor = (index + 1) % size;
            }
        }
    }

    private boolean participatesInActiveOrder(ServerLevel currentLevel)
    {
        for (RecipeOrderJob job : RecipeOrderSavedData.get(currentLevel.getServer()).active())
        {
            if (job.networkId() != getNetId()) continue;
            for (RecipeOrderJob.StepExecution execution : job.executions())
            {
                RecipeOrderJob.ExternalWait wait = execution.externalWait();
                // Mixed input-group routes are ticked as bound-machine waits, so wait.provisioner()
                // is false even when this provisioner is one of the occupied endpoints.
                if (wait == null) continue;
                boolean occupied = wait.occupiedMachines().stream().anyMatch(machine ->
                        machine.dimension().equals(currentLevel.dimension())
                                && machine.position().equals(worldPosition));
                if (ProvisionerParticipationLogic.shouldActivate(wait.provisioner(), occupied))
                    return true;
            }
        }
        return false;
    }

    private void extractWireless(ServerLevel currentLevel)
    {
        var network = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet.getNetFromId(getNetId());
        if (network == null) return;
        UnifiedStorage networkStorage = network.getUnifiedStorage();
        for (WirelessConnection connection : wirelessConnections)
        {
            if (connection.role() != ConnectionRole.EXTRACT) continue;
            ServerLevel targetLevel = currentLevel.getServer().getLevel(connection.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(connection.position())
                    || !BuiltInRegistries.BLOCK.getKey(targetLevel.getBlockState(connection.position()).getBlock())
                    .equals(connection.blockId())) continue;
            for (KeyAmount visible : BoundMachineAutomation.extractableStacks(
                    targetLevel, connection.position(), connection.face()))
            {
                KeyAmount simulatedRemainder = networkStorage.insert(visible.key(), visible.amount(), true);
                long accepted = visible.amount() - (simulatedRemainder.isEmpty()
                        ? 0 : simulatedRemainder.amount());
                if (accepted <= 0) continue;
                for (KeyAmount extracted : BoundMachineAutomation.extractStacks(targetLevel,
                        connection.position(), connection.face(), visible.key(), accepted))
                {
                    KeyAmount rejected = networkStorage.insert(extracted.key(), extracted.amount(), false);
                    if (!rejected.isEmpty()) preserveRejected(targetLevel, connection, rejected);
                }
            }
        }
    }

    private static void preserveRejected(ServerLevel targetLevel, WirelessConnection connection,
                                         KeyAmount rejected)
    {
        long restored = BoundMachineAutomation.insert(targetLevel, connection.position(), connection.face(),
                rejected.key(), rejected.amount());
        long stranded = rejected.amount() - restored;
        if (stranded <= 0) return;
        ItemStack ball = new ItemStack(BDItems.MATTER_COMPRESS_BALL.get());
        ball.set(BDDataComponents.ISTACK_SLOTS, List.of(new KeyAmount(rejected.key(), stranded)));
        Block.popResource(targetLevel, connection.position(), ball);
    }

    private List<Integer> connectionOrder()
    {
        int size = wirelessConnections.size();
        ArrayList<Integer> order = new ArrayList<>(size);
        if (deliveryStrategy == ProvisionerDeliveryStrategy.ROUND_ROBIN)
        {
            for (int offset = 0; offset < size; offset++) order.add((connectionCursor + offset) % size);
            return order;
        }
        for (int index = 0; index < size; index++) order.add(index);
        Comparator<Integer> comparator = Comparator
                .comparingLong((Integer index) -> distanceSquared(wirelessConnections.get(index)))
                .thenComparingInt(Integer::intValue);
        if (deliveryStrategy == ProvisionerDeliveryStrategy.FARTHEST_FIRST)
            comparator = comparator.reversed();
        order.sort(comparator);
        return order;
    }

    private long distanceSquared(WirelessConnection connection)
    {
        if (level == null || !connection.dimension().equals(level.dimension())) return Long.MAX_VALUE;
        long dx = (long) connection.position().getX() - worldPosition.getX();
        long dy = (long) connection.position().getY() - worldPosition.getY();
        long dz = (long) connection.position().getZ() - worldPosition.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Moves every staged resource accepted by the target network and keeps any rejected remainder. */
    public void returnContentTo(UnifiedStorage networkStorage)
    {
        if (networkStorage == null || storage.isEmpty()) return;
        for (KeyAmount stack : List.copyOf(storage.getStorage()))
        {
            if (stack.isEmpty()) continue;
            KeyAmount taken = storage.extract(stack.key(), stack.amount(), false, false);
            if (taken.isEmpty()) continue;
            KeyAmount remainder = networkStorage.insert(taken.key(), taken.amount(), false);
            if (!remainder.isEmpty())
                storage.insertFromOrder(remainder.key(), remainder.amount(), false);
        }
    }

    /** Drops every staged BD resource in the same lossless container used by BD machines. */
    public void dropContent()
    {
        if (level == null || level.isClientSide()) return;

        List<KeyAmount> compressed = new ArrayList<>();
        for (KeyAmount stack : storage.getStorage())
        {
            if (stack.isEmpty()) continue;
            if (stack.key() instanceof ItemStackKey itemKey
                    && itemKey.getSource() instanceof MatterCompressionBall)
            {
                Block.popResource(level, worldPosition, itemKey.copyStackWithCount(stack.amount()));
            }
            else compressed.add(stack);
        }
        if (!compressed.isEmpty())
        {
            ItemStack ball = new ItemStack(BDItems.MATTER_COMPRESS_BALL.get());
            ball.set(BDDataComponents.ISTACK_SLOTS, List.copyOf(compressed));
            Block.popResource(level, worldPosition, ball);
        }
        storage.clearStorage();
    }

    public void addRecipeCandidates(Set<ResourceLocation> candidates)
    { addRecipeCandidates(candidates, null, ItemStack.EMPTY); }

    public void addRecipeCandidates(Set<ResourceLocation> candidates, ResourceLocation targetBlock,
                                    ItemStack targetItem)
    {
        boolean changed = false;
        for (ResourceLocation candidate : candidates.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList())
        {
            if (recipeCandidates.size() >= 32) break;
            changed |= recipeCandidates.add(candidate);
        }
        if (targetBlock != null && !targetBlock.equals(targetBlockIcon))
        {
            targetBlockIcon = targetBlock;
            changed = true;
        }
        if (!targetItem.isEmpty() && !ItemStack.isSameItemSameComponents(targetItemIcon, targetItem))
        {
            targetItemIcon = targetItem.copyWithCount(1);
            changed = true;
        }
        if (changed) syncChanged();
    }

    public void clearRecipeCandidates()
    {
        if (recipeCandidates.isEmpty() && targetBlockIcon == null && targetItemIcon.isEmpty()) return;
        recipeCandidates.clear();
        targetBlockIcon = null;
        targetItemIcon = ItemStack.EMPTY;
        syncChanged();
    }

    private void syncChanged()
    {
        setChanged();
        if (level != null && !level.isClientSide())
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public ModelData getModelData()
    {
        return targetItemIcon.isEmpty() ? ModelData.EMPTY
                : ModelData.builder().with(TARGET_ITEM_ICON, targetItemIcon.copy()).build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        CapabilityHelper.BlockCapabilityMap.forEach((typeId, capability) -> {
            USHandler handler = CapabilityHelper.USHandlerMap.get(typeId);
            if (handler == null) return;
            event.registerBlockEntity((BlockCapability) capability,
                    CraftlinesBlockEntities.CRAFTLINE_PROVISIONER.get(), (be, side) ->
                            handler.apply(be.storage, handler.isContextual()
                                    ? new CapCtx(be.getLevel(), be.getBlockPos(), be) : null));
        });
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        tag.put("provisioned_resources", storage.serializeNBT(registries));
        ListTag candidates = new ListTag();
        recipeCandidates.stream().sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(type -> candidates.add(StringTag.valueOf(type.toString())));
        tag.put("recipe_candidates", candidates);
        if (targetBlockIcon != null) tag.putString("target_block_icon", targetBlockIcon.toString());
        if (!targetItemIcon.isEmpty()) tag.put("target_item_icon", targetItemIcon.saveOptional(registries));
        tag.putInt("delivery_strategy", deliveryStrategy.id());
        ListTag connections = new ListTag();
        for (WirelessConnection connection : wirelessConnections)
        {
            CompoundTag encoded = new CompoundTag();
            encoded.putString("dimension", connection.dimension().location().toString());
            encoded.putLong("position", connection.position().asLong());
            encoded.putByte("face", (byte) connection.face().get3DDataValue());
            encoded.putString("block", connection.blockId().toString());
            encoded.putByte("role", (byte) connection.role().id());
            connections.add(encoded);
        }
        tag.put("wireless_connections", connections);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        if (tag.contains("provisioned_resources"))
            storage.deserializeNBT(registries, tag.getCompound("provisioned_resources"));
        recipeCandidates.clear();
        ListTag candidates = tag.getList("recipe_candidates", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(32, candidates.size()); i++)
        {
            ResourceLocation type = ResourceLocation.tryParse(candidates.getString(i));
            if (type != null) recipeCandidates.add(type);
        }
        targetBlockIcon = ResourceLocation.tryParse(tag.getString("target_block_icon"));
        targetItemIcon = ItemStack.parseOptional(registries, tag.getCompound("target_item_icon"));
        deliveryStrategy = ProvisionerDeliveryStrategy.fromId(tag.getInt("delivery_strategy"));
        wirelessConnections.clear();
        ListTag connections = tag.getList("wireless_connections", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(1_024, connections.size()); i++)
        {
            CompoundTag encoded = connections.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(encoded.getString("dimension"));
            ResourceLocation blockId = ResourceLocation.tryParse(encoded.getString("block"));
            if (dimensionId == null || blockId == null) continue;
            wirelessConnections.add(new WirelessConnection(
                    ResourceKey.create(Registries.DIMENSION, dimensionId),
                    BlockPos.of(encoded.getLong("position")),
                    Direction.from3DDataValue(encoded.getByte("face")), blockId,
                    ConnectionRole.fromId(encoded.getByte("role"))));
        }
        connectionCursor = 0;
        if (targetItemIcon.isEmpty() && targetBlockIcon != null)
        {
            var block = BuiltInRegistries.BLOCK.get(targetBlockIcon);
            if (block != null) targetItemIcon = new ItemStack(block);
        }
        requestModelDataUpdate();
        // A block-entity data packet updates ModelData, but does not itself guarantee that the
        // already baked chunk mesh is rebuilt. Force the client chunk renderer to consume the
        // new target icon immediately instead of waiting for an unrelated block update.
        if (level != null && level.isClientSide())
        {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state,
                    Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        }
    }

    public enum ConnectionEdit { ADDED, UPDATED, EXTRACTING, REMOVED, LIMIT_REACHED }

    public enum ConnectionRole
    {
        SUPPLY(0), EXTRACT(1);
        private final int id;
        ConnectionRole(int id) { this.id = id; }
        public int id() { return id; }
        public static ConnectionRole fromId(int id) { return id == 1 ? EXTRACT : SUPPLY; }
    }

    public record WirelessConnection(ResourceKey<net.minecraft.world.level.Level> dimension,
                                     BlockPos position, Direction face, ResourceLocation blockId,
                                     ConnectionRole role) {}
}
