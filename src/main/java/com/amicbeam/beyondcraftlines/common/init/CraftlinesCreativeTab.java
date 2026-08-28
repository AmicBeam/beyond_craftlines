package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CraftlinesCreativeTab
{
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, BeyondCraftlines.MOD_ID);

    static
    {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.beyond_craftlines"))
                .icon(() -> CraftlinesItems.NETWORK_LINKER.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(CraftlinesItems.NETWORK_LINKER);
                    output.accept(CraftlinesItems.CRAFTLINE_PROVISIONER);
                    output.accept(CraftlinesItems.CRAFTLINE_DASHBOARD);
                }).build());
    }

    public static void register(IEventBus bus) { TABS.register(bus); }
    private CraftlinesCreativeTab() {}
}
