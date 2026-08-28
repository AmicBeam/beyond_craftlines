package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.block.CraftlineProvisionerBlock;
import com.amicbeam.beyondcraftlines.common.block.CraftlineDashboardBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CraftlinesBlocks
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BeyondCraftlines.MOD_ID);
    public static final DeferredBlock<Block> CRAFTLINE_PROVISIONER = BLOCKS.register("craftline_provisioner",
            () -> new CraftlineProvisionerBlock(BlockBehaviour.Properties.of()
                    .strength(1.5f).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> CRAFTLINE_DASHBOARD = BLOCKS.register("craftline_dashboard",
            () -> new CraftlineDashboardBlock(BlockBehaviour.Properties.of()
                    .strength(1.0f).requiresCorrectToolForDrops().noOcclusion()));

    public static void register(IEventBus bus) { BLOCKS.register(bus); }
    private CraftlinesBlocks() {}
}
