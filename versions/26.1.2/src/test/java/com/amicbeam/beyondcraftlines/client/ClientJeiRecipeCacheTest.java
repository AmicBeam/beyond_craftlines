package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse;
import com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ClientJeiRecipeCacheTest
{
    @TempDir Path directory;
    @BeforeAll static void registerType() { CacheTestKeys.register(); }
    @BeforeEach void clearBefore() { VirtualProvisionerRecipeRegistry.clear(); }
    @AfterEach void clearAfter() { VirtualProvisionerRecipeRegistry.clear(); }

    @Test void roundTripsCompleteExecutionDescriptionAndNativeInputGroups() throws Exception
    {
        var descriptor = descriptor("test:crusher", "output");
        var sources = Map.of("test:crusher", List.of("category:crusher", "id:test:a"),
                "minecraft:crafting", List.of("category:crafting", "id:test:b"));
        Path path = save(sources, List.of(descriptor));
        VirtualProvisionerRecipeRegistry.clear();
        var loaded = ClientJeiRecipeCache.loadAsync(path, RegistryAccess.EMPTY, sources);
        await(loaded);
        assertFalse(loaded.failed);
        assertEquals(sources.keySet(), loaded.result.types());
        assertEquals(Set.of("ingredients", "tool"), loaded.result.groups().get("minecraft:crafting"));
        var holder = VirtualProvisionerRecipeRegistry.find(descriptor.id()).orElseThrow();
        assertEquals(descriptor, VirtualProvisionerRecipeRegistry.descriptor(holder.value()));
        assertEquals(1, loaded.result.recipes());
    }

    @Test void onlyChangedCategoryNeedsRematerialization() throws Exception
    {
        var original = descriptor("test:crusher", "crushed");
        var unknown = (com.wintercogs.beyonddimensions.api.storage.key.IStackKey<?>) java.lang.reflect.Proxy.newProxyInstance(
                com.wintercogs.beyonddimensions.api.storage.key.IStackKey.class.getClassLoader(),
                new Class<?>[] {com.wintercogs.beyonddimensions.api.storage.key.IStackKey.class},
                (proxy, method, args) -> method.getName().equals("getTypeId")
                        ? net.minecraft.resources.Identifier.parse("removed_mod:resource")
                        : method.invoke(original.output(), args));
        var crusher = new VirtualProvisionerRecipeRegistry.Descriptor(original.family(), unknown,
                original.outputAmount(), original.inputs(), original.byproducts(), original.guaranteedByproducts());
        var mixer = descriptor("test:mixer", "mixed");
        var sources = Map.of("test:crusher", List.of("id:a"), "test:mixer", List.of("id:b"));
        Path path = save(sources, List.of(crusher, mixer));
        var loaded = ClientJeiRecipeCache.loadAsync(path, RegistryAccess.EMPTY,
                Map.of("test:crusher", List.of("id:a", "id:added"), "test:mixer", List.of("id:b")));
        await(loaded);
        assertFalse(loaded.failed);
        assertEquals(Set.of("test:mixer"), loaded.result.types());
        assertEquals(1, loaded.result.recipes());
        assertTrue(VirtualProvisionerRecipeRegistry.find(crusher.id()).isEmpty());
        assertTrue(VirtualProvisionerRecipeRegistry.find(mixer.id()).isPresent());
    }

    @Test void staleWorldReaderCannotRepopulateTheRegistry() throws Exception
    {
        var descriptor = descriptor("test:crusher", "output");
        var sources = Map.of("test:crusher", List.of("id:a"));
        Path path = save(sources, List.of(descriptor));
        var stale = new ClientJeiRecipeCache.LoadJob(path, RegistryAccess.EMPTY, sources);
        VirtualProvisionerRecipeRegistry.clear();
        stale.read();
        assertTrue(stale.cancelled);
        assertTrue(stale.result.types().isEmpty());
        assertTrue(VirtualProvisionerRecipeRegistry.recipes().isEmpty());
    }

    @Test void malformedCacheDoesNotMarkAnyCategoryComplete() throws Exception
    {
        Path path = directory.resolve("broken.dat");
        Files.writeString(path, "not gzip");
        var loaded = ClientJeiRecipeCache.loadAsync(path, RegistryAccess.EMPTY, Map.of("test:crusher", List.of("id:a")));
        await(loaded);
        assertTrue(loaded.failed);
        assertTrue(loaded.result.types().isEmpty());
    }

    @Test void corruptGzipTrailerCannotBeAcceptedAfterAllRecipesWereDecoded() throws Exception
    {
        var sources = Map.of("test:crusher", List.of("id:a"));
        Path path = save(sources, List.of(descriptor("test:crusher", "output")));
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 8] ^= 1;
        Files.write(path, bytes);
        var loaded = ClientJeiRecipeCache.loadAsync(path, RegistryAccess.EMPTY, sources);
        await(loaded);
        assertTrue(loaded.failed);
        assertTrue(loaded.result.types().isEmpty());
    }

    @Test void manualReloadRejectsAnInFlightSave() throws Exception
    {
        Path path = directory.resolve("stale.dat");
        long revision = PlanningCatalogCacheFiles.revision(path);
        PlanningCatalogCacheFiles.invalidate(path);
        ClientJeiRecipeCache.write(RegistryAccess.EMPTY, path, revision,
                Map.of("test:crusher", "fingerprint"), Set.of("test:crusher"), Map.of(), List.of());
        assertFalse(Files.exists(path));
    }

    private Path save(Map<String, List<String>> sources, List<VirtualProvisionerRecipeRegistry.Descriptor> descriptors)
    {
        Path path = directory.resolve("jei.dat");
        Map<String, String> fingerprints = new HashMap<>();
        sources.forEach((type, tokens) -> fingerprints.put(type, JeiRecipeSourceFingerprint.fingerprint(tokens)));
        ClientJeiRecipeCache.write(RegistryAccess.EMPTY, path, PlanningCatalogCacheFiles.revision(path),
                fingerprints, sources.keySet(), Map.of("minecraft:crafting", Set.of("ingredients", "tool")),
                descriptors.stream().map(value -> new VirtualProvisionerRecipeRegistry.CachedDescriptor(value.id(), value)).toList());
        assertTrue(Files.isRegularFile(path));
        return path;
    }

    private static VirtualProvisionerRecipeRegistry.Descriptor descriptor(String family, String output)
    {
        var bonus = new KeyAmount(CacheTestKeys.key("bonus"), 2L);
        return new VirtualProvisionerRecipeRegistry.Descriptor(family, CacheTestKeys.key(output), 3L,
                List.of(new VirtualProvisionerRecipeRegistry.InputSlot("tool",
                        List.of(new KeyAmount(CacheTestKeys.key("input"), 4L)), VirtualInputUse.durability(2))),
                List.of(bonus, new KeyAmount(CacheTestKeys.key("chance_output"), 1L)), List.of(bonus));
    }
    private static void await(ClientJeiRecipeCache.LoadJob job)
    { assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { while (!job.finished) Thread.sleep(1L); }); }
}
