package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.nbt.CompoundTag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Persistent execution IDs use normalized resource data, never a source object's identity. */
public final class VirtualRecipeIdentity
{
    private static final BoundedIdentityCache<IStackKey<?>, String> KEYS = new BoundedIdentityCache<>(4096);
    private VirtualRecipeIdentity() {}
    public static String key(IStackKey<?> key) { return KEYS.computeIfAbsent(key, VirtualRecipeIdentity::encode); }
    public static void clear() { KEYS.clear(); }

    private static String encode(IStackKey<?> key)
    {
        try
        {
            var registries = com.wintercogs.beyonddimensions.util.RegistryAccessResolver.resolve();
            CompoundTag encoded = key.serializeNBT(registries);
            IStackKey<?> normalized = StackKeyRegistry.getType(key.getTypeId()).deserializeNBT(encoded, registries);
            if (normalized == null || normalized.isEmpty()) throw new IllegalArgumentException("invalid normalized resource");
            CompoundTag stable = normalized.serializeNBT(registries);
            if (stable == null || stable.isEmpty()) throw new IllegalArgumentException("empty resource encoding");
            if (!stable.equals(encoded))
            {
                IStackKey<?> second = StackKeyRegistry.getType(key.getTypeId()).deserializeNBT(stable, registries);
                if (second == null || second.isEmpty() || !stable.equals(second.serializeNBT(registries)))
                    throw new IllegalArgumentException("resource encoding does not stabilize");
            }
            // Minecraft's StringTagVisitor recursively sorts compound keys and preserves list order/types.
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(stable.toString().getBytes(StandardCharsets.UTF_8));
            return key.getTypeId() + "|" + java.util.HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        catch (RuntimeException | LinkageError exception)
        { throw new IllegalArgumentException("cannot create persistent resource identity type=" + key.getTypeId(), exception); }
    }
}
