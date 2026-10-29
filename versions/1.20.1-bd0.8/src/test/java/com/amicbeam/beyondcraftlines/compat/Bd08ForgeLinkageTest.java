package com.amicbeam.beyondcraftlines.compat;

import com.amicbeam.beyondcraftlines.common.runtime.ProvisionerStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class Bd08ForgeLinkageTest
{
    @Test void blockEntitiesAndNewUiLinkWithoutLegacyGui() throws Exception
    {
        for (String name : new String[]{
                "com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity",
                "com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity",
                "com.amicbeam.beyondcraftlines.client.CraftlinesClientEvents$GameBus",
                "com.amicbeam.beyondcraftlines.client.bd08.CraftlinesPages"})
            assertTrue(Class.forName(name, false, getClass().getClassLoader()).getDeclaredMethods().length > 0);
    }
    @Test void admissionInsertionOverloadIsExplicitlyOverridden() throws Exception
    {
        assertEquals(ProvisionerStorage.class, ProvisionerStorage.class.getDeclaredMethod(
                "insert", IStackKey.class, long.class, boolean.class, java.util.function.Predicate.class).getDeclaringClass());
    }
    @Test void artifactRejectsTheLegacyBdFamily() throws Exception
    {
        try (var stream = getClass().getResourceAsStream("/META-INF/mods.toml"))
        {
            String metadata = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(metadata.contains("[0.8.8,0.9)"));
            assertFalse(metadata.contains("[0.7.30,0.8)"));
        }
    }
}
