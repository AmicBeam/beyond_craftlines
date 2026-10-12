package com.amicbeam.beyondcraftlines.common.data;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ResourceFilterPersistenceTest
{
    @Test void emptyWhitelistModeSurvivesNbtRoundtrip()
    {
        ResourceFilter original = new ResourceFilter(true, List.of());
        ResourceFilter restored = ResourceFilter.load(original.save(null), null);
        assertTrue(restored.whitelist());
        assertEquals(36, restored.entries().size());
        assertTrue(restored.entries().stream().allMatch(key -> key.isEmpty()));
    }
    @Test void resourceMarkerInLastSlotRetainsIdentityAfterNbtRoundtrip()
    {
        var prototype = key("sample");
        if (com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry.getAllTypes().stream()
                .noneMatch(value -> value.getTypeId().equals(prototype.getTypeId())))
            com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry.registerType(prototype);
        ResourceFilter original = ResourceFilter.EMPTY.withEntry(35, prototype);
        ResourceFilter restored = ResourceFilter.load(original.save(null), null);
        assertFalse(restored.entries().get(35).isEmpty());
        assertTrue(com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch.exact(prototype, restored.entries().get(35)));
        assertFalse(restored.accepts(prototype));
        assertTrue(restored.entries().get(34).isEmpty());
    }

    private static com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?> key(String name)
    {
        return (com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>) java.lang.reflect.Proxy.newProxyInstance(
                com.wintercogs.beyonddimensions.api.storage.key.IStackKey.class.getClassLoader(),
                new Class<?>[]{com.wintercogs.beyonddimensions.api.storage.key.IStackKey.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTypeId" -> net.minecraft.resources.Identifier.parse("test:filter_persistence");
                    case "isEmpty" -> false;
                    case "serializeNBT" -> {
                        CompoundTag tag = new CompoundTag(); tag.putString("name", name); yield tag;
                    }
                    case "deserializeNBT" -> key(((CompoundTag) args[0]).getStringOr("name", ""));
                    case "isSame", "isSameTypeSameComponents" -> name.equals(String.valueOf(args[0]));
                    case "fromStackObject", "fromSourceObject" -> null;
                    case "hashCode" -> name.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString", "getSource" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test void nbtAndTargetOptionsSurviveSavingClearingAndSlotEdits()
    {
        ResourceFilter original = new ResourceFilter(true, false, true, List.of());
        ResourceFilter restored = ResourceFilter.load(original.save(null), null);
        assertFalse(restored.matchNbt());
        assertTrue(restored.matchOutputs());
        assertEquals(restored, restored.clear());
        assertFalse(restored.withEntry(35, null).matchNbt());
        assertTrue(restored.withEntry(35, null).matchOutputs());
        assertTrue(ResourceFilter.load(new CompoundTag(), null).matchNbt());
        assertFalse(ResourceFilter.load(new CompoundTag(), null).matchOutputs());
    }

    @Test void productModeAllowsIngredientsOfAnAcceptedRecipeAndRejectsOtherProducts()
    {
        var existingPrototype = com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry.getAllTypes().stream()
                .filter(value -> value.getTypeId().toString().equals("test:filter_persistence")).findFirst();
        if (existingPrototype.isEmpty()) com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry.registerType(key("sample"));
        ResourceFilter products = new ResourceFilter(true, true, true, List.of(key("steel")));
        assertTrue(products.acceptsRecipeInput(key("iron"), key("steel")));
        assertFalse(products.acceptsRecipeInput(key("iron"), key("copper")));
        assertTrue(products.acceptsSupply(key("iron"), null));
        ResourceFilter restored = ResourceFilter.load(products.save(null), null);
        assertTrue(restored.matchOutputs());
        assertTrue(restored.acceptsRecipeInput(key("iron"), key("steel")));
        assertFalse(products.withOptions(true, true, false).acceptsRecipeInput(key("iron"), key("steel")));
    }

    @Test void obsoleteDurabilityFlagDoesNotSelectProductMode()
    {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("match_durability", true);
        assertFalse(ResourceFilter.load(tag, null).matchOutputs());
        assertFalse(ResourceFilter.load(tag, null).save(null).contains("match_durability"));
    }

    @Test void oldSaveWithoutFilterDefaultsToEmptyBlacklist()
    {
        ResourceFilter restored = ResourceFilter.load(new CompoundTag(), null);
        assertFalse(restored.whitelist());
        assertTrue(restored.entries().stream().allMatch(key -> key.isEmpty()));
    }
}
