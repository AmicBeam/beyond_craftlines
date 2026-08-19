package com.amicbeam.beyondcraftlines.common.item;

import com.amicbeam.beyondcraftlines.common.data.BlueprintComponents;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintLibrarySavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public final class StabilizedSchematicItem extends Item
{
    public StabilizedSchematicItem(Properties properties) { super(properties.stacksTo(1)); }

    public static ItemStack of(BlueprintLibrarySavedData library, java.util.UUID blueprintId)
    {
        ItemStack stack = new ItemStack(CraftlinesItems.STABILIZED_SCHEMATIC.get());
        library.get(blueprintId).filter(record -> record.compiled() != null).ifPresent(record -> {
            stack.set(BlueprintComponents.BLUEPRINT_ID,
                    ResourceLocation.fromNamespaceAndPath("beyond_craftlines", record.id().toString()));
            stack.set(BlueprintComponents.BLUEPRINT_HASH, record.compiled().structureHash());
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
        if (id == null || player == null || player.getServer() == null
                || !com.amicbeam.beyondcraftlines.common.structure.BlueprintReferenceValidator.isValid(
                id.getNamespace(), id.getPath(), stack.get(BlueprintComponents.BLUEPRINT_HASH)))
        {
            if (player != null) player.sendSystemMessage(
                    Component.translatable("error.beyond_craftlines.schematic_empty"));
            return InteractionResult.FAIL;
        }
        if (context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof com.amicbeam.beyondcraftlines.common.runtime.ExecutorBlockEntity executor)
        {
            if (player.isShiftKeyDown())
            {
                executor.setBlueprint(stack);
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_craftlines.executor_loaded", id));
                return InteractionResult.SUCCESS;
            }
        }
        player.sendSystemMessage(Component.translatable("message.beyond_craftlines.schematic_info", id));
        return InteractionResult.SUCCESS;
    }
}
