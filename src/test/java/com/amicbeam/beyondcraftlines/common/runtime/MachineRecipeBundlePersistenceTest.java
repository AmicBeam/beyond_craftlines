package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class MachineRecipeBundlePersistenceTest
{
    @Test void serializedWaitRestoresBundleCountsResourcesAndCoordinates() throws Exception
    {
        Map<String, IStackKey<?>> keys = new HashMap<>();
        IStackKey<?> oxygen = serializableKey("oxygen", keys);
        IStackKey<?> output = serializableKey("output", keys);
        Class.forName("com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry")
                .getMethod("registerType", IStackKey.class).invoke(null, oxygen);
        MachineRecipeBundle bundle = new MachineRecipeBundle(0, 20, 1, List.of(new KeyAmount(oxygen, 200)));
        RecipeOrderJob.ExternalWait original = MachineRecipeBundleTest.waitFor(bundle, output)
                .withInputs(List.of(new com.amicbeam.beyondcraftlines.common.crafting.RecipePlan.Material(output, 1)))
                .withProgress(1, 1);
        Object encoded = Class.forName("net.minecraft.nbt.CompoundTag").getConstructor().newInstance();
        Method write = Arrays.stream(RecipeOrderSavedData.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("writeExternalWait")).findFirst().orElseThrow();
        Method read = Arrays.stream(RecipeOrderSavedData.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("readExternalWait")).findFirst().orElseThrow();
        write.setAccessible(true); read.setAccessible(true);
        if (write.getParameterCount() == 3) write.invoke(null, encoded, original, null);
        else write.invoke(null, encoded, original);
        Object waitTag = compound(encoded, "external_wait");
        Method readBundle = Arrays.stream(RecipeOrderSavedData.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("readMachineBundle")).findFirst().orElseThrow();
        readBundle.setAccessible(true);
        MachineRecipeBundle restoredBundle = (MachineRecipeBundle) (readBundle.getParameterCount() == 2
                ? readBundle.invoke(null, waitTag, null) : readBundle.invoke(null, waitTag));
        assertEquals(bundle, restoredBundle);
        boolean legacyRuntime = Arrays.stream(IStackKey.class.getMethods())
                .anyMatch(method -> method.getName().equals("serializeNBT") && method.getParameterCount() == 0);
        if (legacyRuntime)
        {
            // Forge's unit-test runner has no world registry bootstrap; still exercise the actual NBT parser.
            waitTag.getClass().getMethod("remove", String.class).invoke(waitTag, "machine_bundle");
            assertNull(readBundle.getParameterCount() == 2 ? readBundle.invoke(null, waitTag, null)
                    : readBundle.invoke(null, waitTag));
            return;
        }
        RecipeOrderJob.ExternalWait restored = (RecipeOrderJob.ExternalWait)
                (read.getParameterCount() == 2 ? read.invoke(null, encoded, null) : read.invoke(null, encoded));
        assertEquals(original, restored);
        assertSame(oxygen, restored.bundle().retainedOutputs().getFirst().key());
        assertEquals(200, restored.bundle().retainedOutputs().getFirst().amount());

        // Old saved orders have no bundle tag and must keep their previous behavior.
        waitTag.getClass().getMethod("remove", String.class).invoke(waitTag, "machine_bundle");
        RecipeOrderJob.ExternalWait legacy = (RecipeOrderJob.ExternalWait)
                (read.getParameterCount() == 2 ? read.invoke(null, encoded, null) : read.invoke(null, encoded));
        assertNull(legacy.bundle());
        assertEquals(original.remainingInputs(), legacy.remainingInputs());
    }

    private static IStackKey<?> serializableKey(String name, Map<String, IStackKey<?>> keys) throws Exception
    {
        Class<?> idClass;
        try { idClass = Class.forName("net.minecraft.resources.ResourceLocation"); }
        catch (ClassNotFoundException ignored) { idClass = Class.forName("net.minecraft.resources.Identifier"); }
        Object type = MachineRecipeBundleTest.identifier(idClass, "bundle_test_key");
        IStackKey<?> key = (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(),
                new Class<?>[]{IStackKey.class}, (proxy, method, args) -> switch (method.getName())
                {
                    case "isEmpty" -> false;
                    case "getTypeId" -> type;
                    case "isSame", "isSameTypeSameComponents", "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "serializeNBT" -> {
                        Object tag = Class.forName("net.minecraft.nbt.CompoundTag").getConstructor().newInstance();
                        tag.getClass().getMethod("putString", String.class, String.class).invoke(tag, "name", name);
                        yield tag;
                    }
                    case "deserializeNBT" -> keys.get(string(args[0], "name"));
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        keys.put(name, key);
        return key;
    }

    private static Object compound(Object tag, String name) throws Exception
    {
        try { return tag.getClass().getMethod("getCompoundOrEmpty", String.class).invoke(tag, name); }
        catch (NoSuchMethodException ignored)
        { return tag.getClass().getMethod("getCompound", String.class).invoke(tag, name); }
    }

    private static String string(Object tag, String name) throws Exception
    {
        try { return (String) tag.getClass().getMethod("getStringOr", String.class, String.class).invoke(tag, name, ""); }
        catch (NoSuchMethodException ignored)
        { return (String) tag.getClass().getMethod("getString", String.class).invoke(tag, name); }
    }
}
