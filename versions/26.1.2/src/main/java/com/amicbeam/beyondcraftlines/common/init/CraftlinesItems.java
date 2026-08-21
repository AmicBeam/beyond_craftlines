package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.item.NetworkLinkerItem;
import com.amicbeam.beyondcraftlines.common.item.CraftlineProvisionerItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CraftlinesItems
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondCraftlines.MOD_ID);
    public static final DeferredItem<Item> NETWORK_LINKER = ITEMS.registerItem(
            "network_linker", NetworkLinkerItem::new);
    public static final DeferredItem<CraftlineProvisionerItem> CRAFTLINE_PROVISIONER = ITEMS.registerItem(
            "craftline_provisioner", properties -> new CraftlineProvisionerItem(
                    CraftlinesBlocks.CRAFTLINE_PROVISIONER.get(), properties));

    public static void register(IEventBus bus) { ITEMS.register(bus); }

    private CraftlinesItems() {}
}
