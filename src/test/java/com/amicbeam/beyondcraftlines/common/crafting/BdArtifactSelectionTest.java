package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Prevents a successful compilation from publishing a JAR that accepts the wrong BD ABI family. */
final class BdArtifactSelectionTest
{
    @Test void dependencyRangeAndUiAdapterAgree() throws Exception
    {
        String metadata;
        var module = getClass().getModule();
        var source = module.getResourceAsStream("META-INF/neoforge.mods.toml");
        if (source == null) source = module.getResourceAsStream("META-INF/mods.toml");
        try (var stream = source)
        { metadata = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8); }
        var dependency = Pattern.compile("modId=\\\"beyonddimensions\\\".*?versionRange=\\\"([^\\\"]+)\\\"", Pattern.DOTALL).matcher(metadata);
        assertTrue(dependency.find());
        boolean modern = metadata.contains("(for BD 0.8)");
        assertEquals(modern ? "[0.8.8,0.9)" : ",0.8)",
                modern ? dependency.group(1) : dependency.group(1).substring(dependency.group(1).indexOf(',')));
        assertEquals(!modern, hasOwnResource("com/amicbeam/beyondcraftlines/mixin/bd/DimensionsNetGuiMixin.class"));
        assertEquals(modern, hasOwnResource("com/amicbeam/beyondcraftlines/client/bd08/CraftlinesPages.class"));
    }
    private boolean hasOwnResource(String path) throws Exception
    {
        try (var stream = getClass().getModule().getResourceAsStream(path)) { return stream != null; }
    }
}
