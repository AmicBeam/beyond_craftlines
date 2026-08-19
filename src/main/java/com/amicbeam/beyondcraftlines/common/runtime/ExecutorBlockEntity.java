package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.data.BlueprintComponents;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintLibrarySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class ExecutorBlockEntity extends BlockEntity
{
    private ExecutorState state = ExecutorState.idle();
    private ResourceLocation blueprintId;
    private String blueprintHash = "";
    private int networkId = -1;

    public ExecutorBlockEntity(BlockPos pos, BlockState blockState)
    {
        super(CraftlinesBlockEntities.SCHEMATIC_EXECUTOR.get(), pos, blockState);
    }

    public void setBlueprint(ItemStack stack)
    {
        if (stack.getItem() != com.amicbeam.beyondcraftlines.common.init.CraftlinesItems.STABILIZED_SCHEMATIC.get()) return;
        ResourceLocation id = stack.get(BlueprintComponents.BLUEPRINT_ID);
        String hash = stack.get(BlueprintComponents.BLUEPRINT_HASH);
        if (id == null || hash == null) return;
        blueprintId = id;
        blueprintHash = hash;
        state = ExecutorState.idle();
        setChanged();
    }

    public void setNetworkId(int value)
    {
        networkId = value;
        setChanged();
    }

    public ResourceLocation blueprintId() { return blueprintId; }
    public int networkId() { return networkId; }
    public ExecutorState state() { return state; }

    public String statusSummary()
    {
        long remaining = level == null ? 0 : state.finishAt() - level.getGameTime();
        return ExecutorStatusFormatter.format(state, remaining);
    }

    public void serverTick()
    {
        if (level == null || level.isClientSide()) return;
        if (state.status() == ExecutorState.Status.IDLE && blueprintId != null && networkId >= 0)
        {
            try
            {
                UUID id = UUID.fromString(blueprintId.getPath());
                if (!ExecutorService.matchesBlueprint((net.minecraft.server.level.ServerLevel) level, id, blueprintHash)) return;
                ExecutorService.begin((net.minecraft.server.level.ServerLevel) level, id, networkId, level.getGameTime())
                        .ifPresent(value -> { state = value; setChanged(); });
            }
            catch (IllegalArgumentException ignored)
            {
                state = new ExecutorState(ExecutorState.Status.ERROR, 0, 0, "");
                setChanged();
            }
        }
        if ((state.status() == ExecutorState.Status.RUNNING || state.status() == ExecutorState.Status.PAUSED)
                && level.getGameTime() >= state.finishAt()
                && blueprintId != null)
        {
            try
            {
                UUID id = UUID.fromString(blueprintId.getPath());
                ExecutorState next = ExecutorService.tick(
                        (net.minecraft.server.level.ServerLevel) level,
                        id,
                        networkId,
                        state,
                        level.getGameTime());
                if (next != state) { state = next; setChanged(); }
            }
            catch (IllegalArgumentException ignored)
            {
                state = new ExecutorState(ExecutorState.Status.ERROR, 0, 0, state.blueprintHash());
                setChanged();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        if (blueprintId != null) tag.putString("blueprint", blueprintId.toString());
        tag.putString("blueprint_hash", blueprintHash);
        tag.putInt("network", networkId);
        tag.putString("status", state.status().name());
        tag.putLong("started", state.startedAt());
        tag.putLong("finish", state.finishAt());
        tag.putString("hash", state.blueprintHash());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        if (tag.contains("blueprint")) blueprintId = ResourceLocation.parse(tag.getString("blueprint"));
        blueprintHash = tag.getString("blueprint_hash");
        networkId = tag.getInt("network");
        try
        {
            state = new ExecutorState(ExecutorState.Status.valueOf(tag.getString("status")),
                    tag.getLong("started"), tag.getLong("finish"), tag.getString("hash"));
        }
        catch (IllegalArgumentException ignored) { state = ExecutorState.idle(); }
    }
}
