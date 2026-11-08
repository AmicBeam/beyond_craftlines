package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.*;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.nbt.CompoundTag;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ProducedReceiptPersistenceTest
{
    @Test void chosenOutputAndOnlyActualReceiptsSurviveRestartAndPartialExecution() throws Exception
    {
        StackKeyRegistry.registerType(key("prototype"));
        var requested = key("requested"); var declared = key("declared"); var actual = key("actual");
        var step = new RecipePlan.Step(DynamicCraftingFixtures.id("test:recipe"), "crafting", requested, 1, 2,
                List.of(), List.of(), List.of(), 0, List.of(), declared);
        var job = RecipeOrderJob.create(UUID.randomUUID(), UUID.randomUUID(), 1, DynamicCraftingFixtures.id("test:weapon"),
                2, List.of(step), false, RecipeOrderJob.Status.QUEUED, "", 1, 0, List.of()).activate(0)
                .recordProduced(actual, 1).completeCrafts(1, 20).deactivate();
        assertTrue(StackKeyMatch.exact(declared, job.step(0).executionOutputKey()));
        RecipeOrderSavedData data = new RecipeOrderSavedData(); data.put(job);
        var save = Arrays.stream(RecipeOrderSavedData.class.getMethods()).filter(method -> method.getName().equals("save") && method.getParameterTypes()[0] == CompoundTag.class).findFirst().orElseThrow();
        CompoundTag encoded = (CompoundTag) save.invoke(data, save.getParameterCount() == 1 ? new Object[]{new CompoundTag()} : new Object[]{new CompoundTag(), null});
        var load = Arrays.stream(RecipeOrderSavedData.class.getMethods()).filter(method -> method.getName().equals("load")).findFirst().orElseThrow();
        // No loader config lifecycle in the plain Forge/NeoForge JUnit harness.
        Object limit = com.amicbeam.beyondcraftlines.CraftlinesConfig.TERMINAL_ORDER_HISTORY_LIMIT;
        Class<?> type = limit.getClass(); java.lang.reflect.Field cached = null;
        while (type != null && cached == null)
        {
            try { cached = type.getDeclaredField("cachedValue"); } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        java.util.Objects.requireNonNull(cached).setAccessible(true);
        Object previous = cached.get(limit); RecipeOrderSavedData restored;
        java.lang.reflect.Field childConfig = null; Object spec = null; Object previousConfig = null;
        try
        {
            var specField = cached.getDeclaringClass().getDeclaredField("spec"); specField.setAccessible(true);
            spec = specField.get(limit);
            childConfig = spec.getClass().getDeclaredField("childConfig"); childConfig.setAccessible(true);
            previousConfig = childConfig.get(spec);
            childConfig.set(spec, Class.forName("com.electronwill.nightconfig.core.CommentedConfig").getMethod("inMemory").invoke(null));
        }
        catch (NoSuchFieldException ignored) { /* NeoForge uses the cached ConfigValue directly. */ }
        try
        {
            cached.set(limit, 20);
            restored = (RecipeOrderSavedData) load.invoke(null, load.getParameterCount() == 1 ? new Object[]{encoded} : new Object[]{encoded, null});
        }
        finally { cached.set(limit, previous); if (childConfig != null) childConfig.set(spec, previousConfig); }
        var recovered = restored.get(job.id()); assertNotNull(recovered);
        assertTrue(StackKeyMatch.exact(declared, recovered.step(0).executionOutputKey()));
        assertTrue(recovered.matchesProduced(requested, actual));
        assertFalse(recovered.matchesProduced(requested, key("foreign-weapon")));
        assertTrue(recovered.matchesFinalReceipt(actual));
        assertEquals(1, recovered.step(0).crafts());
        assertEquals(1, recovered.executions().get(0).produced().get(0).amount());
    }
    private static IStackKey<?> key(String state)
    {
        return (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTypeId" -> DynamicCraftingFixtures.id("test:receipts");
                    case "getModId" -> "test";
                    case "getSource" -> "test:weapon";
                    case "getReadOnlyStack", "toString" -> state;
                    case "isEmpty" -> false;
                    case "hashCode" -> state.hashCode();
                    case "equals", "isSameTypeSameComponents" -> args[0] instanceof IStackKey<?> other && state.equals(other.getReadOnlyStack());
                    case "isSame" -> args[0] instanceof IStackKey<?>;
                    case "serializeNBT" -> { CompoundTag tag = new CompoundTag(); tag.putString("state", state); yield tag; }
                    case "deserializeNBT" -> key(((CompoundTag) args[0]).get("state").toString().replace("\"", ""));
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
