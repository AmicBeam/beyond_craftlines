package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.block.SchematicAnchorBlock;
import com.amicbeam.beyondcraftlines.common.block.SchematicDuplicatorBlock;
import com.amicbeam.beyondcraftlines.common.block.SchematicExecutorBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CraftlinesBlocks
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BeyondCraftlines.MOD_ID);
    public static final DeferredBlock<Block> SCHEMATIC_ANCHOR = BLOCKS.register("schematic_anchor", () -> new SchematicAnchorBlock(BlockBehaviour.Properties.of().strength(3.5f)));
    public static final DeferredBlock<Block> SCHEMATIC_EXECUTOR = BLOCKS.register("schematic_executor", () -> new SchematicExecutorBlock(BlockBehaviour.Properties.of().strength(3.5f)));
    public static final DeferredBlock<Block> SCHEMATIC_DUPLICATOR = BLOCKS.register("schematic_duplicator",
            () -> new SchematicDuplicatorBlock(BlockBehaviour.Properties.of().strength(3.5f)));

    private static DeferredBlock<Block> register(String id, float strength)
    {
        DeferredBlock<Block> block = BLOCKS.register(id, () -> new Block(BlockBehaviour.Properties.of().strength(strength)));
        CraftlinesItems.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus bus) { BLOCKS.register(bus); }
    private CraftlinesBlocks() {}
}
