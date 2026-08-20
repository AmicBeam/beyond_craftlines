package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.util.CapCtx;
import com.wintercogs.beyonddimensions.api.util.USHandler;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CraftlineProvisionerBlockEntity extends NetedBlockEntity
{
    private final ProvisionerStorage storage = new ProvisionerStorage(this::setChanged);
    private final LinkedHashSet<ResourceLocation> recipeCandidates = new LinkedHashSet<>();

    public CraftlineProvisionerBlockEntity(BlockPos pos, BlockState state)
    {
        super(CraftlinesBlockEntities.CRAFTLINE_PROVISIONER.get(), pos, state);
    }

    public ProvisionerStorage storage() { return storage; }
    public boolean isEmpty() { return storage.isEmpty(); }
    public Set<ResourceLocation> recipeCandidates() { return Set.copyOf(recipeCandidates); }

    public void addRecipeCandidates(Set<ResourceLocation> candidates)
    {
        boolean changed = false;
        for (ResourceLocation candidate : candidates.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList())
        {
            if (recipeCandidates.size() >= 32) break;
            changed |= recipeCandidates.add(candidate);
        }
        if (changed) setChanged();
    }

    public void clearRecipeCandidates()
    {
        if (recipeCandidates.isEmpty()) return;
        recipeCandidates.clear();
        setChanged();
    }

    public boolean giveOneItemStack(Player player)
    {
        for (KeyAmount stored : storage.getStorage())
        {
            if (!(stored.key() instanceof ItemStackKey itemKey) || stored.amount() <= 0) continue;
            int amount = (int) Math.min(stored.amount(), itemKey.getVanillaMaxStackSize());
            KeyAmount extracted = storage.extract(itemKey, amount, false, false);
            if (extracted.amount() <= 0) return false;
            ItemStack stack = itemKey.copyStackWithCount(extracted.amount());
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            return true;
        }
        return false;
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
    }
}
