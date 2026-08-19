package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.runtime.DuplicatorBlockEntity;
import com.amicbeam.beyondcraftlines.common.runtime.ExecutorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class CraftlinesBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, BeyondCraftlines.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExecutorBlockEntity>> SCHEMATIC_EXECUTOR = BLOCK_ENTITIES.register(
            "schematic_executor", () -> BlockEntityType.Builder.of(ExecutorBlockEntity::new,
                    CraftlinesBlocks.SCHEMATIC_EXECUTOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DuplicatorBlockEntity>> SCHEMATIC_DUPLICATOR = BLOCK_ENTITIES.register(
            "schematic_duplicator", () -> BlockEntityType.Builder.of(DuplicatorBlockEntity::new,
                    CraftlinesBlocks.SCHEMATIC_DUPLICATOR.get()).build(null));

    public static void register(IEventBus bus) { BLOCK_ENTITIES.register(bus); }
    private CraftlinesBlockEntities() {}
}
