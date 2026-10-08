package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.PlannerPreferences;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record PlannerPreferencesPayload(List<SubmitOrderPayload.RecipeChoice> recipes,
                                        List<SubmitOrderPayload.IngredientChoice> ingredients)
        implements CustomPacketPayload
{
    public static final Type<PlannerPreferencesPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "planner_preferences"));
    public static final StreamCodec<ByteBuf, PlannerPreferencesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, SubmitOrderPayload.RECIPE_CHOICE_CODEC, 1024),
            PlannerPreferencesPayload::recipes,
            ByteBufCodecs.collection(ArrayList::new, SubmitOrderPayload.INGREDIENT_CHOICE_CODEC, 1024),
            PlannerPreferencesPayload::ingredients,
            PlannerPreferencesPayload::new);
    public static volatile Consumer<PlannerPreferencesPayload> clientReceiver = ignored -> {};

    public static PlannerPreferencesPayload from(PlannerPreferences.Snapshot snapshot)
    {
        return new PlannerPreferencesPayload(snapshot.recipes().entrySet().stream()
                .map(entry -> new SubmitOrderPayload.RecipeChoice(
                        entry.getKey().toString(), entry.getValue().toString())).toList(),
                snapshot.ingredients().entrySet().stream()
                        .map(entry -> new SubmitOrderPayload.IngredientChoice(entry.getKey().recipe().toString(),
                                entry.getKey().slot(), entry.getValue().toString())).toList());
    }

    public static void handle(PlannerPreferencesPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload)); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
