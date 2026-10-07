package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class JeiRecipeSourceFingerprintTest
{
    @Test void sourceOrderDoesNotInvalidateCategoryCache()
    {
        assertEquals(JeiRecipeSourceFingerprint.fingerprint(List.of("category:test", "id:a", "id:b")),
                JeiRecipeSourceFingerprint.fingerprint(List.of("id:b", "category:test", "id:a")));
    }
    @Test void addedRemovedAndReplacedSourceIdsInvalidateTheirCategory()
    {
        String original = JeiRecipeSourceFingerprint.fingerprint(List.of("category:test", "id:a"));
        assertNotEquals(original, JeiRecipeSourceFingerprint.fingerprint(List.of("category:test")));
        assertNotEquals(original, JeiRecipeSourceFingerprint.fingerprint(List.of("category:test", "id:a", "id:b")));
        assertNotEquals(original, JeiRecipeSourceFingerprint.fingerprint(List.of("category:test", "id:b")));
        assertNotEquals(original, JeiRecipeSourceFingerprint.fingerprint(List.of("category:changed", "id:a")));
    }
    @Test void anonymousRecipeCountsStillAffectTheFingerprint()
    {
        assertNotEquals(JeiRecipeSourceFingerprint.fingerprint(List.of("class:anonymous")),
                JeiRecipeSourceFingerprint.fingerprint(List.of("class:anonymous", "class:anonymous")));
    }
}
