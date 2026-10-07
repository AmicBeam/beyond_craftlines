package com.amicbeam.beyondcraftlines.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Local cache maintenance; available to every player, including on remote servers. */
public final class ClientRecipeCacheCommands
{
    private ClientRecipeCacheCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("craftlines")
                .then(Commands.literal("reload").executes(context -> {
                    if (Minecraft.getInstance().level == null)
                    {
                        context.getSource().sendFailure(Component.translatable(
                                "command.beyond_craftlines.reload.no_world"));
                        return 0;
                    }
                    try
                    {
                        ClientPlanningCatalogWarmup.reload();
                        context.getSource().sendSuccess(() -> Component.translatable(
                                "command.beyond_craftlines.reload.success"), false);
                        return 1;
                    }
                    catch (java.io.IOException exception)
                    {
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                                "Unable to clear the client planning cache", exception);
                        context.getSource().sendFailure(Component.translatable(
                                "command.beyond_craftlines.reload.failed"));
                        return 0;
                    }
                })));
    }
}
