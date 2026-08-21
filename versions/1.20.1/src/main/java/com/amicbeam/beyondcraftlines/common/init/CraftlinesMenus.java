package com.amicbeam.beyondcraftlines.common.init;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineStatusMenu;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CraftlinesMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, BeyondCraftlines.MOD_ID);
    public static final RegistryObject<MenuType<CraftlineOrderMenu>> ORDER = MENUS.register("craftline_order", () -> IForgeMenuType.create(CraftlineOrderMenu::new));
    public static final RegistryObject<MenuType<CraftlineStatusMenu>> STATUS = MENUS.register("craftline_status", () -> IForgeMenuType.create(CraftlineStatusMenu::new));
    public static final RegistryObject<MenuType<ProvisionerConfigMenu>> PROVISIONER = MENUS.register("provisioner_config", () -> IForgeMenuType.create(ProvisionerConfigMenu::new));
    public static void register(IEventBus bus) { MENUS.register(bus); }
    private CraftlinesMenus() {}
}
