package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.block.CraftlineProvisionerBlock;
import com.amicbeam.beyondcraftlines.common.block.CraftlineDashboardBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CraftlinesBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BeyondCraftlines.MOD_ID);
    public static final RegistryObject<Block> CRAFTLINE_PROVISIONER = BLOCKS.register("craftline_provisioner",
            () -> new CraftlineProvisionerBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CRAFTLINE_DASHBOARD = BLOCKS.register("craftline_dashboard",
            () -> new CraftlineDashboardBlock(BlockBehaviour.Properties.of()
                    .strength(1.0F).requiresCorrectToolForDrops().noOcclusion()));
    public static void register(IEventBus bus) { BLOCKS.register(bus); }
    private CraftlinesBlocks() {}
}
