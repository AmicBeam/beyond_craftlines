package com.amicbeam.beyondcraftlines.common.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class RememberedNetworkPersistenceTest
{
    @Test void memorySurvivesSaveLoadAndIsIsolatedPerPlayer()
    {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        BindingSavedData data = new BindingSavedData();
        data.rememberNetwork(alice, 12);
        data.rememberNetwork(bob, 34);
        data.rememberNetwork(alice, 56);
        BindingSavedData restored = BindingSavedData.load(data.save(new CompoundTag(), null), null);
        assertEquals(56, restored.lastUsedNetwork(alice));
        assertEquals(34, restored.lastUsedNetwork(bob));
        assertNull(restored.lastUsedNetwork(UUID.randomUUID()));
        assertTrue(restored.records().isEmpty());
    }

    @Test void oldSavesHaveNoMemoryAndInvalidEntriesAreIgnored()
    {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        assertNull(BindingSavedData.load(new CompoundTag(), null).lastUsedNetwork(alice));
        CompoundTag remembered = new CompoundTag();
        remembered.putInt("invalid-uuid", 4);
        remembered.putInt(alice.toString(), -1);
        remembered.putString(bob.toString(), "not a network id");
        CompoundTag saved = new CompoundTag();
        saved.put("last_used_networks", remembered);
        BindingSavedData restored = BindingSavedData.load(saved, null);
        assertNull(restored.lastUsedNetwork(alice));
        assertNull(restored.lastUsedNetwork(bob));
    }

    @Test void forgettingInvalidMemoryPersistsAndRepeatedUseDoesNotDirtyTheSave()
    {
        UUID player = UUID.randomUUID();
        BindingSavedData data = new BindingSavedData();
        data.rememberNetwork(player, 7);
        data.setDirty(false);
        data.rememberNetwork(player, 7);
        assertFalse(data.isDirty());
        data.forgetNetwork(player);
        assertTrue(data.isDirty());
        assertNull(BindingSavedData.load(data.save(new CompoundTag(), null), null).lastUsedNetwork(player));
    }
}
