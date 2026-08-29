package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Uploads only the JEI virtual recipes selected by one recursive client proposal. */
public record VirtualRecipeUploadPayload(long nonce, int pageIndex, int pageCount, List<Entry> recipes)
        implements CustomPacketPayload
{
    private static final int PAGE_SIZE = 8;
    public static final Type<VirtualRecipeUploadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "virtual_recipe_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VirtualRecipeUploadPayload> STREAM_CODEC =
            StreamCodec.of(VirtualRecipeUploadPayload::encode, VirtualRecipeUploadPayload::decode);

    public static List<VirtualRecipeUploadPayload> pages(long nonce, Set<ResourceLocation> recipeIds)
    {
        List<Entry> entries = recipeIds.stream().sorted().map(id -> {
            var holder = VirtualProvisionerRecipeRegistry.find(id).orElse(null);
            var descriptor = holder == null ? null : VirtualProvisionerRecipeRegistry.descriptor(holder.value());
            return descriptor == null ? null : new Entry(id.toString(), descriptor.family(), descriptor.output(),
                    descriptor.outputAmount(), descriptor.inputs().stream().map(input ->
                    new Input(input.inputGroup(), input.candidates())).toList());
        }).filter(java.util.Objects::nonNull).toList();
        int count = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        List<VirtualRecipeUploadPayload> pages = new ArrayList<>();
        for (int page = 0; page < count; page++)
            pages.add(new VirtualRecipeUploadPayload(nonce, page, count,
                    entries.subList(page * PAGE_SIZE, Math.min(entries.size(), (page + 1) * PAGE_SIZE))));
        return List.copyOf(pages);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VirtualRecipeUploadPayload payload)
    {
        buffer.writeVarLong(payload.nonce());
        buffer.writeVarInt(payload.pageIndex());
        buffer.writeVarInt(payload.pageCount());
        buffer.writeVarInt(payload.recipes().size());
        for (Entry recipe : payload.recipes())
        {
            buffer.writeUtf(recipe.id(), 256);
            buffer.writeUtf(recipe.family(), 256);
            IStackKey.STREAM_CODEC.encode(buffer, recipe.output());
            buffer.writeVarLong(recipe.outputAmount());
            buffer.writeVarInt(recipe.inputs().size());
            for (Input input : recipe.inputs())
            {
                buffer.writeUtf(input.inputGroup(), 64);
                buffer.writeVarInt(input.candidates().size());
                for (KeyAmount candidate : input.candidates())
                {
                    IStackKey.STREAM_CODEC.encode(buffer, candidate.key());
                    buffer.writeVarLong(candidate.amount());
                }
            }
        }
    }

    private static VirtualRecipeUploadPayload decode(RegistryFriendlyByteBuf buffer)
    {
        long nonce = buffer.readVarLong();
        int page = buffer.readVarInt();
        int pages = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > PAGE_SIZE) throw new IllegalArgumentException("invalid virtual recipe page");
        List<Entry> recipes = new ArrayList<>();
        for (int index = 0; index < size; index++)
        {
            String id = buffer.readUtf(256);
            String family = buffer.readUtf(256);
            IStackKey<?> output = IStackKey.STREAM_CODEC.decode(buffer);
            long outputAmount = buffer.readVarLong();
            int inputCount = buffer.readVarInt();
            if (inputCount < 1 || inputCount > 32) throw new IllegalArgumentException("invalid virtual inputs");
            List<Input> inputs = new ArrayList<>();
            for (int slot = 0; slot < inputCount; slot++)
            {
                String group = buffer.readUtf(64);
                int candidateCount = buffer.readVarInt();
                if (candidateCount < 1 || candidateCount > 64)
                    throw new IllegalArgumentException("invalid virtual candidates");
                List<KeyAmount> candidates = new ArrayList<>();
                for (int candidate = 0; candidate < candidateCount; candidate++)
                    candidates.add(new KeyAmount(IStackKey.STREAM_CODEC.decode(buffer), buffer.readVarLong()));
                inputs.add(new Input(group, candidates));
            }
            recipes.add(new Entry(id, family, output, outputAmount, inputs));
        }
        return new VirtualRecipeUploadPayload(nonce, page, pages, recipes);
    }

    public static void handle(VirtualRecipeUploadPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                || !menu.canAccessNetwork(player) || payload.pageCount() < 1 || payload.pageCount() > 64
                || payload.pageIndex() < 0 || payload.pageIndex() >= payload.pageCount()) return;
        for (Entry recipe : payload.recipes())
        {
            if (!menu.availableFamilies().contains(recipe.family()))
                return;
            var holder = VirtualProvisionerRecipeRegistry.register(recipe.family(), recipe.output(),
                    recipe.outputAmount(), recipe.inputs().stream().map(input ->
                            new VirtualProvisionerRecipeRegistry.InputSlot(
                                    input.inputGroup(), input.candidates())).toList());
            if (!holder.id().toString().equals(recipe.id()))
                return;
        }
    }); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    public record Entry(String id, String family, IStackKey<?> output, long outputAmount, List<Input> inputs) {}
    public record Input(String inputGroup, List<KeyAmount> candidates) {}
}
