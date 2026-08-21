package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

public final class CraftlineOrderMenu extends AbstractContainerMenu
{
    private final Player player;
    private final int networkId;
    private final IStackKey<?> initialTarget;
    private final ResourceLocation initialRecipe;
    private final Set<String> availableFamilies;
    private final List<RecipeHolder<?>> recipes;
    private final Map<ResourceLocation, RecipeHolder<?>> recipeByOutput;
    private final Map<ResourceLocation, List<RecipeHolder<?>>> recipesByOutput;
    private final Map<IStackKey<?>, List<RecipeHolder<?>>> recipesByResourceOutput;

    public CraftlineOrderMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, data.readVarInt(), IStackKey.STREAM_CODEC.decode((RegistryFriendlyByteBuf) data),
                ResourceLocation.parse(data.readUtf()), readFamilies(data));
    }

    public CraftlineOrderMenu(int id, Inventory inventory, int networkId, IStackKey<?> initialTarget,
                              ResourceLocation initialRecipe, Set<String> availableFamilies)
    {
        super(CraftlinesMenus.ORDER.get(), id);
        this.player = inventory.player;
        this.networkId = networkId;
        this.initialTarget = initialTarget;
        this.initialRecipe = initialRecipe;
        this.availableFamilies = Set.copyOf(availableFamilies);
        this.recipes = RecipePlanningService.visibleRecipes(player.level()).stream()
                .filter(holder -> "crafting".equals(RecipePlanningService.family(holder))
                        || this.availableFamilies.contains(RecipePlanningService.family(holder)))
                .toList();
        LinkedHashMap<ResourceLocation, RecipeHolder<?>> byOutput = new LinkedHashMap<>();
        LinkedHashMap<ResourceLocation, List<RecipeHolder<?>>> allByOutput = new LinkedHashMap<>();
        LinkedHashMap<IStackKey<?>, List<RecipeHolder<?>>> allByResourceOutput = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : recipes)
        {
            for (var output : RecipeOutputResolver.outputs(holder.value(), player.level().registryAccess()))
            {
                allByResourceOutput.computeIfAbsent(output.key(), ignored -> new java.util.ArrayList<>()).add(holder);
                if (output.key() instanceof ItemStackKey itemKey)
                {
                    ResourceLocation outputId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(itemKey.getSource());
                    byOutput.putIfAbsent(outputId, holder);
                    allByOutput.computeIfAbsent(outputId, ignored -> new java.util.ArrayList<>()).add(holder);
                }
            }
        }
        this.recipeByOutput = Map.copyOf(byOutput);
        LinkedHashMap<ResourceLocation, List<RecipeHolder<?>>> frozen = new LinkedHashMap<>();
        allByOutput.forEach((output, holders) -> frozen.put(output, List.copyOf(holders)));
        this.recipesByOutput = Map.copyOf(frozen);
        LinkedHashMap<IStackKey<?>, List<RecipeHolder<?>>> frozenResources = new LinkedHashMap<>();
        allByResourceOutput.forEach((output, holders) -> frozenResources.put(output, List.copyOf(holders)));
        this.recipesByResourceOutput = Map.copyOf(frozenResources);
    }

    public int networkId() { return networkId; }
    public IStackKey<?> initialTarget() { return initialTarget; }
    public String targetToken()
    { return com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(initialTarget); }
    public ResourceLocation initialRecipe() { return initialRecipe; }
    public Set<String> availableFamilies() { return availableFamilies; }
    public List<RecipeHolder<?>> recipes() { return recipes; }
    public RecipeHolder<?> recipeForOutput(ResourceLocation output) { return recipeByOutput.get(output); }
    public List<RecipeHolder<?>> recipesForOutput(ResourceLocation output)
    { return recipesByOutput.getOrDefault(output, List.of()); }
    public List<RecipeHolder<?>> recipesForResourceOutput(IStackKey<?> output)
    {
        for (var entry : recipesByResourceOutput.entrySet()) if (output.isSame(entry.getKey())) return entry.getValue();
        return List.of();
    }
    public RecipeHolder<?> recipeForResourceOutput(IStackKey<?> output)
    { return recipesForResourceOutput(output).stream().findFirst().orElse(null); }

    public boolean canAccessNetwork(Player player)
    {
        if (player != this.player) return false;
        if (player.level().isClientSide()) return true;
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        return network != null && (network.isOwner(player) || network.isManager(player)
                || network.getPlayers().contains(player.getUUID()));
    }

    private static Set<String> readFamilies(FriendlyByteBuf data)
    {
        int count = Math.min(512, Math.max(0, data.readVarInt()));
        LinkedHashSet<String> families = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) families.add(data.readUtf(256));
        return Set.copyOf(families);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return canAccessNetwork(player); }
}
