package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record PlanPreviewPayload(long nonce, String itemId, Header header,
                                 List<SubmitOrderPayload.RecipeChoice> recipeChoices,
                                 List<SubmitOrderPayload.IngredientChoice> ingredientChoices)
        implements CustomPacketPayload
{
    public static final Type<PlanPreviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "plan_preview"));
    public static final StreamCodec<ByteBuf, PlanPreviewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PlanPreviewPayload::nonce,
            ByteBufCodecs.stringUtf8(256), PlanPreviewPayload::itemId,
            Header.STREAM_CODEC, PlanPreviewPayload::header,
            ByteBufCodecs.collection(ArrayList::new, SubmitOrderPayload.RECIPE_CHOICE_CODEC, 256),
            PlanPreviewPayload::recipeChoices,
            ByteBufCodecs.collection(ArrayList::new, SubmitOrderPayload.INGREDIENT_CHOICE_CODEC, 256),
            PlanPreviewPayload::ingredientChoices,
            PlanPreviewPayload::new);

    public static volatile Consumer<PlanPreviewPayload> clientReceiver = ignored -> {};

    public static List<PlanPreviewPayload> from(long nonce, RecipePlan plan)
    {
        Map<ResourceLocation, ResourceLocation> recipes = new LinkedHashMap<>();
        Map<Slot, ResourceLocation> ingredients = new LinkedHashMap<>();
        for (RecipePlan.Step step : plan.steps())
        {
            recipes.put(step.output(), step.recipe());
            for (RecipePlan.IngredientSelection selection : step.ingredientSelections())
                ingredients.put(new Slot(step.recipe(), selection.slot()), selection.item());
        }
        List<SubmitOrderPayload.RecipeChoice> recipeChoices = recipes.entrySet().stream()
                .map(entry -> new SubmitOrderPayload.RecipeChoice(
                        entry.getKey().toString(), entry.getValue().toString())).toList();
        List<SubmitOrderPayload.IngredientChoice> ingredientChoices = ingredients.entrySet().stream()
                .map(entry -> new SubmitOrderPayload.IngredientChoice(
                        entry.getKey().recipe().toString(), entry.getKey().slot(),
                        entry.getValue().toString())).toList();
        List<PreviewPagePartitioner.Page<SubmitOrderPayload.RecipeChoice,
                SubmitOrderPayload.IngredientChoice>> pages = PreviewPagePartitioner.partition(
                recipeChoices, ingredientChoices, 256);
        List<PlanPreviewPayload> payloads = new ArrayList<>(pages.size());
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++)
        {
            var page = pages.get(pageIndex);
            payloads.add(new PlanPreviewPayload(nonce, plan.target().toString(),
                    new Header(true, "", "", pageIndex, pages.size()), page.first(), page.second()));
        }
        return List.copyOf(payloads);
    }

    public static PlanPreviewPayload failure(long nonce, String itemId, String error)
    {
        String message = error == null || error.isBlank() ? "plan preview failed" : error;
        if (message.length() > 512) message = message.substring(0, 512);
        return new PlanPreviewPayload(nonce, itemId, new Header(false, message, "generic", 0, 1), List.of(), List.of());
    }

    public static PlanPreviewPayload stale(long nonce, String itemId)
    {
        return new PlanPreviewPayload(nonce, itemId, new Header(false,
                "planning snapshot changed; refreshing", "stale", 0, 1), List.of(), List.of());
    }

    public static void handle(PlanPreviewPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload)); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public boolean success() { return header.success(); }
    public String error() { return header.error(); }
    public String failureKind() { return header.failureKind(); }
    public int pageIndex() { return header.pageIndex(); }
    public int pageCount() { return header.pageCount(); }

    public record Header(boolean success, String error, String failureKind, int pageIndex, int pageCount)
    {
        static final StreamCodec<ByteBuf, Header> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Header::success,
                ByteBufCodecs.stringUtf8(512), Header::error,
                ByteBufCodecs.stringUtf8(32), Header::failureKind,
                ByteBufCodecs.VAR_INT, Header::pageIndex,
                ByteBufCodecs.VAR_INT, Header::pageCount,
                Header::new);
    }

    private record Slot(ResourceLocation recipe, int slot) {}
}
