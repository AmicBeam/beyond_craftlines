package com.amicbeam.beyondcraftlines.client;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import java.lang.reflect.Proxy;

final class CacheTestKeys
{
    private static boolean registered;
    static synchronized void register()
    {
        if (!registered) { StackKeyRegistry.registerType(key("prototype")); registered = true; }
    }
    static IStackKey<?> key(String name)
    {
        return (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTypeId" -> Identifier.fromNamespaceAndPath("test", "persistent_cache_resource");
                    case "getModId" -> "test";
                    case "isEmpty" -> false;
                    case "getSource", "getReadOnlyStack", "toString" -> name;
                    case "getStackClass", "getSourceClass" -> String.class;
                    case "hashCode" -> name.hashCode();
                    case "equals", "isSame", "isSameTypeSameComponents" -> args[0] instanceof IStackKey<?> other
                            && name.equals(other.getReadOnlyStack());
                    case "serializeNBT" -> { CompoundTag tag = new CompoundTag(); tag.putString("name", name); yield tag; }
                    case "deserializeNBT" -> key(((CompoundTag) args[0]).getString("name").orElseThrow());
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
