package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class CraftlinesMenus
{
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BeyondCraftlines.MOD_ID);
    public static final Supplier<MenuType<CraftlineOrderMenu>> ORDER = MENUS.register("craftline_order",
            () -> IMenuTypeExtension.create(CraftlineOrderMenu::new));

    public static void register(IEventBus bus) { MENUS.register(bus); }
    private CraftlinesMenus() {}
}
