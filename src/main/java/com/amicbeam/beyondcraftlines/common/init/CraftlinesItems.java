package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.item.SpacetimeLinkerItem;
import com.amicbeam.beyondcraftlines.common.item.StabilizedSchematicItem;
import com.amicbeam.beyondcraftlines.common.item.UnstableSchematicItem;
import com.amicbeam.beyondcraftlines.common.item.TrialReportItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CraftlinesItems
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondCraftlines.MOD_ID);
    public static final DeferredItem<Item> SPACETIME_LINKER = ITEMS.register("spacetime_linker", () -> new SpacetimeLinkerItem(new Item.Properties()));
    public static final DeferredItem<Item> UNSTABLE_SCHEMATIC = ITEMS.register("unstable_schematic", () -> new UnstableSchematicItem(new Item.Properties()));
    public static final DeferredItem<Item> STABILIZED_SCHEMATIC = ITEMS.register("stabilized_schematic", () -> new StabilizedSchematicItem(new Item.Properties()));
    public static final DeferredItem<Item> TRIAL_REPORT = ITEMS.register("trial_report", () -> new TrialReportItem(new Item.Properties()));

    public static void register(IEventBus bus) { ITEMS.register(bus); }
    private CraftlinesItems() {}
}
