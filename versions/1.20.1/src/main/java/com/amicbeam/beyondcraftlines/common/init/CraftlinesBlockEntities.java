package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CraftlinesBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BeyondCraftlines.MOD_ID);
    public static final RegistryObject<BlockEntityType<CraftlineProvisionerBlockEntity>> CRAFTLINE_PROVISIONER = BLOCK_ENTITIES.register(
            "craftline_provisioner", () -> BlockEntityType.Builder.of(CraftlineProvisionerBlockEntity::new,
                    CraftlinesBlocks.CRAFTLINE_PROVISIONER.get()).build(null));
    public static void register(IEventBus bus) { BLOCK_ENTITIES.register(bus); }
    private CraftlinesBlockEntities() {}
}
