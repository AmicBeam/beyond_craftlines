package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class RecipeOrderJobStepAccessTest
{
    @Test
    void accessesExecutionStepsWithoutProjectingTheWholeList() throws ReflectiveOperationException
    {
        assumeTrue(classPresent("net.minecraft.resources.ResourceLocation")
                || classPresent("net.minecraft.resources.Identifier"),
                "This unit-test runtime does not include Minecraft classes");
        RecipePlan.Step first = step("first");
        RecipePlan.Step second = step("second");
        Constructor<?> jobConstructor = Arrays.stream(RecipeOrderJob.class.getDeclaredConstructors())
                .filter(value -> value.getParameterCount() == 13)
                .findFirst().orElseThrow();
        RecipeOrderJob job = (RecipeOrderJob) jobConstructor.newInstance(
                UUID.randomUUID(), UUID.randomUUID(), 1, null, 2L,
                List.of(RecipeOrderJob.StepExecution.pending(first),
                        RecipeOrderJob.StepExecution.pending(second)),
                -1, false, RecipeOrderJob.Status.QUEUED, "", 1L, 0L, List.of());

        assertEquals(2, job.stepCount());
        assertEquals(OrderOutputDestination.NETWORK, job.outputDestination());
        assertSame(first, job.step(0));
        assertSame(second, job.step(1));
    }

    @Test
    void selfIncrementExternalBatchKeepsSeedAndDecrementsOtherInputs() throws ReflectiveOperationException
    {
        assumeTrue(classPresent("net.minecraft.resources.ResourceLocation")
                || classPresent("net.minecraft.resources.Identifier"),
                "This unit-test runtime does not include Minecraft classes");
        IStackKey<?> seed = key("seed");
        IStackKey<?> other = key("other");
        Constructor<?> stepConstructor = Arrays.stream(RecipePlan.Step.class.getDeclaredConstructors())
                .filter(value -> value.getParameterCount() == 9).findFirst().orElseThrow();
        Object recipe = identifier(stepConstructor.getParameterTypes()[0], "self_increment");
        RecipePlan.Step step = (RecipePlan.Step) stepConstructor.newInstance(
                recipe, "crafting", seed, 2L, 10L,
                List.of(new RecipePlan.Material(seed, 1), new RecipePlan.Material(other, 10)),
                List.of(), List.of(), 1L);
        Constructor<?> jobConstructor = Arrays.stream(RecipeOrderJob.class.getDeclaredConstructors())
                .filter(value -> value.getParameterCount() == 13).findFirst().orElseThrow();
        RecipeOrderJob job = (RecipeOrderJob) jobConstructor.newInstance(
                UUID.randomUUID(), UUID.randomUUID(), 1, recipe, 10L,
                List.of(RecipeOrderJob.StepExecution.pending(step)), 0, false,
                RecipeOrderJob.Status.RUNNING, "", 1L, 0L, List.of());
        job = job.completeExternalBatch();

        RecipePlan.Step remaining = job.step(0);
        assertEquals(9, remaining.crafts());
        assertTrue(remaining.inputs().stream().anyMatch(input -> input.key() == seed && input.amount() == 1));
        assertTrue(remaining.inputs().stream().anyMatch(input -> input.key() == other && input.amount() == 9));
    }

    private static RecipePlan.Step step(String path) throws ReflectiveOperationException
    {
        Constructor<?> constructor = Arrays.stream(RecipePlan.Step.class.getDeclaredConstructors())
                .filter(value -> value.getParameterCount() == 8)
                .findFirst().orElseThrow();
        Class<?> idType = constructor.getParameterTypes()[0];
        Object recipe = identifier(idType, path);
        IStackKey<?> key = (IStackKey<?>) Proxy.newProxyInstance(
                IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class}, (proxy, method, args) ->
                {
                    return switch (method.getName())
                    {
                        case "isEmpty" -> false;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "test-key";
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
        return (RecipePlan.Step) constructor.newInstance(
                recipe, "crafting", key, 1L, 1L, List.of(), List.of(), List.of());
    }

    private static IStackKey<?> key(String name)
    {
        return (IStackKey<?>) Proxy.newProxyInstance(
                IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class}, (proxy, method, args) ->
                {
                    return switch (method.getName())
                    {
                        case "isEmpty" -> false;
                        case "isSame", "isSameTypeSameComponents" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> name;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    private static Object identifier(Class<?> type, String path) throws ReflectiveOperationException
    {
        try
        {
            Method factory = type.getMethod("fromNamespaceAndPath", String.class, String.class);
            return factory.invoke(null, "beyond_craftlines", path);
        }
        catch (NoSuchMethodException ignored) {}
        try
        {
            Method parse = type.getMethod("parse", String.class);
            return parse.invoke(null, "beyond_craftlines:" + path);
        }
        catch (NoSuchMethodException ignored) {}
        return type.getConstructor(String.class, String.class).newInstance("beyond_craftlines", path);
    }

    private static boolean classPresent(String name)
    {
        try
        {
            Class.forName(name, false, RecipeOrderJobStepAccessTest.class.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException ignored) { return false; }
    }
}
