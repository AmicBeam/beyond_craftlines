package com.amicbeam.beyondcraftlines.common.block;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DashboardFluidResistanceTest
{
    private static final List<String> BLOCK_REGISTRIES = List.of(
            "src/main/java/com/amicbeam/beyondcraftlines/common/init/CraftlinesBlocks.java",
            "versions/1.20.1/src/main/java/com/amicbeam/beyondcraftlines/common/init/CraftlinesBlocks.java",
            "versions/26.1.2/src/main/java/com/amicbeam/beyondcraftlines/common/init/CraftlinesBlocks.java"
    );

    @Test void dashboardBlocksFluidFlowInEverySupportedVersion() throws IOException
    {
        Path repository = findRepositoryRoot();
        assertNotNull(repository, "Could not locate the Beyond Craftlines repository root");

        for (String relativePath : BLOCK_REGISTRIES)
        {
            String source = Files.readString(repository.resolve(relativePath));
            int dashboard = source.indexOf("CRAFTLINE_DASHBOARD");
            int registrationEnd = source.indexOf("public static void register", dashboard);
            assertTrue(dashboard >= 0 && registrationEnd > dashboard,
                    () -> "Could not isolate dashboard registration in " + relativePath);
            String registration = source.substring(dashboard, registrationEnd);
            assertTrue(registration.contains(".noOcclusion().forceSolidOn()"),
                    () -> "Dashboard must remain non-occluding but block fluid flow in " + relativePath);
        }
    }

    private static Path findRepositoryRoot()
    {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null)
        {
            if (Files.isRegularFile(candidate.resolve(BLOCK_REGISTRIES.get(0)))
                    && Files.isDirectory(candidate.resolve("versions/1.20.1"))
                    && Files.isDirectory(candidate.resolve("versions/26.1.2")))
                return candidate;
            candidate = candidate.getParent();
        }
        return null;
    }
}
