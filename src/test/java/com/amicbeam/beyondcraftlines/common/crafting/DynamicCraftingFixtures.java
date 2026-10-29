package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Uses real Minecraft stacks and vanilla recipe assembly, with version-specific API construction only. */
public final class DynamicCraftingFixtures
{
    private DynamicCraftingFixtures() {}
    public static void bootstrap()
    {
        // The plain 1.21 JUnit harness has no mod discovery; vanilla bootstrap still consults feature flags.
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }
    public static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    public static FireworkRocketRecipe source() { return new FireworkRocketRecipe(CraftingBookCategory.MISC); }
    public static com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> key(ItemStack item)
    { return new com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey(item); }
    public static ItemStack rocket(int flight)
    { return source().assemble(CraftingInput.of(3, 3, FireworkRocketRecipeVariants.samples(flight)), null); }
    public static ItemStack assemble(FireworkRocketCraftingVariant variant, int flight)
    { return variant.assemble(CraftingInput.of(3, 3, FireworkRocketRecipeVariants.samples(flight)), null); }
    public static boolean matches(FireworkRocketCraftingVariant variant, int flight)
    { return variant.matches(CraftingInput.of(3, 3, FireworkRocketRecipeVariants.samples(flight)), null); }
    public static ItemStack potion(boolean awkward, boolean splash)
    { return PotionContents.createItemStack(splash ? Items.SPLASH_POTION : Items.POTION, awkward ? Potions.AWKWARD : Potions.WATER); }
    public static ItemStack blade(String state)
    {
        ItemStack item = new ItemStack(Items.IRON_SWORD);
        CompoundTag tag = new CompoundTag(); tag.putString("bladeState", state);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return item;
    }
}
