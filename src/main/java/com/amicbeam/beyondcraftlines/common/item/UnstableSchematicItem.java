package com.amicbeam.beyondcraftlines.common.item;

import com.amicbeam.beyondcraftlines.common.data.BlueprintComponents;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintLibrarySavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public final class UnstableSchematicItem extends Item
{
    public UnstableSchematicItem(Properties properties) { super(properties.stacksTo(1)); }

    public static ItemStack of(BlueprintLibrarySavedData library, java.util.UUID blueprintId)
    {
        ItemStack stack = new ItemStack(com.amicbeam.beyondcraftlines.common.init.CraftlinesItems.UNSTABLE_SCHEMATIC.get());
        library.get(blueprintId).ifPresent(record -> {
            stack.set(BlueprintComponents.BLUEPRINT_ID, ResourceLocation.fromNamespaceAndPath("beyond_craftlines", record.id().toString()));
            stack.set(BlueprintComponents.BLUEPRINT_HASH, record.snapshot().hash());
        });
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        ResourceLocation id = stack.get(BlueprintComponents.BLUEPRINT_ID);
        if (id == null || player == null || player.getServer() == null)
        {
            if (player != null) player.sendSystemMessage(Component.translatable("error.beyond_craftlines.schematic_empty"));
            return InteractionResult.FAIL;
        }
        player.sendSystemMessage(Component.translatable("message.beyond_craftlines.schematic_info", id));
        return InteractionResult.SUCCESS;
    }
}
