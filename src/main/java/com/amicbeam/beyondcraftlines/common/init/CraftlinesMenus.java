package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineStatusMenu;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.menu.DashboardConfigMenu;
import com.amicbeam.beyondcraftlines.common.menu.DashboardStatusMenu;
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
    public static final Supplier<MenuType<CraftlineStatusMenu>> STATUS = MENUS.register("craftline_status",
            () -> IMenuTypeExtension.create(CraftlineStatusMenu::new));
    public static final Supplier<MenuType<ProvisionerConfigMenu>> PROVISIONER = MENUS.register(
            "provisioner_config", () -> IMenuTypeExtension.create(ProvisionerConfigMenu::new));
    public static final Supplier<MenuType<DashboardConfigMenu>> DASHBOARD = MENUS.register(
            "craftline_dashboard", () -> IMenuTypeExtension.create(DashboardConfigMenu::new));
    public static final Supplier<MenuType<DashboardStatusMenu>> DASHBOARD_STATUS = MENUS.register(
            "craftline_dashboard_status", () -> IMenuTypeExtension.create(DashboardStatusMenu::new));

    public static void register(IEventBus bus) { MENUS.register(bus); }
    private CraftlinesMenus() {}
}
