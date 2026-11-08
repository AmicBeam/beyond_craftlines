package com.amicbeam.beyondcraftlines.compat;

import com.amicbeam.beyondcraftlines.common.runtime.ProvisionerStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class Bd08ProvisionerStorageTest
{
    @Test void newAdmissionOverloadCannotBypassOrderOnlyStorage()
    {
        var storage = new ProvisionerStorage(() -> {});
        IStackKey<?> key = key();
        for (boolean simulate : new boolean[]{true, false})
        {
            assertEquals(64, storage.insert(key, 64, simulate).amount());
            assertEquals(64, storage.insert(key, 64, simulate, ignored -> true).amount());
            assertEquals(64, storage.insert(0, key, 64, simulate).amount());
        }
        assertEquals(0, storage.getStackByKey(key).amount());
    }

    @Test void orderInsertionAndExtractionStillWorkAndCloseTheGate()
    {
        var storage = new ProvisionerStorage(() -> {});
        IStackKey<?> key = key();
        assertEquals(0, storage.insertFromOrder(key, 64, true).amount());
        assertEquals(0, storage.getStackByKey(key).amount());
        assertEquals(0, storage.insertFromOrder(key, 64, false).amount());
        assertEquals(64, storage.getStackByKey(key).amount());
        assertEquals(8, storage.insert(key, 8, false, ignored -> true).amount());
        assertEquals(16, storage.extract(key, 16, false, false).amount());
        assertEquals(48, storage.getStackByKey(key).amount());
    }

    private static IStackKey<?> key()
    {
        return (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(),
                new Class<?>[]{IStackKey.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isEmpty" -> false;
                    case "getTypeId" -> ResourceLocation.fromNamespaceAndPath("test", "resource");
                    case "fuzzyGroup", "getSource", "getReadOnlyStack" -> proxy;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals", "isSame", "isSameTypeSameComponents" -> proxy == args[0];
                    case "toString" -> "test:resource";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
