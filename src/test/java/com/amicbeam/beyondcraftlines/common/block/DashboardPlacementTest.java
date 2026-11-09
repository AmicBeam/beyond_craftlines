package com.amicbeam.beyondcraftlines.common.block;

import com.amicbeam.beyondcraftlines.common.crafting.DynamicCraftingFixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

final class DashboardPlacementTest
{
    @BeforeAll static void bootstrap() { DynamicCraftingFixtures.bootstrap(); }

    @Test void vanillaChestsAllowAttachmentOnAllSixFaces()
    {
        for (BlockState chest : new BlockState[]{Blocks.CHEST.defaultBlockState(), Blocks.TRAPPED_CHEST.defaultBlockState()})
        {
            LevelReader level = supportLevel(chest);
            for (Direction face : Direction.values())
            {
                assertFalse(chest.isFaceSturdy(level, BlockPos.ZERO, face));
                assertTrue(CraftlineDashboardBlock.canAttachTo(level, BlockPos.ZERO, face), face.toString());
            }
        }
    }

    @Test void solidSupportsRemainValid()
    {
        LevelReader level = supportLevel(Blocks.STONE.defaultBlockState());
        for (Direction face : Direction.values())
            assertTrue(CraftlineDashboardBlock.canAttachTo(level, BlockPos.ZERO, face));
    }

    @Test void airFluidsAndUnsupportedDecorationsCannotSupportDashboard()
    {
        for (BlockState state : new BlockState[]{Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.TORCH.defaultBlockState()})
        {
            LevelReader level = supportLevel(state);
            for (Direction face : Direction.values())
                assertFalse(CraftlineDashboardBlock.canAttachTo(level, BlockPos.ZERO, face));
        }
    }

    @Test void removingChestInvalidatesAttachment()
    {
        assertTrue(CraftlineDashboardBlock.canAttachTo(supportLevel(Blocks.CHEST.defaultBlockState()),
                BlockPos.ZERO, Direction.NORTH));
        assertFalse(CraftlineDashboardBlock.canAttachTo(supportLevel(Blocks.AIR.defaultBlockState()),
                BlockPos.ZERO, Direction.NORTH));
    }

    /** Real vanilla block states and block entities; only the surrounding world is synthetic. */
    private static LevelReader supportLevel(BlockState state)
    {
        BlockEntity blockEntity = state.getBlock() instanceof EntityBlock block
                ? block.newBlockEntity(BlockPos.ZERO, state) : null;
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "getBlockState" -> state;
                    case "getBlockEntity" -> blockEntity;
                    case "getFluidState" -> state.getFluidState();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
