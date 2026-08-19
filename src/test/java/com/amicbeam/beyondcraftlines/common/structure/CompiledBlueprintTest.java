package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CompiledBlueprintTest
{
    @Test
    void copiesResourceListsAndKeepsMetadata()
    {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        List<ResourceAmount> inputs = List.of();
        CompiledBlueprint blueprint = new CompiledBlueprint(id, owner, "hash", List.of(), inputs,
                List.of(), 0, 200, 1, 1);

        assertEquals(id, blueprint.id());
        assertEquals(owner, blueprint.owner());
        assertEquals("hash", blueprint.structureHash());
        assertEquals(inputs, blueprint.inputs());
        assertEquals(200, blueprint.cycleTicks());
    }

    @Test
    void rejectsNonPositiveCycle()
    {
        assertThrows(IllegalArgumentException.class, () -> new CompiledBlueprint(
                UUID.randomUUID(), UUID.randomUUID(), "hash", List.of(), List.of(), List.of(),
                0, 0, 1, 1));
    }
}
