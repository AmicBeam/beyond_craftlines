package com.amicbeam.beyondcraftlines.client.bd08

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.amicbeam.beyondcraftlines.common.network.OpenDashboardStatusMenuPayload
import com.amicbeam.beyondcraftlines.common.network.OpenOrderStatusMenuPayload
import com.wintercogs.beyonddimensions.api.ui.page.BdPage
import com.wintercogs.beyonddimensions.api.ui.page.BdPageContext
import com.wintercogs.beyonddimensions.api.ui.page.BdPageScope
import com.wintercogs.beyonddimensions.api.ui.page.BdPages
import com.wintercogs.beyonddimensions.client.ui.theme.Bd
import com.wintercogs.beyonddimensions.client.ui.kit.BdChip
import com.wintercogs.beyonddimensions.client.ui.theme.BdColors
import com.wintercogs.beyonddimensions.client.ui.kit.BdIcons
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu
import dev.compixel.ui.ore.display.OreText
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor

/** Uses BD's supported extension API; actions run on the Minecraft thread. */
object CraftlinesPages {
    @JvmStatic
    fun register() {
        BdPages.register<DimensionsNetMenu>(
            ResourceLocation.fromNamespaceAndPath("beyond_craftlines", "status"),
            StatusPage,
        )
    }

    data class Labels(val orders: String, val dashboards: String)
    enum class Action { ORDERS, DASHBOARDS }

    private object StatusPage : BdPage<DimensionsNetMenu, Labels, Action> {
        override val icon = BdIcons.Crafting
        override fun title(context: BdPageContext<DimensionsNetMenu>) = Component.literal("Beyond: Craftlines")
        override fun snapshot(context: BdPageContext<DimensionsNetMenu>) = Labels(
            Component.translatable("tooltip.beyond_craftlines.open_crafting_status").string,
            Component.translatable("tooltip.beyond_craftlines.open_dashboard_status").string,
        )
        override fun handle(context: BdPageContext<DimensionsNetMenu>, action: Action) {
            // Keep the network menu open until the server validates it and opens the requested menu.
            when (action) {
                Action.ORDERS -> PacketDistributor.sendToServer(OpenOrderStatusMenuPayload())
                Action.DASHBOARDS -> PacketDistributor.sendToServer(OpenDashboardStatusMenuPayload())
            }
        }
        @Composable
        override fun ColumnScope.Content(state: Labels, scope: BdPageScope<Action>) {
            BdChip({ scope.send(Action.ORDERS) }, enabled = scope.shown) {
                OreText(state.orders, color = Bd.colors[BdColors.text])
            }
            BdChip({ scope.send(Action.DASHBOARDS) }, enabled = scope.shown) {
                OreText(state.dashboards, color = Bd.colors[BdColors.text])
            }
        }
    }
}
