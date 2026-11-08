package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import com.amicbeam.beyondcraftlines.compat.crafting.CraftingInput;
import net.minecraft.world.item.alchemy.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Uses real Minecraft stacks and vanilla recipe assembly, with version-specific API construction only. */
public final class DynamicCraftingFixtures
{
    private DynamicCraftingFixtures() {}
    public static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    public static com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> key(ItemStack item)
    {
        ItemStack sample = item.copy(); sample.setCount(1);
        return (com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>) java.lang.reflect.Proxy.newProxyInstance(
                DynamicCraftingFixtures.class.getClassLoader(),
                new Class<?>[]{com.wintercogs.beyonddimensions.api.storage.key.IStackKey.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTypeId" -> new ResourceLocation("stack_type", "item");
                    case "getModId" -> BuiltInRegistries.ITEM.getKey(sample.getItem()).getNamespace();
                    case "getSource" -> sample.getItem();
                    case "getReadOnlyStack" -> sample;
                    case "isEmpty" -> sample.isEmpty();
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "isSameTypeSameComponents" -> args[0] instanceof com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> other
                            && other.getReadOnlyStack() instanceof ItemStack otherStack
                            && ItemStack.isSameItemSameTags(sample, otherStack);
                    case "isSame" -> args[0] instanceof com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> other
                            && other.getReadOnlyStack() instanceof ItemStack otherStack && ItemStack.isSameItem(sample, otherStack);
                    case "serializeNBT" -> sample.save(new CompoundTag());
                    case "toString" -> sample.toString();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    public static ResourceLocation id(String value) { return new ResourceLocation(value); }
    public static FireworkRocketRecipe source() { return new FireworkRocketRecipe(new ResourceLocation("minecraft", "firework_rocket"), CraftingBookCategory.MISC); }
    public static ItemStack rocket(int flight)
    { return source().assemble(CraftingInput.of(3, 3, FireworkRocketRecipeVariants.samples(flight)), null); }
    public static ItemStack assemble(FireworkRocketCraftingVariant variant, int flight)
    { return variant.assemble(CraftingInput.of(3, 3, FireworkRocketRecipeVariants.samples(flight)), null); }
    public static boolean matches(FireworkRocketCraftingVariant variant, int flight)
    { return variant.matches(CraftingInput.of(3, 3, FireworkRocketRecipeVariants.samples(flight)), null); }
    public static ItemStack potion(boolean awkward, boolean splash)
    { return PotionUtils.setPotion(new ItemStack(splash ? Items.SPLASH_POTION : Items.POTION), awkward ? Potions.AWKWARD : Potions.WATER); }
    public static ItemStack blade(String state)
    {
        ItemStack item = new ItemStack(Items.IRON_SWORD);
        CompoundTag tag = new CompoundTag(); tag.putString("bladeState", state);
        item.setTag(tag);
        return item;
    }
}
