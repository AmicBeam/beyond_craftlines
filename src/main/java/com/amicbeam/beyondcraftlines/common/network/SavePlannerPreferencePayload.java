package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.PlannerPreferences;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeIngredientResolver;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SavePlannerPreferencePayload(String kind, String parent, int slot, String choice)
        implements CustomPacketPayload
{
    public static final Type<SavePlannerPreferencePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "save_planner_preference"));
    public static final StreamCodec<ByteBuf, SavePlannerPreferencePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1), SavePlannerPreferencePayload::kind,
            ByteBufCodecs.stringUtf8(256), SavePlannerPreferencePayload::parent,
            ByteBufCodecs.VAR_INT, SavePlannerPreferencePayload::slot,
            ByteBufCodecs.stringUtf8(256), SavePlannerPreferencePayload::choice,
            SavePlannerPreferencePayload::new);

    public static void handle(SavePlannerPreferencePayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                    || !menu.canAccessNetwork(player)) return;
            ResourceLocation parent = ResourceLocation.tryParse(payload.parent());
            ResourceLocation choice = payload.choice().isEmpty() ? null : ResourceLocation.tryParse(payload.choice());
            boolean valid = parent != null && (payload.choice().isEmpty() || choice != null);
            if (valid && "R".equals(payload.kind()))
            {
                valid = choice == null || menu.recipesForOutput(parent).stream().anyMatch(holder -> holder.id().equals(choice));
                if (valid) PlannerPreferences.setRecipe(player, parent, choice);
            }
            else if (valid && "I".equals(payload.kind()) && payload.slot() >= 0)
            {
                RecipeHolder<?> holder = menu.recipes().stream().filter(recipe -> recipe.id().equals(parent)).findFirst().orElse(null);
                var ingredients = holder == null ? java.util.List.<net.minecraft.world.item.crafting.Ingredient>of()
                        : RecipeIngredientResolver.ingredients(holder.value());
                valid = holder != null && payload.slot() < ingredients.size()
                        && (choice == null || java.util.Arrays.stream(ingredients
                        .get(payload.slot()).getItems()).anyMatch(stack ->
                                BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(choice)));
                if (valid) PlannerPreferences.setIngredient(player,
                        new PlannerPreferences.IngredientKey(parent, payload.slot()), choice);
            }
            else valid = false;
            if (!valid)
            {
                player.displayClientMessage(Component.translatable(
                        "error.beyond_craftlines.invalid_planner_preference"), false);
                return;
            }
            player.displayClientMessage(Component.translatable(payload.choice().isEmpty()
                    ? "message.beyond_craftlines.planner_default_cleared"
                    : "message.beyond_craftlines.planner_default_saved"), true);
            PacketDistributor.sendToPlayer(player, PlannerPreferencesPayload.from(PlannerPreferences.read(player)));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
