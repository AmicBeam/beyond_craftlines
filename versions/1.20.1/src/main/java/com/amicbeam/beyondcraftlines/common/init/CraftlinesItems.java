package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.item.CraftlineProvisionerItem;
import com.amicbeam.beyondcraftlines.common.item.NetworkLinkerItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CraftlinesItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BeyondCraftlines.MOD_ID);
    public static final RegistryObject<Item> NETWORK_LINKER = ITEMS.register("network_linker", () -> new NetworkLinkerItem(new Item.Properties()));
    public static final RegistryObject<CraftlineProvisionerItem> CRAFTLINE_PROVISIONER = ITEMS.register(
            "craftline_provisioner", () -> new CraftlineProvisionerItem(CraftlinesBlocks.CRAFTLINE_PROVISIONER.get(), new Item.Properties()));
    public static void register(IEventBus bus) { ITEMS.register(bus); }
    private CraftlinesItems() {}
}
