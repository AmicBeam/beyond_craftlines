package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class MachineRecipeBundleTest
{
    private IStackKey<?> flint, oxygen, metal, product, byproduct;

    @BeforeEach void keys()
    {
        assumeTrue(classPresent("net.minecraft.resources.ResourceLocation")
                || classPresent("net.minecraft.resources.Identifier"));
        flint = key("flint"); oxygen = key("oxygen"); metal = key("metal");
        product = key("product"); byproduct = key("byproduct");
    }

    @Test void bundleRequiresOneExactMachineForEveryInputGroup()
    {
        assertEquals("overworld:1", MachineRecipeBundle.singleMachine(
                List.of(List.of("overworld:1"), List.of("overworld:1"))));
        assertNull(MachineRecipeBundle.singleMachine(List.of(List.of("overworld:1"), List.of("nether:1"))));
        assertNull(MachineRecipeBundle.singleMachine(List.of(List.of("overworld:1"), List.of("overworld:2"))));
        assertNull(MachineRecipeBundle.singleMachine(List.of(List.of("overworld:1", "overworld:2"))));
        assertNull(MachineRecipeBundle.singleMachine(List.of(List.of("overworld:1"), List.of())));
        assertNull(MachineRecipeBundle.singleMachine(List.of()));
    }

    @Test void purificationUsesFlintAndMetalWithoutNetworkOxygen() throws Exception
    {
        RecipePlan.Step producer = step(oxygen, 200, 1, List.of(material(flint, 1)), List.of(), List.of());
        RecipePlan.Step consumer = step(product, 1, 1,
                List.of(material(oxygen, 200), material(metal, 1)), List.of(0), List.of());
        MachineRecipeBundle bundle = MachineRecipeBundle.plan(0, producer, consumer, false);

        assertEquals(List.of(new KeyAmount(oxygen, 200)), bundle.retainedOutputs());
        List<RecipePlan.Material> external = bundle.externalInputs(producer, consumer);
        assertEquals(1, amount(external, flint));
        assertEquals(1, amount(external, metal));
        assertEquals(0, amount(external, oxygen));
    }

    @Test void retainsOnlyRequiredIntermediateAndLeavesOtherOutputsRecoverable() throws Exception
    {
        RecipePlan.Step producer = step(oxygen, 400, 1, List.of(material(flint, 1)), List.of(),
                List.of(new KeyAmount(byproduct, 7)));
        RecipePlan.Step consumer = step(product, 1, 1,
                List.of(material(oxygen, 200), material(metal, 1)), List.of(0), List.of());
        MachineRecipeBundle bundle = MachineRecipeBundle.plan(0, producer, consumer, false);
        assertEquals(200, bundle.retainedOutputs().getFirst().amount());
        assertTrue(bundle.retainedOutputs().stream().noneMatch(output -> output.key() == byproduct));
        assertEquals(200, 400 - bundle.retainedOutputs().getFirst().amount());
    }

    @Test void retainsSecondaryIntermediateAndDoesNotDoubleCreditDuplicateSlots() throws Exception
    {
        RecipePlan.Step producer = step(oxygen, 200, 1, List.of(material(flint, 1)), List.of(),
                List.of(new KeyAmount(byproduct, 10)));
        RecipePlan.Step consumer = step(product, 1, 1, List.of(material(oxygen, 100),
                material(oxygen, 100), material(byproduct, 15)), List.of(0), List.of());
        MachineRecipeBundle bundle = MachineRecipeBundle.plan(0, producer, consumer, false);
        assertEquals(0, amount(bundle.externalInputs(producer, consumer), oxygen));
        assertEquals(5, amount(bundle.externalInputs(producer, consumer), byproduct));
        assertEquals(2, bundle.retainedOutputs().size());
    }

    @Test void blockingConsumerCanRequireSeveralUpstreamCrafts() throws Exception
    {
        RecipePlan.Step producer = step(oxygen, 10, 60, List.of(material(flint, 60)), List.of(), List.of());
        RecipePlan.Step consumer = step(product, 1, 3,
                List.of(material(oxygen, 600), material(metal, 3)), List.of(0), List.of());
        MachineRecipeBundle bundle = MachineRecipeBundle.plan(0, producer, consumer, true);
        assertEquals(20, bundle.producerCrafts());
        assertEquals(1, bundle.consumerCrafts());
        assertEquals(20, amount(bundle.externalInputs(producer, consumer), flint));
        assertEquals(1, amount(bundle.externalInputs(producer, consumer), metal));

        RecipeOrderJob job = job(producer, consumer, true).activate(0)
                .awaitMachineBundle(1, waitFor(bundle, product));
        assertTrue(job.bundledStep(0));
        assertFalse(job.executions().get(0).complete());
        assertFalse(job.executions().get(1).complete());
        assertTrue(job.reserved().isEmpty());
        RecipeOrderJob remaining = job.completeExternalBatch().deactivate();
        assertEquals(40, remaining.step(0).crafts());
        assertEquals(40, amount(remaining.step(0).inputs(), flint));
        assertEquals(2, remaining.step(1).crafts());
        assertEquals(400, amount(remaining.step(1).inputs(), oxygen));
        assertFalse(remaining.bundledStep(0));
        assertTrue(remaining.dependenciesComplete(0));
        assertFalse(remaining.dependenciesComplete(1));
    }

    @Test void completionFinishesBothStagesWithoutInventingIntermediateReservations() throws Exception
    {
        RecipePlan.Step producer = step(oxygen, 200, 1, List.of(material(flint, 1)), List.of(), List.of());
        RecipePlan.Step consumer = step(product, 1, 1,
                List.of(material(oxygen, 200), material(metal, 1)), List.of(0), List.of());
        MachineRecipeBundle bundle = MachineRecipeBundle.plan(0, producer, consumer, false);
        RecipeOrderJob job = job(producer, consumer, false).activate(0)
                .awaitMachineBundle(1, waitFor(bundle, product));
        RecipeOrderJob restored = new RecipeOrderJob(job.id(), job.owner(), job.networkId(), job.target(),
                job.requested(), job.executions(), -1, job.blockingMode(), job.outputDestination(), job.origin(),
                job.status(), job.message(), job.createdAt(), job.finishedAt(), job.reserved());
        assertThrows(IllegalStateException.class, () -> restored.activate(0));
        assertTrue(restored.bundledStep(0));
        RecipeOrderJob complete = restored.activate(1).completeExternalBatch();
        assertEquals(RecipeOrderJob.Status.COMPLETE, complete.status());
        assertTrue(complete.executions().stream().allMatch(RecipeOrderJob.StepExecution::complete));
        assertTrue(complete.reserved().isEmpty());
    }

    @Test void rejectsUnrelatedAndIndistinguishableRecipes() throws Exception
    {
        RecipePlan.Step producer = step(oxygen, 200, 1, List.of(material(flint, 1)), List.of(), List.of());
        assertNull(MachineRecipeBundle.plan(0, producer,
                step(product, 1, 1, List.of(material(metal, 1)), List.of(0), List.of()), false));
        assertNull(MachineRecipeBundle.plan(0, producer,
                step(oxygen, 200, 1, List.of(material(oxygen, 200)), List.of(0), List.of()), false));
    }

    @Test void splittingLargeBatchesDoesNotOverflow()
    {
        assertEquals(Long.MAX_VALUE - 1,
                MachineRecipeBundle.portion(Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE));
        assertEquals(4, MachineRecipeBundle.portion(10, 1, 3));
    }

    @Test void waitUpdatesPreserveBundleAndPhysicalMachine() throws Exception
    {
        MachineRecipeBundle bundle = new MachineRecipeBundle(0, 20, 1, List.of(new KeyAmount(oxygen, 200)));
        RecipeOrderJob.ExternalWait wait = waitFor(bundle, product);
        assertSame(bundle, wait.withInputs(List.of(material(metal, 1))).bundle());
        assertSame(bundle, wait.withProgress(1, 1).bundle());
        assertSame(bundle, wait.withCollected(1).bundle());
        assertEquals(wait.occupiedMachines(), wait.withCollected(1).occupiedMachines());
    }

    private static RecipePlan.Material material(IStackKey<?> key, long amount)
    { return new RecipePlan.Material(key, amount, -1, "input"); }

    private static long amount(List<RecipePlan.Material> inputs, IStackKey<?> key)
    { return inputs.stream().filter(input -> input.key() == key).mapToLong(RecipePlan.Material::amount).sum(); }

    static RecipePlan.Step step(IStackKey<?> output, long per, long crafts, List<RecipePlan.Material> inputs,
                                List<Integer> dependencies, List<KeyAmount> byproducts) throws Exception
    {
        Constructor<?> constructor = Arrays.stream(RecipePlan.Step.class.getConstructors())
                .filter(value -> value.getParameterCount() == 10).findFirst().orElseThrow();
        return (RecipePlan.Step) constructor.newInstance(identifier(constructor.getParameterTypes()[0], "recipe"),
                "test:machine", output, per, crafts, inputs, List.of(), dependencies, 0L, byproducts);
    }

    private static RecipeOrderJob job(RecipePlan.Step producer, RecipePlan.Step consumer, boolean blocking)
    {
        return new RecipeOrderJob(UUID.randomUUID(), UUID.randomUUID(), 1, consumer.recipe(), 1,
                List.of(RecipeOrderJob.StepExecution.pending(producer), RecipeOrderJob.StepExecution.pending(consumer)),
                -1, blocking, RecipeOrderJob.Status.QUEUED, "", 1, 0, List.of());
    }

    static RecipeOrderJob.ExternalWait waitFor(MachineRecipeBundle bundle, IStackKey<?> output) throws Exception
    {
        Class<?> resourceKey = Class.forName("net.minecraft.resources.ResourceKey");
        Class<?> id = Arrays.stream(RecipePlan.Step.class.getConstructors())
                .filter(value -> value.getParameterCount() == 10).findFirst().orElseThrow().getParameterTypes()[0];
        Object dimension;
        boolean legacy = Arrays.stream(IStackKey.class.getMethods())
                .anyMatch(method -> method.getName().equals("serializeNBT") && method.getParameterCount() == 0);
        if (legacy)
        {
            // The Forge plain-JVM tests have no game bootstrap. Intern a key without loading vanilla registries.
            Method create = resourceKey.getDeclaredMethod("create", id, id);
            create.setAccessible(true);
            dimension = create.invoke(null, id.getMethod("tryParse", String.class)
                    .invoke(null, "minecraft:dimension"), identifier(id, "world"));
        }
        else
        {
            Object registry = resourceKey.getMethod("createRegistryKey", id).invoke(null,
                    id.getMethod("tryParse", String.class).invoke(null, "minecraft:dimension"));
            dimension = resourceKey.getMethod("create", resourceKey, id)
                    .invoke(null, registry, identifier(id, "world"));
        }
        Object position = Class.forName("net.minecraft.core.BlockPos").getConstructor(int.class, int.class, int.class)
                .newInstance(1, 64, 2);
        Constructor<?> location = RecipeOrderJob.MachineLocation.class.getConstructors()[0];
        Object machine = location.newInstance(dimension, position, "input");
        Constructor<?> constructor = Arrays.stream(RecipeOrderJob.ExternalWait.class.getConstructors())
                .filter(value -> value.getParameterCount() == 14).findFirst().orElseThrow();
        return (RecipeOrderJob.ExternalWait) constructor.newInstance(dimension, position, output, false, false,
                0L, 0L, 0L, 1L, 0L, List.of(), List.of(), List.of(machine), bundle);
    }

    static Object identifier(Class<?> type, String path) throws Exception
    {
        try { return type.getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, "beyond_craftlines", path); }
        catch (NoSuchMethodException ignored) {}
        try { return type.getMethod("parse", String.class).invoke(null, "beyond_craftlines:" + path); }
        catch (NoSuchMethodException ignored) {}
        return type.getConstructor(String.class, String.class).newInstance("beyond_craftlines", path);
    }

    static IStackKey<?> key(String name)
    {
        return (IStackKey<?>) Proxy.newProxyInstance(IStackKey.class.getClassLoader(), new Class<?>[]{IStackKey.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "isEmpty" -> false;
                    case "isSame", "isSameTypeSameComponents", "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static boolean classPresent(String name)
    { try { Class.forName(name); return true; } catch (ClassNotFoundException ignored) { return false; } }
}
