package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlueprintReferenceValidatorTest
{
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void acceptsCraftlinesUuidReferenceWithSha256Hash()
    {
        assertTrue(BlueprintReferenceValidator.isValid(
                "beyond_craftlines", UUID.randomUUID().toString(), HASH));
    }

    @Test
    void rejectsForeignNamespace()
    {
        assertFalse(BlueprintReferenceValidator.isValid(
                "minecraft", UUID.randomUUID().toString(), HASH));
    }

    @Test
    void rejectsMalformedOrEmptyReferences()
    {
        assertFalse(BlueprintReferenceValidator.isValid("beyond_craftlines", "not-a-uuid", HASH));
        assertFalse(BlueprintReferenceValidator.isValid("beyond_craftlines", UUID.randomUUID().toString(), ""));
        assertFalse(BlueprintReferenceValidator.isValid("beyond_craftlines", UUID.randomUUID().toString(), null));
        assertFalse(BlueprintReferenceValidator.isValid("beyond_craftlines", UUID.randomUUID().toString(), "not-a-sha256"));
    }
}
