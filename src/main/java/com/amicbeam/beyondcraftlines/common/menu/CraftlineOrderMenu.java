package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CraftlineOrderMenu extends AbstractContainerMenu
{
    private final Player player;
    private final int networkId;
    private final ResourceLocation initialTarget;
    private final Set<String> availableFamilies;

    public CraftlineOrderMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, data.readVarInt(), ResourceLocation.parse(data.readUtf()), readFamilies(data));
    }

    public CraftlineOrderMenu(int id, Inventory inventory, int networkId, ResourceLocation initialTarget,
                              Set<String> availableFamilies)
    {
        super(CraftlinesMenus.ORDER.get(), id);
        this.player = inventory.player;
        this.networkId = networkId;
        this.initialTarget = initialTarget;
        this.availableFamilies = Set.copyOf(availableFamilies);
    }

    public int networkId() { return networkId; }
    public ResourceLocation initialTarget() { return initialTarget; }
    public Set<String> availableFamilies() { return availableFamilies; }
    public List<RecipeHolder<?>> recipes()
    {
        return RecipePlanningService.visibleRecipes(player.level()).stream()
                .filter(holder -> "crafting".equals(RecipePlanningService.family(holder))
                        || availableFamilies.contains(RecipePlanningService.family(holder)))
                .toList();
    }

    private static Set<String> readFamilies(FriendlyByteBuf data)
    {
        int count = Math.min(512, Math.max(0, data.readVarInt()));
        LinkedHashSet<String> families = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) families.add(data.readUtf(256));
        return Set.copyOf(families);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player == this.player; }
}
