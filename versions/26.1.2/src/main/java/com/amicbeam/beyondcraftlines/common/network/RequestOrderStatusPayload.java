package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderSavedData;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record RequestOrderStatusPayload(int networkId) implements CustomPacketPayload
{
    public static final Type<RequestOrderStatusPayload> TYPE = new Type<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "request_order_status"));
    public static final StreamCodec<ByteBuf, RequestOrderStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequestOrderStatusPayload::networkId, RequestOrderStatusPayload::new);

    public static void handle(RequestOrderStatusPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var network = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet.getNetFromId(payload.networkId());
            if (network == null || !(network.isOwner(player) || network.isManager(player)
                    || network.getPlayers().contains(player.getUUID()))) return;
            ListTag list = new ListTag();
            RecipeOrderSavedData data = RecipeOrderSavedData.get(player.level().getServer());
            data.removeExpiredCompleted(player.level().getServer().overworld().getGameTime());
            data.forOwner(player.getUUID()).stream()
                    .filter(job -> job.networkId() == payload.networkId())
                    .sorted(java.util.Comparator.comparingLong(com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderJob::createdAt).reversed())
                    .limit(20).forEach(job -> {
                        CompoundTag value = new CompoundTag();
                        com.amicbeam.beyondcraftlines.common.util.NbtCompat.putUuid(value, "id", job.id());
                        value.putString("target", job.target().toString()); value.putLong("requested", job.requested());
                        value.putInt("next", job.nextStep()); value.putInt("total", job.steps().size());
                        value.putBoolean("blocking_mode", job.blockingMode());
                        value.putString("status", job.status().name()); value.putString("message", job.message());
                        List<StepStatus> unfinished = new java.util.ArrayList<>();
                        for (int stepIndex = job.nextStep(); stepIndex < job.steps().size(); stepIndex++)
                        {
                            RecipePlan.Step step = job.steps().get(stepIndex);
                            long required = SaturatingLongMath.multiply(step.outputPerCraft(), step.crafts());
                            long completed = stepIndex == job.nextStep() && job.externalWait() != null
                                    ? Math.min(required, job.externalWait().collected()) : 0;
                            StepStatus existing = unfinished.stream()
                                    .filter(candidate -> candidate.key().isSame(step.outputKey())).findFirst().orElse(null);
                            if (existing == null) unfinished.add(new StepStatus(step.outputKey(), completed, required));
                            else
                            {
                                int at = unfinished.indexOf(existing);
                                unfinished.set(at, new StepStatus(existing.key(),
                                        SaturatingLongMath.add(existing.completed(), completed),
                                        SaturatingLongMath.add(existing.required(), required)));
                            }
                        }
                        ListTag encodedSteps = new ListTag();
                        for (StepStatus step : unfinished)
                        {
                            CompoundTag encoded = new CompoundTag();
                            encoded.putString("key_type", step.key().getTypeId().toString());
                            encoded.put("key", step.key().serializeNBT(player.registryAccess()));
                            encoded.putString("fallback", step.key().getSource().toString());
                            encoded.putLong("completed", step.completed());
                            encoded.putLong("required", step.required());
                            encodedSteps.add(encoded);
                        }
                        value.put("unfinished_steps", encodedSteps);
                        list.add(value);
                    });
            CompoundTag root = new CompoundTag(); root.put("orders", list);
            PacketDistributor.sendToPlayer(player, new OrderStatusPayload(root));
        });
    }

    private record StepStatus(IStackKey<?> key, long completed, long required) {}
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
