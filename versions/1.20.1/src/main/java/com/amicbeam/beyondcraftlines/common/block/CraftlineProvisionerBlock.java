package com.amicbeam.beyondcraftlines.common.block;

import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class CraftlineProvisionerBlock extends NetedBlock implements EntityBlock {
    public CraftlineProvisionerBlock(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return super.use(state, level, pos, player, hand, hit);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CraftlineProvisionerBlockEntity be) {
            var selected = BindingSavedData.get(serverPlayer.getServer()).recipeTypesForProvisioner(level.dimension(), pos);
            be.addRecipeCandidates(selected);
            var candidates = be.recipeCandidates();
            if (candidates.size() == 1 && selected.isEmpty()
                    && DeviceBindingRegistry.configureProvisioner(serverPlayer, pos, candidates)) {
                selected = candidates;
                BindingVisualsPayload.broadcast(serverPlayer.serverLevel());
            }
            var configured = selected;
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider((id, inventory, ignored) ->
                    new ProvisionerConfigMenu(id, inventory, pos, candidates, configured),
                    Component.translatable("menu.beyond_craftlines.provisioner")), buffer -> {
                buffer.writeBlockPos(pos);
                ProvisionerConfigMenu.writeTypes(buffer, candidates);
                ProvisionerConfigMenu.writeTypes(buffer, configured);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftlineProvisionerBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CraftlineProvisionerBlockEntity provisioner) {
                provisioner.dropContent();
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
