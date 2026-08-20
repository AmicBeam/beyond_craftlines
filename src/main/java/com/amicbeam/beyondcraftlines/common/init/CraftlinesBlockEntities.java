package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class CraftlinesBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, BeyondCraftlines.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftlineProvisionerBlockEntity>> CRAFTLINE_PROVISIONER = BLOCK_ENTITIES.register(
            "craftline_provisioner", () -> BlockEntityType.Builder.of(CraftlineProvisionerBlockEntity::new,
                    CraftlinesBlocks.CRAFTLINE_PROVISIONER.get()).build(null));

    public static void register(IEventBus bus) { BLOCK_ENTITIES.register(bus); }
    private CraftlinesBlockEntities() {}
}
