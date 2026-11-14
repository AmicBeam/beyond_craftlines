package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.common.runtime.OrderOutputDestination;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderJob;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeSlotMetadataTest
{
    @BeforeAll static void bootstrap()
    {
        DynamicCraftingFixtures.bootstrap();
        var prototype = DynamicCraftingFixtures.key(new ItemStack(Items.PAPER));
        try { com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry.getType(prototype.getTypeId()); }
        catch (IllegalArgumentException unregistered)
        { com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry.registerType(prototype); }
    }

    @Test void virtualRecipeRetainsHolesAndShapeAffectsPersistentIdentity()
    {
        var a = DynamicCraftingFixtures.key(new ItemStack(Items.PAPER));
        var b = DynamicCraftingFixtures.key(new ItemStack(Items.GUNPOWDER));
        var output = DynamicCraftingFixtures.key(new ItemStack(Items.FIREWORK_ROCKET));
        var inputs = List.of(new VirtualProvisionerRecipeRegistry.InputSlot("ingredients", List.of(new KeyAmount(a, 1)), VirtualInputUse.CONSUMED, 0),
                new VirtualProvisionerRecipeRegistry.InputSlot("ingredients", List.of(new KeyAmount(b, 1)), VirtualInputUse.CONSUMED, 2));
        var descriptor = new VirtualProvisionerRecipeRegistry.Descriptor("create:mechanical_crafting", output, 1, inputs);
        var holder = VirtualProvisionerRecipeRegistry.register(descriptor);
        var resolved = RecipeResourceResolver.ingredients(holder.value());
        assertEquals(List.of(0, 2), resolved.stream().map(RecipeResourceResolver.ResourceIngredient::gridSlot).toList());
        assertEquals(List.of(0, 1), resolved.stream().map(RecipeResourceResolver.ResourceIngredient::slot).toList(),
                "ingredient selection indices remain separate from original grid cells");
        var compact = new VirtualProvisionerRecipeRegistry.Descriptor("create:mechanical_crafting", output, 1,
                List.of(inputs.get(0), new VirtualProvisionerRecipeRegistry.InputSlot("ingredients", List.of(new KeyAmount(b, 1)), VirtualInputUse.CONSUMED, 1)));
        assertNotEquals(descriptor.id(), compact.id());
    }

    @Test void gridOrderAutomaticallyDispatchesOneCraftAndRetainsCellsForNextCraft()
    {
        var a = DynamicCraftingFixtures.key(new ItemStack(Items.PAPER));
        var b = DynamicCraftingFixtures.key(new ItemStack(Items.GUNPOWDER));
        var output = DynamicCraftingFixtures.key(new ItemStack(Items.FIREWORK_ROCKET));
        var step = new RecipePlan.Step(DynamicCraftingFixtures.id("test:grid"), "create:mechanical_crafting", output, 1, 2,
                List.of(new RecipePlan.Material(a, 2, 0, "ingredients", 0),
                        new RecipePlan.Material(b, 2, 1, "ingredients", 2)), List.of());
        var job = RecipeOrderJob.create(UUID.randomUUID(), UUID.randomUUID(), 1, DynamicCraftingFixtures.id("minecraft:firework_rocket"),
                2, List.of(step), false, OrderOutputDestination.NETWORK, RecipeOrderJob.Status.RUNNING, "", 0, 0, List.of());
        var remaining = job.activate(0).completeExternalBatch();
        assertEquals(1, remaining.step(0).crafts());
        assertEquals(List.of(1L, 1L), remaining.step(0).inputs().stream().map(RecipePlan.Material::amount).toList());
        assertEquals(List.of(0, 2), remaining.step(0).inputs().stream().map(RecipePlan.Material::gridSlot).toList());
    }
}
