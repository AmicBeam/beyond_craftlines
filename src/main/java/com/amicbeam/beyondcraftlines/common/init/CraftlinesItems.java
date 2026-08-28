package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.item.NetworkLinkerItem;
import com.amicbeam.beyondcraftlines.common.item.CraftlineProvisionerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CraftlinesItems
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondCraftlines.MOD_ID);
    public static final DeferredItem<Item> NETWORK_LINKER = ITEMS.register("network_linker", () -> new NetworkLinkerItem(new Item.Properties()));
    public static final DeferredItem<CraftlineProvisionerItem> CRAFTLINE_PROVISIONER = ITEMS.register(
            "craftline_provisioner", () -> new CraftlineProvisionerItem(
                    CraftlinesBlocks.CRAFTLINE_PROVISIONER.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> CRAFTLINE_DASHBOARD = ITEMS.register(
            "craftline_dashboard", () -> new BlockItem(
                    CraftlinesBlocks.CRAFTLINE_DASHBOARD.get(), new Item.Properties()));

    public static void register(IEventBus bus) { ITEMS.register(bus); }

    private CraftlinesItems() {}
}
