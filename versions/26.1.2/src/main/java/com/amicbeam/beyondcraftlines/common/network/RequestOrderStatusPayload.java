package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderJob;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderSavedData;
import com.amicbeam.beyondcraftlines.common.util.NbtCompat;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record RequestOrderStatusPayload(int networkId, UUID sessionId) implements CustomPacketPayload
{
    private static final int MAX_ORDERS = 20;
    private static final long SESSION_TTL_NANOS = 5L * 60L * 1_000_000_000L;
    private static final Map<SessionKey, SyncSession> SESSIONS = new HashMap<>();

    public static final Type<RequestOrderStatusPayload> TYPE = new Type<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "request_order_status"));
    public static final StreamCodec<ByteBuf, RequestOrderStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequestOrderStatusPayload::networkId,
            UUIDUtil.STREAM_CODEC, RequestOrderStatusPayload::sessionId,
            RequestOrderStatusPayload::new);

    public static void handle(RequestOrderStatusPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var network = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet.getNetFromId(payload.networkId());
            if (network == null || !(network.isOwner(player) || network.isManager(player)
                    || network.getPlayers().contains(player.getUUID()))) return;

            var server = player.level().getServer();
            RecipeOrderSavedData data = RecipeOrderSavedData.get(server);
            data.removeExpiredDisplayedTerminal(server.overworld().getGameTime());
            List<RecipeOrderJob> jobs = data.all().stream()
                    .filter(job -> job.networkId() == payload.networkId())
                    .sorted(java.util.Comparator.comparingLong(RecipeOrderJob::createdAt).reversed())
                    .limit(MAX_ORDERS).toList();

            long now = System.nanoTime();
            SessionKey sessionKey = new SessionKey(player.getUUID(), payload.sessionId());
            SESSIONS.entrySet().removeIf(entry -> now - entry.getValue().lastAccessNanos() > SESSION_TTL_NANOS
                    || entry.getKey().playerId().equals(player.getUUID()) && !entry.getKey().equals(sessionKey));
            SyncSession previousSession = SESSIONS.get(sessionKey);
            boolean reset = previousSession == null || previousSession.networkId() != payload.networkId();
            Map<UUID, SyncedOrder> previous = reset ? Map.of() : previousSession.orders();
            Map<UUID, SyncedOrder> next = new LinkedHashMap<>();
            ListTag index = new ListTag();
            ListTag updates = new ListTag();

            for (RecipeOrderJob job : jobs)
            {
                long revision = data.revision(job.id());
                CompoundTag indexed = new CompoundTag();
                NbtCompat.putUuid(indexed, "id", job.id());
                indexed.putLong("revision", revision);
                index.add(indexed);

                SyncedOrder old = previous.get(job.id());
                if (old == null || old.revision() != revision)
                    updates.add(encodeOrder(player, job, revision, old == null ? null : old.job()));
                next.put(job.id(), new SyncedOrder(revision, job));
            }

            SESSIONS.put(sessionKey, new SyncSession(payload.networkId(), Map.copyOf(next), now));
            CompoundTag root = new CompoundTag();
            NbtCompat.putUuid(root, "session", payload.sessionId());
            root.putBoolean("reset", reset);
            root.put("index", index);
            root.put("updates", updates);
            PacketDistributor.sendToPlayer(player, new OrderStatusPayload(root));
        });
    }

    private static CompoundTag encodeOrder(ServerPlayer player, RecipeOrderJob job, long revision,
                                           RecipeOrderJob previous)
    {
        CompoundTag value = new CompoundTag();
        NbtCompat.putUuid(value, "id", job.id());
        value.putLong("revision", revision);
        value.putString("target", job.target().toString());
        value.putLong("requested", job.requested());
        value.putInt("next", (int) job.executions().stream().filter(RecipeOrderJob.StepExecution::complete).count());
        value.putInt("total", job.executions().size());
        value.putBoolean("blocking_mode", job.blockingMode());
        value.putString("origin", job.origin().id());
        value.putString("status", job.status().name());
        value.putString("message", job.message());

        boolean terminal = terminal(job.status());
        boolean resetSteps = previous == null || terminal || previous.executions().size() != job.executions().size();
        value.putBoolean("reset_steps", resetSteps);
        ListTag steps = new ListTag();
        if (!terminal)
        {
            if (resetSteps)
                for (StepStatus step : aggregateSteps(job))
                    steps.add(encodeStep(player, step.key(), step.completed(), step.required(), false));
            else
                for (IStackKey<?> key : changedKeys(previous, job))
                    steps.add(encodeStepUpdate(player, job, key));
        }
        value.put("step_updates", steps);
        return value;
    }

    private static List<StepStatus> aggregateSteps(RecipeOrderJob job)
    {
        Map<IStackKey<?>, long[]> totals = new LinkedHashMap<>();
        for (RecipeOrderJob.StepExecution execution : job.executions())
        {
            if (execution.complete()) continue;
            long required = SaturatingLongMath.multiply(
                    execution.step().outputPerCraft(), execution.step().crafts());
            long completed = execution.externalWait() == null ? 0
                    : Math.min(required, execution.externalWait().collected());
            long[] total = totals.computeIfAbsent(execution.step().outputKey(), ignored -> new long[2]);
            total[0] = SaturatingLongMath.add(total[0], completed);
            total[1] = SaturatingLongMath.add(total[1], required);
        }
        return totals.entrySet().stream().map(entry ->
                new StepStatus(entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList();
    }

    private static List<IStackKey<?>> changedKeys(RecipeOrderJob previous, RecipeOrderJob current)
    {
        Set<IStackKey<?>> keys = new LinkedHashSet<>();
        for (int index = 0; index < current.executions().size(); index++)
        {
            RecipeOrderJob.StepExecution before = previous.executions().get(index);
            RecipeOrderJob.StepExecution after = current.executions().get(index);
            if (Objects.equals(before, after)) continue;
            keys.add(before.step().outputKey());
            keys.add(after.step().outputKey());
        }
        return List.copyOf(keys);
    }

    private static CompoundTag encodeStepUpdate(ServerPlayer player, RecipeOrderJob job, IStackKey<?> key)
    {
        long completed = 0;
        long required = 0;
        IStackKey<?> currentKey = null;
        for (RecipeOrderJob.StepExecution execution : job.executions())
        {
            if (execution.complete() || !key.isSame(execution.step().outputKey())) continue;
            currentKey = execution.step().outputKey();
            long stepRequired = SaturatingLongMath.multiply(
                    execution.step().outputPerCraft(), execution.step().crafts());
            long stepCompleted = execution.externalWait() == null ? 0
                    : Math.min(stepRequired, execution.externalWait().collected());
            completed = SaturatingLongMath.add(completed, stepCompleted);
            required = SaturatingLongMath.add(required, stepRequired);
        }

        IStackKey<?> encodedKey = currentKey == null ? key : currentKey;
        return encodeStep(player, encodedKey, completed, required, currentKey == null);
    }

    private static CompoundTag encodeStep(ServerPlayer player, IStackKey<?> encodedKey,
                                          long completed, long required, boolean removed)
    {
        CompoundTag encoded = new CompoundTag();
        encoded.putString("key_type", encodedKey.getTypeId().toString());
        encoded.put("key", encodedKey.serializeNBT(player.registryAccess()));
        encoded.putString("fallback", encodedKey.getSource().toString());
        encoded.putBoolean("removed", removed);
        if (!removed)
        {
            encoded.putLong("completed", completed);
            encoded.putLong("required", required);
        }
        return encoded;
    }

    private static boolean terminal(RecipeOrderJob.Status status)
    {
        return status == RecipeOrderJob.Status.COMPLETE || status == RecipeOrderJob.Status.CANCELLED
                || status == RecipeOrderJob.Status.ERROR;
    }

    private record SessionKey(UUID playerId, UUID sessionId) {}
    private record StepStatus(IStackKey<?> key, long completed, long required) {}
    private record SyncedOrder(long revision, RecipeOrderJob job) {}
    private record SyncSession(int networkId, Map<UUID, SyncedOrder> orders, long lastAccessNanos) {}

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
