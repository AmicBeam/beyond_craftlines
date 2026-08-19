package com.amicbeam.beyondcraftlines.common.structure;

import com.amicbeam.beyondcraftlines.common.item.TrialReportItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class TrialReportDeliveryService
{
    private TrialReportDeliveryService() {}

    public static boolean deliver(MinecraftServer server, UUID blueprintId, UUID owner,
                                  TrialObservation observation)
    {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null) return false;

        String hash = BlueprintLibrarySavedData.get(server).get(blueprintId)
                .map(record -> record.snapshot().hash()).orElse("");
        ItemStack report = TrialReportItem.of(observation, blueprintId, hash);
        player.getInventory().add(report);
        if (!report.isEmpty()) player.drop(report, false);
        return true;
    }
}
