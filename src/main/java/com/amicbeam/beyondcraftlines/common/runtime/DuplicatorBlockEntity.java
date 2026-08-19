package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.data.BlueprintComponents;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.amicbeam.beyondcraftlines.common.item.StabilizedSchematicItem;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintLibrarySavedData;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintReferenceValidator;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DuplicatorBlockEntity extends BlockEntity
{
    private int networkId = -1;

    public DuplicatorBlockEntity(BlockPos pos, BlockState state)
    {
        super(CraftlinesBlockEntities.SCHEMATIC_DUPLICATOR.get(), pos, state);
    }

    public void setNetworkId(int value)
    {
        networkId = value;
        setChanged();
    }

    public int networkId() { return networkId; }

    public boolean duplicate(Player player, ItemStack source)
    {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        if (source.getItem() != com.amicbeam.beyondcraftlines.common.init.CraftlinesItems.STABILIZED_SCHEMATIC.get()) return false;
        ResourceLocation id = source.get(BlueprintComponents.BLUEPRINT_ID);
        String sourceHash = source.get(BlueprintComponents.BLUEPRINT_HASH);
        if (id == null || sourceHash == null || networkId < 0) return false;
        UUID blueprintId;
        try { blueprintId = UUID.fromString(id.getPath()); }
        catch (IllegalArgumentException ignored) { return false; }

        var record = BlueprintLibrarySavedData.get(serverLevel.getServer()).get(blueprintId);
        if (record.isEmpty() || record.get().compiled() == null
                || !BlueprintReferenceValidator.isValid(id.getNamespace(), id.getPath(), sourceHash)
                || !sourceHash.equals(record.get().compiled().structureHash())
                || !record.get().snapshot().hash().equals(sourceHash)) return false;
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        if (network == null) return false;
        var storage = network.getUnifiedStorage();
        List<com.amicbeam.beyondcraftlines.common.structure.ResourceAmount> extracted = new ArrayList<>();
        for (var amount : record.get().compiled().capex())
        {
            if (amount.amount() > 0 && !storage.extract(keyFor(amount), amount.amount(), true, false).isEmpty()) return false;
        }
        for (var amount : record.get().compiled().capex())
        {
            if (amount.amount() == 0) continue;
            if (!storage.extract(keyFor(amount), amount.amount(), false, false).isEmpty())
            {
                for (var refund : extracted) storage.insert(keyFor(refund), refund.amount(), false);
                return false;
            }
            extracted.add(amount);
        }
        ItemStack copy = StabilizedSchematicItem.of(BlueprintLibrarySavedData.get(serverLevel.getServer()), blueprintId);
        if (copy.isEmpty())
        {
            refund(storage, extracted);
            return false;
        }
        if (!player.getInventory().add(copy))
        {
            refund(storage, extracted);
            return false;
        }
        return true;
    }

    private static ItemStackKey keyFor(com.amicbeam.beyondcraftlines.common.structure.ResourceAmount amount)
    {
        return new ItemStackKey(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(amount.itemId())));
    }

    private static void refund(com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage storage,
                               List<com.amicbeam.beyondcraftlines.common.structure.ResourceAmount> amounts)
    {
        for (var amount : amounts)
        {
            if (amount.amount() > 0) storage.insert(keyFor(amount), amount.amount(), false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        tag.putInt("network", networkId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        networkId = tag.getInt("network");
    }
}
