package com.amicbeam.beyondcraftlines.common.block;

import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class CraftlineProvisionerBlock extends NetedBlock implements EntityBlock
{
    public CraftlineProvisionerBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit)
    {
        if (player.isShiftKeyDown()) return super.useWithoutItem(state, level, pos, player, hit);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CraftlineProvisionerBlockEntity be)
            openConfiguration(serverPlayer, pos, be);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public static boolean openConfiguration(ServerPlayer serverPlayer, BlockPos pos)
    {
        if (serverPlayer.blockPosition().distSqr(pos) > 64
                || !(serverPlayer.level().getBlockEntity(pos) instanceof CraftlineProvisionerBlockEntity be))
            return false;
        openConfiguration(serverPlayer, pos, be);
        return true;
    }

    private static void openConfiguration(ServerPlayer serverPlayer, BlockPos pos,
                                          CraftlineProvisionerBlockEntity be)
    {
            Level level = serverPlayer.level();
            var selected = BindingSavedData.get(serverPlayer.getServer())
                    .recipeTypesForProvisioner(level.dimension(), pos);
            be.addRecipeCandidates(selected);
            var candidates = be.recipeCandidates();
            if (candidates.size() == 1 && selected.isEmpty()
                    && DeviceBindingRegistry.configureProvisioner(serverPlayer, pos, candidates))
            {
                selected = candidates;
                BindingVisualsPayload.broadcast(serverPlayer.serverLevel());
            }
            var configured = selected;
            var availableGroups = DeviceBindingRegistry.inputGroupsByJeiType(serverPlayer.serverLevel(), candidates);
            var selectedGroups = DeviceBindingRegistry.selectedGroupsByJeiType(serverPlayer.serverLevel(), candidates,
                    BindingSavedData.get(serverPlayer.getServer()).inputGroupsForProvisioner(level.dimension(), pos));
            var binding = BindingSavedData.get(serverPlayer.getServer()).at(level.dimension(), pos);
            int priority = binding == null ? 0 : binding.priority();
            boolean manualSelection = candidates.isEmpty() && configured.isEmpty();
            boolean debugMappings = manualSelection
                    && com.amicbeam.beyondcraftlines.CraftlinesConfig.DEBUG_RECIPE_TYPE_MAPPINGS.get();
            var loadedFamilies = manualSelection && !debugMappings
                    ? com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.loadedFamilies(level)
                    : java.util.Set.<String>of();
            var aliases = manualSelection && !debugMappings
                    ? com.amicbeam.beyondcraftlines.common.crafting.RecipeFamilyAliasRegistry.aliases()
                    : java.util.Map.<String, java.util.Set<String>>of();
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new ProvisionerConfigMenu(id, inventory, pos, candidates, configured,
                            availableGroups, selectedGroups, priority, be),
                    Component.translatable("menu.beyond_craftlines.provisioner")), buffer -> {
                        ProvisionerConfigMenu.writeOptions(buffer, pos, candidates, configured,
                                availableGroups, selectedGroups, false, priority,
                                debugMappings, loadedFamilies, aliases);
                    });
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CraftlineProvisionerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type)
    {
        if (level.isClientSide()) return null;
        return (tickLevel, position, tickState, blockEntity) -> {
            if (blockEntity instanceof CraftlineProvisionerBlockEntity provisioner)
                CraftlineProvisionerBlockEntity.serverTick(tickLevel, position, tickState, provisioner);
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston)
    {
        if (!state.is(newState.getBlock()))
        {
            if (level.getBlockEntity(pos) instanceof CraftlineProvisionerBlockEntity provisioner)
                provisioner.dropContent();
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
