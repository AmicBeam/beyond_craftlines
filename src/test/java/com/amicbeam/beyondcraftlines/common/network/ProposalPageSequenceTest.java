package com.amicbeam.beyondcraftlines.common.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProposalPageSequenceTest
{
    @Test
    void acceptsEveryPageExactlyOnceInOrder()
    {
        ProposalPageSequence sequence = new ProposalPageSequence(3, 64);
        sequence.accept(0);
        sequence.accept(1);
        assertFalse(sequence.complete());
        sequence.accept(2);
        assertTrue(sequence.complete());
        assertEquals(3, sequence.nextPage());
    }

    @Test
    void rejectsSkippedRepeatedAndOversizedSequences()
    {
        assertThrows(IllegalArgumentException.class, () -> new ProposalPageSequence(0, 64));
        assertThrows(IllegalArgumentException.class, () -> new ProposalPageSequence(65, 64));
        ProposalPageSequence skipped = new ProposalPageSequence(2, 64);
        assertThrows(IllegalArgumentException.class, () -> skipped.accept(1));
        ProposalPageSequence repeated = new ProposalPageSequence(2, 64);
        repeated.accept(0);
        assertThrows(IllegalArgumentException.class, () -> repeated.accept(0));
    }
}
