package com.amicbeam.beyondcraftlines.common.structure;

import java.util.UUID;

public record BlueprintRecord(UUID id, UUID owner, String name, StructureSnapshot snapshot, State state,
                              CompiledBlueprint compiled) {
    public BlueprintRecord(UUID id, UUID owner, String name, StructureSnapshot snapshot, State state) {
        this(id, owner, name, snapshot, state, null);
    }

    public enum State { DRAFT, COMPILED }
}
