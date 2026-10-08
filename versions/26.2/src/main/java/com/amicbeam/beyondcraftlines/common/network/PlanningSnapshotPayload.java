package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

public record PlanningSnapshotPayload(long nonce, String itemId, Header header, List<Entry> entries)
        implements CustomPacketPayload
{
    private static final int PAGE_SIZE = 256;
    public static final Type<PlanningSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "planning_snapshot"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            IStackKey.STREAM_CODEC, Entry::key,
            ByteBufCodecs.VAR_LONG, Entry::amount,
            Entry::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PlanningSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PlanningSnapshotPayload::nonce,
            ByteBufCodecs.stringUtf8(256), PlanningSnapshotPayload::itemId,
            Header.STREAM_CODEC, PlanningSnapshotPayload::header,
            ByteBufCodecs.collection(ArrayList::new, ENTRY_CODEC, PAGE_SIZE), PlanningSnapshotPayload::entries,
            PlanningSnapshotPayload::new);
    public static volatile Consumer<PlanningSnapshotPayload> clientReceiver = ignored -> {};

    public static List<PlanningSnapshotPayload> from(long nonce, String target,
                                                     PlanningSnapshotService.Snapshot snapshot,
                                                     long recipeEpoch, int maxDepth, int maxNodes)
    {
        List<Entry> all = snapshot.componentEntries().stream()
                .map(entry -> new Entry(entry.key(), entry.amount())).toList();
        int pageCount = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (pageCount > 64) throw new IllegalStateException("planning stock snapshot exceeds the transfer limit");
        List<PlanningSnapshotPayload> result = new ArrayList<>(pageCount);
        for (int page = 0; page < pageCount; page++)
        {
            int from = page * PAGE_SIZE;
            int to = Math.min(all.size(), from + PAGE_SIZE);
            result.add(new PlanningSnapshotPayload(nonce, target, new Header(
                    new Status(true, ""), snapshot.revision(), recipeEpoch, page, pageCount,
                    new Limits(maxDepth, maxNodes)), List.copyOf(all.subList(from, to))));
        }
        return List.copyOf(result);
    }

    public static PlanningSnapshotPayload failure(long nonce, String itemId, String error)
    {
        String message = error == null || error.isBlank() ? "planning snapshot failed" : error;
        if (message.length() > 512) message = message.substring(0, 512);
        return new PlanningSnapshotPayload(nonce, itemId, new Header(new Status(false, message),
                0, 0, 0, 1, new Limits(1, 1)), List.of());
    }

    public static void handle(PlanningSnapshotPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload)); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(IStackKey<?> key, long amount) {}
    public record Limits(int maxDepth, int maxNodes)
    {
        private static final StreamCodec<ByteBuf, Limits> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Limits::maxDepth,
                ByteBufCodecs.VAR_INT, Limits::maxNodes,
                Limits::new);
    }
    public record Status(boolean success, String error)
    {
        private static final StreamCodec<ByteBuf, Status> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Status::success,
                ByteBufCodecs.stringUtf8(512), Status::error,
                Status::new);
    }
    public record Header(Status status, long stockRevision, long recipeEpoch, int pageIndex, int pageCount,
                         Limits limits)
    {
        private static final StreamCodec<ByteBuf, Header> STREAM_CODEC = StreamCodec.composite(
                Status.STREAM_CODEC, Header::status,
                ByteBufCodecs.VAR_LONG, Header::stockRevision,
                ByteBufCodecs.VAR_LONG, Header::recipeEpoch,
                ByteBufCodecs.VAR_INT, Header::pageIndex,
                ByteBufCodecs.VAR_INT, Header::pageCount,
                Limits.STREAM_CODEC, Header::limits,
                Header::new);
    }
}
