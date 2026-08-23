package com.amicbeam.beyondcraftlines.common.runtime;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
    private ResourceLocation targetBlockIcon;
    private ItemStack targetItemIcon = ItemStack.EMPTY;

    public CraftlineProvisionerBlockEntity(BlockPos pos, BlockState state)
    {
        super(CraftlinesBlockEntities.CRAFTLINE_PROVISIONER.get(), pos, state);
    }

    public ProvisionerStorage storage() { return storage; }
    public boolean isEmpty() { return storage.isEmpty(); }
    public Set<ResourceLocation> recipeCandidates() { return Set.copyOf(recipeCandidates); }
    public ItemStack targetItemIcon() { return targetItemIcon; }

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
}
