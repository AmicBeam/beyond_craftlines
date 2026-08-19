package com.amicbeam.beyondcraftlines.common.command;

import com.amicbeam.beyondcraftlines.common.structure.BlueprintLibrarySavedData;
import com.amicbeam.beyondcraftlines.common.block.SchematicAnchorBlock;
import com.amicbeam.beyondcraftlines.common.structure.CaptureService;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "beyond_craftlines")
public final class CraftlinesCommands
{
    private CraftlinesCommands() {}

    private static net.minecraft.world.item.ItemStack findTrialReport(
            net.minecraft.server.level.ServerPlayer player, java.util.UUID blueprintId)
    {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            var stack = inventory.getItem(slot);
            if (com.amicbeam.beyondcraftlines.common.item.TrialReportItem.blueprintId(stack) != null
                    && blueprintId.equals(com.amicbeam.beyondcraftlines.common.item.TrialReportItem.blueprintId(stack)))
                return stack;
        }
        return null;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("beyond_craftlines")
                .then(Commands.literal("list_blueprints").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    int count = BlueprintLibrarySavedData.get(player.getServer()).all().size();
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "command.beyond_craftlines.list_blueprints", count), false);
                    return count;
                }))
                .then(Commands.literal("compile")
                        .then(Commands.argument("blueprint", StringArgumentType.word()).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            try {
                                var id = java.util.UUID.fromString(StringArgumentType.getString(context, "blueprint"));
                                var compiled = BlueprintLibrarySavedData.get(player.getServer()).compile(id, player.getUUID());
                                if (compiled.isEmpty()) {
                                    context.getSource().sendFailure(Component.translatable("error.beyond_craftlines.compile_denied"));
                                    return 0;
                                }
                                context.getSource().sendSuccess(() -> Component.translatable("command.beyond_craftlines.compiled", id), false);
                                return 1;
                            } catch (IllegalArgumentException exception) {
                                context.getSource().sendFailure(Component.translatable("error.beyond_craftlines.invalid_blueprint"));
                                return 0;
                            }
                        })))
                .then(Commands.literal("test")
                        .then(Commands.argument("blueprint", StringArgumentType.word()).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            try
                            {
                                var blueprintId = java.util.UUID.fromString(
                                        StringArgumentType.getString(context, "blueprint"));
                                var library = BlueprintLibrarySavedData.get(player.getServer());
                                var record = library.get(blueprintId);
                                if (record.isEmpty() || !record.get().owner().equals(player.getUUID()))
                                {
                                    context.getSource().sendFailure(Component.translatable(
                                            "error.beyond_craftlines.compile_denied"));
                                    return 0;
                                }
                                net.minecraft.server.level.ServerLevel sandboxLevel =
                                        com.amicbeam.beyondcraftlines.common.structure.SandboxDimension.resolve(
                                                player.getServer());
                                if (!com.amicbeam.beyondcraftlines.common.structure.SandboxDimension
                                        .isDedicatedDimension(player.getServer()))
                                {
                                    context.getSource().sendFailure(Component.translatable(
                                            "error.beyond_craftlines.sandbox_unavailable"));
                                    return 0;
                                }
                                if (com.amicbeam.beyondcraftlines.common.structure.SandboxSessionSavedData
                                        .get(player.getServer()).findByOwner(player.getUUID()) != null)
                                {
                                    context.getSource().sendFailure(Component.translatable(
                                            "error.beyond_craftlines.sandbox_already_active"));
                                    return 0;
                                }
                                var session = com.amicbeam.beyondcraftlines.common.structure.SandboxManager.allocate(
                                        player.getServer(), blueprintId, player.getUUID());
                                com.amicbeam.beyondcraftlines.common.structure.SandboxExitService.enter(
                                        player.getServer(), player, session);
                                context.getSource().sendSuccess(() -> Component.literal(
                                        "Sandbox session " + session.id() + " started at "
                                                + session.slot().originX() + ", " + session.slot().originY() + ", "
                                                + session.slot().originZ()), false);
                                return 1;
                            }
                            catch (IllegalArgumentException exception)
                            {
                                context.getSource().sendFailure(Component.translatable(
                                        "error.beyond_craftlines.invalid_blueprint"));
                                return 0;
                            }
                        })))
                .then(Commands.literal("stop_test")
                        .then(Commands.argument("session", StringArgumentType.word()).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            try
                            {
                                var sessionId = java.util.UUID.fromString(
                                        StringArgumentType.getString(context, "session"));
                                if (!com.amicbeam.beyondcraftlines.common.structure.SandboxExitService.exit(
                                        player.getServer(), player, sessionId))
                                {
                                    context.getSource().sendFailure(Component.literal(
                                            "Sandbox session is not active for you."));
                                    return 0;
                                }
                                context.getSource().sendSuccess(() -> Component.literal(
                                        "Sandbox session released: " + sessionId), false);
                                return 1;
                            }
                            catch (IllegalArgumentException exception)
                            {
                                context.getSource().sendFailure(Component.literal(
                                        "Sandbox session not found or not owned by you."));
                                return 0;
                            }
                        })))
                .then(Commands.literal("trial_start")
                        .then(Commands.argument("blueprint", StringArgumentType.word())
                                .then(Commands.argument("duration", LongArgumentType.longArg(1)).executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    try
                                    {
                                        var blueprintId = java.util.UUID.fromString(
                                                StringArgumentType.getString(context, "blueprint"));
                                        var network = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet
                                                .getNetFromPlayer(player);
                                        if (network == null)
                                            throw new IllegalStateException("Beyond Dimensions network is required");
                                        com.amicbeam.beyondcraftlines.common.structure.TrialMeasurementService.begin(
                                                player.getServer(), blueprintId, network.getId());
                                        var session = com.amicbeam.beyondcraftlines.common.structure.TrialSessionService.start(
                                                player.getServer(), blueprintId, player.getUUID(),
                                                player.level().getGameTime(), LongArgumentType.getLong(context, "duration"));
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                "Trial started: " + session.blueprintId()), false);
                                        return 1;
                                    }
                                    catch (IllegalArgumentException | IllegalStateException exception)
                                    {
                                        context.getSource().sendFailure(Component.translatable(
                                                "error.beyond_craftlines.trial_start_failed"));
                                        return 0;
                                    }
                                }))))
                .then(Commands.literal("trial_confirm")
                        .then(Commands.argument("blueprint", StringArgumentType.word()).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            try
                            {
                                var blueprintId = java.util.UUID.fromString(
                                        StringArgumentType.getString(context, "blueprint"));
                                var network = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet
                                        .getNetFromPlayer(player);
                                if (network == null)
                                    throw new IllegalStateException("Beyond Dimensions network is required");
                                var trial = com.amicbeam.beyondcraftlines.common.structure.TrialSessionSavedData
                                        .get(player.getServer()).get(blueprintId);
                                if (trial == null || trial.state().status()
                                        != com.amicbeam.beyondcraftlines.common.structure.TrialRunState.Status.COMPLETE)
                                    com.amicbeam.beyondcraftlines.common.structure.TrialMeasurementService.finish(
                                            player.getServer(), blueprintId, player.getUUID(),
                                            player.level().getGameTime(), network.getId());
                                var report = findTrialReport(player, blueprintId);
                                if (report == null)
                                    throw new IllegalStateException("trial report item is required");
                                var record = BlueprintLibrarySavedData.get(player.getServer()).get(blueprintId)
                                        .orElseThrow(() -> new IllegalArgumentException("blueprint not found"));
                                if (!com.amicbeam.beyondcraftlines.common.item.TrialReportItem.matches(
                                        report, blueprintId, record.snapshot().hash()))
                                    throw new IllegalStateException("trial report does not match blueprint");
                                var compiled = com.amicbeam.beyondcraftlines.common.structure.TrialReportConfirmationService.confirm(
                                        player.getServer(), blueprintId, player.getUUID(),
                                        player.level().getGameTime());
                                report.shrink(1);
                                context.getSource().sendSuccess(() -> Component.literal(
                                        "Trial report confirmed: " + compiled.id()), false);
                                return 1;
                            }
                            catch (IllegalArgumentException | IllegalStateException exception)
                            {
                                context.getSource().sendFailure(Component.translatable(
                                        "error.beyond_craftlines.trial_confirm_failed"));
                                return 0;
                            }
                        })))
                .then(Commands.literal("queue")
                        .then(Commands.argument("blueprint", StringArgumentType.word())
                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            var player = context.getSource().getPlayerOrException();
                                            try
                                            {
                                                var blueprintId = java.util.UUID.fromString(StringArgumentType.getString(context, "blueprint"));
                                                var network = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet.getNetFromPlayer(player);
                                                if (network == null) throw new IllegalStateException("Beyond Dimensions network is required");
                                                var job = com.amicbeam.beyondcraftlines.common.runtime.ProductionQueueService.enqueue(
                                                        player.getServer(), blueprintId, player.getUUID(), network.getId(),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count"));
                                                context.getSource().sendSuccess(() -> Component.literal("Production queued: " + job.id()), false);
                                                return 1;
                                            }
                                            catch (IllegalArgumentException | IllegalStateException exception)
                                            {
                                                context.getSource().sendFailure(Component.literal("Production queue failed: " + exception.getMessage()));
                                                return 0;
                                            }
                                        }))))
                .then(Commands.literal("capture")
                        .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            if (!SchematicAnchorBlock.capture(player, StringArgumentType.getString(context, "name"))) {
                                context.getSource().sendFailure(Component.translatable("error.beyond_craftlines.no_selection"));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "command.beyond_craftlines.captured", StringArgumentType.getString(context, "name")), false);
                            return 1;
                        }))));
    }
}
