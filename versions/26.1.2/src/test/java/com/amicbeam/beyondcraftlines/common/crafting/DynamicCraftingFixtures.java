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
import net.minecraft.resources.Identifier;
import java.util.List;

/** Uses real Minecraft stacks and vanilla recipe assembly, with version-specific API construction only. */
public final class DynamicCraftingFixtures
{
    private DynamicCraftingFixtures() {}
    private static boolean initialized;
    public static synchronized void bootstrap()
    {
        if (initialized) return;
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        // 26.x normally binds defaults during datapack reload. These focused unit fixtures need
        // only common defaults; potion, blade-state and firework components are supplied explicitly.
        for (Item item : List.of(Items.PAPER, Items.GUNPOWDER, Items.FIREWORK_STAR, Items.FIREWORK_ROCKET,
                Items.POTION, Items.SPLASH_POTION, Items.IRON_SWORD))
            item.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
        initialized = true;
    }
    public static Identifier id(String value) { return Identifier.parse(value); }
    public static FireworkRocketRecipe source() { return new FireworkRocketRecipe(Ingredient.of(Items.PAPER), Ingredient.of(Items.GUNPOWDER),
            Ingredient.of(Items.FIREWORK_STAR), ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.FIREWORK_ROCKET, 3))); }
    public static com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> key(ItemStack item)
    { return new com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey(item); }
    public static ItemStack rocket(int flight)
    { return source().assemble(CraftingInput.of(3, 3, FireworkRocketRecipeVariants.samples(flight))); }
    public static ItemStack assemble(FireworkRocketCraftingVariant variant, int flight)
    { return variant.assemble(CraftingInput.of(3, 3, FireworkRocketRecipeVariants.samples(flight))); }
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
