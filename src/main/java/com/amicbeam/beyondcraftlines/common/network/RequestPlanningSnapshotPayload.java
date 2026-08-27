package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RequestPlanningSnapshotPayload(long nonce, String itemId) implements CustomPacketPayload
{
    private static final String LAST_SNAPSHOT_TICK = "beyond_craftlines_last_snapshot_tick";
    public static final Type<RequestPlanningSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "request_planning_snapshot"));
    public static final StreamCodec<ByteBuf, RequestPlanningSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RequestPlanningSnapshotPayload::nonce,
            ByteBufCodecs.stringUtf8(256), RequestPlanningSnapshotPayload::itemId,
            RequestPlanningSnapshotPayload::new);

    public static void handle(RequestPlanningSnapshotPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                    || !menu.canAccessNetwork(player)) return;
            try
            {
                long now = player.serverLevel().getGameTime();
                long last = player.getPersistentData().getLong(LAST_SNAPSHOT_TICK);
                if (last > 0 && now >= last && now - last < 5) return;
                player.getPersistentData().putLong(LAST_SNAPSHOT_TICK, now);
                if (!menu.targetToken().equals(payload.itemId()))
                    throw new IllegalArgumentException("target is unavailable");
                var snapshot = PlanningSnapshotService.capture(menu.networkId());
                long recipeEpoch = PlanningSnapshotService.recipeEpoch(player.level(), menu.availableFamilies());
                for (PlanningSnapshotPayload page : PlanningSnapshotPayload.from(payload.nonce(), payload.itemId(),
                        snapshot, recipeEpoch, CraftlinesConfig.MAX_PLANNING_DEPTH.get(),
                        CraftlinesConfig.MAX_PLANNING_NODES.get()))
                    PacketDistributor.sendToPlayer(player, page);
            }
            catch (RuntimeException exception)
            {
                PacketDistributor.sendToPlayer(player, PlanningSnapshotPayload.failure(
                        payload.nonce(), payload.itemId(), exception.getMessage()));
            }
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
