package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.crafting.*;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class VirtualRecipeIdentityTest
{
    @BeforeEach void before() { CacheTestKeys.register(); VirtualRecipeIdentity.clear(); }

    @Test void sourceObjectIdentityDoesNotChangeThePersistentDescriptorId()
    {
        var first = variant("resource", false, false);
        var second = variant("resource", false, false);
        assertNotEquals(RecipeResourceResolver.resolutionKey(first), RecipeResourceResolver.resolutionKey(second));
        assertEquals(VirtualRecipeIdentity.key(first), VirtualRecipeIdentity.key(second));
        assertEquals(descriptor(first, VirtualInputUse.CONSUMED).id(), descriptor(second, VirtualInputUse.CONSUMED).id());
    }

    @Test void normalizesFieldsThatTheResourceCodecDoesNotPreserve()
    {
        assertEquals(VirtualRecipeIdentity.key(CacheTestKeys.key("resource")),
                VirtualRecipeIdentity.key(variant("resource", true, false)));
        assertNotEquals(VirtualRecipeIdentity.key(CacheTestKeys.key("resource")),
                VirtualRecipeIdentity.key(CacheTestKeys.key("different")));
    }

    @Test void encodingFailuresNeverFallBackToObjectHashOrAnEmptyIdentity()
    { assertThrows(IllegalArgumentException.class, () -> VirtualRecipeIdentity.key(variant("resource", false, true))); }

    @Test void preservesInputConsumptionSemanticsInTheId()
    {
        var key = CacheTestKeys.key("resource");
        assertNotEquals(descriptor(key, VirtualInputUse.CONSUMED).id(), descriptor(key, VirtualInputUse.REUSABLE).id());
        assertNotEquals(descriptor(key, VirtualInputUse.durability(1)).id(), descriptor(key, VirtualInputUse.durability(2)).id());
    }

    private static VirtualProvisionerRecipeRegistry.Descriptor descriptor(IStackKey<?> output, VirtualInputUse use)
    {
        return new VirtualProvisionerRecipeRegistry.Descriptor("test:machine", output, 1L,
                List.of(new VirtualProvisionerRecipeRegistry.InputSlot("input",
                        List.of(new KeyAmount(CacheTestKeys.key("raw"), 1L)), use)));
    }

    private static IStackKey<?> variant(String name, boolean transientData, boolean fail)
    {
        var base = CacheTestKeys.key(name);
        return (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getSource")) return new Object();
                    if (method.getName().equals("serializeNBT"))
                    {
                        if (fail) throw new IllegalStateException("fixture encoding failure");
                        CompoundTag tag = new CompoundTag();
                        tag.putString("name", name);
                        if (transientData) tag.putString("runtime_only", "discarded by the resource codec");
                        return tag;
                    }
                    return method.invoke(base, args);
                });
    }
}
